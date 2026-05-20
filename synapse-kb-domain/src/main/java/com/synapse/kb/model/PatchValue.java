package com.synapse.kb.model;

import java.util.function.Function;

/**
 * 语义化字段更新值对象。区分三种状态：
 * - unset(): 字段未出现在请求中，不修改
 * - set(T):  字段显式设置为值
 * - clear(): 字段显式清空（语义上等价于 set(null)，用于区分意图）
 */
public final class PatchValue<T> {
    private static final PatchValue<?> UNSET = new PatchValue<>(null, false, false);
    private static final PatchValue<?> CLEAR = new PatchValue<>(null, true, true);

    private final T value;
    private final boolean present;
    private final boolean clear;

    private PatchValue(T value, boolean present, boolean clear) {
        this.value = value;
        this.present = present;
        this.clear = clear;
    }

    @SuppressWarnings("unchecked")
    public static <T> PatchValue<T> unset() {
        return (PatchValue<T>) UNSET;
    }

    @SuppressWarnings("unchecked")
    public static <T> PatchValue<T> clear() {
        return (PatchValue<T>) CLEAR;
    }

    public static <T> PatchValue<T> set(T value) {
        return new PatchValue<>(value, true, false);
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isClear() {
        return clear;
    }

    public T value() {
        return value;
    }

    public <R> PatchValue<R> map(Function<T, R> mapper) {
        if (!present) {
            return unset();
        }
        if (clear) {
            return clear();
        }
        return set(mapper.apply(value));
    }
}
