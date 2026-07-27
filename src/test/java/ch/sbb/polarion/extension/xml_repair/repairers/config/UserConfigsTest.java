package ch.sbb.polarion.extension.xml_repair.repairers.config;

import ch.sbb.polarion.extension.xml_repair.repairers.FieldsInvalidEnumerationValueRepairer;
import ch.sbb.polarion.extension.xml_repair.repairers.FieldsRichTextLinksRepairer;
import ch.sbb.polarion.extension.xml_repair.repairers.FieldsWrongTypeRepairer;
import ch.sbb.polarion.extension.xml_repair.repairers.ModuleContentLinksRepairer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserConfigsTest {

    @Test
    void testGetBooleanReturnsValueWhenPresent() {
        UserConfigs configs = new UserConfigs();
        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(),
                Map.of("removeInvalidValues", true));

        assertTrue(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidValues"));
    }

    @Test
    void testGetBooleanReturnsFalseValueWhenPresent() {
        UserConfigs configs = new UserConfigs();
        configs.put(FieldsRichTextLinksRepairer.class.getSimpleName(),
                Map.of("convertToPlainText", false));

        assertFalse(configs.getBoolean(FieldsRichTextLinksRepairer.class, "convertToPlainText"));
    }

    @Test
    void testGetBooleanReturnsFalseWhenRepairerKeyMissing() {
        UserConfigs configs = new UserConfigs();
        // Configured for one repairer only: a lookup keyed by any other repairer must not see this entry,
        // even when the parameter id matches.
        configs.put(FieldsWrongTypeRepairer.class.getSimpleName(), Map.of("removeInvalidValues", true));

        assertFalse(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidValues"));
        assertFalse(configs.getBoolean(FieldsRichTextLinksRepairer.class, "convertToPlainText"));
        assertFalse(configs.getBoolean(ModuleContentLinksRepairer.class, "convertToPlainText"));
    }

    @Test
    void testGetBooleanReturnsFalseWhenRepairerValueIsNull() {
        UserConfigs configs = new UserConfigs();
        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(), null);

        assertFalse(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidValues"));
    }

    @Test
    void testMultipleRepairerConfigs() {
        UserConfigs configs = new UserConfigs();
        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(),
                Map.of("removeInvalidValues", true));
        configs.put(FieldsRichTextLinksRepairer.class.getSimpleName(),
                Map.of("convertToPlainText", false));
        configs.put(ModuleContentLinksRepairer.class.getSimpleName(),
                Map.of("convertToPlainText", true));

        assertEquals(3, configs.size());
        assertTrue(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidValues"));
        assertFalse(configs.getBoolean(FieldsRichTextLinksRepairer.class, "convertToPlainText"));
        assertTrue(configs.getBoolean(ModuleContentLinksRepairer.class, "convertToPlainText"));
    }

    @Test
    void testEmptyConfigs() {
        UserConfigs configs = new UserConfigs();
        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(),
                Map.of("removeInvalidValues", true));
        configs.clear();

        // Cleared configs behave like never-configured ones: the lookup that would have returned true
        // before the clear falls back to false, with no stale entry left behind. Asserted through
        // getBoolean rather than isEmpty()/size(): those are inherited HashMap methods, so they add no
        // coverage of this class, and any assertion on them is statically decidable (IDEA rightly reports
        // "always true"/"always 0").
        assertFalse(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidValues"));
    }
}
