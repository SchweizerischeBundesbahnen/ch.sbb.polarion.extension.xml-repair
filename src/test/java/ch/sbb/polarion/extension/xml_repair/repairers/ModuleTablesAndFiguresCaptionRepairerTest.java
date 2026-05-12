package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.tracker.IModuleManager;
import com.polarion.alm.tracker.IModulePageLayouter;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.types.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(PlatformContextMockExtension.class)
class ModuleTablesAndFiguresCaptionRepairerTest {

    @Test
    void testDisplayNameAndDescription() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();
        assertEquals("Document content: ToT/ToF captions", repairer.getDisplayName());
        assertNotNull(repairer.getDescription());
        assertEquals("ModuleTablesAndFiguresCaptionRepairer", repairer.getRepairerId());
    }

    @Test
    void testCleanupHtmlSpaces() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        assertEquals("hello world", repairer.cleanupHtmlSpaces("hello world"));
        // Non-breaking space (U+00A0)
        assertEquals("hello world", repairer.cleanupHtmlSpaces("hello\u00A0world"));
        // Thin space (U+2009)
        assertEquals("hello world", repairer.cleanupHtmlSpaces("hello\u2009world"));
        // Zero-width space (U+200B)
        assertEquals("hello world", repairer.cleanupHtmlSpaces("hello\u200Bworld"));
        // Zero-width non-joiner (U+200C)
        assertEquals("hello world", repairer.cleanupHtmlSpaces("hello\u200Cworld"));
        // Zero-width joiner (U+200D)
        assertEquals("hello world", repairer.cleanupHtmlSpaces("hello\u200Dworld"));
        // Word joiner (U+2060)
        assertEquals("hello world", repairer.cleanupHtmlSpaces("hello\u2060world"));
        // BOM (U+FEFF)
        assertEquals("hello world", repairer.cleanupHtmlSpaces("hello\uFEFFworld"));
        // Empty string
        assertEquals("", repairer.cleanupHtmlSpaces(""));
    }

    @Test
    void testFixCaptionIdsMatchingEntry() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        String content = "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">";
        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        RepairResult result = new RepairResult(metaInfo, false);

        String fixed = repairer.fixCaptionIds(content, metaInfo, result);

        assertTrue(result.isSuccess());
        assertTrue(fixed.contains("data-sequence=\"Table 1\""));
        assertFalse(fixed.contains("data-sequence=\"Table 2\""));
    }

    @Test
    void testFixCaptionIdsNoMatch() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        String content = "\nTable 1<span data-sequence=\"Table 1\" class=\"polarion-rte-caption\">";
        IssueMetaInfo metaInfo = createRealMetaInfo("Figure 1", "Figure 2");
        RepairResult result = new RepairResult(metaInfo, false);

        String fixed = repairer.fixCaptionIds(content, metaInfo, result);

        assertFalse(result.isSuccess());
        assertEquals(content, fixed);
    }

    @Test
    void testFixCaptionIdsAlreadyAligned() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        // prefix and dataSequence are equal — this wouldn't be an issue in practice, but test the path
        String content = "\nTable 1<span data-sequence=\"Table 1\" class=\"polarion-rte-caption\">";
        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 1");
        RepairResult result = new RepairResult(metaInfo, false);

        String fixed = repairer.fixCaptionIds(content, metaInfo, result);

        // The regex won't match because prefix == dataSequence in the content itself,
        // but the metaInfo expects equal prefix/dataSequence, so the replacement happens
        assertTrue(result.isSuccess());
        assertEquals(content, fixed);
    }

    @SuppressWarnings("java:S3457") // \n is intentional here — this is HTML content, not platform-dependent output
    @ParameterizedTest
    @CsvSource({
            "Table 1, Table 1, 0",
            "Table 1, Table 2, 1"
    })
    void testScanHomepageContent(String caption, String sequence, int expectedIssues) {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getHomePageContent().getContent()).thenReturn(
                "\n%s<span data-sequence=\"%s\" class=\"polarion-rte-caption\">".formatted(caption, sequence)
        );
        when(module.getContainedWorkItems()).thenReturn(List.of());

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertEquals(expectedIssues, issues.size());
        if (expectedIssues > 0) {
            assertTrue(issues.getFirst().getDescription().contains("Misaligned"));
            assertTrue(issues.getFirst().getDescription().contains(sequence));
            assertTrue(issues.getFirst().getDescription().contains(caption));
        }
    }

    @Test
    void testScanDetectsMismatchInWorkItemField() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        // No issues in homepage
        when(module.getHomePageContent().getContent()).thenReturn("plain text");

        IWorkItem workItem = mockWorkItem();
        when(module.getContainedWorkItems()).thenReturn(List.of(workItem));

        Text fieldText = mock(Text.class);
        when(fieldText.getContent()).thenReturn(
                "\nFigure 5<span data-sequence=\"Figure 6\" class=\"polarion-rte-caption\">"
        );
        when(workItem.getValue("description")).thenReturn(fieldText);

        doReturn(Set.of("description")).when(repairer).getRenderedFieldIds(eq(module), any(), eq("task"));

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains("Figure 6"));
    }

    @Test
    void testScanDuplicateIssuesMerged() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        // Same mismatch in homepage
        when(module.getHomePageContent().getContent()).thenReturn(
                "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">"
        );

        // Same mismatch in work item
        IWorkItem workItem = mockWorkItem();
        when(module.getContainedWorkItems()).thenReturn(List.of(workItem));

        Text fieldText = mock(Text.class);
        when(fieldText.getContent()).thenReturn(
                "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">"
        );
        when(workItem.getValue("description")).thenReturn(fieldText);

        doReturn(Set.of("description")).when(repairer).getRenderedFieldIds(eq(module), any(), eq("task"));

        List<Issue> issues = repairer.scan(module, createScanContext());
        // Should be merged into a single issue
        assertEquals(1, issues.size());
    }

    @Test
    void testScanWorkItemFlagPreservedWhenTrueAlreadySet() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        // Same mismatch in homepage (sets WORK_ITEM=false first)
        when(module.getHomePageContent().getContent()).thenReturn(
                "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">"
        );

        // Same mismatch in work item (should set WORK_ITEM=true)
        IWorkItem workItem = mockWorkItem();
        when(module.getContainedWorkItems()).thenReturn(List.of(workItem));

        Text fieldText = mock(Text.class);
        when(fieldText.getContent()).thenReturn(
                "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">"
        );
        when(workItem.getValue("description")).thenReturn(fieldText);

        doReturn(Set.of("description")).when(repairer).getRenderedFieldIds(eq(module), any(), eq("task"));

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertEquals(1, issues.size());
        // The WORK_ITEM flag should be true (work item scan was last and found it)
        assertEquals(Boolean.TRUE, issues.getFirst().getRawMetaInfo().getBoolean("isWorkItem"));
    }

    @Test
    void testScanSkipsNonTextFieldValues() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getHomePageContent().getContent()).thenReturn("plain text");

        IWorkItem workItem = mockWorkItem();
        when(module.getContainedWorkItems()).thenReturn(List.of(workItem));
        // Field value is a String, not Text
        when(workItem.getValue("description")).thenReturn("just a string");

        doReturn(Set.of("description")).when(repairer).getRenderedFieldIds(eq(module), any(), eq("task"));

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanNullHomePageContent() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getHomePageContent().getContent()).thenReturn(null);
        when(module.getContainedWorkItems()).thenReturn(List.of());

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertTrue(issues.isEmpty());
    }

    @Test
    void testRepairHomepageContentOnly() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        String content = "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">";
        when(module.getHomePageContent().getContent()).thenReturn(content);
        when(module.getHomePageContent().isPlain()).thenReturn(false);

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", false);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertTrue(result.isSuccess());
        verify(module).setHomePageContent(any(Text.class));
    }

    @Test
    void testRepairHomepageContentPlainText() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        String content = "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">";
        when(module.getHomePageContent().getContent()).thenReturn(content);
        when(module.getHomePageContent().isPlain()).thenReturn(true);

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", false);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertTrue(result.isSuccess());
        verify(module).setHomePageContent(any(Text.class));
    }

    @Test
    void testRepairNoChangeInHomepage() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn("no captions here");

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", false);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertFalse(result.isSuccess());
        verify(module, never()).setHomePageContent(any(Text.class));
    }

    @Test
    void testRepairWorkItemFields() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn("no captions");

        IWorkItem workItem = mockWorkItem();
        when(module.getContainedWorkItems()).thenReturn(List.of(workItem));

        Text fieldText = mock(Text.class);
        when(fieldText.getContent()).thenReturn(
                "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">"
        );
        when(fieldText.isPlain()).thenReturn(false);
        when(workItem.getValue("description")).thenReturn(fieldText);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        doReturn(Set.of("description")).when(repairer).getRenderedFieldIds(module, polarionService, "task");

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", true);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertTrue(result.isSuccess());
        verify(workItem).setValue(eq("description"), any(Text.class));
        verify(workItem).save();
    }

    @Test
    void testRepairWorkItemFieldPlainText() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn("no captions");

        IWorkItem workItem = mockWorkItem();
        when(module.getContainedWorkItems()).thenReturn(List.of(workItem));

        Text fieldText = mock(Text.class);
        when(fieldText.getContent()).thenReturn(
                "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">"
        );
        when(fieldText.isPlain()).thenReturn(true);
        when(workItem.getValue("description")).thenReturn(fieldText);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        doReturn(Set.of("description")).when(repairer).getRenderedFieldIds(module, polarionService, "task");

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", true);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertTrue(result.isSuccess());
        verify(workItem).setValue(eq("description"), any(Text.class));
        verify(workItem).save();
    }

    @Test
    void testRepairWorkItemFieldNoChange() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn("no captions");

        IWorkItem workItem = mockWorkItem();
        when(module.getContainedWorkItems()).thenReturn(List.of(workItem));

        Text fieldText = mock(Text.class);
        when(fieldText.getContent()).thenReturn("no captions in field either");
        when(workItem.getValue("description")).thenReturn(fieldText);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        doReturn(Set.of("description")).when(repairer).getRenderedFieldIds(module, polarionService, "task");

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", true);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        repairer.repair(module, repairContext);

        verify(workItem, never()).setValue(any(), any());
        verify(workItem, never()).save();
    }

    @Test
    void testRepairSkipsWorkItemsWhenFlagFalse() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn("no captions");

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", false);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        repairer.repair(module, repairContext);

        verify(module, never()).getContainedWorkItems();
    }

    @Test
    void testGetRenderedFieldIds() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class);
        IModule.IRenderingLayoutStruct layout1 = mock(IModule.IRenderingLayoutStruct.class);
        when(layout1.getType()).thenReturn("task");
        when(layout1.getLayouter()).thenReturn("layouter1");

        IModule.IRenderingLayoutStruct layout2 = mock(IModule.IRenderingLayoutStruct.class);
        when(layout2.getType()).thenReturn("requirement");

        when(module.getRenderingLayouts()).thenReturn(List.of(layout1, layout2));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class, RETURNS_DEEP_STUBS);
        IModuleManager moduleManager = mock(IModuleManager.class);
        when(polarionService.getTrackerService().getModuleManager()).thenReturn(moduleManager);

        IModulePageLayouter layouter = mock(IModulePageLayouter.class);
        when(moduleManager.getModulePageLayouter("layouter1")).thenReturn(layouter);
        when(layouter.getRenderedFieldIds(layout1)).thenReturn(Set.of("description", "title"));

        Set<String> fieldIds = repairer.getRenderedFieldIds(module, polarionService, "task");

        assertEquals(Set.of("description", "title"), fieldIds);
    }

    @Test
    void testScanMultipleMismatchesInContent() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getHomePageContent().getContent()).thenReturn(
                """

                        Table 1<span data-sequence="Table 2" class="polarion-rte-caption">
                        Figure A<span data-sequence="Figure B" class="polarion-rte-caption">"""
        );
        when(module.getContainedWorkItems()).thenReturn(List.of());

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertEquals(2, issues.size());
    }

    @Test
    void testScanWithHtmlSpacesInPrefix() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        // Non-breaking space in prefix that when cleaned up matches dataSequence
        when(module.getHomePageContent().getContent()).thenReturn(
                "\nTable\u00A01<span data-sequence=\"Table 1\" class=\"polarion-rte-caption\">"
        );
        when(module.getContainedWorkItems()).thenReturn(List.of());

        List<Issue> issues = repairer.scan(module, createScanContext());
        // After cleanup, "Table\u00A01" becomes "Table 1" which equals dataSequence — no issue
        assertTrue(issues.isEmpty());
    }

    @Test
    void testFixCaptionIdsWithMultipleCaptions() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        String content = """

                Table 1<span data-sequence="Table 2" class="polarion-rte-caption">
                Figure A<span data-sequence="Figure B" class="polarion-rte-caption">""";

        // Only fix the first mismatch
        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        RepairResult result = new RepairResult(metaInfo, false);

        String fixed = repairer.fixCaptionIds(content, metaInfo, result);

        assertTrue(result.isSuccess());
        assertTrue(fixed.contains("data-sequence=\"Table 1\""));
        // Second caption should remain unchanged
        assertTrue(fixed.contains("data-sequence=\"Figure B\""));
    }

    // === BaseRepairer.scan(IUniqueObject, ScanContext) ===

    @Test
    void testScanViaPublicInterface() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getHomePageContent().getContent()).thenReturn("plain text");
        when(module.getContainedWorkItems()).thenReturn(List.of());

        List<Issue> issues = repairer.scan((IUniqueObject) module, createScanContext());
        assertTrue(issues.isEmpty());
    }

    // === BaseRepairer.repair(IUniqueObject, RepairContext) — revision handling ===

    @Test
    void testRepairViaPublicInterface_NullRevision_Success() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getRevision()).thenReturn(null);
        when(module.getHomePageContent().getContent()).thenReturn(
                "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">"
        );
        when(module.getHomePageContent().isPlain()).thenReturn(false);

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", false);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair((IUniqueObject) module, repairContext);

        assertTrue(result.isSuccess());
        verify(module).save();
    }

    @Test
    void testRepairViaPublicInterface_NullRevision_NoChange() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getRevision()).thenReturn(null);
        when(module.getHomePageContent().getContent()).thenReturn("no captions");

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", false);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair((IUniqueObject) module, repairContext);

        assertFalse(result.isSuccess());
        verify(module, never()).save();
    }

    @Test
    void testRepairViaPublicInterface_WithRevision_Resolvable() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule revisionModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(revisionModule.getRevision()).thenReturn("456");

        IModule headModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(headModule.isUnresolvable()).thenReturn(false);
        when(headModule.getModuleName()).thenReturn("TestModule");
        when(headModule.getHomePageContent().getContent()).thenReturn(
                "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">"
        );
        when(headModule.getHomePageContent().isPlain()).thenReturn(false);

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", false);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.getModule(any(), any())).thenReturn(headModule);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair((IUniqueObject) revisionModule, repairContext);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("HEAD revision")));
        verify(headModule).save();
    }

    @Test
    void testRepairViaPublicInterface_WithRevision_Unresolvable() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule revisionModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(revisionModule.getRevision()).thenReturn("789");

        IModule headModule = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(headModule.isUnresolvable()).thenReturn(true);
        when(headModule.getModuleName()).thenReturn("DeletedModule");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.getModule(any(), any())).thenReturn(headModule);
        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair((IUniqueObject) revisionModule, repairContext);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("unresolvable")));
    }

    // === streamModuleWorkItems filtering ===

    @Test
    void testScanSkipsUnresolvableWorkItems() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getHomePageContent().getContent()).thenReturn("plain text");

        IWorkItem unresolvable = mock(IWorkItem.class);
        when(unresolvable.isUnresolvable()).thenReturn(true);
        when(module.getContainedWorkItems()).thenReturn(List.of(unresolvable));

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertTrue(issues.isEmpty());
        verify(repairer, never()).getRenderedFieldIds(any(), any(), any());
    }

    @Test
    void testScanSkipsWorkItemsWithNullType() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getHomePageContent().getContent()).thenReturn("plain text");

        IWorkItem nullTypeItem = mock(IWorkItem.class);
        when(nullTypeItem.isUnresolvable()).thenReturn(false);
        when(nullTypeItem.getType()).thenReturn(null);
        when(module.getContainedWorkItems()).thenReturn(List.of(nullTypeItem));

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertTrue(issues.isEmpty());
        verify(repairer, never()).getRenderedFieldIds(any(), any(), any());
    }

    @Test
    void testScanSkipsHeadingTypeWorkItems() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getHomePageContent().getContent()).thenReturn("plain text");

        IWorkItem headingItem = mock(IWorkItem.class);
        when(headingItem.isUnresolvable()).thenReturn(false);
        ITypeOpt headingType = mock(ITypeOpt.class);
        when(headingItem.getType()).thenReturn(headingType);
        when(headingType.getId()).thenReturn("heading");
        when(module.getContainedWorkItems()).thenReturn(List.of(headingItem));

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertTrue(issues.isEmpty());
        verify(repairer, never()).getRenderedFieldIds(any(), any(), any());
    }

    // === Other edge cases ===

    @Test
    void testGetConfigsDefault() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();
        assertTrue(repairer.getConfigs().isEmpty());
    }

    @Test
    void testGetRenderedFieldIdsNoMatchingLayout() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class);
        IModule.IRenderingLayoutStruct layout = mock(IModule.IRenderingLayoutStruct.class);
        when(layout.getType()).thenReturn("requirement");
        when(module.getRenderingLayouts()).thenReturn(List.of(layout));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class, RETURNS_DEEP_STUBS);

        Set<String> fieldIds = repairer.getRenderedFieldIds(module, polarionService, "task");
        assertTrue(fieldIds.isEmpty());
    }

    @Test
    void testGetRenderedFieldIdsMultipleMatchingLayouts() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class);
        IModule.IRenderingLayoutStruct layout1 = mock(IModule.IRenderingLayoutStruct.class);
        when(layout1.getType()).thenReturn("task");
        when(layout1.getLayouter()).thenReturn("layouter1");

        IModule.IRenderingLayoutStruct layout2 = mock(IModule.IRenderingLayoutStruct.class);
        when(layout2.getType()).thenReturn("task");
        when(layout2.getLayouter()).thenReturn("layouter2");

        when(module.getRenderingLayouts()).thenReturn(List.of(layout1, layout2));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class, RETURNS_DEEP_STUBS);
        IModuleManager moduleManager = mock(IModuleManager.class);
        when(polarionService.getTrackerService().getModuleManager()).thenReturn(moduleManager);

        IModulePageLayouter layouterObj1 = mock(IModulePageLayouter.class);
        when(moduleManager.getModulePageLayouter("layouter1")).thenReturn(layouterObj1);
        when(layouterObj1.getRenderedFieldIds(layout1)).thenReturn(Set.of("description"));

        IModulePageLayouter layouterObj2 = mock(IModulePageLayouter.class);
        when(moduleManager.getModulePageLayouter("layouter2")).thenReturn(layouterObj2);
        when(layouterObj2.getRenderedFieldIds(layout2)).thenReturn(Set.of("title", "description"));

        Set<String> fieldIds = repairer.getRenderedFieldIds(module, polarionService, "task");
        assertEquals(Set.of("description", "title"), fieldIds);
    }

    @Test
    void testRepairWorkItemMultipleFields() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn("no captions");

        IWorkItem workItem = mockWorkItem();
        when(module.getContainedWorkItems()).thenReturn(List.of(workItem));

        Text descText = mock(Text.class);
        when(descText.getContent()).thenReturn(
                "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">"
        );
        when(descText.isPlain()).thenReturn(false);
        when(workItem.getValue("description")).thenReturn(descText);

        Text titleText = mock(Text.class);
        when(titleText.getContent()).thenReturn("no captions here");
        when(workItem.getValue("title")).thenReturn(titleText);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        doReturn(Set.of("description", "title")).when(repairer).getRenderedFieldIds(module, polarionService, "task");

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", true);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertTrue(result.isSuccess());
        verify(workItem).setValue(eq("description"), any(Text.class));
        verify(workItem, never()).setValue(eq("title"), any());
    }

    @Test
    void testScanWithMultipleWorkItemTypes() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getHomePageContent().getContent()).thenReturn("plain text");

        IWorkItem task = mockWorkItem();
        Text taskText = mock(Text.class);
        when(taskText.getContent()).thenReturn(
                "\nFigure 1<span data-sequence=\"Figure 2\" class=\"polarion-rte-caption\">"
        );
        when(task.getValue("description")).thenReturn(taskText);

        IWorkItem requirement = mock(IWorkItem.class);
        when(requirement.isUnresolvable()).thenReturn(false);
        ITypeOpt reqType = mock(ITypeOpt.class);
        when(requirement.getType()).thenReturn(reqType);
        when(reqType.getId()).thenReturn("requirement");
        Text reqText = mock(Text.class);
        when(reqText.getContent()).thenReturn("no captions");
        when(requirement.getValue("summary")).thenReturn(reqText);

        when(module.getContainedWorkItems()).thenReturn(List.of(task, requirement));

        doReturn(Set.of("description")).when(repairer).getRenderedFieldIds(eq(module), any(), eq("task"));
        doReturn(Set.of("summary")).when(repairer).getRenderedFieldIds(eq(module), any(), eq("requirement"));

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertEquals(1, issues.size());
    }

    @Test
    void testRepairWithMultipleWorkItems() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn("no captions");

        IWorkItem workItem1 = mockWorkItem();
        Text text1 = mock(Text.class);
        when(text1.getContent()).thenReturn(
                "\nTable 1<span data-sequence=\"Table 2\" class=\"polarion-rte-caption\">"
        );
        when(text1.isPlain()).thenReturn(false);
        when(workItem1.getValue("description")).thenReturn(text1);

        IWorkItem workItem2 = mockWorkItem();
        Text text2 = mock(Text.class);
        when(text2.getContent()).thenReturn("no captions");
        when(workItem2.getValue("description")).thenReturn(text2);

        when(module.getContainedWorkItems()).thenReturn(List.of(workItem1, workItem2));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        doReturn(Set.of("description")).when(repairer).getRenderedFieldIds(module, polarionService, "task");

        IssueMetaInfo metaInfo = createRealMetaInfo("Table 1", "Table 2");
        metaInfo.set("isWorkItem", true);
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertTrue(result.isSuccess());
        verify(workItem1).setValue(eq("description"), any(Text.class));
        verify(workItem1).save();
        verify(workItem2, never()).setValue(any(), any());
        verify(workItem2, never()).save();
    }

    @Test
    void testFixCaptionIdsWithHtmlEntitiesInPrefix() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        String content = "\nTable &amp; 1<span data-sequence=\"Table &amp; 2\" class=\"polarion-rte-caption\">";
        IssueMetaInfo metaInfo = createRealMetaInfo("Table &amp; 1", "Table &amp; 2");
        RepairResult result = new RepairResult(metaInfo, false);

        String fixed = repairer.fixCaptionIds(content, metaInfo, result);

        assertTrue(result.isSuccess());
        assertTrue(fixed.contains("data-sequence=\"Table &amp; 1\""));
    }

    @Test
    void testScanEmptyWorkItemFields() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getHomePageContent().getContent()).thenReturn("plain text");

        IWorkItem workItem = mockWorkItem();
        when(module.getContainedWorkItems()).thenReturn(List.of(workItem));

        // Field returns null value
        when(workItem.getValue("description")).thenReturn(null);

        doReturn(Set.of("description")).when(repairer).getRenderedFieldIds(eq(module), any(), eq("task"));

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanWorkItemFieldWithEmptyContent() {
        ModuleTablesAndFiguresCaptionRepairer repairer = spy(new ModuleTablesAndFiguresCaptionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getHomePageContent().getContent()).thenReturn("plain text");

        IWorkItem workItem = mockWorkItem();
        when(module.getContainedWorkItems()).thenReturn(List.of(workItem));

        Text fieldText = mock(Text.class);
        when(fieldText.getContent()).thenReturn(null);
        when(workItem.getValue("description")).thenReturn(fieldText);

        doReturn(Set.of("description")).when(repairer).getRenderedFieldIds(eq(module), any(), eq("task"));

        List<Issue> issues = repairer.scan(module, createScanContext());
        assertTrue(issues.isEmpty());
    }

    @Test
    void testGetRenderedFieldIdsEmptyLayouts() {
        ModuleTablesAndFiguresCaptionRepairer repairer = new ModuleTablesAndFiguresCaptionRepairer();

        IModule module = mock(IModule.class);
        when(module.getRenderingLayouts()).thenReturn(List.of());

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class, RETURNS_DEEP_STUBS);

        Set<String> fieldIds = repairer.getRenderedFieldIds(module, polarionService, "task");
        assertTrue(fieldIds.isEmpty());
    }

    private ScanContext createScanContext() {
        return RepairerTestFixtures.createScanContext(mock(XmlRepairPolarionService.class));
    }

    private IWorkItem mockWorkItem() {
        IWorkItem workItem = mock(IWorkItem.class);
        lenient().when(workItem.isUnresolvable()).thenReturn(false);
        ITypeOpt type = mock(ITypeOpt.class);
        lenient().when(workItem.getType()).thenReturn(type);
        lenient().when(type.getId()).thenReturn("task");
        return workItem;
    }

    private IssueMetaInfo createRealMetaInfo(String prefix, String dataSequence) {
        IModule module = mock(IModule.class);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getRelativePath()).thenReturn("/test/module");
        IssueMetaInfo metaInfo = IssueMetaInfo.create(module);
        metaInfo.set("prefix", prefix);
        metaInfo.set("dataSequence", dataSequence);
        return metaInfo;
    }
}
