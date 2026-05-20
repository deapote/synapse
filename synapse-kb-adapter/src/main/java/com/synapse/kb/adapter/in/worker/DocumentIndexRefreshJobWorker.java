package com.synapse.kb.adapter.in.worker;

import com.synapse.kb.port.in.ProcessDocumentIndexRefreshJobUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DocumentIndexRefreshJobWorker implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(DocumentIndexRefreshJobWorker.class);

    private final ProcessDocumentIndexRefreshJobUseCase processUseCase;
    private final ExecutorService executorService;
    private final Semaphore permits;
    private final AtomicBoolean enabled;
    private final String workerId;
    private final Counter claimedCounter;

    public DocumentIndexRefreshJobWorker(
            ProcessDocumentIndexRefreshJobUseCase processUseCase,
            MeterRegistry meterRegistry,
            @Value("${synapse.index.refresh.enabled:true}") boolean enabled,
            @Value("${synapse.index.refresh.concurrency:2}") int concurrency,
            @Value("${synapse.index.refresh.virtual-threads:true}") boolean virtualThreads) {
        this.processUseCase = processUseCase;
        int safeConcurrency = Math.max(1, concurrency);
        this.executorService = virtualThreads
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(safeConcurrency);
        this.permits = new Semaphore(safeConcurrency);
        this.enabled = new AtomicBoolean(enabled);
        this.workerId = ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();
        this.claimedCounter = Counter.builder("synapse.index.refresh.claimed")
                .description("Claimed index refresh jobs")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${synapse.index.refresh.poll-delay-ms:2000}")
    public void poll() {
        if (!enabled.get()) {
            return;
        }
        while (permits.tryAcquire()) {
            executorService.execute(() -> {
                try {
                    boolean processed = processUseCase.processNextRefreshJob(workerId);
                    if (processed) {
                        claimedCounter.increment();
                    }
                } catch (Exception e) {
                    log.error("索引刷新任务 worker 执行失败", e);
                } finally {
                    permits.release();
                }
            });
        }
    }

    @Override
    public void destroy() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
}
