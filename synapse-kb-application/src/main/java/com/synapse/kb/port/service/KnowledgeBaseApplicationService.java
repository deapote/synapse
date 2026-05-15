package com.synapse.kb.port.service;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.*;
import com.synapse.kb.port.out.*;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.kb.repository.KnowledgeBaseRepository;
import com.synapse.kb.service.RecursiveChunkingStrategy;
import com.synapse.shared.exception.DomainException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库应用服务。
 *
 * <p>应用层的核心编排器，实现了所有入站端口（UseCase）。
 * 它不负责具体的技术实现（如怎么调用 Ollama、怎么存 MongoDB），
 * 而是通过<strong>出站端口</strong>（Port）把这些工作委派给适配器层。
 *
 * <p>设计原则：
 * <ul>
 *   <li>一个方法对应一个完整的业务用例（如"创建知识库"、"上传文档"、"问答检索"）</li>
 *   <li>所有外部依赖（Repository、Port）通过构造函数注入，便于测试时替换为 Mock</li>
 *   <li>领域异常（DomainException）贯穿各层，适配器层再转换为 HTTP 状态码</li>
 * </ul>
 */
public class KnowledgeBaseApplicationService implements
        CreateKnowledgeBaseUseCase,
        DeleteKnowledgeBaseUseCase,
        ListKnowledgeBaseUseCase,
        IngestDocumentUseCase,
        ListDocumentUseCase,
        DeleteDocumentUseCase,
        QueryKnowledgeBaseUseCase {

    /**
     * 知识库元数据持久化接口（领域层定义，MongoDB 适配器实现）
     */
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 文档元数据持久化接口（领域层定义，MongoDB 适配器实现）
     */
    private final DocumentRepository documentRepository;

    /**
     * 文档解析端口：把 PDF/Word 等二进制文件转成纯文本（Tika 适配器实现）
     */
    private final DocumentParserPort documentParserPort;

    /**
     * 递归文本分块策略：把长文本切成带重叠的小块（纯算法，无框架依赖）
     */
    private final RecursiveChunkingStrategy recursiveChunkingStrategy;

    /**
     * 文本向量化端口：把文本转成高维向量（Ollama 适配器实现）
     */
    private final EmbeddingPort embeddingPort;

    /**
     * 向量存储端口：存取向量数据（Milvus 适配器实现）
     */
    private final VectorStorePort vectorStorePort;

    /**
     * LLM prompt 模板，需包含两个 {@code %s} 占位符（上下文、问题）
     */
    private final String promptTemplate;

    /**
     * 向量检索 topK：返回相似度最高的文档片段数量
     */
    private final int topK;

    /**
     * 构造应用服务，所有依赖通过构造函数注入。
     *
     * @param knowledgeBaseRepository    知识库仓储
     * @param documentRepository         文档仓储
     * @param documentParserPort         文档解析端口
     * @param recursiveChunkingStrategy  分块策略
     * @param embeddingPort              向量化端口
     * @param vectorStorePort            向量存储端口
     * @param promptTemplate             LLM prompt 模板（两个 %s 占位符）
     * @param topK                       向量检索返回数量
     */
    public KnowledgeBaseApplicationService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentRepository documentRepository,
            DocumentParserPort documentParserPort,
            RecursiveChunkingStrategy recursiveChunkingStrategy,
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            String promptTemplate,
            int topK
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.documentParserPort = documentParserPort;
        this.recursiveChunkingStrategy = recursiveChunkingStrategy;
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.promptTemplate = promptTemplate;
        this.topK = topK;
    }

    // ========================== 知识库管理 ==========================

    /**
     * 创建知识库。
     *
     * <p>流程：调用领域层的 {@link KnowledgeBase#create} 工厂方法生成聚合根 → 保存到仓储。
     *
     * @param command 创建命令，包含名称和描述
     * @return 新创建的知识库 ID
     */
    @Override
    public KnowledgeBaseId create(CreateKnowledgeBaseCommand command) {
        // KnowledgeBase.create 是静态工厂方法，封装了名称校验等业务规则
        KnowledgeBase kb = KnowledgeBase.create(command.name(), command.description());
        // 保存到仓储（MongoDB 适配器负责实际的持久化）
        return knowledgeBaseRepository.save(kb).getId();
    }

    /**
     * 列出所有知识库。
     *
     * <p>直接调用仓储层的 findAll()，按创建时间倒序返回。
     *
     * @return 知识库列表
     */
    @Override
    public List<KnowledgeBase> listAll() {
        return knowledgeBaseRepository.findAll();
    }

    @Override
    public List<KnowledgeBase> listAll(int page, int size) {
        return knowledgeBaseRepository.findAll(page, size);
    }

    /**
     * 删除知识库（级联删除）。
     *
     * <p>流程：
     * <ol>
     *   <li>查出该知识库下的所有文档</li>
     *   <li>对每个文档：删除向量库中的向量 → 删除文档元数据</li>
     *   <li>删除知识库本身</li>
     * </ol>
     *
     * @param id 知识库 ID
     */
    @Override
    public void delete(KnowledgeBaseId id) {
        // 查出该知识库下的所有文档
        List<Document> documents = documentRepository.findByKnowledgeBaseId(id);
        // 级联清理：先删向量（Milvus），再删文档元数据（MongoDB）
        for (Document doc : documents) {
            // VectorStorePort 要求传入 KnowledgeBaseId 和 DocumentId 对象，不是 String
            vectorStorePort.deleteByDocumentId(id, doc.getId());
            documentRepository.deleteById(doc.getId());
        }
        // 最后删除知识库
        knowledgeBaseRepository.deleteById(id);
    }

    // ========================== 文档管理 ==========================

    /**
     * 删除单个文档。
     *
     * <p>流程：查出文档 → 删除向量 → 删除元数据。
     *
     * @param id 文档 ID
     */
    @Override
    public void delete(DocumentId id) {
        // 先查出文档，获取其 knowledgeBaseId（向量库隔离需要）
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DomainException("未找到文档: " + id.value()));
        // 删除该文档在向量库中的所有向量片段
        vectorStorePort.deleteByDocumentId(document.getKnowledgeBaseId(), id);
        // 删除文档元数据
        documentRepository.deleteById(id);
    }

    /**
     * 列出知识库下的所有文档。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 文档列表
     */
    @Override
    public List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId) {
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    public List<Document> listByKnowledgeBase(KnowledgeBaseId knowledgeBaseId, int page, int size) {
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId, page, size);
    }

    /**
     * 摄入文档（上传 + 完整处理）。
     *
     * <p>这是文档进入系统的核心入口，完成完整的处理流水线：
     * <ol>
     *   <li>去重检查（相同内容哈希不再重复上传）</li>
     *   <li>创建文档聚合根（状态为 PENDING）</li>
     *   <li>保存文档元数据到 MongoDB</li>
     *   <li>解析 → 分块 → 向量化 → 存入向量库</li>
     *   <li>更新文档状态为 COMPLETED（或 FAILED）</li>
     * </ol>
     *
     * @param command 摄入命令，包含文件信息和内容输入流
     * @return 新创建的文档 ID
     */
    @Override
    public DocumentId ingest(IngestDocumentCommand command) {
        // Step 1: 去重与失败清理。
        // 同一知识库中，查询相同哈希的所有文档记录
        List<Document> existingDocs = documentRepository.findByKnowledgeBaseIdAndContentHash(
                command.knowledgeBaseId(), command.contentHash()
        );
        for (Document existing : existingDocs) {
            // 已完成或正在处理的文档：不允许重复上传
            if (existing.getStatus() == DocumentStatus.COMPLETED || existing.getStatus() == DocumentStatus.PROCESSING) {
                throw new DomainException("此知识库已存在相同内容的文档");
            }
            // 失败文档：清理残留数据（向量 + 元数据），允许重新上传
            if (existing.getStatus() == DocumentStatus.FAILED) {
                vectorStorePort.deleteByDocumentId(existing.getKnowledgeBaseId(), existing.getId());
                documentRepository.deleteById(existing.getId());
            }
        }

        // Step 2: 创建领域对象。Document.create 是工厂方法，会自动设置状态为 PENDING
        Document document = Document.create(
                command.knowledgeBaseId(),   // KnowledgeBaseId 值对象
                command.fileName(),          // 原始文件名
                command.contentType(),       // MIME 类型（对应 Document 的 fileType）
                command.fileSize(),          // 文件大小（字节）
                command.contentHash()        // 内容哈希（MD5/SHA256），用于去重
        );

        // Step 3: 保存文档元数据（MongoDB）
        document = documentRepository.save(document);

        // Step 4: 处理文档内容（解析 → 分块 → 向量化 → 存储）
        // 注意：InputStream 只能读一次，所以处理逻辑紧跟在创建之后
        processDocument(document, command.content());

        return document.getId();
    }

    /**
     * 处理文档的核心逻辑：解析 → 分块 → 向量化 → 存储。
     *
     * <p>这是一个私有方法，封装了文档内容处理的完整流水线：
     * <ol>
     *   <li>状态流转：PENDING → PROCESSING</li>
     *   <li>调用 DocumentParserPort 把二进制文件解析为纯文本</li>
     *   <li>调用 RecursiveChunkingStrategy 把长文本切成带重叠的小块</li>
     *   <li>调用 EmbeddingPort 把每块文本转成向量</li>
     *   <li>调用 VectorStorePort 把（块 + 向量）存入 Milvus</li>
     *   <li>状态流转：PROCESSING → COMPLETED</li>
     * </ol>
     *
     * <p>任何步骤出错都会流转到 FAILED 状态，并记录失败原因。
     *
     * @param document 文档聚合根（已保存到仓储）
     * @param content  文件内容输入流
     */
    private void processDocument(Document document, InputStream content) {
        try {
            // 状态流转：PENDING → PROCESSING
            document.transitionTo(DocumentStatus.PROCESSING);
            // 先保存状态变更，让前端/监控能看到"处理中"
            documentRepository.save(document);

            // 1. 解析：PDF/Word → 纯文本（Apache Tika）
            String text = documentParserPort.parse(content, document.getFileName());

            // 2. 分块：长文本 → 带重叠的短文本片段（纯 Java 算法）
            List<DocumentChunk> chunks = recursiveChunkingStrategy.split(text);

            // 3. 向量化：每条文本 → 1536 维向量（Ollama Embedding 模型）
            // chunks.stream().map(DocumentChunk::text) 提取所有文本块的内容
            // .toList() 转成 List<String> 传给 embed 批量接口
            List<float[]> embeddings = embeddingPort.embed(
                    chunks.stream().map(DocumentChunk::text).toList()
            );

            // 4. 存储：把（文档块 + 对应向量）写入 Milvus 向量库
            // 传入 documentId 和 documentName，用于后续删除和搜索结果展示
            vectorStorePort.store(
                    document.getKnowledgeBaseId(),
                    document.getId(),
                    document.getFileName(),
                    chunks,
                    embeddings
            );

            // 5. 记录分块数量，供前端展示和监控统计
            document.setChunkCount(chunks.size());
            // 状态流转：PROCESSING → COMPLETED
            document.transitionTo(DocumentStatus.COMPLETED);

        } catch (Exception e) {
            // 状态流转：PROCESSING → FAILED，同时记录失败原因
            document.transitionTo(DocumentStatus.FAILED, e.getMessage());
            // 把异常继续抛出去，让上层（如 Controller）捕获并返回错误响应
            throw new DomainException("文档处理失败: " + e.getMessage(), e);
        } finally {
            // 无论成功还是失败，都要保存最终的文档状态
            documentRepository.save(document);
        }
    }

    // ========================== 问答检索 ==========================

    /**
     * 准备 RAG 检索上下文。
     *
     * <p>这是问答流程的核心：把用户的自然语言问题转换成向量，
     * 在向量库中检索最相似的文档片段，然后组装成 LLM 可用的 prompt。
     *
     * <p>完整流程：
     * <ol>
     *   <li>把用户问题转成向量（EmbeddingPort）</li>
     *   <li>在向量库中搜索最相似的 topK=5 个文档片段（VectorStorePort）</li>
     *   <li>把检索结果格式化为带编号的上下文文本</li>
     *   <li>组装 prompt："基于以下上下文回答问题..."</li>
     *   <li>把 prompt 和引用来源打包成 {@link RagContext} 返回</li>
     * </ol>
     *
     * <p>返回的 RagContext 交给适配器层，通过 SSE 流式逐字推送 token，最后发 references。
     *
     * @param query 用户查询，包含知识库 ID 和查询文本
     * @return 包含组装后 prompt 和引用来源的检索上下文
     */
    @Override
    public RagContext prepare(Query query) {
        // Step 1: 把用户的问题文本转成向量（1536 维浮点数组）
        float[] queryEmbedding = embeddingPort.embed(query.text());

        // Step 2: 向量相似度检索，找出最相关的 topK 个文档片段
        // Query 中的 knowledgeBaseId 已经是 KnowledgeBaseId 值对象，无需转换
        List<ChunkReference> results = vectorStorePort.search(
                query.knowledgeBaseId(),
                queryEmbedding,
                topK
        );

        // Step 3: 组装上下文文本和引用来源列表
        StringBuilder contextBuilder = new StringBuilder();
        List<ChunkReference> references = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            ChunkReference result = results.get(i);

            // 把每条结果格式化为 "[1] 片段文本\n\n" 的形式
            contextBuilder.append("[").append(i + 1).append("] ")
                    .append(result.chunkText()).append("\n\n");

            // ChunkReference 已经是引用来源对象，直接复用
            references.add(result);
        }

        // Step 4: 组装 LLM prompt（模板通过构造函数注入，便于外部配置）
        String prompt = String.format(promptTemplate,
                contextBuilder.toString(),
                query.text()
        );

        // Step 5: 返回 RagContext，包含 prompt 和引用来源
        return new RagContext(prompt, references);
    }
}
