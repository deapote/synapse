package com.synapse.kb.port.service.support;

import com.synapse.kb.model.ChunkReference;

import java.time.LocalDate;
import java.util.List;

/**
 * Prompt 上下文构建器。
 * 将检索到的 ChunkReference 按结构化 XML 格式追加到 Prompt，隔离用户问题与检索内容。
 */
public class PromptContextBuilder {
    public void appendSourceContexts(StringBuilder contextBuilder, List<ChunkReference> results, LocalDate asOfDate) {
        for (int i = 0; i < results.size(); i++) {
            ChunkReference result = results.get(i);
            contextBuilder.append("<source id=\"").append(i + 1).append("\">\n")
                    .append("<documentName>").append(escapeSourceMetadata(result.documentName())).append("</documentName>\n")
                    .append("<chunkIndex>").append(result.chunkIndex()).append("</chunkIndex>\n")
                    .append("<score>").append(String.format(java.util.Locale.ROOT, "%.4f", result.score())).append("</score>\n");
            if (result.canonicalKey() != null && !result.canonicalKey().isBlank()) {
                contextBuilder.append("<canonicalKey>").append(escapeSourceMetadata(result.canonicalKey())).append("</canonicalKey>\n");
            }
            if (result.versionLabel() != null && !result.versionLabel().isBlank()) {
                contextBuilder.append("<versionLabel>").append(escapeSourceMetadata(result.versionLabel())).append("</versionLabel>\n");
            }
            if (result.effectiveFrom() != null) {
                contextBuilder.append("<effectiveFrom>").append(result.effectiveFrom()).append("</effectiveFrom>\n");
            }
            if (result.effectiveTo() != null) {
                contextBuilder.append("<effectiveTo>").append(result.effectiveTo()).append("</effectiveTo>\n");
            }
            if (result.lifecycleStatus() != null) {
                contextBuilder.append("<lifecycleStatus>").append(result.lifecycleStatus()).append("</lifecycleStatus>\n");
            }
            if (result.authorityLevel() > 0) {
                contextBuilder.append("<authorityLevel>").append(result.authorityLevel()).append("</authorityLevel>\n");
            }
            contextBuilder.append("<chunkText>\n")
                    .append(result.chunkText())
                    .append("\n</chunkText>\n")
                    .append("</source>\n\n");
        }
    }

    private String escapeSourceMetadata(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
