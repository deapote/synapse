package com.synapse.kb.adapter.out.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.kb.port.out.DocumentParserPort;
import com.synapse.shared.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * MinerU 文档解析适配器。
 *
 * <p>MinerU 作为独立解析服务部署，本适配器只通过 REST API 调用，避免把 Python/模型依赖
 * 混入 Java 进程。默认兼容 mineru-api 的 multipart `/file_parse` 接口。</p>
 */
@Component
@ConditionalOnProperty(name = "synapse.parser.provider", havingValue = "mineru")
public class MineruDocumentParserAdapter implements DocumentParserPort {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ApacheTikaDocumentParserAdapter fallbackParser;
    private final String endpoint;
    private final String backend;
    private final String parseMethod;
    private final String language;
    private final boolean formulaEnable;
    private final boolean tableEnable;
    private final boolean fallbackEnabled;
    private final Duration timeout;
    private final int maxParsedChars;
    private final long maxParserInputBytes;

    public MineruDocumentParserAdapter(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${synapse.parser.mineru.base-url:http://localhost:8000}") String baseUrl,
            @Value("${synapse.parser.mineru.endpoint:/file_parse}") String endpoint,
            @Value("${synapse.parser.mineru.backend:pipeline}") String backend,
            @Value("${synapse.parser.mineru.parse-method:auto}") String parseMethod,
            @Value("${synapse.parser.mineru.language:ch}") String language,
            @Value("${synapse.parser.mineru.formula-enable:true}") boolean formulaEnable,
            @Value("${synapse.parser.mineru.table-enable:true}") boolean tableEnable,
            @Value("${synapse.parser.mineru.timeout-seconds:600}") long timeoutSeconds,
            @Value("${synapse.parser.mineru.fallback-to-tika:true}") boolean fallbackEnabled,
            @Value("${synapse.upload.max-parsed-chars:10000000}") int maxParsedChars,
            @Value("${synapse.upload.max-file-bytes:20971520}") long maxParserInputBytes) {
        this.webClient = webClientBuilder.baseUrl(trimTrailingSlash(baseUrl)).build();
        this.objectMapper = objectMapper;
        this.endpoint = normalizeEndpoint(endpoint);
        this.backend = backend;
        this.parseMethod = parseMethod;
        this.language = language;
        this.formulaEnable = formulaEnable;
        this.tableEnable = tableEnable;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.fallbackEnabled = fallbackEnabled;
        this.maxParsedChars = maxParsedChars;
        this.maxParserInputBytes = Math.max(1, maxParserInputBytes);
        this.fallbackParser = new ApacheTikaDocumentParserAdapter(maxParsedChars, maxParserInputBytes);
    }

    @Override
    public String parse(InputStream inputStream, String fileName) {
        byte[] bytes = readLimited(inputStream);
        String text;
        try {
            text = parseWithMineru(bytes, fileName);
            if (text.isBlank()) {
                throw new DomainException("MinerU 未解析出有效文本");
            }
        } catch (DomainException e) {
            if (!fallbackEnabled) {
                throw e;
            }
            text = fallbackParser.parse(new java.io.ByteArrayInputStream(bytes), fileName);
        } catch (Exception e) {
            if (!fallbackEnabled) {
                throw new DomainException("MinerU 文档解析失败: " + safeFileName(fileName), e);
            }
            text = fallbackParser.parse(new java.io.ByteArrayInputStream(bytes), fileName);
        }
        if (text.length() > maxParsedChars) {
            throw new DomainException("文档解析后文本过长，请拆分后上传");
        }
        return text;
    }

    private String parseWithMineru(byte[] bytes, String fileName) {
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("files", new NamedByteArrayResource(bytes, safeFileName(fileName)))
                .filename(safeFileName(fileName))
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        multipart.part("backend", backend);
        multipart.part("parse_method", parseMethod);
        multipart.part("lang_list", language);
        multipart.part("formula_enable", Boolean.toString(formulaEnable));
        multipart.part("table_enable", Boolean.toString(tableEnable));
        multipart.part("return_md", "true");
        multipart.part("return_middle_json", "false");
        multipart.part("return_content_list", "false");
        multipart.part("response_format_zip", "false");

        String response = webClient.post()
                .uri(endpoint)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(multipart.build())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new DomainException(
                                        "MinerU 服务返回异常: HTTP " + clientResponse.statusCode().value()
                                                + truncateResponseBody(body)))))
                .bodyToMono(String.class)
                .block(timeout);

        if (response == null || response.isBlank()) {
            throw new DomainException("MinerU 服务返回空响应");
        }
        return extractMarkdown(response);
    }

    private String extractMarkdown(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String text = findFirstText(root, "md_content");
            if (text == null) {
                text = findFirstText(root, "markdown");
            }
            if (text == null) {
                text = findFirstText(root, "md");
            }
            if (text != null) {
                return text;
            }
            throw new DomainException("MinerU 响应缺少 Markdown 内容");
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("MinerU 响应解析失败", e);
        }
    }

    private String findFirstText(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode direct = node.get(fieldName);
        if (direct != null && direct.isTextual() && !direct.asText().isBlank()) {
            return direct.asText();
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String text = findFirstText(field.getValue(), fieldName);
                if (text != null) {
                    return text;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String text = findFirstText(child, fieldName);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private byte[] readLimited(InputStream inputStream) {
        try (InputStream is = inputStream) {
            byte[] bytes = is.readNBytes(Math.toIntExact(Math.min(maxParserInputBytes + 1, Integer.MAX_VALUE)));
            if (bytes.length > maxParserInputBytes) {
                throw new DomainException("文件大小超过解析安全限制");
            }
            return bytes;
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("读取文档内容失败", e);
        }
    }

    private String truncateResponseBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String sanitized = body.replace('\n', ' ').replace('\r', ' ');
        int limit = 300;
        return ": " + (sanitized.length() <= limit ? sanitized : sanitized.substring(0, limit));
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unknown";
        }
        String normalized = fileName.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        return basename.isBlank() ? "unknown" : basename;
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "/file_parse";
        }
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }

    private String trimTrailingSlash(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8000" : baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
