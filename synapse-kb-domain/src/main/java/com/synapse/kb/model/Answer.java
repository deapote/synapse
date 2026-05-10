package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.util.List;

/**
 * LLM 回答值对象（非流式场景）。
 *
 * <p>封装完整的生成文本及其引用来源，由适配器层返回给前端展示。
 * 流式场景不走此对象，而是直接通过 SSE 逐 token 推送。
 * 作为 {@link record} 实现，天然不可变、线程安全。
 *
 * @param text       LLM 生成的完整回答文本
 * @param references 回答所引用的检索来源列表，不可为 null（可为空列表）
 */
public record Answer(
        String text,
        List<ChunkReference> references
) {

    /**
     * 紧凑构造方法：在编译器自动完成字段赋值前执行校验。
     */
    public Answer {
        if (text == null || text.isBlank()) {
            throw new DomainException("回答文本不能为空");
        }
        if (references == null) {
            throw new DomainException("引用内容不能为空");
        }
    }
}