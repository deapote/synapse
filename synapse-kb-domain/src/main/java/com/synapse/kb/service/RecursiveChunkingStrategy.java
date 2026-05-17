package com.synapse.kb.service;

import com.synapse.kb.model.DocumentChunk;
import com.synapse.shared.exception.DomainException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 规则优先的语义文本分块策略。 */
public class RecursiveChunkingStrategy {

    /** 防止异常解析结果拖垮摄入任务。 */
    private static final int MAX_TEXT_LENGTH = 10_000_000;
    private static final double MIN_OVERLAP_RATIO = 0.10;
    private static final double MAX_OVERLAP_RATIO = 0.20;
    private static final Pattern MARKDOWN_BOUNDARY = Pattern.compile(
            "^\\s*(#{1,6}\\s+|[-*+]\\s+|\\d+[.)]\\s+|>\\s+|```|\\|.+\\|)\\S.*",
            Pattern.MULTILINE
    );

    private final int maxSegmentSize;
    private final int overlapSize;

    public RecursiveChunkingStrategy(int maxSegmentSize, int maxOverlapSize) {
        this(maxSegmentSize, (double) maxOverlapSize / maxSegmentSize, maxOverlapSize, maxOverlapSize);
    }

    public RecursiveChunkingStrategy(int maxSegmentSize, double overlapRatio, int minOverlapSize, int maxOverlapSize) {
        if (maxSegmentSize <= 0) {
            throw new DomainException("最大分块字符大小必须为正数");
        }
        if (overlapRatio < MIN_OVERLAP_RATIO || overlapRatio > MAX_OVERLAP_RATIO) {
            throw new DomainException("分块重叠比例必须在 0.10 到 0.20 之间");
        }
        if (minOverlapSize < 0 || maxOverlapSize < minOverlapSize || maxOverlapSize >= maxSegmentSize) {
            throw new DomainException("分块重叠字符范围无效");
        }
        this.maxSegmentSize = maxSegmentSize;
        this.overlapSize = Math.max(minOverlapSize, Math.min((int) Math.round(maxSegmentSize * overlapRatio), maxOverlapSize));
    }

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
        int index = 0;
        int position = 0;

        while (position < text.length()) {
            int end = Math.min(position + maxSegmentSize, text.length());
            int breakPoint = findBreakPoint(text, position, end);

            String chunkText = text.substring(position, breakPoint).strip();
            if (chunkText.isEmpty()) {
                position = breakPoint;
                continue;
            }
            chunks.add(new DocumentChunk(index, chunkText, position, breakPoint));

            index++;
            int nextPosition = findOverlapStart(text, position, breakPoint);
            if (nextPosition <= position) {
                nextPosition = breakPoint;
            }
            position = nextPosition;
        }
        return chunks;
    }

    public int findBreakPoint(String text, int start, int end) {
        if (end >= text.length()) {
            return text.length();
        }

        int lowerBound = start + maxSegmentSize / 2;

        int markdownBreak = findMarkdownBoundary(text, start, end);
        if (markdownBreak > lowerBound) {
            return markdownBreak;
        }

        int paragraphBreak = findParagraphBreak(text, start, end);
        if (paragraphBreak > lowerBound) {
            return paragraphBreak;
        }

        int sentenceBreak = findSentenceBreak(text, start, end);
        if (sentenceBreak > lowerBound) {
            return sentenceBreak;
        }

        int softBreak = findSoftBreak(text, start, end);
        if (softBreak > lowerBound) {
            return softBreak;
        }

        return end;
    }

    private int findOverlapStart(String text, int start, int breakPoint) {
        int overlapStart = Math.max(start, breakPoint - overlapSize);
        int sentenceBreak = findSentenceBreak(text, overlapStart, breakPoint);
        if (sentenceBreak > overlapStart && sentenceBreak < breakPoint) {
            return sentenceBreak;
        }
        int paragraphBreak = findParagraphBreak(text, overlapStart, breakPoint);
        if (paragraphBreak > overlapStart && paragraphBreak < breakPoint) {
            return paragraphBreak;
        }
        return overlapStart;
    }

    private int findMarkdownBoundary(String text, int start, int end) {
        var matcher = MARKDOWN_BOUNDARY.matcher(text);
        int best = -1;
        int searchFrom = start;
        while (matcher.find(searchFrom)) {
            if (matcher.start() >= end) {
                break;
            }
            if (matcher.start() > start) {
                best = matcher.start();
            }
            searchFrom = Math.max(matcher.end(), matcher.start() + 1);
        }
        return best;
    }

    private int findParagraphBreak(String text, int start, int end) {
        int best = -1;
        int i = start;
        while (i < end) {
            int next = text.indexOf("\n\n", i);
            if (next < 0 || next >= end) {
                break;
            }
            best = next + 2;
            i = next + 2;
        }
        return best;
    }

    private int findSentenceBreak(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (isSentenceTerminator(c)) {
                if (i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1))) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private int findSoftBreak(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == ',' || c == ';' || c == ':' || c == '，' || c == '；' || c == '：') {
                return i + 1;
            }
        }
        return -1;
    }

    private boolean isSentenceTerminator(char c) {
        return c == '.' || c == '!' || c == '?' || c == '。' || c == '！' || c == '？';
    }
}
