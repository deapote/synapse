package com.synapse.kb.adapter.out.llm;

import com.synapse.kb.port.out.StreamingLlmPort;
import com.synapse.shared.exception.DomainException;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Ollama 流式模型适配器，将 LangChain4j 回调桥接为可取消的 JDK Stream。
 */
@Component
public class OllamaStreamingLlmAdapter implements StreamingLlmPort {

    private static final Object END_OF_STREAM = new Object();

    private final OllamaStreamingChatModel streamingChatModel;

    public OllamaStreamingLlmAdapter(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:qwen2.5:7b}") String modelName,
            @Value("${ollama.chat-timeout-seconds:120}") long timeoutSeconds) {
        this.streamingChatModel = OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    @CircuitBreaker(name = "ollamaStreaming")
    @Bulkhead(name = "ollamaStreaming")
    public Stream<String> generateStream(String prompt) {
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>(1000);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<StreamingHandle> handleRef = new AtomicReference<>();

        Thread thread = Thread.ofVirtual()
                .name("ollama-streaming-" + System.currentTimeMillis())
                .start(() -> {
                    try {
                        streamingChatModel.chat(prompt, new StreamingChatResponseHandler() {
                            @Override
                            public void onPartialResponse(String partialResponse) {
                                if (cancelled.get()) {
                                    return;
                                }
                                enqueue(queue, partialResponse, errorRef);
                            }

                            @Override
                            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                                handleRef.set(context.streamingHandle());
                                if (cancelled.get()) {
                                    context.streamingHandle().cancel();
                                    return;
                                }
                                enqueue(queue, partialResponse.text(), errorRef);
                            }

                            @Override
                            public void onCompleteResponse(ChatResponse response) {
                                enqueue(queue, END_OF_STREAM, errorRef);
                            }

                            @Override
                            public void onError(Throwable error) {
                                errorRef.set(error);
                                enqueue(queue, END_OF_STREAM, errorRef);
                            }
                        });
                    } catch (Exception e) {
                        errorRef.set(e);
                        enqueue(queue, END_OF_STREAM, errorRef);
                    }
                });

        Iterator<String> iterator = new Iterator<>() {
            Object next = null;
            Throwable pendingError = null;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }
                if (cancelled.get()) {
                    return false;
                }
                try {
                    next = queue.take();
                    if (next == END_OF_STREAM) {
                        next = null;
                        pendingError = errorRef.get();
                        if (pendingError != null) {
                            throw new DomainException("LLM 流式生成失败: " + pendingError.getMessage(), pendingError);
                        }
                        return false;
                    }
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            @Override
            public String next() {
                if (pendingError != null) {
                    throw new DomainException("LLM 流式生成失败: " + pendingError.getMessage(), pendingError);
                }
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                String result = (String) next;
                next = null;
                return result;
            }
        };

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
                false
        ).onClose(() -> {
            cancelled.set(true);
            StreamingHandle handle = handleRef.get();
            if (handle != null) {
                handle.cancel();
            }
            thread.interrupt();
        });
    }

    private static void enqueue(BlockingQueue<Object> queue, Object value, AtomicReference<Throwable> errorRef) {
        try {
            queue.put(value);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorRef.compareAndSet(null, e);
        }
    }
}
