package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.core.util.types.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(PlatformContextMockExtension.class)
class ModuleNonExistentWorkItemsRepairerTest {

    private static final String MODULE_PROJECT = "elibrary";

    // --- metadata ---

    @Test
    void testDisplayName() {
        assertEquals("Document: Non-existent Work Items", ModuleNonExistentWorkItemsRepairer.NAME);
        assertEquals(ModuleNonExistentWorkItemsRepairer.NAME, new ModuleNonExistentWorkItemsRepairer().getDisplayName());
    }

    @Test
    void testDescription() {
        String description = new ModuleNonExistentWorkItemsRepairer().getDescription();
        assertNotNull(description);
        assertTrue(description.toLowerCase().contains("work item"));
    }

    @Test
    void testRepairerId() {
        assertEquals("ModuleNonExistentWorkItemsRepairer",
                new ModuleNonExistentWorkItemsRepairer().getRepairerId());
    }

    // --- scan() ---

    @Test
    void testScanFindsNonExistentWorkItemWithExplicitProject() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(
                "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(eq("drivepilot"), eq("EL-1"), isNull())).thenReturn(false);

        ScanContext scanContext = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(module, scanContext);

        assertEquals(1, issues.size());
        Issue issue = issues.getFirst();
        assertEquals("Work item 'EL-1' does not exist in the project 'drivepilot'.", issue.getDescription());
        assertEquals("ModuleNonExistentWorkItemsRepairer", issue.getRepairer());
    }

    @Test
    void testScanFindsNonExistentWorkItemUsingModuleProjectWhenNoExplicitProject() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(
                "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-2\"></div>");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(eq(MODULE_PROJECT), eq("EL-2"), isNull())).thenReturn(false);

        ScanContext scanContext = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(module, scanContext);

        assertEquals(1, issues.size());
        assertEquals("Work item 'EL-2' does not exist in the project 'elibrary'.", issues.getFirst().getDescription());
    }

    @Test
    void testScanReturnsNoIssuesWhenWorkItemsExist() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(
                "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=elibrary\"></div>" +
                        "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-2\"></div>");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(eq(MODULE_PROJECT), anyString(), isNull())).thenReturn(true);

        ScanContext scanContext = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(module, scanContext);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanWithEmptyContentReturnsNoIssues() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn(null);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext scanContext = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(module, scanContext);
        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanFindsMultipleNonExistentWorkItems() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(
                "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>" +
                        "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-2|project=drivepilot\"></div>" +
                        "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-3|project=elibrary\"></div>");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(eq("drivepilot"), anyString(), isNull())).thenReturn(false);
        when(polarionService.isWorkItemExists(eq("elibrary"), anyString(), isNull())).thenReturn(true);

        ScanContext scanContext = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(module, scanContext);

        assertEquals(2, issues.size());
        assertTrue(issues.stream().map(Issue::getDescription).toList().containsAll(List.of(
                "Work item 'EL-1' does not exist in the project 'drivepilot'.",
                "Work item 'EL-2' does not exist in the project 'drivepilot'."
        )));
    }

    @Test
    void testScanSkipsMacroWithoutIdParam() {
        // Without an `id` param, workItemId is null and the issue must not be reported.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(
                "<div id=\"polarion_wiki macro name=module-workitem;params=project=drivepilot\"></div>");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext scanContext = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(module, scanContext);
        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanStampsLinkInIssueMetaInfo() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String linkDiv = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>";
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(linkDiv);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(eq("drivepilot"), eq("EL-1"), isNull())).thenReturn(false);

        ScanContext scanContext = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(module, scanContext);

        assertEquals(1, issues.size());
        assertEquals(linkDiv, issues.getFirst().getRawMetaInfo().getString("link"));
    }

    // --- repair() ---

    @Test
    void testRepairLinkNoLongerInContent() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn("<p>Empty doc, no macros here.</p>");

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("link")).thenReturn(
                "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>");

        RepairContext context = new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());
        RepairResult result = repairer.repair(module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().contains(
                "Work item was not found in the content, possibly it was already fixed or the content was changed since the scan."));
        verify(module, never()).setHomePageContent(any(Text.class));
    }

    @Test
    void testRepairSucceedsWhenItemExistsInModuleProject() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>";
        String html = "<p>Before</p>" + link + "<p>After</p>";

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(html);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(eq(MODULE_PROJECT), eq("EL-1"), isNull())).thenReturn(true);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("link")).thenReturn(link);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().isEmpty());

        String expectedFixedHtml = "<p>Before</p>" +
                "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"></div>" +
                "<p>After</p>";
        verify(module).setHomePageContent(Text.html(expectedFixedHtml));
    }

    @Test
    void testRepairNotPossibleWhenProjectParamMissing() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"></div>";
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(link);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("link")).thenReturn(link);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().contains("Issue cannot be repaired automatically."));
        verify(module, never()).setHomePageContent(any(Text.class));
    }

    @Test
    void testRepairNotPossibleWhenWorkItemMissingInModuleProject() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>";
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(link);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(eq(MODULE_PROJECT), eq("EL-1"), isNull())).thenReturn(false);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("link")).thenReturn(link);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().contains("Issue cannot be repaired automatically."));
        verify(module, never()).setHomePageContent(any(Text.class));
    }

    @Test
    void testRepairSkipsOtherMacrosInContent() {
        // Only the link matching linkToFix should be modified; sibling macros are left alone.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String targetLink = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>";
        String otherLink = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-9|project=elsewhere\"></div>";
        String html = targetLink + otherLink;

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(html);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(eq(MODULE_PROJECT), eq("EL-1"), isNull())).thenReturn(true);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("link")).thenReturn(targetLink);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertTrue(result.isSuccess());
        String expectedFixedHtml = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"></div>" + otherLink;
        verify(module).setHomePageContent(Text.html(expectedFixedHtml));
    }

    // --- parseParams() ---

    @Test
    void testParseParamsSimple() {
        Map<String, String> params = new ModuleNonExistentWorkItemsRepairer().parseParams("id=EL-1|project=drivepilot");
        assertEquals(2, params.size());
        assertEquals("EL-1", params.get("id"));
        assertEquals("drivepilot", params.get("project"));
    }

    @Test
    void testParseParamsSkipsEmptyValue() {
        // `id=` has no value after `=`, so the entry must be dropped.
        Map<String, String> params = new ModuleNonExistentWorkItemsRepairer().parseParams("id=|project=drivepilot");
        assertEquals(1, params.size());
        assertEquals("drivepilot", params.get("project"));
    }

    @Test
    void testParseParamsSkipsLeadingEquals() {
        // `=foo` has the `=` at index 0, so the entry must be dropped.
        Map<String, String> params = new ModuleNonExistentWorkItemsRepairer().parseParams("=foo|id=EL-1");
        assertEquals(1, params.size());
        assertEquals("EL-1", params.get("id"));
    }

    @Test
    void testParseParamsSkipsPartWithoutEquals() {
        // A part without `=` must be dropped.
        Map<String, String> params = new ModuleNonExistentWorkItemsRepairer().parseParams("lonely|id=EL-1");
        assertEquals(1, params.size());
        assertEquals("EL-1", params.get("id"));
    }

    @Test
    void testParseParamsEmptyString() {
        // An empty string splits into one empty part; the empty part has no `=` and is dropped.
        Map<String, String> params = new ModuleNonExistentWorkItemsRepairer().parseParams("");
        assertTrue(params.isEmpty());
    }
}
