package com.synapse.kb.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.kb.adapter.in.web.dto.CitationValidationResponse;
import com.synapse.kb.model.Query;
import com.synapse.kb.model.RagContext;
import com.synapse.kb.port.in.QueryKnowledgeBaseUseCase;
import com.synapse.kb.port.out.StreamingLlmPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StreamingQueryControllerValidationTests {

    private final StreamingQueryController controller = new StreamingQueryController(
            new NoopQueryUseCase(),
            prompt -> Stream.empty(),
            new ObjectMapper(),
            new SimpleMeterRegistry()
    );

    @Test
    void validatesLegalCitations() {
        CitationValidationResponse validation = controller.validateAnswer(
                "异常不要用来做流程控制。[1]\nfinally 块中不要 return。[2]",
                2
        );

        assertTrue(validation.trusted());
        assertEquals(List.of(1, 2), validation.usedSourceIds());
        assertTrue(validation.warnings().isEmpty());
    }

    @Test
    void rejectsUnknownCitationIds() {
        CitationValidationResponse validation = controller.validateAnswer(
                "异常处理需要区分类型。[3]",
                2
        );

        assertFalse(validation.trusted());
        assertTrue(validation.warnings().contains("回答引用了不存在的来源 [3]"));
    }

    @Test
    void rejectsFactAnswerWithoutAnyCitation() {
        CitationValidationResponse validation = controller.validateAnswer(
                "异常不要用来做流程控制。",
                2
        );

        assertFalse(validation.trusted());
        assertTrue(validation.warnings().contains("回答没有引用任何检索来源"));
    }

    @Test
    void allowsInsufficientAnswerWithoutCitation() {
        CitationValidationResponse validation = controller.validateAnswer(
                "知识库片段不足以回答该问题。",
                2
        );

        assertTrue(validation.trusted());
        assertTrue(validation.usedSourceIds().isEmpty());
        assertTrue(validation.warnings().isEmpty());
    }

    private static class NoopQueryUseCase implements QueryKnowledgeBaseUseCase {
        @Override public RagContext prepare(Query query) { return new RagContext("prompt", List.of()); }
        @Override public void complete(RagContext ragContext, String answerText) {}
    }
}
