package com.synapse.kb.port.in;

/**
 * 处理文档摄入任务用例。
 * 由后台工作器调用，认领并执行下一个可用的摄入作业。
 */
public interface ProcessIngestionJobUseCase {

    boolean processNextAvailable(String workerId);
}
