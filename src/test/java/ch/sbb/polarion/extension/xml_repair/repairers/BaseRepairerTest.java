package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.service.EntityRenderer;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.ModuleUtils;
import com.polarion.alm.tracker.internal.ModulePagePart;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.types.Text;
import com.polarion.subterra.base.location.ILocation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import java.util.ArrayList;
import java.util.List;

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

    private ScanContext createScanContext(XmlRepairPolarionService polarionService, List<String> repairers, UserConfigs configs, Report report) {
        ITrackerService trackerService = mock(ITrackerService.class);
        lenient().when(polarionService.getTrackerService()).thenReturn(trackerService);
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored = mockConstruction(EntityRenderer.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);
            return new ScanContext(polarionService, repairers, configs, report);
        }
    }

    // ---- Utility method tests ----

    @Test
    void testIsEmptyParagraphTrue() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<p>  </p>");
        assertTrue(repairer.isEmptyParagraph(part));

        when(part.getElementHtml()).thenReturn("<p>\n</p>");
        assertTrue(repairer.isEmptyParagraph(part));

        when(part.getElementHtml()).thenReturn("<p class=\"test\"> \t </p>");
        assertTrue(repairer.isEmptyParagraph(part));

        when(part.getElementHtml()).thenReturn("<p></p>");
        assertTrue(repairer.isEmptyParagraph(part));
    }

    @Test
    void testIsEmptyParagraphFalse() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<p>Some text</p>");
        assertFalse(repairer.isEmptyParagraph(part));

        when(part.getElementHtml()).thenReturn("<div></div>");
        assertFalse(repairer.isEmptyParagraph(part));

        when(part.getElementHtml()).thenReturn("<p>  text  </p>");
        assertFalse(repairer.isEmptyParagraph(part));
    }

    @Test
    void testIsMacroTrue() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<div class=\"polarion-dle-wiki-block\">macro content</div>");
        assertTrue(repairer.isMacro(part));

        when(part.getElementHtml()).thenReturn("<div id=\"m1\" class=\"polarion-dle-wiki-block other-class\">content</div>");
        assertTrue(repairer.isMacro(part));
    }

    @Test
    void testIsMacroFalse() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<div class=\"regular-div\">content</div>");
        assertFalse(repairer.isMacro(part));

        when(part.getElementHtml()).thenReturn("<p class=\"polarion-dle-wiki-block\">content</p>");
        assertFalse(repairer.isMacro(part));

        when(part.getElementHtml()).thenReturn("<span>text</span>");
        assertFalse(repairer.isMacro(part));
    }

    @Test
    void testIsPageBreakTrue() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<div class=\"page\" name=page_break style=\"page-break-before:always\"></div>");
        assertTrue(repairer.isPageBreak(part));

        when(part.getElementHtml()).thenReturn("<div name=page_break></div>");
        assertTrue(repairer.isPageBreak(part));
    }

    @Test
    void testIsPageBreakFalse() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<div class=\"regular-div\">content</div>");
        assertFalse(repairer.isPageBreak(part));

        when(part.getElementHtml()).thenReturn("<div name=other_break></div>");
        assertFalse(repairer.isPageBreak(part));
    }

    // ---- findDesiredHeadingPosition tests ----

    @Test
    void testFindDesiredHeadingPositionEmptyList() {
        TestableRepairer repairer = new TestableRepairer();
        assertEquals(0, repairer.findDesiredHeadingPosition(List.of()));
    }

    @Test
    void testFindDesiredHeadingPositionNoMacro() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart heading = mockPart(false, false, false, false);
        assertEquals(0, repairer.findDesiredHeadingPosition(List.of(heading)));
    }

    @Test
    void testFindDesiredHeadingPositionMacroThenPageBreak() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart macro = mockPart(false, true, false, false);
        ModulePagePart pageBreak = mockPart(false, false, true, false);
        ModulePagePart heading = mockPart(true, false, false, false);

        assertEquals(2, repairer.findDesiredHeadingPosition(List.of(macro, pageBreak, heading)));
    }

    @Test
    void testFindDesiredHeadingPositionPageBreakWithoutMacro() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart pageBreak = mockPart(false, false, true, false);
        ModulePagePart heading = mockPart(true, false, false, false);

        assertEquals(0, repairer.findDesiredHeadingPosition(List.of(pageBreak, heading)));
    }

    @Test
    void testFindDesiredHeadingPositionEmptyParagraphsThenMacroThenPageBreak() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart emptyP = mockPart(false, false, false, true);
        ModulePagePart macro = mockPart(false, true, false, false);
        ModulePagePart pageBreak = mockPart(false, false, true, false);

        assertEquals(3, repairer.findDesiredHeadingPosition(List.of(emptyP, macro, pageBreak)));
    }

    @Test
    void testFindDesiredHeadingPositionNonEmptyParagraphBreaksLoop() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart nonEmpty = mockPart(false, false, false, false);
        ModulePagePart macro = mockPart(false, true, false, false);

        assertEquals(0, repairer.findDesiredHeadingPosition(List.of(nonEmpty, macro)));
    }

    // ---- reorderHeadingToPosition tests ----

    @Test
    void testReorderHeadingToPositionMovesToFront() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart work1 = mockPart(false, false, false, false);
        ModulePagePart work2 = mockPart(false, false, false, false);
        ModulePagePart heading = mockPart(true, false, false, false);

        List<ModulePagePart> parts = new ArrayList<>(List.of(work1, work2, heading));
        repairer.reorderHeadingToPosition(parts, 0);

        assertEquals(heading, parts.get(0));
        assertEquals(work1, parts.get(1));
        assertEquals(work2, parts.get(2));
    }

    @Test
    void testReorderHeadingToPositionAlreadyAtDesiredPosition() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart heading = mockPart(true, false, false, false);
        ModulePagePart work1 = mockPart(false, false, false, false);

        List<ModulePagePart> parts = new ArrayList<>(List.of(heading, work1));
        repairer.reorderHeadingToPosition(parts, 0);

        assertEquals(heading, parts.get(0));
        assertEquals(work1, parts.get(1));
    }

    @Test
    void testReorderHeadingToMiddlePosition() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart macro = mockPart(false, true, false, false);
        ModulePagePart pageBreak = mockPart(false, false, true, false);
        ModulePagePart work1 = mockPart(false, false, false, false);
        ModulePagePart heading = mockPart(true, false, false, false);

        List<ModulePagePart> parts = new ArrayList<>(List.of(macro, pageBreak, work1, heading));
        repairer.reorderHeadingToPosition(parts, 2);

        assertEquals(macro, parts.get(0));
        assertEquals(pageBreak, parts.get(1));
        assertEquals(heading, parts.get(2));
        assertEquals(work1, parts.get(3));
    }

    @Test
    void testReorderHeadingNoHeadingPresent() {
        TestableRepairer repairer = new TestableRepairer();
        ModulePagePart work1 = mockPart(false, false, false, false);
        ModulePagePart work2 = mockPart(false, false, false, false);

        List<ModulePagePart> parts = new ArrayList<>(List.of(work1, work2));
        repairer.reorderHeadingToPosition(parts, 0);

        assertEquals(work1, parts.get(0));
        assertEquals(work2, parts.get(1));
    }

    // ---- moveHeadingToProperPosition test ----

    @Test
    void testMoveHeadingToProperPosition() {
        TestableRepairer repairer = new TestableRepairer();
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);

        Text htmlText = mock(Text.class);
        when(module.getHomePageContent()).thenReturn(htmlText);
        when(htmlText.convertToHTML()).thenReturn(htmlText);
        when(htmlText.getContent()).thenReturn("<div>content</div>");
        when(module.getProjectId()).thenReturn("proj");

        ModulePagePart part = mock(ModulePagePart.class);
        when(part.isHeading()).thenReturn(true);
        when(part.getElementHtml()).thenReturn("<h1>Title</h1>");
        doAnswer(inv -> {
            ((StringBuilder) inv.getArgument(0)).append("<h1>Title</h1>");
            return null;
        }).when(part).append(any(StringBuilder.class));

        try (MockedStatic<ModuleUtils> moduleUtilsMock = mockStatic(ModuleUtils.class)) {
            moduleUtilsMock.when(() -> ModuleUtils.getContentPartsNew("<div>content</div>", "proj"))
                    .thenReturn(new ArrayList<>(List.of(part)));

            repairer.moveHeadingToProperPosition(module);

            verify(module).setHomePageContent(Text.html("<h1>Title</h1>"));
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
    void testScanModuleWithoutRevisionNoWarning() {
        Issue issue = createDummyIssue();
        ScanRoutingRepairer repairer = new ScanRoutingRepairer(List.of(issue));

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getRevision()).thenReturn(null);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), new Report());

        List<Issue> result = repairer.scan((IUniqueObject) module, context);

        assertEquals(1, result.size());
        assertTrue(result.getFirst().getWarnings().isEmpty());
    }

    @Test
    void testScanModuleWithRevisionAddsWarningToIssues() {
        Issue issue = createDummyIssue();
        ScanRoutingRepairer repairer = new ScanRoutingRepairer(List.of(issue));

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getRevision()).thenReturn("42");
        when(module.getModuleName()).thenReturn("TestDoc");
        ILocation location = mock(ILocation.class);
        when(module.getModuleLocation()).thenReturn(location);
        when(location.removeRevision()).thenReturn(location);

        IModule headModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(headModule.isUnresolvable()).thenReturn(false);
        when(headModule.getModuleName()).thenReturn("TestDoc");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.getModule(module.getProject(), location)).thenReturn(headModule);
        Report report = new Report();
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), report);

        List<Issue> result = repairer.scan((IUniqueObject) module, context);

        assertEquals(1, result.size());
        assertFalse(result.getFirst().getWarnings().isEmpty());
        assertTrue(result.getFirst().getWarnings().getFirst().contains("HEAD revision was loaded"));
        assertTrue(result.getFirst().getWarnings().getFirst().contains("rev.42"));
        assertTrue(report.toString().contains("HEAD revision was loaded"));
    }

    @Test
    void testScanModuleWithRevisionUnresolvableReturnsEmpty() {
        ScanRoutingRepairer repairer = new ScanRoutingRepairer(List.of());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getRevision()).thenReturn("42");
        when(module.getModuleName()).thenReturn("DeletedDoc");
        ILocation location = mock(ILocation.class);
        when(module.getModuleLocation()).thenReturn(location);
        when(location.removeRevision()).thenReturn(location);

        IModule headModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(headModule.isUnresolvable()).thenReturn(true);
        when(headModule.getModuleName()).thenReturn("DeletedDoc");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.getModule(module.getProject(), location)).thenReturn(headModule);
        Report report = new Report();
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), report);

        List<Issue> result = repairer.scan((IUniqueObject) module, context);

        assertTrue(result.isEmpty());
        assertTrue(report.toString().contains("unresolvable"));
        assertFalse(context.globalWarnings().isEmpty());
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
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());

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
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());

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
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());

        RepairResult result = repairer.repair((IUniqueObject) module, context);

        assertTrue(result.isSuccess());
        verify(module).save();
    }

    @Test
    void testRepairModuleWithRevisionSavesHeadModule() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairResult successResult = new RepairResult(metaInfo, true);
        RepairRoutingRepairer repairer = new RepairRoutingRepairer(successResult);

        IModule originalModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(originalModule.getRevision()).thenReturn("42");
        when(originalModule.getModuleName()).thenReturn("TestDoc");
        ILocation location = mock(ILocation.class);
        when(originalModule.getModuleLocation()).thenReturn(location);
        when(location.removeRevision()).thenReturn(location);

        IModule headModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(headModule.isUnresolvable()).thenReturn(false);
        when(headModule.getModuleName()).thenReturn("TestDoc");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.getModule(originalModule.getProject(), location)).thenReturn(headModule);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());

        RepairResult result = repairer.repair((IUniqueObject) originalModule, context);

        assertTrue(result.isSuccess());
        verify(headModule).save();
        verify(originalModule, never()).save();
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("HEAD revision was loaded")));
    }

    @Test
    void testRepairModuleWithRevisionUnresolvable() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairResult successResult = new RepairResult(metaInfo, true);
        RepairRoutingRepairer repairer = new RepairRoutingRepairer(successResult);

        IModule originalModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(originalModule.getRevision()).thenReturn("42");
        ILocation location = mock(ILocation.class);
        when(originalModule.getModuleLocation()).thenReturn(location);
        when(location.removeRevision()).thenReturn(location);

        IModule headModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(headModule.isUnresolvable()).thenReturn(true);
        when(headModule.getModuleName()).thenReturn("DeletedDoc");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.getModule(originalModule.getProject(), location)).thenReturn(headModule);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());

        RepairResult result = repairer.repair((IUniqueObject) originalModule, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("unresolvable")));
        verify(originalModule, never()).save();
        verify(headModule, never()).save();
    }

    @Test
    void testRepairModuleWithRevisionAddsWarningToResult() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairResult failResult = new RepairResult(metaInfo, false);
        RepairRoutingRepairer repairer = new RepairRoutingRepairer(failResult);

        IModule originalModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(originalModule.getRevision()).thenReturn("99");
        when(originalModule.getModuleName()).thenReturn("SomeDoc");
        ILocation location = mock(ILocation.class);
        when(originalModule.getModuleLocation()).thenReturn(location);
        when(location.removeRevision()).thenReturn(location);

        IModule headModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(headModule.isUnresolvable()).thenReturn(false);
        when(headModule.getModuleName()).thenReturn("SomeDoc");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.getModule(originalModule.getProject(), location)).thenReturn(headModule);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());

        RepairResult result = repairer.repair((IUniqueObject) originalModule, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("rev.99")));
        verify(originalModule, never()).save();
    }

    @Test
    void testRepairDefaultThrowsForUnsupportedType() {
        TestableRepairer repairer = new TestableRepairer();
        IWorkflowObject entity = mock(IWorkflowObject.class);
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());

        assertThrows(IllegalArgumentException.class, () -> repairer.repair((IUniqueObject) entity, context));
    }

    // ---- Helper ----

    private Issue createDummyIssue() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        BaseRepairer repairer = mock(BaseRepairer.class);
        lenient().when(repairer.getRepairerId()).thenReturn("DummyRepairer");
        return new Issue(metaInfo, repairer, "dummy");
    }

    private ModulePagePart mockPart(boolean isHeading, boolean isMacro, boolean isPageBreak, boolean isEmptyParagraph) {
        ModulePagePart part = mock(ModulePagePart.class);
        when(part.isHeading()).thenReturn(isHeading);
        if (isMacro) {
            when(part.getElementHtml()).thenReturn("<div class=\"polarion-dle-wiki-block\">macro</div>");
        } else if (isPageBreak) {
            when(part.getElementHtml()).thenReturn("<div name=page_break></div>");
        } else if (isEmptyParagraph) {
            when(part.getElementHtml()).thenReturn("<p>  </p>");
        } else {
            when(part.getElementHtml()).thenReturn("<div class=\"workItem\">content</div>");
        }
        return part;
    }
}
