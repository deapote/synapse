package com.synapse.kb.adapter.in.web;

import com.synapse.kb.adapter.in.web.dto.DocumentAuditEventResponse;
import com.synapse.kb.adapter.in.web.dto.DocumentResponse;
import com.synapse.kb.adapter.in.web.dto.RetireDocumentRequest;
import com.synapse.kb.adapter.in.web.dto.SupersedeDocumentRequest;
import com.synapse.kb.adapter.in.web.dto.UpdateDocumentMetadataRequest;
import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.DocumentIndexStatus;
import com.synapse.kb.model.DocumentLifecycleStatus;
import com.synapse.kb.model.DocumentMetadata;
import com.synapse.kb.model.DocumentSourceType;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.model.PatchDocumentMetadata;
import com.synapse.kb.model.PatchValue;
import com.synapse.kb.port.in.*;
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
    private final RetireDocumentUseCase retireUseCase;
    private final ReactivateDocumentUseCase reactivateUseCase;
    private final ReindexDocumentUseCase reindexUseCase;
    private final GetDocumentVersionChainUseCase versionChainUseCase;
    private final GetDocumentAuditEventsUseCase auditEventsUseCase;
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
                              RetireDocumentUseCase retireUseCase,
                              ReactivateDocumentUseCase reactivateUseCase,
                              ReindexDocumentUseCase reindexUseCase,
                              GetDocumentVersionChainUseCase versionChainUseCase,
                              GetDocumentAuditEventsUseCase auditEventsUseCase,
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
        this.retireUseCase = retireUseCase;
        this.reactivateUseCase = reactivateUseCase;
        this.reindexUseCase = reindexUseCase;
        this.versionChainUseCase = versionChainUseCase;
        this.auditEventsUseCase = auditEventsUseCase;
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
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String lifecycleStatus,
            @RequestParam(required = false) String indexStatus,
            @RequestParam(required = false) String canonicalKey
    ) {
        return SaTokenReactorBridge.blockingCall(() -> {
            validatePage(page, size);
            ListDocumentUseCase.ListDocumentQuery query = new ListDocumentUseCase.ListDocumentQuery(
                    new KnowledgeBaseId(kbId),
                    page,
                    size,
                    parseEnum(sourceType, DocumentSourceType.class),
                    parseEnum(lifecycleStatus, DocumentLifecycleStatus.class),
                    parseEnum(indexStatus, DocumentIndexStatus.class),
                    canonicalKey
            );
            return listUseCase.listDocuments(query).stream()
                    .map(this::toResponse)
                    .toList();
        });
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainException("非法枚举值: " + value + " (允许值: " + java.util.Arrays.toString(enumClass.getEnumConstants()) + ")");
        }
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
            PatchDocumentMetadata patch = new PatchDocumentMetadata(
                    toPatchValue(request.sourceType(), DocumentSourceType::valueOf),
                    toPatchValue(request.canonicalKey(), v -> v),
                    toPatchValue(request.versionLabel(), v -> v),
                    toPatchValue(request.effectiveFrom(), v -> v),
                    toPatchValue(request.effectiveTo(), v -> v),
                    toPatchValue(request.authorityLevel(), v -> v),
                    toPatchValue(request.jurisdiction(), v -> v)
            );
            Document document = updateMetadataUseCase.updateMetadata(new DocumentId(id), patch);
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

    @PostMapping("/api/documents/{id}/retire")
    public Mono<DocumentResponse> retire(
            @PathVariable String id,
            @RequestBody RetireDocumentRequest request
    ) {
        return SaTokenReactorBridge.blockingCall(() ->
                toResponse(retireUseCase.retire(new DocumentId(id), request.effectiveTo())));
    }

    @PostMapping("/api/documents/{id}/reactivate")
    public Mono<DocumentResponse> reactivate(@PathVariable String id) {
        return SaTokenReactorBridge.blockingCall(() ->
                toResponse(reactivateUseCase.reactivate(new DocumentId(id))));
    }

    @PostMapping("/api/documents/{id}/reindex")
    public Mono<DocumentResponse> reindex(@PathVariable String id) {
        return SaTokenReactorBridge.blockingCall(() ->
                toResponse(reindexUseCase.reindex(new DocumentId(id))));
    }

    @GetMapping("/api/documents/{id}/version-chain")
    public Mono<List<DocumentResponse>> versionChain(@PathVariable String id) {
        return SaTokenReactorBridge.blockingCall(() ->
                versionChainUseCase.getVersionChain(new DocumentId(id)).stream()
                        .map(this::toResponse)
                        .toList());
    }

    @GetMapping("/api/documents/{id}/audit-events")
    public Mono<List<DocumentAuditEventResponse>> auditEvents(@PathVariable String id) {
        return SaTokenReactorBridge.blockingCall(() ->
                auditEventsUseCase.getAuditEvents(new DocumentId(id)).stream()
                        .map(e -> new DocumentAuditEventResponse(
                                e.id(), e.documentId(), e.knowledgeBaseId(), e.actorUserId(),
                                e.action(), e.beforeSnapshot(), e.afterSnapshot(),
                                e.reason(), e.createdAt()
                        ))
                        .toList());
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
                document.getJurisdiction(),
                document.getMetadataVersion(),
                document.getIndexedMetadataVersion(),
                document.getIndexStatus() != null ? document.getIndexStatus().name() : null,
                document.getLastIndexRefreshAt(),
                document.getLastIndexFailureReason()
        );
    }

    private DocumentResponse toResponse(DocumentId documentId, String kbId, String fileName,
                                         String contentType, long fileSize) {
        return new DocumentResponse(
                documentId.value(), kbId, fileName, contentType, fileSize,
                "PENDING", 0, null, null, null, null, null, null, null, null, 0, null,
                null, null, null, null, null
        );
    }

    private static <T, R> PatchValue<R> toPatchValue(java.util.Optional<T> optional, java.util.function.Function<T, R> mapper) {
        if (optional == null) {
            return PatchValue.unset();
        }
        if (optional.isEmpty()) {
            return PatchValue.clear();
        }
        return PatchValue.set(mapper.apply(optional.get()));
    }
}
