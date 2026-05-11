package com.synapse.kb.config;

import com.synapse.kb.port.in.*;
import com.synapse.kb.port.out.*;
import com.synapse.kb.port.service.KnowledgeBaseApplicationService;
import com.synapse.kb.repository.DocumentRepository;
import com.synapse.kb.repository.KnowledgeBaseRepository;
import com.synapse.kb.service.RecursiveChunkingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识模块 Bean 组装配置。
 *
 * <p>负责创建 application 层和 domain 层中无 Spring 注解的 Bean，
 * 将 adapter 层的各种技术实现通过构造函数注入到应用服务中。
 *
 * <p>自动扫描的 Bean（无需在此显式声明）：
 * <ul>
 *   <li>{@code MongoKnowledgeBaseRepository} — {@code @Component}</li>
 *   <li>{@code MongoDocumentRepository} — {@code @Component}</li>
 *   <li>{@code ApacheTikaDocumentParserAdapter} — {@code @Component}</li>
 *   <li>{@code OllamaEmbeddingAdapter} — {@code @Component}</li>
 *   <li>{@code OllamaLlmAdapter} — {@code @Component}</li>
 *   <li>{@code MilvusVectorStoreAdapter} — {@code @Component}</li>
 *   <li>所有 Controller — {@code @RestController}</li>
 * </ul>
 */
@Configuration
public class KnowledgeBaseBeanConfig {

    /**
     * 创建递归文本分块策略 Bean。
     *
     * <p>纯 Java 算法类，无 Spring 注解，需要手动注册为 Bean。
     */
    @Bean
    public RecursiveChunkingStrategy recursiveChunkingStrategy() {
        return new RecursiveChunkingStrategy(1000, 100);
    }

    /**
     * 创建知识库应用服务 Bean。
     *
     * <p>注入所有出站端口和仓储接口，由 Spring 自动从扫描到的 {@code @Component} 实现中解析。
     *
     * @param knowledgeBaseRepository   知识库仓储（MongoDB 实现）
     * @param documentRepository        文档仓储（MongoDB 实现）
     * @param documentParserPort        文档解析端口（Tika 实现）
     * @param recursiveChunkingStrategy 分块策略（上方手动创建的 Bean）
     * @param embeddingPort             向量化端口（Ollama 实现）
     * @param vectorStorePort           向量存储端口（Milvus 实现）
     * @return 应用服务实例
     */
    @Bean
    public KnowledgeBaseApplicationService knowledgeBaseApplicationService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentRepository documentRepository,
            DocumentParserPort documentParserPort,
            RecursiveChunkingStrategy recursiveChunkingStrategy,
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort
    ) {
        return new KnowledgeBaseApplicationService(
                knowledgeBaseRepository,
                documentRepository,
                documentParserPort,
                recursiveChunkingStrategy,
                embeddingPort,
                vectorStorePort
        );
    }
}
