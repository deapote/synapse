package com.synapse.kb.port.in;

/**
 * 处理文档索引刷新任务的入站端口。
 */
public interface ProcessDocumentIndexRefreshJobUseCase {

    /**
     * 尝试认领并处理一个可用的索引刷新任务。
     *
     * @param workerId worker 标识
     * @return true 如果处理了一个任务，false 如果没有可用任务
     */
    boolean processNextRefreshJob(String workerId);
}
