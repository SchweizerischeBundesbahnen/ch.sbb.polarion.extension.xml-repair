package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.types.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(PlatformContextMockExtension.class)
class ModuleNonExistentWorkItemsRepairerTest {

    private static final String MODULE_PROJECT = "elibrary";
    private static final String EXTERNAL_PROJECT = "drivepilot";
    private static final String LINK_KEY = "link";
    private static final String PROJECT_ID_PARAM_KEY = "projectIdParam";
    private static final String WORK_ITEM_ID_PARAM_KEY = "workItemIdParam";
    private static final String EXTERNAL_PARAM_KEY = "isExternalParam";

    private static final String INVALID_MSG_PREFIX = "Invalid work item declaration in the document body: '";
    private static final String CANNOT_REPAIR_MSG = "Issue cannot be repaired automatically.";
    private static final String LINK_GONE_MSG = "Work item was not found in the content, possibly it was already fixed or the content was changed since the scan.";

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
        assertTrue(description.toLowerCase().contains("invalid"));
    }

    @Test
    void testRepairerId() {
        assertEquals("ModuleNonExistentWorkItemsRepairer",
                new ModuleNonExistentWorkItemsRepairer().getRepairerId());
    }

    // --- scan() ---

    @Test
    void testScanFlagsMacroWithoutIdAsInvalid() {
        // Macro has no `id` param at all -> "Invalid work item declaration".
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=project=drivepilot\"></div>";
        IModule module = stubModule(link);

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));

        assertEquals(1, issues.size());
        assertEquals(INVALID_MSG_PREFIX + link + "'.", issues.getFirst().getDescription());
        IssueMetaInfo meta = issues.getFirst().getRawMetaInfo();
        assertEquals("", meta.getString(WORK_ITEM_ID_PARAM_KEY));
        assertEquals("drivepilot", meta.getString(PROJECT_ID_PARAM_KEY));
        assertEquals(Boolean.FALSE, meta.getBoolean(EXTERNAL_PARAM_KEY));
    }

    @Test
    void testScanFlagsMacroWithEmptyIdAsInvalid() {
        // Macro has `id=` (empty value) -> parseParams drops it -> "Invalid work item declaration".
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=|project=drivepilot\"></div>";
        IModule module = stubModule(link);

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));

        assertEquals(1, issues.size());
        assertEquals(INVALID_MSG_PREFIX + link + "'.", issues.getFirst().getDescription());
    }

    @Test
    void testScanFlagsInternalWorkItemMissingFromModule() {
        // No explicit project. The work item isn't part of this module -> not in getAllWorkItems() -> flagged.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"></div>";
        IModule module = stubModule(link, Collections.emptyList());

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));

        assertEquals(1, issues.size());
        // No explicit project param -> message uses workItemId alone (no `project/id` format).
        assertEquals("Work item 'EL-1' doesn't exist or doesn't belong to the current document.",
                issues.getFirst().getDescription());

        IssueMetaInfo meta = issues.getFirst().getRawMetaInfo();
        assertEquals(link, meta.getString(LINK_KEY));
        assertEquals("EL-1", meta.getString(WORK_ITEM_ID_PARAM_KEY));
        assertEquals("", meta.getString(PROJECT_ID_PARAM_KEY));
        assertEquals(Boolean.FALSE, meta.getBoolean(EXTERNAL_PARAM_KEY));
    }

    @Test
    void testScanFlagsInternalWorkItemWithExplicitProjectMissingFromModule() {
        // Explicit project given. The combo (project, id) isn't in the module's work items -> flagged with `project/id` format.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>";
        IModule module = stubModule(link, Collections.emptyList());

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));

        assertEquals(1, issues.size());
        assertEquals("Work item 'drivepilot/EL-1' doesn't exist or doesn't belong to the current document.",
                issues.getFirst().getDescription());

        IssueMetaInfo meta = issues.getFirst().getRawMetaInfo();
        assertEquals("EL-1", meta.getString(WORK_ITEM_ID_PARAM_KEY));
        assertEquals("drivepilot", meta.getString(PROJECT_ID_PARAM_KEY));
        assertEquals(Boolean.FALSE, meta.getBoolean(EXTERNAL_PARAM_KEY));
    }

    @Test
    void testScanFlagsInternalWorkItemBelongingToDifferentModule() {
        // The work item is in module.getAllWorkItems() but its own getModule() is a DIFFERENT module -> flagged for internal refs.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"></div>";
        IModule otherModule = mock(IModule.class);
        IWorkItem wi = stubWorkItem("EL-1", MODULE_PROJECT, otherModule, false);

        IModule module = stubModule(link, List.of(wi));

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));

        assertEquals(1, issues.size());
        assertEquals("Work item 'EL-1' doesn't exist or doesn't belong to the current document.",
                issues.getFirst().getDescription());
    }

    @Test
    void testScanFlagsUnresolvableInternalWorkItem() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"></div>";
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(link);
        IWorkItem wi = stubWorkItem("EL-1", MODULE_PROJECT, module, true);
        when(module.getAllWorkItems()).thenReturn(List.of(wi));

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));

        assertEquals(1, issues.size());
        assertEquals("Work item 'EL-1' doesn't exist or doesn't belong to the current document.",
                issues.getFirst().getDescription());
    }

    @Test
    void testScanAcceptsValidInternalWorkItem() {
        // Work item in module's all-work-items, belongs to this module, resolvable -> no issue.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"></div>";
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(link);
        IWorkItem wi = stubWorkItem("EL-1", MODULE_PROJECT, module, false);
        when(module.getAllWorkItems()).thenReturn(List.of(wi));

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));
        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanAcceptsExternalReferenceInDifferentModule() {
        // external=true -> the "belongs to current module" check is skipped. Work item exists & is resolvable -> no issue.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot|external=true\"></div>";
        IModule otherModule = mock(IModule.class);
        IWorkItem wi = stubWorkItem("EL-1", EXTERNAL_PROJECT, otherModule, false);

        IModule module = stubModule(link, List.of(wi));

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));
        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanFlagsExternalReferenceWhenWorkItemMissing() {
        // external=true but work item isn't in module.getAllWorkItems() -> still flagged.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot|external=true\"></div>";
        IModule module = stubModule(link, Collections.emptyList());

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));

        assertEquals(1, issues.size());
        assertEquals("Work item 'drivepilot/EL-1' doesn't exist or doesn't belong to the current document.",
                issues.getFirst().getDescription());
        assertEquals(Boolean.TRUE, issues.getFirst().getRawMetaInfo().getBoolean(EXTERNAL_PARAM_KEY));
    }

    @Test
    void testScanFlagsExternalReferenceWhenUnresolvable() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot|external=true\"></div>";
        IModule otherModule = mock(IModule.class);
        IWorkItem wi = stubWorkItem("EL-1", EXTERNAL_PROJECT, otherModule, true);

        IModule module = stubModule(link, List.of(wi));

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));
        assertEquals(1, issues.size());
        assertEquals("Work item 'drivepilot/EL-1' doesn't exist or doesn't belong to the current document.",
                issues.getFirst().getDescription());
    }

    @Test
    void testScanWithEmptyContentReturnsNoIssues() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn(null);

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));
        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanFindsMultipleIssues() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String invalidLink = "<div id=\"polarion_wiki macro name=module-workitem;params=project=drivepilot\"></div>";
        String missingLink = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-2|project=drivepilot\"></div>";
        String validLink = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-3\"></div>";
        String html = invalidLink + missingLink + validLink;

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(html);
        IWorkItem validWi = stubWorkItem("EL-3", MODULE_PROJECT, module, false);
        when(module.getAllWorkItems()).thenReturn(List.of(validWi));

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));

        assertEquals(2, issues.size());
        assertTrue(issues.stream().map(Issue::getDescription).toList().containsAll(List.of(
                INVALID_MSG_PREFIX + invalidLink + "'.",
                "Work item 'drivepilot/EL-2' doesn't exist or doesn't belong to the current document."
        )));
    }

    @Test
    void testScanStampsLinkAndParamsInMetaInfo() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot|external=true\"></div>";
        IModule module = stubModule(link, Collections.emptyList());

        List<Issue> issues = repairer.scan(module, createScanContext(mock(XmlRepairPolarionService.class)));

        assertEquals(1, issues.size());
        IssueMetaInfo meta = issues.getFirst().getRawMetaInfo();
        assertEquals(link, meta.getString(LINK_KEY));
        assertEquals("EL-1", meta.getString(WORK_ITEM_ID_PARAM_KEY));
        assertEquals("drivepilot", meta.getString(PROJECT_ID_PARAM_KEY));
        assertEquals(Boolean.TRUE, meta.getBoolean(EXTERNAL_PARAM_KEY));
    }

    // --- repair() ---

    @Test
    void testRepairLinkNoLongerInContent() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn("<p>Empty doc, no macros here.</p>");

        IssueMetaInfo metaInfo = mockMetaInfo(
                "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>",
                "EL-1", "drivepilot", false);

        RepairContext context = new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());
        RepairResult result = repairer.repair(module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().contains(LINK_GONE_MSG));
        verify(module, never()).setHomePageContent(any(Text.class));
    }

    @Test
    void testRepairWithNullContentTreatsAsEmpty() {
        // null content -> getEmptyIfNull -> "" -> link not contained -> early-return warning.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent().getContent()).thenReturn(null);

        IssueMetaInfo metaInfo = mockMetaInfo(
                "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>",
                "EL-1", "drivepilot", false);

        RepairContext context = new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());
        RepairResult result = repairer.repair(module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().contains(LINK_GONE_MSG));
    }

    @Test
    void testRepairSucceedsWhenItemExistsInModuleProject() {
        // Internal ref, work item exists in module project and is resolvable -> strip |project=... and persist.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>";
        String html = "<p>Before</p>" + link + "<p>After</p>";

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(html);
        IWorkItem wi = stubWorkItem("EL-1", MODULE_PROJECT, module, false);
        when(module.getAllWorkItems()).thenReturn(List.of(wi));

        IssueMetaInfo metaInfo = mockMetaInfo(link, "EL-1", "drivepilot", false);
        RepairContext context = new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().isEmpty());

        String expectedFixedHtml = "<p>Before</p>" +
                "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"></div>" +
                "<p>After</p>";
        verify(module).setHomePageContent(Text.html(expectedFixedHtml));
    }

    @Test
    void testRepairRefusedWhenExternal() {
        // external=true short-circuits past the repair attempt.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot|external=true\"></div>";
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(link);

        IssueMetaInfo metaInfo = mockMetaInfo(link, "EL-1", "drivepilot", true);
        RepairContext context = new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().contains(CANNOT_REPAIR_MSG));
        verify(module, never()).setHomePageContent(any(Text.class));
        verify(module, never()).getAllWorkItems();
    }

    @Test
    void testRepairRefusedWhenWorkItemIdEmpty() {
        // The "invalid declaration" scan-only branch produces a meta info with empty workItemId -> repair cannot fix anything.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=project=drivepilot\"></div>";
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(link);

        IssueMetaInfo metaInfo = mockMetaInfo(link, "", "drivepilot", false);
        RepairContext context = new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().contains(CANNOT_REPAIR_MSG));
        verify(module, never()).setHomePageContent(any(Text.class));
        verify(module, never()).getAllWorkItems();
    }

    @Test
    void testRepairRefusedWhenProjectIdParamEmpty() {
        // No explicit project on the original macro -> nothing to strip -> repair refused.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"></div>";
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(link);

        IssueMetaInfo metaInfo = mockMetaInfo(link, "EL-1", "", false);
        RepairContext context = new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().contains(CANNOT_REPAIR_MSG));
        verify(module, never()).setHomePageContent(any(Text.class));
        verify(module, never()).getAllWorkItems();
    }

    @Test
    void testRepairRefusedWhenWorkItemNotInModuleProject() {
        // The work item isn't part of the module project either -> nothing to do.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>";
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(link);
        when(module.getAllWorkItems()).thenReturn(Collections.emptyList());

        IssueMetaInfo metaInfo = mockMetaInfo(link, "EL-1", "drivepilot", false);
        RepairContext context = new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().contains(CANNOT_REPAIR_MSG));
        verify(module, never()).setHomePageContent(any(Text.class));
    }

    @Test
    void testRepairRefusedWhenWorkItemUnresolvable() {
        // Work item is in module's project but isUnresolvable() -> repair refused.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>";
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(link);
        IWorkItem wi = stubWorkItem("EL-1", MODULE_PROJECT, module, true);
        when(module.getAllWorkItems()).thenReturn(List.of(wi));

        IssueMetaInfo metaInfo = mockMetaInfo(link, "EL-1", "drivepilot", false);
        RepairContext context = new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().contains(CANNOT_REPAIR_MSG));
        verify(module, never()).setHomePageContent(any(Text.class));
    }

    @Test
    void testRepairRefusedWhenStampedProjectParamNotFoundInLink() {
        // Defensive: stamped projectIdParam doesn't actually appear in the link -> fixedLink == linkToFix -> no replacement.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String link = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"></div>";
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(link);
        IWorkItem wi = stubWorkItem("EL-1", MODULE_PROJECT, module, false);
        when(module.getAllWorkItems()).thenReturn(List.of(wi));

        IssueMetaInfo metaInfo = mockMetaInfo(link, "EL-1", "drivepilot", false);
        RepairContext context = new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().contains(CANNOT_REPAIR_MSG));
        verify(module, never()).setHomePageContent(any(Text.class));
    }

    @Test
    void testRepairSkipsOtherMacrosInContent() {
        // Only the link matching linkToFix should be touched; sibling macros are left alone.
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        String targetLink = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1|project=drivepilot\"></div>";
        String otherLink = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-9|project=elsewhere\"></div>";
        String html = targetLink + otherLink;

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(html);
        IWorkItem wi = stubWorkItem("EL-1", MODULE_PROJECT, module, false);
        when(module.getAllWorkItems()).thenReturn(List.of(wi));

        IssueMetaInfo metaInfo = mockMetaInfo(targetLink, "EL-1", "drivepilot", false);
        RepairContext context = new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, context);

        assertTrue(result.isSuccess());
        String expectedFixedHtml = "<div id=\"polarion_wiki macro name=module-workitem;params=id=EL-1\"></div>" + otherLink;
        verify(module).setHomePageContent(Text.html(expectedFixedHtml));
    }

    // --- getModuleWorkItem() ---

    @Test
    void testGetModuleWorkItemFindsMatch() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class);
        IWorkItem wiOther = stubWorkItem("EL-2", MODULE_PROJECT, module, false);
        IWorkItem wiTarget = stubWorkItem("EL-1", MODULE_PROJECT, module, false);
        when(module.getAllWorkItems()).thenReturn(List.of(wiOther, wiTarget));

        assertSame(wiTarget, repairer.getModuleWorkItem(module, MODULE_PROJECT, "EL-1"));
    }

    @Test
    void testGetModuleWorkItemReturnsNullOnIdMismatch() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class);
        IWorkItem wi = stubWorkItem("EL-2", MODULE_PROJECT, module, false);
        when(module.getAllWorkItems()).thenReturn(List.of(wi));

        assertNull(repairer.getModuleWorkItem(module, MODULE_PROJECT, "EL-1"));
    }

    @Test
    void testGetModuleWorkItemReturnsNullOnProjectMismatch() {
        ModuleNonExistentWorkItemsRepairer repairer = new ModuleNonExistentWorkItemsRepairer();

        IModule module = mock(IModule.class);
        IWorkItem wi = stubWorkItem("EL-1", EXTERNAL_PROJECT, module, false);
        when(module.getAllWorkItems()).thenReturn(List.of(wi));

        assertNull(repairer.getModuleWorkItem(module, MODULE_PROJECT, "EL-1"));
    }

    // --- parseParams() ---

    @Test
    void testParseParamsSimple() {
        Map<String, String> params = new ModuleNonExistentWorkItemsRepairer().parseParams("id=EL-1|project=drivepilot|external=true");
        assertEquals(3, params.size());
        assertEquals("EL-1", params.get("id"));
        assertEquals("drivepilot", params.get("project"));
        assertEquals("true", params.get("external"));
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

    // --- helpers ---

    private static IModule stubModule(String homePageHtml) {
        return stubModule(homePageHtml, Collections.emptyList());
    }

    private static IModule stubModule(String homePageHtml, List<IWorkItem> workItems) {
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn(ModuleNonExistentWorkItemsRepairerTest.MODULE_PROJECT);
        when(module.getHomePageContent().getContent()).thenReturn(homePageHtml);
        lenient().when(module.getAllWorkItems()).thenReturn(workItems);
        return module;
    }

    private static IWorkItem stubWorkItem(String id, String projectId, IModule containingModule, boolean unresolvable) {
        IWorkItem wi = mock(IWorkItem.class);
        lenient().when(wi.getId()).thenReturn(id);
        lenient().when(wi.getProjectId()).thenReturn(projectId);
        lenient().when(wi.getModule()).thenReturn(containingModule);
        lenient().when(wi.isUnresolvable()).thenReturn(unresolvable);
        return wi;
    }

    private static IssueMetaInfo mockMetaInfo(String link, String workItemId, String projectIdParam, boolean external) {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString(LINK_KEY)).thenReturn(link);
        lenient().when(metaInfo.getString(WORK_ITEM_ID_PARAM_KEY)).thenReturn(workItemId);
        lenient().when(metaInfo.getString(PROJECT_ID_PARAM_KEY)).thenReturn(projectIdParam);
        lenient().when(metaInfo.getBoolean(EXTERNAL_PARAM_KEY)).thenReturn(external);
        return metaInfo;
    }
}
