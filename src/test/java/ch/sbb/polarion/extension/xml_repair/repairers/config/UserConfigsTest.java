package ch.sbb.polarion.extension.xml_repair.repairers.config;

import ch.sbb.polarion.extension.xml_repair.repairers.FieldsInvalidEnumerationValueRepairer;
import ch.sbb.polarion.extension.xml_repair.repairers.FieldsRichTextLinksRepairer;
import ch.sbb.polarion.extension.xml_repair.repairers.FieldsWrongTypeRepairer;
import ch.sbb.polarion.extension.xml_repair.repairers.ModuleContentLinksRepairer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
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
    void testGetBooleanReturnsFalseWhenParamIdMissing() {
        UserConfigs configs = new UserConfigs();
        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(), Map.of("someOtherParam", true));

        assertFalse(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidValues"));
    }

    @Test
    void testGetBooleanReturnsFalseWhenValueIsNotBoolean() {
        UserConfigs configs = new UserConfigs();
        // A JSON body may carry any type here, e.g. the string "true" instead of the boolean true.
        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(), Map.of("removeInvalidValues", "true"));

        assertFalse(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidValues"));
    }

    @Test
    void testGetBooleanReturnsFalseWhenRepairerValueIsNotMap() {
        UserConfigs configs = new UserConfigs();
        // The map is deserialized from an unvalidated request body, so a repairer key may hold any JSON value.
        // Each of these must read as "not configured" rather than abort the run with a ClassCastException.
        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(), "notAMap");
        assertFalse(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidValues"));

        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(), 42);
        assertFalse(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidValues"));

        configs.put(FieldsInvalidEnumerationValueRepairer.class.getSimpleName(), List.of("removeInvalidValues"));
        assertFalse(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidValues"));
    }

    @Test
    void testGetBooleanSurvivesMalformedJsonBody() throws JsonProcessingException {
        UserConfigs configs = new ObjectMapper()
                .readValue("{\"ModuleContentLinksRepairer\":\"oops\"}", UserConfigs.class);

        assertFalse(configs.getBoolean(ModuleContentLinksRepairer.class, "convertToPlainText"));
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
        // before the clear falls back to false, with no stale entry left behind.
        assertFalse(configs.getBoolean(FieldsInvalidEnumerationValueRepairer.class, "removeInvalidValues"));
        assertEquals(0, configs.size());
    }

    @Test
    void testSerializesAsFlatJsonObject() throws JsonProcessingException {
        UserConfigs configs = new UserConfigs();
        configs.put(ModuleContentLinksRepairer.class.getSimpleName(), Map.of("convertToPlainText", true));

        assertEquals("{\"ModuleContentLinksRepairer\":{\"convertToPlainText\":true}}", new ObjectMapper().writeValueAsString(configs));
    }

    @Test
    void testDeserializesFromFlatJsonObject() throws JsonProcessingException {
        UserConfigs configs = new ObjectMapper()
                .readValue("{\"ModuleContentLinksRepairer\":{\"convertToPlainText\":true}}", UserConfigs.class);

        assertTrue(configs.getBoolean(ModuleContentLinksRepairer.class, "convertToPlainText"));
    }

    @Test
    void testGetConfigsReturnsUnmodifiableView() {
        UserConfigs configs = new UserConfigs();
        configs.put(FieldsWrongTypeRepairer.class.getSimpleName(), Map.of("removeInvalidValues", true));

        Map<String, Object> view = configs.configs();
        // noinspection DataFlowIssue
        assertThrows(UnsupportedOperationException.class, () -> view.put("other", Map.of()));
        assertEquals(1, view.size());
    }

    @Test
    void testContainsKey() {
        UserConfigs configs = new UserConfigs();
        configs.put(FieldsWrongTypeRepairer.class.getSimpleName(), Map.of("removeInvalidValues", true));

        assertTrue(configs.containsKey(FieldsWrongTypeRepairer.class.getSimpleName()));
        assertFalse(configs.containsKey(FieldsRichTextLinksRepairer.class.getSimpleName()));
    }

    @Test
    void testToStringRendersUnderlyingMap() {
        UserConfigs configs = new UserConfigs();
        configs.put(ModuleContentLinksRepairer.class.getSimpleName(), Map.of("convertToPlainText", true));

        assertEquals("{ModuleContentLinksRepairer={convertToPlainText=true}}", configs.toString());
    }

    @Test
    void testEqualsAndHashCodeCompareContent() {
        UserConfigs configs = new UserConfigs();
        configs.put(ModuleContentLinksRepairer.class.getSimpleName(), Map.of("convertToPlainText", true));
        UserConfigs same = new UserConfigs(Map.of(ModuleContentLinksRepairer.class.getSimpleName(), Map.of("convertToPlainText", true)));
        UserConfigs different = new UserConfigs(Map.of(ModuleContentLinksRepairer.class.getSimpleName(), Map.of("convertToPlainText", false)));

        assertEquals(configs, same);
        assertEquals(configs.hashCode(), same.hashCode());
        assertNotEquals(configs, different);
        assertNotEquals(configs, new UserConfigs());
    }

    @Test
    void testConstructorCopiesSourceMap() {
        Map<String, Object> source = new HashMap<>();
        source.put(ModuleContentLinksRepairer.class.getSimpleName(), Map.of("convertToPlainText", true));

        UserConfigs configs = new UserConfigs(source);
        source.clear();

        // The record must own its map: later edits to the source must not reach it.
        assertTrue(configs.getBoolean(ModuleContentLinksRepairer.class, "convertToPlainText"));
        assertEquals(1, configs.size());
    }
}
