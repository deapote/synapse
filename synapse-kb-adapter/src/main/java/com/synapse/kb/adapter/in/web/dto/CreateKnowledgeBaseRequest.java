package com.synapse.kb.adapter.in.web.dto;

/**
 * 创建知识库请求 DTO。
 *
 * @param name        知识库名称，必填，长度 1–200
 * @param description 知识库描述，可为空
 */
public record CreateKnowledgeBaseRequest(String name, String description) {
}
