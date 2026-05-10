package com.synapse.kb.port.in;

import com.synapse.kb.model.KnowledgeBaseId;

/**
 * 创建知识库用例。
 *
 * <p>入站端口（Driving Port），由适配器层（如 Web Controller）调用。
 */
public interface CreateKnowledgeBaseUseCase {

    /**
     * 创建新的知识库。
     *
     * @param command 创建命令
     * @return 新创建的知识库 ID
     */
    KnowledgeBaseId create(CreateKnowledgeBaseCommand command);

    /**
     * 创建知识库命令参数。
     *
     * @param name        知识库名称
     * @param description 知识库描述
     */
    record CreateKnowledgeBaseCommand(String name, String description) {
    }
}
