package ch.sbb.polarion.extension.xml_repair.service.model.repair;

import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
class RepairContextTest {

    @Test
    void testAccessors() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        UserConfigs configs = new UserConfigs();

        RepairContext context = new RepairContext(metaInfo, polarionService, configs);

        assertSame(metaInfo, context.issueMetaInfo());
        assertSame(polarionService, context.polarionService());
        assertSame(configs, context.configs());
    }

    @Test
    void testEqualsAndHashCode() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        UserConfigs configs = new UserConfigs();

        RepairContext context1 = new RepairContext(metaInfo, polarionService, configs);
        RepairContext context2 = new RepairContext(metaInfo, polarionService, configs);

        assertEquals(context1, context2);
        assertEquals(context1.hashCode(), context2.hashCode());
    }

    @Test
    void testNotEqualWithDifferentComponents() {
        IssueMetaInfo metaInfo1 = mock(IssueMetaInfo.class);
        IssueMetaInfo metaInfo2 = mock(IssueMetaInfo.class);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        UserConfigs configs = new UserConfigs();

        RepairContext context1 = new RepairContext(metaInfo1, polarionService, configs);
        RepairContext context2 = new RepairContext(metaInfo2, polarionService, configs);

        assertNotEquals(context1, context2);
    }

    @Test
    void testToString() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        UserConfigs configs = new UserConfigs();

        RepairContext context = new RepairContext(metaInfo, polarionService, configs);

        assertNotNull(context.toString());
        assertTrue(context.toString().contains("RepairContext"));
    }
}
