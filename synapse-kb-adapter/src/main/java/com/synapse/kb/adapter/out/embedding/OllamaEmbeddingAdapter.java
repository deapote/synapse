package com.synapse.kb.adapter.out.embedding;

import com.synapse.kb.port.out.EmbeddingPort;
import com.synapse.shared.exception.DomainException;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Ollama Embedding 适配器。
 */
@Component
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private final OllamaEmbeddingModel embeddingModel;
    private final Timer embeddingTimer;

    public OllamaEmbeddingAdapter(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.embedding-model:hf.co/sinequa/gme-Qwen2-VL-2B-Instruct-GGUF:Q8_0}") String modelName,
            @Value("${ollama.embedding-timeout-seconds:120}") long timeoutSeconds,
            MeterRegistry meterRegistry) {
        this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(0)
                .build();
        this.embeddingTimer = Timer.builder("synapse.embedding.batch")
                .description("Ollama embedding latency")
                .register(meterRegistry);
    }

    @Override
    @CircuitBreaker(name = "ollamaEmbedding")
    @Retry(name = "ollamaEmbedding")
    @Bulkhead(name = "ollamaEmbedding")
    public float[] embed(String text) {
        return embeddingTimer.record(() -> {
            try {
                return embeddingModel.embed(text).content().vector();
            } catch (Exception e) {
                throw new DomainException("文本向量化失败", e);
            }
        });
    }

    @Override
    @CircuitBreaker(name = "ollamaEmbedding")
    @Retry(name = "ollamaEmbedding")
    @Bulkhead(name = "ollamaEmbedding")
    public List<float[]> embed(List<String> texts) {
        return embeddingTimer.record(() -> {
            try {
                List<TextSegment> segments = texts.stream()
                        .map(TextSegment::from)
                        .toList();

                return embeddingModel.embedAll(segments).content().stream()
                        .map(embedding -> embedding.vector())
                        .toList();
            } catch (Exception e) {
                throw new DomainException("批量文本向量化失败", e);
            }
        });
    }
}
