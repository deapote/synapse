package com.synapse.kb.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 聊天会话 MongoDB 文档实体，对应 ChatSession 领域模型。
 * 按 ownerUserId + knowledgeBaseId 隔离，保存会话摘要及消息计数。
 */
@Document(collection = "chat_sessions")
public class ChatSessionDocument {
    @Id
    private String id;
    private String ownerUserId;
    private String knowledgeBaseId;
    private String title;
    private String summary;
    private long summarizedUntilSequence;
    private long messageCount;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public long getSummarizedUntilSequence() {
        return summarizedUntilSequence;
    }

    public void setSummarizedUntilSequence(long summarizedUntilSequence) {
        this.summarizedUntilSequence = summarizedUntilSequence;
    }

    public long getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(long messageCount) {
        this.messageCount = messageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
