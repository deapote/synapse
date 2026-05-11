package com.synapse.kb.adapter.in.web.dto;

/**
 * 问答查询请求 DTO。
 *
 * @param query 用户提问内容，必填
 */
public record QueryRequest(String query) {
}
