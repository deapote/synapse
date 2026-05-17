package com.synapse.kb.port.in;

public interface ProcessIngestionJobUseCase {

    boolean processNextAvailable(String workerId);
}
