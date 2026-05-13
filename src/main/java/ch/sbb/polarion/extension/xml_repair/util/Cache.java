package ch.sbb.polarion.extension.xml_repair.util;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public final class Cache {

    // ConcurrentHashMap disallows null values, so use a sentinel to still cache nulls returned by suppliers.
    private static final Object NULL_SENTINEL = new Object();

    private final Map<String, Object> store = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(@NotNull String key, @NotNull Callable<T> supplier) {
        Object value = store.computeIfAbsent(key, k -> {
            try {
                T computed = supplier.call();
                return computed == null ? NULL_SENTINEL : computed;
            } catch (Exception e) {
                throw sneakyThrow(e);
            }
        });
        return value == NULL_SENTINEL ? null : (T) value;
    }

    // Rethrows a checked exception as if it were unchecked, preserving the original type.
    // Uses generic erasure so the compiler does not require the caller to declare it.
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(@NotNull Throwable t) throws T {
        throw (T) t;
    }
}
