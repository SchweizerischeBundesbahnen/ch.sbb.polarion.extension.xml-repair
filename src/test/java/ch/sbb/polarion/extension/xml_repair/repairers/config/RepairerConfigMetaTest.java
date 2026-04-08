package ch.sbb.polarion.extension.xml_repair.repairers.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepairerConfigMetaTest {

    @Test
    void testGetters() {
        RepairerConfigMeta meta = new RepairerConfigMeta("removeInvalidEnumValues", "Remove invalid enumeration values", RepairerConfigType.BOOLEAN, false);

        assertEquals("removeInvalidEnumValues", meta.getKey());
        assertEquals("Remove invalid enumeration values", meta.getDescription());
        assertEquals(RepairerConfigType.BOOLEAN, meta.getType());
        assertEquals(false, meta.getDefaultValue());
    }

    @Test
    void testWithTrueDefaultValue() {
        RepairerConfigMeta meta = new RepairerConfigMeta("someFlag", "Some flag description", RepairerConfigType.BOOLEAN, true);

        assertEquals("someFlag", meta.getKey());
        assertEquals("Some flag description", meta.getDescription());
        assertEquals(RepairerConfigType.BOOLEAN, meta.getType());
        assertEquals(true, meta.getDefaultValue());
    }

    @Test
    void testDefaultValueCanBeNonBoolean() {
        RepairerConfigMeta meta = new RepairerConfigMeta("threshold", "Max threshold", RepairerConfigType.BOOLEAN, 42);

        assertEquals(42, meta.getDefaultValue());
    }
}
