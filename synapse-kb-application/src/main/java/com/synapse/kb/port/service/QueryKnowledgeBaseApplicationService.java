package com.synapse.kb.port.service;

import com.synapse.kb.model.*;
import com.synapse.kb.port.in.QueryKnowledgeBaseUseCase;
import com.synapse.kb.port.out.AccessControlPort;
import com.synapse.kb.port.service.support.*;
import com.synapse.kb.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库查询应用服务，编排 RAG 问答流程。
 * 负责会话解析、Query 改写、混合检索、时效过滤、去重及 Prompt 组装。
 */
public class QueryKnowledgeBaseApplicationService implements QueryKnowledgeBaseUseCase {

    private static final Logger log = LoggerFactory.getLogger(QueryKnowledgeBaseApplicationService.class);

    private final KnowledgeBaseAccessGuard accessGuard;
    private final DocumentRepository documentRepository;
    private final ChatApplicationService chatApplicationService;
    private final QueryPreparationService queryPreparationService;
    private final HybridRetrievalService hybridRetrievalService;
    private final PromptContextBuilder promptContextBuilder;
    private final AccessControlPort accessControlPort;
    private final String promptTemplate;
    private final boolean chatMemoryEnabled;

    public QueryKnowledgeBaseApplicationService(KnowledgeBaseAccessGuard accessGuard,
                                                DocumentRepository documentRepository,
                                                ChatApplicationService chatApplicationService,
                                                QueryPreparationService queryPreparationService,
                                                HybridRetrievalService hybridRetrievalService,
                                                PromptContextBuilder promptContextBuilder,
                                                AccessControlPort accessControlPort,
                                                String promptTemplate,
                                                boolean chatMemoryEnabled) {
        this.accessGuard = accessGuard;
        this.documentRepository = documentRepository;
        this.chatApplicationService = chatApplicationService;
        this.queryPreparationService = queryPreparationService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.promptContextBuilder = promptContextBuilder;
        this.accessControlPort = accessControlPort;
        this.promptTemplate = promptTemplate;
        this.chatMemoryEnabled = chatMemoryEnabled;
    }

    @Override
    public RagContext prepare(Query query) {
        KnowledgeBase kb = accessGuard.requireKnowledgeBase(query.knowledgeBaseId());
        accessGuard.checkKnowledgeBaseAccess(kb, "KB_READ");
        ChatSession chatSession = chatApplicationService.resolveChatSessionForQuery(query.knowledgeBaseId(), query.sessionId());
        if (chatMemoryEnabled && chatSession != null) {
            chatApplicationService.appendUserMessage(chatSession, query.text());
            chatSession.renameFromUserQuestion(query.text());
            chatApplicationService.summarizeIfNeeded(chatSession);
        }
        QueryPreparationService.PreparedQuery preparedQuery = queryPreparationService.prepareQuery(query.text());
        LocalDate asOfDate = resolveAsOfDate(query);

        List<ChunkReference> results = hybridRetrievalService.retrieveReferences(
                query.knowledgeBaseId(), preparedQuery, asOfDate, query.sourceType(), query.jurisdiction()
        );
        results = hybridRetrievalService.filterByDocumentEffectiveDate(
                results, asOfDate, query.sourceType(), query.jurisdiction(), documentRepository
        );
        results = hybridRetrievalService.deduplicateByCanonicalKey(results);

        StringBuilder contextBuilder = new StringBuilder();
        chatApplicationService.appendMemoryContext(contextBuilder, chatSession);
        promptContextBuilder.appendSourceContexts(contextBuilder, results, asOfDate);

        String prompt = String.format(promptTemplate,
                contextBuilder.toString(),
                query.text()
        );

        return new RagContext(
                prompt,
                new ArrayList<>(results),
                chatSession == null ? null : chatSession.getId().value(),
                chatSession == null ? null : chatSession.getOwnerUserId()
        );
    }

    @Override
    public void complete(RagContext ragContext, String answerText) {
        if (!chatMemoryEnabled || ragContext.sessionId() == null || answerText == null || answerText.isBlank()) {
            return;
        }
        ChatSession session = requireSessionForOwner(
                new ChatSessionId(ragContext.sessionId()),
                ragContext.ownerUserId()
        );
        chatApplicationService.appendAssistantMessage(session, answerText, ragContext.references());
    }

    private ChatSession requireSessionForOwner(ChatSessionId sessionId, String ownerUserId) {
        return chatApplicationService.requireSessionForOwner(sessionId, ownerUserId);
    }

    private LocalDate resolveAsOfDate(Query query) {
        if (query.asOfDate() != null) {
            return query.asOfDate();
        }
        LocalDate parsed = parseTemporalIntent(query.text());
        return parsed != null ? parsed : LocalDate.now();
    }

    private LocalDate parseTemporalIntent(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replaceAll("\s+", "");
        java.util.regex.Matcher m;

        m = java.util.regex.Pattern.compile("(\\d{4})年(?:规定|规则|法规|政策|制度|办法|意见|通知|新规|旧规|适用|有效|生效)").matcher(normalized);
        if (m.find()) {
            int year = Integer.parseInt(m.group(1));
            return LocalDate.of(year, 12, 31);
        }

        m = java.util.regex.Pattern.compile("(\\d{2})年(?:新规|新规程|新规定|新办法|新政策|新制度)").matcher(normalized);
        if (m.find()) {
            int year = Integer.parseInt(m.group(1));
            int fullYear = year >= 50 ? 1900 + year : 2000 + year;
            return LocalDate.of(fullYear, 12, 31);
        }

        m = java.util.regex.Pattern.compile("现在适用|当前适用|现在规定|目前有效|现行").matcher(normalized);
        if (m.find()) {
            return LocalDate.now();
        }

        return null;
    }
}
