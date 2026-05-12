package ch.sbb.polarion.extension.xml_repair.util;

import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public final class Cache {

    private final Map<String, Object> store = new HashMap<>();

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public <T> T getOrCompute(@NotNull String key, @NotNull Callable<T> supplier) {
        if (!store.containsKey(key)) {
            store.put(key, supplier.call());
        }
        return (T) store.get(key);
    }
}
