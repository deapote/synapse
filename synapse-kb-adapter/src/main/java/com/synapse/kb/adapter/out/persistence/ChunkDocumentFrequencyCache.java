package com.synapse.kb.adapter.out.persistence;

import com.synapse.kb.adapter.out.persistence.entity.ChunkPostingDocument;
import com.synapse.kb.model.KnowledgeBaseId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ChunkDocumentFrequencyCache {

    private final MongoTemplate mongoTemplate;
    private final long documentFrequencyCacheTtlMillis;
    private final Map<DocumentFrequencyCacheKey, CachedLong> documentFrequencyCache = new ConcurrentHashMap<>();

    ChunkDocumentFrequencyCache(MongoTemplate mongoTemplate, long documentFrequencyCacheTtlMillis) {
        this.mongoTemplate = mongoTemplate;
        this.documentFrequencyCacheTtlMillis = documentFrequencyCacheTtlMillis;
    }

    long cachedDocumentFrequency(KnowledgeBaseId knowledgeBaseId, String token) {
        long now = System.currentTimeMillis();
        DocumentFrequencyCacheKey key = new DocumentFrequencyCacheKey(knowledgeBaseId.value(), token);
        CachedLong cached = documentFrequencyCache.get(key);
        if (cached != null && cached.expiresAtMillis > now) {
            return cached.value;
        }
        long count = mongoTemplate.count(new Query(Criteria.where("knowledgeBaseId").is(knowledgeBaseId.value())
                .and("token").is(token)), ChunkPostingDocument.class);
        documentFrequencyCache.put(key, new CachedLong(count, now + documentFrequencyCacheTtlMillis));
        return count;
    }

    void invalidate(KnowledgeBaseId knowledgeBaseId) {
        String id = knowledgeBaseId.value();
        documentFrequencyCache.keySet().removeIf(key -> key.knowledgeBaseId().equals(id));
    }

    private record DocumentFrequencyCacheKey(String knowledgeBaseId, String token) {
    }

    private record CachedLong(long value, long expiresAtMillis) {
    }
}
