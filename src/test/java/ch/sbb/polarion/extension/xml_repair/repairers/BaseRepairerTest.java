package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkflowObject;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import java.util.List;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class BaseRepairerTest {

    // Simple concrete subclass for utility method tests
    private static class TestableRepairer extends BaseRepairer {
        @Override
        public String getDisplayName() {
            return "Testable";
        }

        @Override
        public String getDescription() {
            return "Testable";
        }
    }

    // Routing subclass for scan tests
    private static class ScanRoutingRepairer extends BaseRepairer {
        private final List<Issue> issuesToReturn;

        ScanRoutingRepairer(List<Issue> issuesToReturn) {
            this.issuesToReturn = issuesToReturn;
        }

        @Override
        public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
            return issuesToReturn;
        }

        @Override
        protected List<Issue> scan(IModule entity, ScanContext context) {
            return issuesToReturn;
        }

        @Override
        public String getDisplayName() {
            return "ScanRouting";
        }

        @Override
        public String getDescription() {
            return "ScanRouting";
        }
    }

    // Routing subclass for repair tests
    private static class RepairRoutingRepairer extends BaseRepairer {
        private final RepairResult resultToReturn;

        RepairRoutingRepairer(RepairResult resultToReturn) {
            this.resultToReturn = resultToReturn;
        }

        @Override
        protected @NotNull RepairResult repair(IModule entity, RepairContext context) {
            return resultToReturn;
        }

        @Override
        protected @NotNull RepairResult repair(IWorkflowObject entity, RepairContext context) {
            return resultToReturn;
        }

        @Override
        public String getDisplayName() {
            return "RepairRouting";
        }

        @Override
        public String getDescription() {
            return "RepairRouting";
        }
    }

    // ---- Scan routing tests ----

    @Test
    void testScanWorkflowObjectDelegates() {
        Issue issue = createDummyIssue();
        ScanRoutingRepairer repairer = new ScanRoutingRepairer(List.of(issue));

        IWorkflowObject entity = mock(IWorkflowObject.class);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), new Report());

        List<Issue> result = repairer.scan((IUniqueObject) entity, context);

        assertEquals(1, result.size());
    }

    @Test
    void testScanModuleDelegates() {
        Issue issue = createDummyIssue();
        ScanRoutingRepairer repairer = new ScanRoutingRepairer(List.of(issue));

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), new Report());

        List<Issue> result = repairer.scan((IUniqueObject) module, context);

        assertEquals(1, result.size());
    }

    @Test
    void testScanDefaultThrowsForUnsupportedType() {
        TestableRepairer repairer = new TestableRepairer();
        IWorkflowObject entity = mock(IWorkflowObject.class);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), new Report());

        assertThrows(IllegalArgumentException.class, () -> repairer.scan((IUniqueObject) entity, context));
    }

    // ---- Repair routing tests ----

    @Test
    void testRepairWorkflowObjectSavesOnSuccess() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairResult successResult = new RepairResult(metaInfo, true);
        RepairRoutingRepairer repairer = new RepairRoutingRepairer(successResult);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair((IUniqueObject) entity, context);

        assertTrue(result.isSuccess());
        verify(entity).save();
    }

    @Test
    void testRepairWorkflowObjectNoSaveOnFailure() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairResult failResult = new RepairResult(metaInfo, false);
        RepairRoutingRepairer repairer = new RepairRoutingRepairer(failResult);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair((IUniqueObject) entity, context);

        assertFalse(result.isSuccess());
        verify(entity, never()).save();
    }

    @Test
    void testRepairModuleWithoutRevisionSavesOnSuccess() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairResult successResult = new RepairResult(metaInfo, true);
        RepairRoutingRepairer repairer = new RepairRoutingRepairer(successResult);

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getRevision()).thenReturn(null);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair((IUniqueObject) module, context);

        assertTrue(result.isSuccess());
        verify(module).save();
    }

    @Test
    void testRepairModuleWithRevisionFailsFastWithoutInvokingNestedRepairOrSave() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        // Nested repair returns success so we can prove it was NOT invoked
        RepairResult nestedResult = new RepairResult(metaInfo, true);
        RepairRoutingRepairer repairer = spy(new RepairRoutingRepairer(nestedResult));

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getRevision()).thenReturn("42");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair((IUniqueObject) module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("baseline/revision") && w.contains("switch to HEAD")));
        verify(module, never()).save();
        verify(repairer, never()).repair(any(IModule.class), any(RepairContext.class));
        verify(repairer, never()).repair(any(IWorkflowObject.class), any(RepairContext.class));
        verifyNoInteractions(polarionService);
    }

    @Test
    void testRepairDefaultThrowsForUnsupportedType() {
        TestableRepairer repairer = new TestableRepairer();
        IWorkflowObject entity = mock(IWorkflowObject.class);
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        assertThrows(IllegalArgumentException.class, () -> repairer.repair((IUniqueObject) entity, context));
    }

    // ---- Helper ----

    private Issue createDummyIssue() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        BaseRepairer repairer = mock(BaseRepairer.class);
        lenient().when(repairer.getRepairerId()).thenReturn("DummyRepairer");
        return new Issue(metaInfo, repairer, "dummy");
    }
}
