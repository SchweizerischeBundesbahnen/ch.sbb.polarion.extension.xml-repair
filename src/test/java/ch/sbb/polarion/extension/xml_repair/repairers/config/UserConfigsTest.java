package ch.sbb.polarion.extension.xml_repair.repairers.config;

import ch.sbb.polarion.extension.xml_repair.repairers.FieldsInvalidEnumerationValueRepairer;
import ch.sbb.polarion.extension.xml_repair.repairers.FieldsRichTextLinksRepairer;
import ch.sbb.polarion.extension.xml_repair.repairers.ModuleContentLinksRepairer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserConfigsTest {

    @Test
    void testGetBooleanReturnsValueWhenPresent() {
        UserConfigs configs = new UserConfigs();
        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(),
                Map.of("removeInvalidEnumValues", true));

        assertTrue(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidEnumValues"));
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

        assertFalse(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidEnumValues"));
        assertFalse(configs.getBoolean(FieldsRichTextLinksRepairer.class, "convertToPlainText"));
        assertFalse(configs.getBoolean(ModuleContentLinksRepairer.class, "convertToPlainText"));
    }

    @Test
    void testGetBooleanReturnsFalseWhenRepairerValueIsNull() {
        UserConfigs configs = new UserConfigs();
        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(), null);

        assertFalse(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidEnumValues"));
    }

    @Test
    void testMultipleRepairerConfigs() {
        UserConfigs configs = new UserConfigs();
        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(),
                Map.of("removeInvalidEnumValues", true));
        configs.put(FieldsRichTextLinksRepairer.class.getSimpleName(),
                Map.of("convertToPlainText", false));
        configs.put(ModuleContentLinksRepairer.class.getSimpleName(),
                Map.of("convertToPlainText", true));

        assertEquals(3, configs.size());
        assertTrue(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidEnumValues"));
        assertFalse(configs.getBoolean(FieldsRichTextLinksRepairer.class, "convertToPlainText"));
        assertTrue(configs.getBoolean(ModuleContentLinksRepairer.class, "convertToPlainText"));
    }

    @Test
    void testEmptyConfigs() {
        UserConfigs configs = new UserConfigs();
        assertTrue(configs.isEmpty());
        assertEquals(0, configs.size());
    }
}
