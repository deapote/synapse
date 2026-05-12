package com.synapse.kb.service;

import com.synapse.kb.model.DocumentChunk;
import com.synapse.shared.exception.DomainException;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归文本分块策略。
 *
 * <p>将长文本切分为多个 {@link DocumentChunk}，用于后续向量化存储。
 * 切分策略优先保证语义完整性：段落 > 句子 > 单词 > 硬切。
 * 相邻块之间通过重叠区域保证跨边界语义不丢失。
 *
 * @see DocumentChunk
 */
public class RecursiveChunkingStrategy {

    /** 单文档最大允许字符数，防止超大文本导致 OOM（1000 万字符 ≈ 20 MB UTF-16） */
    private static final int MAX_TEXT_LENGTH = 10_000_000;

    /** 单个块的最大字符数 */
    private final int maxSegmentSize;
    /** 相邻块之间的重叠字符数，用于保留跨边界语义 */
    private final int maxOverlapSize;

    /**
     * 构造分块策略。
     *
     * @param maxSegmentSize 单个块最大字符数，必须为正数
     * @param maxOverlapSize 相邻块重叠字符数，必须为非负数且小于 maxSegmentSize
     * @throws DomainException 参数非法时抛出
     */
    public RecursiveChunkingStrategy(int maxSegmentSize, int maxOverlapSize) {
        if (maxSegmentSize <= 0) {
            throw new DomainException("最大分块字符大小必须为正数");
        }
        if (maxOverlapSize < 0 || maxOverlapSize >= maxSegmentSize) {
            throw new DomainException("最大重叠字符大小必须为非负值并且小于最大分段大小");
        }
        this.maxSegmentSize = maxSegmentSize;
        this.maxOverlapSize = maxOverlapSize;
    }

    /**
     * 将文本切分为多个带重叠的块。
     *
     * <p>切分流程：
     * <ol>
     *   <li>从 position=0 开始，计算本轮切分终点 end = position + maxSegmentSize</li>
     *   <li>在 [position, end] 范围内寻找最佳语义断点（段落/句子/单词）</li>
     *   <li>切出 text[position, breakPoint] 作为当前块</li>
     *   <li>下一轮的起点回退 overlap：position = breakPoint - maxOverlapSize</li>
     *   <li>重复直到文本末尾</li>
     * </ol>
     *
     * @param text 待切分的原始文本
     * @return 分块结果列表，空文本返回空列表
     */
    public List<DocumentChunk> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new DomainException(
                    "文本过长，无法分块：当前 " + text.length() + " 字符，最大允许 " + MAX_TEXT_LENGTH + " 字符。" +
                    "请拆分文档后重新上传。");
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        int index = 0;       // 块序号，从 0 递增
        int position = 0;    // 当前切分起点（字符位置）

        while (position < text.length()) {
            // 本轮切分终点：不能超过文本总长度
            int end = Math.min(position + maxSegmentSize, text.length());

            // 在 [position, end] 范围内找最佳断点
            int breakPoint = findBreakPoint(text, position, end);

            // 切出当前块并记录
            String chunkText = text.substring(position, breakPoint);
            chunks.add(new DocumentChunk(index, chunkText, position, breakPoint));

            index++;
            // 下一轮的起点回退 overlap 大小，保证相邻块有重叠
            int nextPosition = breakPoint - maxOverlapSize;
            // 防御：必须保证严格前进，防止死循环或无限递归
            if (nextPosition <= position) {
                nextPosition = breakPoint;
            }
            position = nextPosition;
        }
        return chunks;
    }

    /**
     * 在指定范围内寻找最佳语义断点。
     *
     * <p>优先级从高到低：
     * <ol>
     *   <li>段落分隔 {@code \n\n}</li>
     *   <li>句子结束符（.{@code !}? 后跟空格）</li>
     *   <li>单词边界（空格）</li>
     *   <li>硬切在 end 位置</li>
     * </ol>
     *
     * <p>所有断点必须满足 {@code > start + maxSegmentSize / 2}，防止切出过小的块。
     *
     * @param text  原始文本
     * @param start 切分起点（包含）
     * @param end   切分终点（不包含）
     * @return 最佳断点字符位置
     */
    public int findBreakPoint(String text, int start, int end) {
        // 已到达文本末尾，直接返回总长度
        if (end >= text.length()) {
            return text.length();
        }

        // 优先级1：找段落分隔符（从后往前找，尽量让块大）
        int paragraphBreak = text.lastIndexOf("\n\n", end);
        if (paragraphBreak > start + maxSegmentSize / 2) {
            return paragraphBreak;
        }

        // 优先级2：找句子断点
        int sentenceBreak = findSentenceBreak(text, start, end);
        if (sentenceBreak > start + maxSegmentSize / 2) {
            return sentenceBreak;
        }

        // 优先级3：找空格（单词边界），从 end 往前找，保证断点不超过 end
        int wordBreak = text.lastIndexOf(" ", end);
        if (wordBreak > start + maxSegmentSize / 2) {
            return wordBreak;
        }

        // 优先级4：实在找不到，硬切
        return end;
    }

    /**
     * 在 [start, end] 范围内从后往前找句子结束位置。
     *
     * <p>句子结束定义为 {@code .}!? 后面紧跟空白字符，
     * 排除小数点等非句子结束场景。
     *
     * @param text  原始文本
     * @param start 搜索起点（不包含）
     * @param end   搜索终点（不包含）
     * @return 句子断点位置（空格后的位置），没找到返回 -1
     */
    private int findSentenceBreak(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                // 确认后面是空白字符（排除 "3.14" 这类小数点）
                if (i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1))) {
                    return i + 1;
                }
            }
        }
        return -1;
    }
}
