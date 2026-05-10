package com.synapse.kb.model;

import com.synapse.shared.exception.DomainException;

/**
 * 文档分块值对象。
 *
 * <p>表示文档被切分后的单个文本片段，包含在原文中的位置信息（用于前端高亮）。
 * 作为 {@link record} 实现，天然不可变、线程安全。
 *
 * @param index         块序号，从 0 开始
 * @param text          分块后的纯文本内容
 * @param startPosition 该块在原文中的起始字符位置（含）
 * @param endPosition   该块在原文中的结束字符位置（含）
 */
public record DocumentChunk(
        int index,
        String text,
        int startPosition,
        int endPosition
) {

    /**
     * 紧凑构造方法：在编译器自动完成字段赋值前执行校验。
     *
     * <p>无需声明参数列表，编译器自动沿用 record 的组件参数。
     */
    public DocumentChunk {
        if (text == null || text.isBlank()) {
            throw new DomainException("文本块不能为空");
        }
        if (startPosition < 0 || endPosition < startPosition) {
            throw new DomainException("无效的位置范围");
        }
    }
}
