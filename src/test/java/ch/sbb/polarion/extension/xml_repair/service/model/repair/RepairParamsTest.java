package ch.sbb.polarion.extension.xml_repair.service.model.repair;

import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepairParamsTest {

    @Test
    void testNoArgConstructorDefaults() {
        RepairParams params = new RepairParams();

        assertNull(params.getIssueMetaInfos());
        assertNotNull(params.getConfigs());
        assertInstanceOf(UserConfigs.class, params.getConfigs());
    }

    @Test
    void testAllArgsConstructor() {
        UserConfigs configs = new UserConfigs();
        List<String> metaInfos = List.of("meta1", "meta2");

        RepairParams params = new RepairParams(metaInfos, configs);

        assertEquals(metaInfos, params.getIssueMetaInfos());
        assertSame(configs, params.getConfigs());
    }

    @Test
    void testSetters() {
        RepairParams params = new RepairParams();
        UserConfigs configs = new UserConfigs();
        List<String> metaInfos = List.of("m1", "m2", "m3");

        params.setIssueMetaInfos(metaInfos);
        params.setConfigs(configs);

        assertEquals(metaInfos, params.getIssueMetaInfos());
        assertSame(configs, params.getConfigs());
    }

    @Test
    void testEqualsAndHashCode() {
        UserConfigs configs = new UserConfigs();
        List<String> metaInfos = List.of("meta1");

        RepairParams params1 = new RepairParams(metaInfos, configs);
        RepairParams params2 = new RepairParams(metaInfos, configs);

        assertEquals(params1, params2);
        assertEquals(params1.hashCode(), params2.hashCode());
    }

    @Test
    void testNotEqual() {
        RepairParams params1 = new RepairParams();
        params1.setIssueMetaInfos(List.of("a"));

        RepairParams params2 = new RepairParams();
        params2.setIssueMetaInfos(List.of("b"));

        assertNotEquals(params1, params2);
    }

    @Test
    void testToString() {
        RepairParams params = new RepairParams();
        params.setIssueMetaInfos(List.of("test"));

        String str = params.toString();
        assertNotNull(str);
        assertTrue(str.contains("RepairParams"));
        assertTrue(str.contains("test"));
    }
}
