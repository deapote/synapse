package com.synapse.kb.model;

import java.util.UUID;

/**
 * 文档索引刷新作业的领域标识值对象。
 * 封装索引刷新任务的唯一标识，确保 ID 非空且不可变。
 */
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
