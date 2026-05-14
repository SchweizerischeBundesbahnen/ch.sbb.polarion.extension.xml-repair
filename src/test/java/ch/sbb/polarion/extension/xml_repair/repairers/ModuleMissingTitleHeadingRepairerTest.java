package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(PlatformContextMockExtension.class)
class ModuleMissingTitleHeadingRepairerTest {

    @Test
    void testDisplayNameAndDescription() {
        ModuleMissingTitleHeadingRepairer repairer = new ModuleMissingTitleHeadingRepairer();
        assertEquals("Document content: Missing title-heading", repairer.getDisplayName());
        assertNotNull(repairer.getDescription());
        assertTrue(repairer.getDescription().contains("title-heading"));
        assertEquals("ModuleMissingTitleHeadingRepairer", repairer.getRepairerId());
    }

    @Test
    void testNoIssueWhenTitleHeadingExists() {
        ModuleMissingTitleHeadingRepairer repairer = spy(new ModuleMissingTitleHeadingRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        doReturn(true).when(repairer).hasTitleHeading(module);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertTrue(issues.isEmpty());
    }

    @Test
    void testIssueWhenTitleHeadingMissing() {
        ModuleMissingTitleHeadingRepairer repairer = spy(new ModuleMissingTitleHeadingRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleName()).thenReturn("TestDocument");
        doReturn(false).when(repairer).hasTitleHeading(module);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertEquals(1, issues.size());
        Issue issue = issues.getFirst();
        assertTrue(issue.getDescription().contains("TestDocument"));
        assertTrue(issue.getDescription().contains("missing title-heading"));
    }

    @Test
    void testRepairWhenTitleHeadingMissing() {
        ModuleMissingTitleHeadingRepairer repairer = spy(new ModuleMissingTitleHeadingRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleName()).thenReturn("TestDocument");
        doReturn(false).when(repairer).hasTitleHeading(module);
        doNothing().when(repairer).moveHeadingToProperPosition(module);

        IWorkItem newHeading = mock(IWorkItem.class);
        when(module.createWorkItem("heading")).thenReturn(newHeading);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertTrue(result.isSuccess());
        verify(module).createWorkItem(anyString());
        verify(repairer).moveHeadingToProperPosition(module);
        verify(newHeading).setTitle("TestDocument");
        verify(newHeading).save();
    }

    @Test
    void testRepairWhenTitleHeadingAlreadyExists() {
        ModuleMissingTitleHeadingRepairer repairer = spy(new ModuleMissingTitleHeadingRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleName()).thenReturn("TestDocument");
        doReturn(true).when(repairer).hasTitleHeading(module);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertFalse(result.isSuccess());
    }

    @Test
    void testIssueDescriptionContent() {
        ModuleMissingTitleHeadingRepairer repairer = spy(new ModuleMissingTitleHeadingRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleName()).thenReturn("My Document");
        doReturn(false).when(repairer).hasTitleHeading(module);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        Issue issue = issues.getFirst();
        assertTrue(issue.getDescription().contains("My Document"));
        assertTrue(issue.getDescription().contains("missing title-heading"));
    }

}
