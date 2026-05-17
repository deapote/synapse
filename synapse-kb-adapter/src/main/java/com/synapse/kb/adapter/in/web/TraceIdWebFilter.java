package com.synapse.kb.adapter.in.web;

import org.slf4j.MDC;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class TraceIdWebFilter implements WebFilter {
    public static final String TRACE_ID = "traceId";
    private static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = resolveTraceId(exchange.getRequest());
        exchange.getAttributes().put(TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(TRACE_HEADER, traceId);
        return Mono.defer(() -> {
                    MDC.put(TRACE_ID, traceId);
                    return chain.filter(exchange);
                })
                .contextWrite(context -> context.put(TRACE_ID, traceId))
                .doFinally(signalType -> MDC.remove(TRACE_ID));
    }

    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID);
        return traceId == null || traceId.isBlank() ? "unknown" : traceId;
    }

    private String resolveTraceId(ServerHttpRequest request) {
        String traceId = request.getHeaders().getFirst(TRACE_HEADER);
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }
}
