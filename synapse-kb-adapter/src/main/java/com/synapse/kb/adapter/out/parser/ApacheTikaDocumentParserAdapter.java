package com.synapse.kb.adapter.out.parser;

import com.synapse.kb.port.out.DocumentParserPort;
import com.synapse.shared.exception.DomainException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Apache Tika 文档解析适配器。
 *
 * <p>实现 {@link DocumentParserPort}，使用 LangChain4j 封装的 Apache Tika
 * 将 PDF、Word、Excel 等二进制文档解析为纯文本。
 */
@Component
public class ApacheTikaDocumentParserAdapter implements DocumentParserPort {

    /**
     * Tika 解析器实例，复用以避免重复创建。
     */
    private final DocumentParser parser = new ApacheTikaDocumentParser();
    private final int maxParsedChars;

    public ApacheTikaDocumentParserAdapter(
            @Value("${synapse.upload.max-parsed-chars:10000000}") int maxParsedChars) {
        this.maxParsedChars = maxParsedChars;
    }

    /**
     * 解析文档为纯文本。
     *
     * @param inputStream 文档内容输入流
     * @param fileName    原始文件名（用于识别格式，当前实现不依赖它）
     * @return 解析后的纯文本内容
     */
    @Override
    public String parse(InputStream inputStream, String fileName) {
        try (InputStream is = inputStream) {
            Document document = parser.parse(is);
            String text = document.text();
            if (text != null && text.length() > maxParsedChars) {
                throw new DomainException("文档解析后文本过长，请拆分后上传");
            }
            return text != null ? text : "";
        } catch (Exception e) {
            throw new DomainException("文档解析失败: " + fileName, e);
        }
    }
}
