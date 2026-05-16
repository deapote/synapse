package com.synapse.kb.adapter.in.web;

import cn.dev33.satoken.reactor.context.SaReactorHolder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
