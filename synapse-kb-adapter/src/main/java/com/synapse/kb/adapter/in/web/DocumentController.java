package com.synapse.kb.adapter.in.web;

import com.synapse.kb.adapter.in.web.dto.DocumentResponse;
import com.synapse.kb.adapter.in.web.dto.SupersedeDocumentRequest;
import com.synapse.kb.adapter.in.web.dto.UpdateDocumentMetadataRequest;
import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentLifecycleStatus;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.model.DocumentSourceType;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.in.DeleteDocumentUseCase;
import com.synapse.kb.port.in.IngestDocumentUseCase;
import com.synapse.kb.port.in.ListDocumentUseCase;
import com.synapse.kb.port.in.RetryDocumentIngestionUseCase;
import com.synapse.kb.port.in.SupersedeDocumentUseCase;
import com.synapse.kb.port.in.UpdateDocumentMetadataUseCase;
import com.synapse.shared.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;

/**
 * 文档接口适配器。负责上传边界校验，并将阻塞用例调用桥接到安全的线程上下文。
 */
@RestController
public class DocumentController {

    private final IngestDocumentUseCase ingestUseCase;
    private final ListDocumentUseCase listUseCase;
    private final DeleteDocumentUseCase deleteUseCase;
    private final RetryDocumentIngestionUseCase retryUseCase;
    private final UpdateDocumentMetadataUseCase updateMetadataUseCase;
    private final SupersedeDocumentUseCase supersedeUseCase;
    private final long maxFileBytes;
    private final List<String> allowedExtensions;
    private final List<String> allowedContentTypes;
    private final int maxPageSize;

    public DocumentController(IngestDocumentUseCase ingestUseCase,
                              ListDocumentUseCase listUseCase,
                              DeleteDocumentUseCase deleteUseCase,
                              RetryDocumentIngestionUseCase retryUseCase,
                              UpdateDocumentMetadataUseCase updateMetadataUseCase,
                              SupersedeDocumentUseCase supersedeUseCase,
                              @Value("${synapse.upload.max-file-bytes:20971520}") long maxFileBytes,
                              @Value("${synapse.upload.allowed-extensions:pdf,doc,docx,txt,md}") List<String> allowedExtensions,
                              @Value("${synapse.upload.allowed-content-types:application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown}") List<String> allowedContentTypes,
                              @Value("${synapse.web.max-page-size:100}") int maxPageSize) {
        this.ingestUseCase = ingestUseCase;
        this.listUseCase = listUseCase;
        this.deleteUseCase = deleteUseCase;
        this.retryUseCase = retryUseCase;
        this.updateMetadataUseCase = updateMetadataUseCase;
        this.supersedeUseCase = supersedeUseCase;
        this.maxFileBytes = maxFileBytes;
        this.allowedExtensions = allowedExtensions;
        this.allowedContentTypes = allowedContentTypes;
        this.maxPageSize = maxPageSize;
    }

    @PostMapping(value = "/api/knowledge-bases/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentResponse> upload(
            @PathVariable String kbId,
            @RequestPart("file") FilePart filePart,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String canonicalKey,
            @RequestParam(required = false) String versionLabel,
            @RequestParam(required = false) String effectiveFrom,
            @RequestParam(required = false) String effectiveTo,
            @RequestParam(required = false) String supersedesDocumentId,
            @RequestParam(required = false) Integer authorityLevel,
            @RequestParam(required = false) String jurisdiction
    ) {
        return DataBufferUtils.join(filePart.content(), maxJoinBytes())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .onErrorMap(DataBufferLimitException.class, e -> new DomainException("文件大小超过限制", e))
                .flatMap(bytes -> SaTokenReactorBridge.blockingCall(() -> {
                    if (bytes.length <= 0 || bytes.length > maxFileBytes) {
                        throw new DomainException("文件大小超过限制");
                    }
                    String fileName = safeFileName(filePart.filename());
                    String contentType = filePart.headers().getContentType() != null
                            ? filePart.headers().getContentType().toString()
                            : "application/octet-stream";
                    validateFile(fileName, contentType);
                    String contentHash = bytesToHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                    InputStream content = new ByteArrayInputStream(bytes);

                    DocumentMetadata metadata = parseMetadata(
                            sourceType, canonicalKey, versionLabel, effectiveFrom, effectiveTo,
                            supersedesDocumentId, authorityLevel, jurisdiction);

                    IngestDocumentUseCase.IngestDocumentCommand command =
                            new IngestDocumentUseCase.IngestDocumentCommand(
                                    new KnowledgeBaseId(kbId),
                                    fileName,
                                    contentType,
                                    bytes.length,
                                    contentHash,
                                    content,
                                    metadata
                            );

                    DocumentId documentId = ingestUseCase.ingest(command);

                    return toResponse(documentId, kbId, fileName, contentType, bytes.length);
                }));
    }

    @GetMapping("/api/knowledge-bases/{kbId}/documents")
    public Mono<List<DocumentResponse>> list(
            @PathVariable String kbId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return SaTokenReactorBridge.blockingCall(() -> {
            validatePage(page, size);
            List<Document> docs = listUseCase.listByKnowledgeBase(new KnowledgeBaseId(kbId), page, size);
            return docs.stream()
                    .map(this::toResponse)
                    .toList();
        });
    }

    @DeleteMapping("/api/documents/{id}")
    public Mono<Void> delete(@PathVariable String id) {
        return SaTokenReactorBridge.blockingAction(() -> deleteUseCase.delete(new DocumentId(id)));
    }

    @PostMapping("/api/documents/{id}/retry")
    public Mono<DocumentResponse> retry(@PathVariable String id) {
        return SaTokenReactorBridge.blockingCall(() -> toResponse(retryUseCase.retry(new DocumentId(id))));
    }

    @PutMapping("/api/documents/{id}/metadata")
    public Mono<DocumentResponse> updateMetadata(
            @PathVariable String id,
            @RequestBody UpdateDocumentMetadataRequest request
    ) {
        return SaTokenReactorBridge.blockingCall(() -> {
            DocumentMetadata metadata = new DocumentMetadata(
                    request.sourceType() != null ? DocumentSourceType.valueOf(request.sourceType()) : null,
                    request.canonicalKey(),
                    request.versionLabel(),
                    request.effectiveFrom(),
                    request.effectiveTo(),
                    request.supersedesDocumentId(),
                    request.authorityLevel(),
                    request.jurisdiction()
            );
            Document document = updateMetadataUseCase.updateMetadata(new DocumentId(id), metadata);
            return toResponse(document);
        });
    }

    @PostMapping("/api/documents/{id}/supersede")
    public Mono<Void> supersede(
            @PathVariable String id,
            @RequestBody SupersedeDocumentRequest request
    ) {
        return SaTokenReactorBridge.blockingAction(() ->
                supersedeUseCase.supersede(
                        new DocumentId(id),
                        new DocumentId(request.newDocumentId()),
                        request.effectiveTo()
                ));
    }

    private DocumentMetadata parseMetadata(String sourceType, String canonicalKey, String versionLabel,
                                            String effectiveFrom, String effectiveTo, String supersedesDocumentId,
                                            Integer authorityLevel, String jurisdiction) {
        DocumentSourceType type = null;
        if (sourceType != null && !sourceType.isBlank()) {
            type = DocumentSourceType.valueOf(sourceType.toUpperCase());
        }
        LocalDate from = null;
        if (effectiveFrom != null && !effectiveFrom.isBlank()) {
            from = LocalDate.parse(effectiveFrom);
        }
        LocalDate to = null;
        if (effectiveTo != null && !effectiveTo.isBlank()) {
            to = LocalDate.parse(effectiveTo);
        }
        return new DocumentMetadata(type, canonicalKey, versionLabel, from, to, supersedesDocumentId, authorityLevel, jurisdiction);
    }

    private String bytesToHex(byte[] bytes) throws NoSuchAlgorithmException {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > maxPageSize) {
            throw new DomainException("分页参数非法");
        }
    }

    private int maxJoinBytes() {
        if (maxFileBytes > Integer.MAX_VALUE) {
            throw new DomainException("文件大小限制配置超过系统支持上限");
        }
        return (int) maxFileBytes;
    }

    private String safeFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new DomainException("文件名不能为空");
        }
        String normalized = filename.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (basename.isBlank() || ".".equals(basename) || "..".equals(basename)) {
            throw new DomainException("文件名不能为空");
        }
        return basename;
    }

    private void validateFile(String filename, String contentType) {
        String lowerName = filename == null ? "" : filename.toLowerCase();
        boolean extensionAllowed = allowedExtensions.stream()
                .map(String::trim)
                .map(ext -> ext.startsWith(".") ? ext.toLowerCase() : "." + ext.toLowerCase())
                .anyMatch(lowerName::endsWith);
        if (!extensionAllowed) {
            throw new DomainException("不支持的文件类型");
        }
        boolean contentTypeAllowed = allowedContentTypes.stream()
                .map(String::trim)
                .anyMatch(allowed -> allowed.equalsIgnoreCase(contentType));
        if (!contentTypeAllowed) {
            throw new DomainException("不支持的文件内容类型");
        }
    }

    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId().value(),
                document.getKnowledgeBaseId().value(),
                document.getFileName(),
                document.getFileType(),
                document.getFileSize(),
                document.getStatus().name(),
                document.getChunkCount(),
                document.getUploadedAt(),
                document.getSourceType() != null ? document.getSourceType().name() : null,
                document.getCanonicalKey(),
                document.getVersionLabel(),
                document.getEffectiveFrom(),
                document.getEffectiveTo(),
                document.getLifecycleStatus() != null ? document.getLifecycleStatus().name() : null,
                document.getSupersedesDocumentId(),
                document.getAuthorityLevel(),
                document.getJurisdiction()
        );
    }

    private DocumentResponse toResponse(DocumentId documentId, String kbId, String fileName,
                                         String contentType, long fileSize) {
        return new DocumentResponse(
                documentId.value(), kbId, fileName, contentType, fileSize,
                "PENDING", 0, null, null, null, null, null, null, null, null, 0, null
        );
    }
}
