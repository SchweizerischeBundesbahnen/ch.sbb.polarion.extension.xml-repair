package ch.sbb.polarion.extension.xml_repair.repairers.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepairerConfigMetaTest {

    @Test
    void testGetters() {
        RepairerConfigMeta meta = new RepairerConfigMeta("removeInvalidEnumValues", "Remove invalid enumeration values", "Clear/remove value if it is not defined in the specified enumeration", RepairerConfigType.BOOLEAN, false);

        assertEquals("removeInvalidEnumValues", meta.getKey());
        assertEquals("Remove invalid enumeration values", meta.getDescription());
        assertEquals("Clear/remove value if it is not defined in the specified enumeration", meta.getHint());
        assertEquals(RepairerConfigType.BOOLEAN, meta.getType());
        assertEquals(false, meta.getDefaultValue());
    }

    @Test
    void testWithTrueDefaultValue() {
        RepairerConfigMeta meta = new RepairerConfigMeta("someFlag", "Some flag description", "Some flag hint", RepairerConfigType.BOOLEAN, true);

        assertEquals("someFlag", meta.getKey());
        assertEquals("Some flag description", meta.getDescription());
        assertEquals("Some flag hint", meta.getHint());
        assertEquals(RepairerConfigType.BOOLEAN, meta.getType());
        assertEquals(true, meta.getDefaultValue());
    }

    @Test
    void testDefaultValueCanBeNonBoolean() {
        RepairerConfigMeta meta = new RepairerConfigMeta("threshold", "Max threshold", "Threshold hint", RepairerConfigType.BOOLEAN, 42);

        assertEquals(42, meta.getDefaultValue());
    }
}
