package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.service.EntityRenderer;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.internal.model.module.Module;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.persistence.spi.AbstractTypedList;
import com.polarion.subterra.base.data.model.IStructType;
import ch.sbb.polarion.extension.xml_repair.util.LayoutUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
class ModuleWrongLayoutAssignmentsRepairerTest {

    @Test
    void testDisplayNameAndDescription() {
        ModuleWrongLayoutAssignmentsRepairer repairer = new ModuleWrongLayoutAssignmentsRepairer();
        assertEquals("Document content: Wrong layout assignments", repairer.getDisplayName());
        assertNotNull(repairer.getDescription());
        assertEquals("ModuleWrongLayoutAssignmentsRepairer", repairer.getRepairerId());
    }

    @Test
    void testScanDetectsWrongLayouts() {
        ModuleWrongLayoutAssignmentsRepairer repairer = new ModuleWrongLayoutAssignmentsRepairer();

        IModule entity = mock(IModule.class, RETURNS_DEEP_STUBS);
        lenient().when(entity.getProjectId()).thenReturn("elibrary");

        IWorkItem w1 = mockWorkItem("EL-1", "task");
        IWorkItem w2 = mockWorkItem("EL-2", "requirement");
        IWorkItem w3 = mockWorkItem("EL-3", "testCase");
        IWorkItem w5 = mockWorkItem("EL-5", "issue");
        List<IWorkItem> items = List.of(w1, w2, w3, w5);
        when(entity.getContainedWorkItems()).thenReturn(items);
        when(entity.getStructureNodeOfWI(w1).getLayout()).thenReturn(0);
        when(entity.getStructureNodeOfWI(w2).getLayout()).thenReturn(2);
        when(entity.getStructureNodeOfWI(w3).getLayout()).thenReturn(1);
        when(entity.getStructureNodeOfWI(w5).getLayout()).thenReturn(2);

        List<IModule.IRenderingLayoutStruct> layouts = List.of(
                mockLayout("issue"),
                mockLayout("task"),
                mockLayout("requirement")
        );
        when(entity.getRenderingLayouts()).thenReturn(layouts);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        List<Issue> issues = repairer.scan(entity, createScanContext(polarionService));
        assertEquals(3, issues.size());
        assertTrue(issues.stream().map(Issue::getDescription).toList().containsAll(List.of(
                "Work item 'EL-1' has wrong layout assigned in the module structure (expected 'task' but found 'issue').",
                "Work item 'EL-3' has wrong layout assigned in the module structure (expected 'testCase' but found 'task').",
                "Work item 'EL-5' has wrong layout assigned in the module structure (expected 'issue' but found 'requirement')."
        )));
        verify(entity.getStructureNodeOfWI(w1), never()).updateWorkItemLayout(anyInt());
        verify(entity.getStructureNodeOfWI(w2), never()).updateWorkItemLayout(anyInt());
        verify(entity.getStructureNodeOfWI(w3), never()).updateWorkItemLayout(anyInt());
        verify(entity.getStructureNodeOfWI(w5), never()).updateWorkItemLayout(anyInt());
    }

    @Test
    void testRepairDeclaredLayouts() {
        try (MockedStatic<LayoutUtils> layoutUtilsMock = mockStatic(LayoutUtils.class)) {
            ModuleWrongLayoutAssignmentsRepairer repairer = new ModuleWrongLayoutAssignmentsRepairer();

            IModule entity = mock(IModule.class, RETURNS_DEEP_STUBS);
            lenient().when(entity.getProjectId()).thenReturn("elibrary");

            // Only include the work item being repaired
            IWorkItem w1 = mockWorkItem("EL-1", "task");
            when(entity.getContainedWorkItems()).thenReturn(List.of(w1));
            when(entity.getStructureNodeOfWI(w1).getLayout()).thenReturn(0);

            List<IModule.IRenderingLayoutStruct> layouts = List.of(
                    mockLayout("issue"),
                    mockLayout("task"),
                    mockLayout("requirement")
            );
            when(entity.getRenderingLayouts()).thenReturn(layouts);

            XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

            IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
            when(metaInfo.serialize()).thenReturn("serialized");
            when(metaInfo.get("issueDescription")).thenReturn("Work item 'EL-1' has wrong layout assigned in the module structure (expected 'task' but found 'issue').");
            RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs());

            RepairResult result = repairer.repair(entity, repairContext);
            assertTrue(result.isSuccess());
            layoutUtilsMock.verify(() -> LayoutUtils.switchLayoutIndex(entity, w1, 1));
        }
    }

    @Test
    void testScanUndeclaredLayout() {
        ModuleWrongLayoutAssignmentsRepairer repairer = new ModuleWrongLayoutAssignmentsRepairer();

        IModule entity = mock(IModule.class, RETURNS_DEEP_STUBS);
        lenient().when(entity.getProjectId()).thenReturn("elibrary");

        IWorkItem w4 = mockWorkItem("EL-4", "issue");
        when(entity.getContainedWorkItems()).thenReturn(List.of(w4));
        when(entity.getStructureNodeOfWI(w4).getLayout()).thenReturn(3);

        List<IModule.IRenderingLayoutStruct> layouts = List.of(
                mockLayout("task"),
                mockLayout("requirement")
        );
        when(entity.getRenderingLayouts()).thenReturn(layouts);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        List<Issue> issues = repairer.scan(entity, createScanContext(polarionService));
        assertEquals(1, issues.size());
        assertEquals("No layout declared for work item 'EL-4' with type 'issue'", issues.getFirst().getDescription());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testRepairUndeclaredLayoutTypeExistsInProject() {
        try (MockedStatic<LayoutUtils> layoutUtilsMock = mockStatic(LayoutUtils.class)) {
            ModuleWrongLayoutAssignmentsRepairer repairer = new ModuleWrongLayoutAssignmentsRepairer();

            IModule entity = mock(IModule.class, RETURNS_DEEP_STUBS);
            lenient().when(entity.getProjectId()).thenReturn("elibrary");

            IWorkItem w4 = mockWorkItem("EL-4", "issue");
            when(Objects.requireNonNull(w4.getType()).getName()).thenReturn("Issue");
            when(entity.getContainedWorkItems()).thenReturn(List.of(w4));
            when(entity.getStructureNodeOfWI(w4).getLayout()).thenReturn(3);

            // Use AbstractTypedList mock so the cast in the repairer works
            IModule.IRenderingLayoutStruct taskLayout = mockLayout("task");
            IModule.IRenderingLayoutStruct reqLayout = mockLayout("requirement");
            AbstractTypedList layouts = mock(AbstractTypedList.class, RETURNS_DEEP_STUBS);
            when(layouts.stream()).thenReturn(Stream.of(taskLayout, reqLayout));
            when(layouts.size()).thenReturn(2);
            when(entity.getRenderingLayouts()).thenReturn(layouts);

            // Mock project type enum - "issue" type exists in project
            ITypeOpt issueTypeOption = mock(ITypeOpt.class);
            when(issueTypeOption.getId()).thenReturn("issue");
            IPObjectList typeOptions = mock(IPObjectList.class);
            when(typeOptions.stream()).thenReturn(Stream.of(issueTypeOption));
            when(entity.getProject().getWorkItemTypeEnum().getAllOptions()).thenReturn(typeOptions);

            // Mock rendering layouts prototype for adding new layout
            IStructType itemType = mock(IStructType.class);
            when(layouts.getPrototype().getItemType()).thenReturn(itemType);

            XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

            IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
            when(metaInfo.serialize()).thenReturn("serialized");
            when(metaInfo.get("issueDescription")).thenReturn("No layout declared for work item 'EL-4' with type 'issue'");
            lenient().when(metaInfo.getString("issueDescription")).thenReturn("No layout declared for work item 'EL-4' with type 'issue'");
            RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs());

            RepairResult result = repairer.repair(entity, repairContext);
            assertTrue(result.isSuccess());
            verify(layouts).add(any(Module.ModuleRenderingLayout.class));
            layoutUtilsMock.verify(() -> LayoutUtils.switchLayoutIndex(eq(entity), eq(w4), anyInt()));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testRepairUndeclaredLayoutTypeNotInProject() {
        ModuleWrongLayoutAssignmentsRepairer repairer = new ModuleWrongLayoutAssignmentsRepairer();

        IModule entity = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");

        IWorkItem w4 = mockWorkItem("EL-4", "unknownType");
        when(entity.getContainedWorkItems()).thenReturn(List.of(w4));
        when(entity.getStructureNodeOfWI(w4).getLayout()).thenReturn(3);

        List<IModule.IRenderingLayoutStruct> layouts = List.of(
                mockLayout("task"),
                mockLayout("requirement")
        );
        when(entity.getRenderingLayouts()).thenReturn(layouts);

        // Mock project type enum - "unknownType" doesn't exist
        ITypeOpt taskTypeOption = mock(ITypeOpt.class);
        when(taskTypeOption.getId()).thenReturn("task");
        IPObjectList typeOptions = mock(IPObjectList.class);
        when(typeOptions.stream()).thenReturn(Stream.of(taskTypeOption));
        when(entity.getProject().getWorkItemTypeEnum().getAllOptions()).thenReturn(typeOptions);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.get("issueDescription")).thenReturn("No layout declared for work item 'EL-4' with type 'unknownType'");
        lenient().when(metaInfo.getString("issueDescription")).thenReturn("No layout declared for work item 'EL-4' with type 'unknownType'");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs());

        RepairResult result = repairer.repair(entity, repairContext);
        assertFalse(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("unknownType"));
        assertTrue(result.getWarnings().iterator().next().contains("elibrary"));
    }

    @Test
    void testHeadingWorkItemsAreSkipped() {
        ModuleWrongLayoutAssignmentsRepairer repairer = new ModuleWrongLayoutAssignmentsRepairer();

        IModule entity = mock(IModule.class, RETURNS_DEEP_STUBS);

        IWorkItem heading = mockWorkItem("EL-10", "heading");
        when(entity.getContainedWorkItems()).thenReturn(List.of(heading));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(entity, createScanContext(polarionService));
        assertTrue(issues.isEmpty());
        verify(entity, never()).getStructureNodeOfWI(heading);
    }

    @Test
    void testDuplicateLayoutTypesNotReportedAsIssue() {
        ModuleWrongLayoutAssignmentsRepairer repairer = new ModuleWrongLayoutAssignmentsRepairer();

        IModule entity = mock(IModule.class, RETURNS_DEEP_STUBS);

        // Work item of type "task" assigned to second "task" layout (index 2 instead of expected index 0)
        IWorkItem w1 = mockWorkItem("EL-20", "task");
        when(entity.getContainedWorkItems()).thenReturn(List.of(w1));
        when(entity.getStructureNodeOfWI(w1).getLayout()).thenReturn(2);

        List<IModule.IRenderingLayoutStruct> layouts = List.of(
                mockLayout("task"),       // index 0 - first "task" layout (expected)
                mockLayout("requirement"),
                mockLayout("task")        // index 2 - second "task" layout (current)
        );
        when(entity.getRenderingLayouts()).thenReturn(layouts);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(entity, createScanContext(polarionService));
        assertTrue(issues.isEmpty());
    }

    @Test
    void testUnresolvableWorkItemsAreSkipped() {
        ModuleWrongLayoutAssignmentsRepairer repairer = new ModuleWrongLayoutAssignmentsRepairer();

        IModule entity = mock(IModule.class, RETURNS_DEEP_STUBS);

        IWorkItem unresolvable = mockWorkItem("EL-30", "task");
        when(unresolvable.isUnresolvable()).thenReturn(true);
        when(entity.getContainedWorkItems()).thenReturn(List.of(unresolvable));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(entity, createScanContext(polarionService));
        assertTrue(issues.isEmpty());
        verify(entity, never()).getStructureNodeOfWI(unresolvable);
    }

    @Test
    void testNullTypeWorkItemsAreSkipped() {
        ModuleWrongLayoutAssignmentsRepairer repairer = new ModuleWrongLayoutAssignmentsRepairer();

        IModule entity = mock(IModule.class, RETURNS_DEEP_STUBS);

        IWorkItem nullTypeItem = mock(IWorkItem.class);
        when(nullTypeItem.isUnresolvable()).thenReturn(false);
        when(nullTypeItem.getType()).thenReturn(null);
        when(entity.getContainedWorkItems()).thenReturn(List.of(nullTypeItem));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(entity, createScanContext(polarionService));
        assertTrue(issues.isEmpty());
        verify(entity, never()).getStructureNodeOfWI(nullTypeItem);
    }

    private ScanContext createScanContext(XmlRepairPolarionService polarionService) {
        lenient().when(polarionService.getTrackerService()).thenReturn(mock(ITrackerService.class));
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored = mockConstruction(EntityRenderer.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(mock(InternalReadOnlyTransaction.class));
            return new ScanContext(polarionService, List.of(), new UserConfigs(), new Report());
        }
    }

    private IWorkItem mockWorkItem(String id, String typeId) {
        IWorkItem workItem = mock(IWorkItem.class);
        lenient().when(workItem.getId()).thenReturn(id);
        lenient().when(workItem.isUnresolvable()).thenReturn(false);
        ITypeOpt type = mock(ITypeOpt.class);
        lenient().when(workItem.getType()).thenReturn(type);
        lenient().when(type.getId()).thenReturn(typeId);
        return workItem;
    }

    private IModule.IRenderingLayoutStruct mockLayout(String typeId) {
        IModule.IRenderingLayoutStruct layout = mock(IModule.IRenderingLayoutStruct.class);
        lenient().when(layout.getType()).thenReturn(typeId);
        return layout;
    }

}
