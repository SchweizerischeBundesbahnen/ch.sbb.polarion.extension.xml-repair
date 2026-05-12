package ch.sbb.polarion.extension.xml_repair.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
