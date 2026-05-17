package com.synapse.kb.adapter.in.worker;

import com.synapse.kb.port.in.ProcessIngestionJobUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@EnableScheduling
public class IngestionJobWorker implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(IngestionJobWorker.class);

    private final ProcessIngestionJobUseCase processUseCase;
    private final ExecutorService executorService;
    private final Semaphore permits;
    private final AtomicBoolean enabled;
    private final String workerId;
    private final Counter claimedCounter;

    public IngestionJobWorker(
            ProcessIngestionJobUseCase processUseCase,
            MeterRegistry meterRegistry,
            @Value("${synapse.ingestion.job.enabled:true}") boolean enabled,
            @Value("${synapse.ingestion.job.concurrency:2}") int concurrency) {
        this.processUseCase = processUseCase;
        int safeConcurrency = Math.max(1, concurrency);
        this.executorService = Executors.newFixedThreadPool(safeConcurrency);
        this.permits = new Semaphore(safeConcurrency);
        this.enabled = new AtomicBoolean(enabled);
        this.workerId = ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();
        this.claimedCounter = Counter.builder("synapse.ingestion.job.claimed")
                .description("Claimed ingestion jobs")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${synapse.ingestion.job.poll-delay-ms:1000}")
    public void poll() {
        if (!enabled.get()) {
            return;
        }
        while (permits.tryAcquire()) {
            executorService.execute(() -> {
                try {
                    boolean processed = processUseCase.processNextAvailable(workerId);
                    if (processed) {
                        claimedCounter.increment();
                    }
                    if (!processed) {
                        enabled.compareAndSet(true, true);
                    }
                } catch (Exception e) {
                    log.error("摄入任务 worker 执行失败", e);
                } finally {
                    permits.release();
                }
            });
        }
    }

    @Override
    public void destroy() {
        executorService.shutdownNow();
    }
}
