package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.time.Instant;

/** 用户在某个知识库下的一段聊天会话。 */
public class ChatSession {

    private final ChatSessionId id;
    private final String ownerUserId;
    private final KnowledgeBaseId knowledgeBaseId;
    private String title;
    private String summary;
    private long summarizedUntilSequence;
    private long messageCount;
    private final Instant createdAt;
    private Instant updatedAt;

    private ChatSession(
            ChatSessionId id,
            String ownerUserId,
            KnowledgeBaseId knowledgeBaseId,
            String title,
            String summary,
            long summarizedUntilSequence,
            long messageCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (id == null) {
            throw new DomainException("聊天会话编号不能为空");
        }
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new DomainException("会话所属用户不能为空");
        }
        if (knowledgeBaseId == null) {
            throw new DomainException("知识库编号不能为空");
        }
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.title = normalizeTitle(title);
        this.summary = summary == null ? "" : summary.strip();
        this.summarizedUntilSequence = Math.max(0, summarizedUntilSequence);
        this.messageCount = Math.max(0, messageCount);
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    public static ChatSession create(String ownerUserId, KnowledgeBaseId knowledgeBaseId) {
        Instant now = Instant.now();
        return new ChatSession(ChatSessionId.newId(), ownerUserId, knowledgeBaseId,
                "新对话", "", 0, 0, now, now);
    }

    public static ChatSession reconstruct(
            ChatSessionId id,
            String ownerUserId,
            KnowledgeBaseId knowledgeBaseId,
            String title,
            String summary,
            long summarizedUntilSequence,
            long messageCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new ChatSession(id, ownerUserId, knowledgeBaseId, title, summary,
                summarizedUntilSequence, messageCount, createdAt, updatedAt);
    }

    public long nextSequence() {
        messageCount++;
        updatedAt = Instant.now();
        return messageCount;
    }

    public void recordMessageSequence(long sequence) {
        if (sequence <= 0) {
            throw new DomainException("消息序号必须大于 0");
        }
        messageCount = Math.max(messageCount, sequence);
        updatedAt = Instant.now();
    }

    public void updateSummary(String summary, long summarizedUntilSequence) {
        this.summary = summary == null ? "" : summary.strip();
        this.summarizedUntilSequence = Math.max(this.summarizedUntilSequence, summarizedUntilSequence);
        this.updatedAt = Instant.now();
    }

    public void renameFromUserQuestion(String question) {
        if (!"新对话".equals(title) || question == null || question.isBlank()) {
            return;
        }
        String stripped = question.strip().replaceAll("\\s+", " ");
        title = stripped.length() <= 30 ? stripped : stripped.substring(0, 30);
        updatedAt = Instant.now();
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "新对话";
        }
        String stripped = title.strip();
        return stripped.length() <= 60 ? stripped : stripped.substring(0, 60);
    }

    public ChatSessionId getId() {
        return id;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public KnowledgeBaseId getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public long getSummarizedUntilSequence() {
        return summarizedUntilSequence;
    }

    public long getMessageCount() {
        return messageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
