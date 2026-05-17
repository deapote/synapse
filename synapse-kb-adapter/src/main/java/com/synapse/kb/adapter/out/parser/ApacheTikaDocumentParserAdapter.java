package com.synapse.kb.adapter.out.parser;

import com.synapse.kb.port.out.DocumentParserPort;
import com.synapse.shared.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Apache Tika 文档解析适配器。
 */
@Component
public class ApacheTikaDocumentParserAdapter implements DocumentParserPort {

    private final AutoDetectParser parser = new AutoDetectParser();
    private final int maxParsedChars;
    private final long maxParserInputBytes;

    public ApacheTikaDocumentParserAdapter(
            @Value("${synapse.upload.max-parsed-chars:10000000}") int maxParsedChars,
            @Value("${synapse.upload.max-file-bytes:20971520}") long maxParserInputBytes) {
        this.maxParsedChars = maxParsedChars;
        this.maxParserInputBytes = Math.max(1, maxParserInputBytes);
    }

    @Override
    public String parse(InputStream inputStream, String fileName) {
        try (InputStream is = inputStream) {
            byte[] bytes = is.readNBytes(Math.toIntExact(Math.min(maxParserInputBytes + 1, Integer.MAX_VALUE)));
            if (bytes.length > maxParserInputBytes) {
                throw new DomainException("文件大小超过解析安全限制");
            }
            rejectDangerousPdfForms(bytes, fileName);

            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName == null ? "" : fileName);
            BodyContentHandler handler = new BodyContentHandler(maxParsedChars + 1);
            parser.parse(new ByteArrayInputStream(bytes), handler, metadata, parseContext());
            String text = handler.toString();
            if (text.length() > maxParsedChars) {
                throw new DomainException("文档解析后文本过长，请拆分后上传");
            }
            return text;
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            if (WriteLimitReachedException.isWriteLimitReached(e)) {
                throw new DomainException("文档解析后文本过长，请拆分后上传", e);
            }
            throw new DomainException("文档解析失败: " + safeFileName(fileName), e);
        }
    }

    private ParseContext parseContext() {
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractAcroFormContent(false);
        pdfConfig.setIfXFAExtractOnlyXFA(false);
        pdfConfig.setExtractInlineImages(false);
        pdfConfig.setExtractActions(false);
        pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);

        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, pdfConfig);
        return context;
    }

    private void rejectDangerousPdfForms(byte[] bytes, String fileName) {
        boolean maybePdf = safeFileName(fileName).toLowerCase(Locale.ROOT).endsWith(".pdf")
                || startsWithPdfHeader(bytes);
        if (!maybePdf) {
            return;
        }
        String content = new String(bytes, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        if (content.contains("/xfa") || content.contains("/acroform")) {
            throw new DomainException("PDF 包含不支持的交互表单内容，请转换为普通 PDF 后上传");
        }
    }

    private boolean startsWithPdfHeader(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unknown";
        }
        String normalized = fileName.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
}
