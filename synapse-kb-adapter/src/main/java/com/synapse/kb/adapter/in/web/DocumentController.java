package com.synapse.kb.adapter.in.web;

import com.synapse.kb.adapter.in.web.dto.DocumentResponse;
import com.synapse.kb.model.Document;
import com.synapse.kb.model.DocumentId;
import com.synapse.kb.model.KnowledgeBaseId;
import com.synapse.kb.port.in.DeleteDocumentUseCase;
import com.synapse.kb.port.in.IngestDocumentUseCase;
import com.synapse.kb.port.in.ListDocumentUseCase;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 文档管理 Web 控制器（入站适配器）。
 *
 * <p>处理文档的上传、列表查询和删除 HTTP 请求，调用 application 层 UseCase 完成业务逻辑。
 * 文件上传使用 WebFlux 的 {@link FilePart} 接收 multipart 表单数据。
 *
 * <p>上传文档路径：{@code /api/knowledge-bases/{kbId}/documents}
 * <br>删除文档路径：{@code /api/documents/{id}}
 */
@RestController
public class DocumentController {

    private final IngestDocumentUseCase ingestUseCase;
    private final ListDocumentUseCase listUseCase;
    private final DeleteDocumentUseCase deleteUseCase;

    public DocumentController(IngestDocumentUseCase ingestUseCase,
                              ListDocumentUseCase listUseCase,
                              DeleteDocumentUseCase deleteUseCase) {
        this.ingestUseCase = ingestUseCase;
        this.listUseCase = listUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    /**
     * 上传文档到指定知识库。
     *
     * <p>流程：接收文件 → 读取字节 → 计算 MD5 哈希 → 调用摄入用例 → 返回文档信息。
     * 文件读取使用 WebFlux 响应式流，处理逻辑通过 {@code subscribeOn(Schedulers.boundedElastic())}
     * 调度到弹性线程池，避免阻塞 Netty 事件循环。
     *
     * @param kbId     目标知识库 ID
     * @param filePart 上传的文件（multipart/form-data）
     * @return 新创建的文档信息
     */
    @PostMapping(value = "/api/knowledge-bases/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentResponse> upload(
            @PathVariable String kbId,
            @RequestPart("file") FilePart filePart
    ) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(bytes -> Mono.fromCallable(() -> {
                    String contentHash = bytesToHex(MessageDigest.getInstance("MD5").digest(bytes));
                    String contentType = filePart.headers().getContentType() != null
                            ? filePart.headers().getContentType().toString()
                            : "application/octet-stream";
                    InputStream content = new ByteArrayInputStream(bytes);

                    IngestDocumentUseCase.IngestDocumentCommand command =
                            new IngestDocumentUseCase.IngestDocumentCommand(
                                    new KnowledgeBaseId(kbId),
                                    filePart.filename(),
                                    contentType,
                                    bytes.length,
                                    contentHash,
                                    content
                            );

                    DocumentId documentId = ingestUseCase.ingest(command);

                    return new DocumentResponse(
                            documentId.value(),
                            kbId,
                            filePart.filename(),
                            contentType,
                            bytes.length,
                            "PENDING",
                            0,
                            null
                    );
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 列出指定知识库下的所有文档。
     *
     * @param kbId 知识库 ID
     * @return 文档列表
     */
    @GetMapping("/api/knowledge-bases/{kbId}/documents")
    public Mono<List<DocumentResponse>> list(@PathVariable String kbId) {
        return Mono.fromCallable(() -> {
            List<Document> docs = listUseCase.listByKnowledgeBase(new KnowledgeBaseId(kbId));
            return docs.stream()
                    .map(doc -> new DocumentResponse(
                            doc.getId().value(),
                            doc.getKnowledgeBaseId().value(),
                            doc.getFileName(),
                            doc.getFileType(),
                            doc.getFileSize(),
                            doc.getStatus().name(),
                            doc.getChunkCount(),
                            doc.getUploadedAt()
                    ))
                    .toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 删除指定文档（连带清理向量库中的相关向量）。
     *
     * @param id 文档唯一标识
     * @return 空响应，删除成功后返回 200 OK
     */
    @DeleteMapping("/api/documents/{id}")
    public Mono<Void> delete(@PathVariable String id) {
        return Mono.<Void>fromCallable(() -> {
            deleteUseCase.delete(new DocumentId(id));
            return null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 将字节数组转换为十六进制字符串。
     *
     * @param bytes 原始字节数组
     * @return 十六进制字符串（小写）
     */
    private String bytesToHex(byte[] bytes) throws NoSuchAlgorithmException {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
