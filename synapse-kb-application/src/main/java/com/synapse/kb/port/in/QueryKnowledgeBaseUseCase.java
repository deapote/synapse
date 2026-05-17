package com.synapse.kb.port.in;

import com.synapse.kb.model.Query;
import com.synapse.kb.model.RagContext;

/** 准备知识库问答上下文的入站端口。 */
public interface QueryKnowledgeBaseUseCase {

    RagContext prepare(Query query);

    void complete(RagContext ragContext, String answerText);
}
