package com.synapse.kb.port.service.support;

import com.synapse.kb.port.out.EmbeddingPort;
import com.synapse.kb.port.out.QueryRewritePort;
import com.synapse.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 查询预处理服务。
 * 对原始 Query 进行可选的 LLM 改写，并通过 embedding 余弦相似度门禁决定是否采纳改写结果。
 */
public class QueryPreparationService {
    private static final Logger log = LoggerFactory.getLogger(QueryPreparationService.class);
    private final EmbeddingPort embeddingPort;
    private final QueryRewritePort queryRewritePort;
    private final boolean queryRewriteEnabled;
    private final double queryRewriteSimilarityThreshold;

    public QueryPreparationService(EmbeddingPort embeddingPort,
                                   QueryRewritePort queryRewritePort,
                                   boolean queryRewriteEnabled,
                                   double queryRewriteSimilarityThreshold) {
        this.embeddingPort = embeddingPort;
        this.queryRewritePort = queryRewritePort;
        this.queryRewriteEnabled = queryRewriteEnabled;
        this.queryRewriteSimilarityThreshold = Math.max(0, Math.min(queryRewriteSimilarityThreshold, 1));
    }

    public PreparedQuery prepareQuery(String originalText) {
        float[] originalEmbedding = embeddingPort.embed(originalText);
        if (!queryRewriteEnabled) {
            return new PreparedQuery(originalText, originalEmbedding);
        }
        try {
            String rewritten = queryRewritePort.rewrite(originalText);
            if (rewritten == null || rewritten.isBlank() || rewritten.equals(originalText)) {
                return new PreparedQuery(originalText, originalEmbedding);
            }

            float[] rewrittenEmbedding = embeddingPort.embed(rewritten);
            double similarity = cosineSimilarity(originalEmbedding, rewrittenEmbedding);
            if (similarity < queryRewriteSimilarityThreshold) {
                log.info("Query 改写未通过相似度门禁 similarity={} threshold={}",
                        String.format("%.4f", similarity), queryRewriteSimilarityThreshold);
                return new PreparedQuery(originalText, originalEmbedding);
            }

            log.debug("Query 改写生效 similarity={}", String.format("%.4f", similarity));
            return new PreparedQuery(rewritten, rewrittenEmbedding);
        } catch (Exception e) {
            log.warn("Query 改写失败，回退原始查询: {}", e.getMessage());
            return new PreparedQuery(originalText, originalEmbedding);
        }
    }

    private double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new DomainException("向量维度不一致");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /**
     * 改写后的查询，包含生效文本与对应 embedding。
     */
    public record PreparedQuery(String effectiveText, float[] embedding) {
    }
}
