package com.synapse.kb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "synapse.ingestion.job.enabled=false")
class SynapseKbBootstrapApplicationTests {

    @Test
    void contextLoads() {
    }

}
