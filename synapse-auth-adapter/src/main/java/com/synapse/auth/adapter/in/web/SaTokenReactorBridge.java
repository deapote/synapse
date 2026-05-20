package com.synapse.auth.adapter.in.web;

import cn.dev33.satoken.reactor.context.SaReactorHolder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Sa-Token Reactor 上下文桥接器。
 * 将阻塞的 application 层调用安全地桥接到 WebFlux 响应式线程，
 * 避免线程切换导致 Sa-Token 上下文丢失。
 */
final class SaTokenReactorBridge {

    private SaTokenReactorBridge() {
    }

    static <T> Mono<T> blockingCall(CheckedSupplier<T> supplier) {
        return SaReactorHolder.sync(() -> {
                    try {
                        return supplier.get();
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    static Mono<Void> blockingAction(CheckedAction action) {
        return SaReactorHolder.sync(() -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return Boolean.TRUE;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    interface CheckedAction {
        void run() throws Exception;
    }
}
