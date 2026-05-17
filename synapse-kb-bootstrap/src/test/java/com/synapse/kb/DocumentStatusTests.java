package com.synapse.kb;

import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentStatus;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentStatusTests {

    @Test
    void failedDocumentCanRetryToPending() {
        Document document = Document.create(KnowledgeBaseId.generate(), "a.txt", "text/plain", 10, "hash");
        document.attachContentObject("content-id");
        document.transitionTo(DocumentStatus.PROCESSING);
        document.transitionTo(DocumentStatus.FAILED, "boom");

        document.retry();

        assertEquals(DocumentStatus.PENDING, document.getStatus());
        assertEquals(0, document.getChunkCount());
        assertEquals(null, document.getFailureReason());
    }

    @Test
    void completedDocumentCannotRetry() {
        Document document = Document.create(KnowledgeBaseId.generate(), "a.txt", "text/plain", 10, "hash");
        document.transitionTo(DocumentStatus.PROCESSING);
        document.transitionTo(DocumentStatus.COMPLETED);

        assertThrows(DomainException.class, document::retry);
    }
}
