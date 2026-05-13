package com.synapse.kb.adapter.out.llm;

import com.synapse.kb.port.out.StreamingLlmPort;
import com.synapse.shared.exception.DomainException;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 * Ollama 流式大语言模型适配器。
 *
 * <p>实现 {@link StreamingLlmPort}，通过本地 Ollama 服务调用流式 chat 模型，
 * 将 LangChain4j 的回调式 API 桥接为 JDK {@link Stream}{@code <String>}。
 *
 * <p>取消信号传播链路：
 * <ol>
 *   <li>前端关闭 SSE 连接 → Spring WebFlux 取消 {@code Flux} 订阅</li>
 *   <li>{@code Flux} 取消时调用 {@code Stream#close()}</li>
 *   <li>{@code close()} 设置取消标志并中断 LLM 线程</li>
 *   <li>LangChain4j 回调中检测到取消后调用 {@code StreamingHandle#cancel()}</li>
 *   <li>Ollama HTTP SSE 连接被关闭，停止生成 token</li>
 * </ol>
 */
@Component
public class OllamaStreamingLlmAdapter implements StreamingLlmPort {

    /** 毒丸对象，用于标记流结束。唯一实例，不会与任何真实 token 混淆。 */
    private static final Object POISON = new Object();

    private final OllamaStreamingChatModel streamingChatModel;

    public OllamaStreamingLlmAdapter(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:qwen2.5:7b}") String modelName) {
        this.streamingChatModel = OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    /**
     * 流式生成文本片段。
     *
     * <p>内部使用 {@link BlockingQueue} 将 LangChain4j 的推送式回调
     * 桥接为 JDK {@code Stream<String>} 的拉取模型。
     *
     * @param prompt 组装好的提示词
     * @return 文本片段流
     */
    @Override
    public Stream<String> generateStream(String prompt) {
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>(1000);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<StreamingHandle> handleRef = new AtomicReference<>();

        Thread thread = new Thread(() -> {
            try {
                streamingChatModel.chat(prompt, new StreamingChatResponseHandler() {
                    /**
                     * 简版回调（无 {@link StreamingHandle}）。
                     * 若 LangChain4j 调用此重载，取消时无法直接中断 Ollama HTTP 连接，
                     * 但线程中断与有界队列满溢仍会阻止 token 继续流入前端。
                     */
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        if (cancelled.get()) {
                            return;
                        }
                        queue.offer(partialResponse);
                    }

                    @Override
                    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                        handleRef.set(context.streamingHandle());
                        if (cancelled.get()) {
                            context.streamingHandle().cancel();
                            return;
                        }
                        queue.offer(partialResponse.text());
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        queue.offer(POISON);
                    }

                    @Override
                    public void onError(Throwable error) {
                        errorRef.set(error);
                        queue.offer(POISON);
                    }
                });
            } catch (Exception e) {
                errorRef.set(e);
                queue.offer(POISON);
            }
        }, "ollama-streaming-" + System.currentTimeMillis());
        thread.setDaemon(true);
        thread.start();

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
                    if (next == POISON) {
                        next = null;
                        pendingError = errorRef.get();
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
}
