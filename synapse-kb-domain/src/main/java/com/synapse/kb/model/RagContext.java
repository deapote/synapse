package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

import java.util.List;

/**
 * RAG 检索上下文值对象。
 *
 * <p>问答流程中，应用层检索完成后将检索结果组装为此对象返回给适配器层：
 * <ul>
 *   <li>{@code prompt}：已注入检索上下文的组装后提示词，直接送给 LLM</li>
 *   <li>{@code references}：引用来源列表，用于 SSE 最后一条事件或同步响应返回给前端</li>
 * </ul>
 * 作为 {@link record} 实现，天然不可变、线程安全。
 *
 * @param prompt     组装好的 LLM 提示词（含检索到的上下文）
 * @param references 检索到的引用来源列表，不可为 null（可为空列表）
 */
public record RagContext(
        String prompt,
        List<ChunkReference> references
) {

    /**
     * 紧凑构造方法：在编译器自动完成字段赋值前执行校验。
     */
    public RagContext {
        if (prompt == null || prompt.isBlank()) {
            throw new DomainException("提示信息不能为空");
        }
        if (references == null) {
            throw new DomainException("引用内容不能为空");
        }
    }
}