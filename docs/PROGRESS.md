# Synapse RAG 项目进度追踪

> 按实施计划逐个任务推进，完成一个勾一个。

---

## 实施计划任务清单

### Phase 1: 基础设施

- [x] **Task 1: 搭建 Maven 多模块骨架**
  - [x] 根 pom.xml 配置正确
  - [x] 6 个子模块 pom.xml 配置正确
  - [x] Maven validate 通过
  - [x] Git 初始 commit

- [x] **Task 2: synapse-shared - 共享异常**
  - [x] `DomainException.java`

### Phase 2: 领域层 (synapse-kb-domain)

- [x] **Task 3: ID 值对象**
  - [x] `KnowledgeBaseId.java`
  - [x] `DocumentId.java`

- [x] **Task 4: 文档状态枚举和实体**
  - [x] `DocumentStatus.java`
  - [x] `KnowledgeBase.java`
  - [x] `Document.java`

- [x] **Task 5: 值对象**
  - [x] `DocumentChunk.java`
  - [x] `Query.java`
  - [x] `RagContext.java`
  - [x] `Answer.java`
  - [ ] `ChunkReference.java`

- [ ] **Task 6: 领域服务和仓储接口**
  - [ ] `RecursiveChunkingStrategy.java`
  - [ ] `KnowledgeBaseRepository.java`
  - [ ] `DocumentRepository.java`
  - [ ] `IngestionException.java`

### Phase 3: 应用层 (synapse-kb-application)

- [ ] **Task 7: 入站端口（UseCase）**
  - [ ] `CreateKnowledgeBaseUseCase.java`
  - [ ] `DeleteKnowledgeBaseUseCase.java`
  - [ ] `IngestDocumentUseCase.java`
  - [ ] `ListDocumentsUseCase.java`
  - [ ] `DeleteDocumentUseCase.java`
  - [ ] `QueryKnowledgeBaseUseCase.java`

- [ ] **Task 8: 出站端口（SPI）**
  - [ ] `VectorStorePort.java`
  - [ ] `ChunkSearchResult.java`
  - [ ] `EmbeddingPort.java`
  - [ ] `LlmPort.java`
  - [ ] `DocumentParserPort.java`

- [ ] **Task 9: 应用服务实现**
  - [ ] `KnowledgeBaseApplicationService.java`

### Phase 4: 适配器层 (synapse-kb-adapter)

- [ ] **Task 10: MongoDB 实体和仓储实现**
  - [ ] `KnowledgeBaseDocument.java`
  - [ ] `DocumentDocument.java`
  - [ ] `MongoKnowledgeBaseRepository.java`
  - [ ] `MongoDocumentRepository.java`

- [ ] **Task 11: 出站适配器实现**
  - [ ] `ApacheTikaDocumentParserAdapter.java`
  - [ ] `OllamaEmbeddingAdapter.java`
  - [ ] `OllamaLlmAdapter.java`
  - [ ] `StreamingLlmService.java`
  - [ ] `MilvusVectorStoreAdapter.java`

- [ ] **Task 12: Web Controller**
  - [ ] `KnowledgeBaseController.java`
  - [ ] `DocumentController.java`
  - [ ] `QueryController.java`
  - [ ] DTO 类

### Phase 5: 配置与启动

- [ ] **Task 13: Bean 组装**
  - [ ] `KnowledgeBaseBeanConfig.java`

- [ ] **Task 14: 启动类和配置**
  - [ ] `SynapseApplication.java`
  - [ ] `application.yml`

### Phase 6: 验证

- [ ] **Task 15: 验证编译和启动**
  - [ ] `mvn clean compile` 通过
  - [ ] `mvn spring-boot:run` 启动成功
  - [ ] 端点测试通过

---

## 当前进度

**已完成:** 0 / 15 任务

**最近更新:** 2026-05-10

**下一步:** Task 1 - 搭建 Maven 多模块骨架（已完成，待 commit）
