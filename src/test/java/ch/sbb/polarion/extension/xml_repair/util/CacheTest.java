package ch.sbb.polarion.extension.xml_repair.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheTest {

    @Test
    void testGetOrComputeRethrowsCheckedExceptionFromSupplier() {
        Cache cache = new Cache();
        IOException boom = new IOException("boom");

        IOException thrown = assertThrows(IOException.class, () -> cache.getOrCompute("key", () -> {
            throw boom;
        }));

        assertEquals(boom, thrown);
    }

    @Test
    @SuppressWarnings("java:S2925") // Thread.sleep is intentional: holds the computation so concurrent threads collide on the same key
    void testGetOrComputeInvokesSupplierAtMostOnceUnderConcurrentAccess() throws Exception {
        Cache cache = new Cache();
        AtomicInteger invocations = new AtomicInteger();
        int threadCount = 16;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(threadCount)) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return cache.getOrCompute("shared", () -> {
                        invocations.incrementAndGet();
                        // Hold the computation so other threads collide on the same key
                        Thread.sleep(20);
                        return "value";
                    });
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();

            for (Future<String> f : futures) {
                assertEquals("value", f.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, invocations.get());
        }
    }
}
