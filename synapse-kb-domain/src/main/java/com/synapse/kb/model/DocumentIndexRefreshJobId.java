package com.synapse.kb.model;

import java.util.UUID;

public record DocumentIndexRefreshJobId(String value) {

    public DocumentIndexRefreshJobId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("索引刷新任务ID不能为空");
        }
    }

    public static DocumentIndexRefreshJobId generate() {
        return new DocumentIndexRefreshJobId(UUID.randomUUID().toString());
    }
}
