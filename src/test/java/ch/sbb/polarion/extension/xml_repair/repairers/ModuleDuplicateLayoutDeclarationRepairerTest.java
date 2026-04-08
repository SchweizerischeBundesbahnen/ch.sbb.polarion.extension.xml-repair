package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.service.EntityRenderer;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.util.LayoutUtils;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(PlatformContextMockExtension.class)
class ModuleDuplicateLayoutDeclarationRepairerTest {

    @Test
    void testDisplayNameAndDescription() {
        ModuleDuplicateLayoutDeclarationRepairer repairer = new ModuleDuplicateLayoutDeclarationRepairer();
        assertEquals("Document content: Duplicate layout declarations", repairer.getDisplayName());
        assertNotNull(repairer.getDescription());
        assertEquals("ModuleDuplicateLayoutDeclarationRepairer", repairer.getRepairerId());
    }

    @Test
    void testNoDuplicates() {
        ModuleDuplicateLayoutDeclarationRepairer repairer = new ModuleDuplicateLayoutDeclarationRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");

        List<IModule.IRenderingLayoutStruct> layouts = List.of(
                mockLayout("task"),
                mockLayout("requirement"),
                mockLayout("issue")
        );
        when(module.getRenderingLayouts()).thenReturn(layouts);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));
        assertTrue(issues.isEmpty());
    }

    @Test
    void testDuplicatesDetectedNoFix() {
        ModuleDuplicateLayoutDeclarationRepairer repairer = new ModuleDuplicateLayoutDeclarationRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleName()).thenReturn("TestModule");

        List<IModule.IRenderingLayoutStruct> layouts = new ArrayList<>(List.of(
                mockLayout("task"),
                mockLayout("requirement"),
                mockLayout("task")
        ));
        when(module.getRenderingLayouts()).thenReturn(layouts);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertEquals(1, issues.size());
        Issue issue = issues.getFirst();
        assertTrue(issue.getDescription().contains("duplicate"));
        assertTrue(issue.getDescription().contains("task"));
    }

    @Test
    void testDuplicatesRepairedWithRepairContext() {
        try (MockedStatic<LayoutUtils> layoutUtilsMock = mockStatic(LayoutUtils.class)) {
            ModuleDuplicateLayoutDeclarationRepairer repairer = new ModuleDuplicateLayoutDeclarationRepairer();

            IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
            when(module.getProjectId()).thenReturn("elibrary");
            when(module.getModuleName()).thenReturn("TestModule");

            IModule.IRenderingLayoutStruct layout0 = mockLayout("task");
            IModule.IRenderingLayoutStruct layout1 = mockLayout("requirement");
            IModule.IRenderingLayoutStruct layout2 = mockLayout("task");

            List<IModule.IRenderingLayoutStruct> layouts = new ArrayList<>(List.of(layout0, layout1, layout2));
            when(module.getRenderingLayouts()).thenReturn(layouts);

            IWorkItem w1 = mockWorkItem("EL-1", "task");
            IWorkItem w2 = mockWorkItem("EL-2", "requirement");
            when(module.getContainedWorkItems()).thenReturn(List.of(w1, w2));
            when(module.getStructureNodeOfWI(w1).getLayout()).thenReturn(2); // wrong, should be 0
            when(module.getStructureNodeOfWI(w2).getLayout()).thenReturn(1);

            XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
            IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
            when(metaInfo.serialize()).thenReturn("serialized");
            RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs());

            RepairResult result = repairer.repair(module, repairContext);

            assertTrue(result.isSuccess());
            layoutUtilsMock.verify(() -> LayoutUtils.switchLayoutIndex(module, w1, 0));
        }
    }

    @Test
    void testHeadingWorkItemsSkippedDuringRepair() {
        ModuleDuplicateLayoutDeclarationRepairer repairer = new ModuleDuplicateLayoutDeclarationRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleName()).thenReturn("TestModule");

        List<IModule.IRenderingLayoutStruct> layouts = new ArrayList<>(List.of(
                mockLayout("task"),
                mockLayout("task")
        ));
        when(module.getRenderingLayouts()).thenReturn(layouts);

        IWorkItem heading = mockWorkItem("EL-10", "heading");
        when(module.getContainedWorkItems()).thenReturn(List.of(heading));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs());

        RepairResult result = repairer.repair(module, repairContext);

        assertTrue(result.isSuccess());
        verify(module, never()).getStructureNodeOfWI(heading);
    }

    @Test
    void testNullTypeWorkItemsSkippedDuringRepair() {
        ModuleDuplicateLayoutDeclarationRepairer repairer = new ModuleDuplicateLayoutDeclarationRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleName()).thenReturn("TestModule");

        List<IModule.IRenderingLayoutStruct> layouts = new ArrayList<>(List.of(
                mockLayout("heading"),
                mockLayout("heading")
        ));
        when(module.getRenderingLayouts()).thenReturn(layouts);

        IWorkItem nullTypeItem = mock(IWorkItem.class);
        when(nullTypeItem.getId()).thenReturn("EL-15");
        when(nullTypeItem.isUnresolvable()).thenReturn(false);
        when(nullTypeItem.getType()).thenReturn(null);
        when(module.getContainedWorkItems()).thenReturn(List.of(nullTypeItem));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs());

        RepairResult result = repairer.repair(module, repairContext);

        assertTrue(result.isSuccess());
        verify(module, never()).getStructureNodeOfWI(nullTypeItem);
    }

    @Test
    void testUnresolvableWorkItemsSkippedDuringRepair() {
        ModuleDuplicateLayoutDeclarationRepairer repairer = new ModuleDuplicateLayoutDeclarationRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleName()).thenReturn("TestModule");

        List<IModule.IRenderingLayoutStruct> layouts = new ArrayList<>(List.of(
                mockLayout("task"),
                mockLayout("task")
        ));
        when(module.getRenderingLayouts()).thenReturn(layouts);

        IWorkItem unresolvable = mockWorkItem("EL-30", "task");
        when(unresolvable.isUnresolvable()).thenReturn(true);
        when(module.getContainedWorkItems()).thenReturn(List.of(unresolvable));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs());

        RepairResult result = repairer.repair(module, repairContext);

        assertTrue(result.isSuccess());
        verify(module, never()).getStructureNodeOfWI(unresolvable);
    }

    @Test
    void testMultipleDuplicateTypes() {
        ModuleDuplicateLayoutDeclarationRepairer repairer = new ModuleDuplicateLayoutDeclarationRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleName()).thenReturn("TestModule");

        List<IModule.IRenderingLayoutStruct> layouts = new ArrayList<>(List.of(
                mockLayout("task"),
                mockLayout("requirement"),
                mockLayout("task"),
                mockLayout("requirement")
        ));
        when(module.getRenderingLayouts()).thenReturn(layouts);

        when(module.getContainedWorkItems()).thenReturn(List.of());

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertEquals(1, issues.size());
        // Description should mention both types and use plural "s"
        assertTrue(issues.getFirst().getDescription().contains("declarations"));
    }

    @Test
    void testFixLayoutIndex() {
        try (MockedStatic<LayoutUtils> layoutUtilsMock = mockStatic(LayoutUtils.class)) {
            ModuleDuplicateLayoutDeclarationRepairer repairer = new ModuleDuplicateLayoutDeclarationRepairer();

            IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
            IWorkItem workItem = mockWorkItem("EL-1", "task");

            List<IModule.IRenderingLayoutStruct> layouts = List.of(
                    mockLayout("requirement"),
                    mockLayout("task"),
                    mockLayout("task")
            );
            when(module.getRenderingLayouts()).thenReturn(layouts);
            when(module.getStructureNodeOfWI(workItem).getLayout()).thenReturn(2);

            repairer.fixLayoutIndex(module, workItem, "task");
            layoutUtilsMock.verify(() -> LayoutUtils.switchLayoutIndex(module, workItem, 1));
        }
    }

    @Test
    void testFixLayoutIndexAlreadyCorrect() {
        try (MockedStatic<LayoutUtils> layoutUtilsMock = mockStatic(LayoutUtils.class)) {
            ModuleDuplicateLayoutDeclarationRepairer repairer = new ModuleDuplicateLayoutDeclarationRepairer();

            IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
            IWorkItem workItem = mockWorkItem("EL-1", "task");

            List<IModule.IRenderingLayoutStruct> layouts = List.of(
                    mockLayout("task"),
                    mockLayout("task")
            );
            when(module.getRenderingLayouts()).thenReturn(layouts);
            when(module.getStructureNodeOfWI(workItem).getLayout()).thenReturn(0);

            repairer.fixLayoutIndex(module, workItem, "task");
            layoutUtilsMock.verify(() -> LayoutUtils.switchLayoutIndex(eq(module), eq(workItem), anyInt()), never());
        }
    }

    @Test
    void testRemoveLayoutDuplicates() {
        ModuleDuplicateLayoutDeclarationRepairer repairer = new ModuleDuplicateLayoutDeclarationRepairer();

        IModule module = mock(IModule.class);

        IModule.IRenderingLayoutStruct layout0 = mockLayout("task");
        IModule.IRenderingLayoutStruct layout1 = mockLayout("requirement");
        IModule.IRenderingLayoutStruct layout2 = mockLayout("task");
        List<IModule.IRenderingLayoutStruct> layouts = new ArrayList<>(List.of(layout0, layout1, layout2));
        when(module.getRenderingLayouts()).thenReturn(layouts);

        repairer.removeLayoutDuplicates(module, "task");

        assertEquals(2, layouts.size());
        assertTrue(layouts.contains(layout0));
        assertTrue(layouts.contains(layout1));
        assertFalse(layouts.contains(layout2));
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
        when(workItem.getId()).thenReturn(id);
        when(workItem.isUnresolvable()).thenReturn(false);
        ITypeOpt type = mock(ITypeOpt.class);
        when(workItem.getType()).thenReturn(type);
        when(type.getId()).thenReturn(typeId);
        return workItem;
    }

    private IModule.IRenderingLayoutStruct mockLayout(String typeId) {
        IModule.IRenderingLayoutStruct layout = mock(IModule.IRenderingLayoutStruct.class);
        when(layout.getType()).thenReturn(typeId);
        return layout;
    }

}
