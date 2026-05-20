package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.ILinkedWorkItemStruct;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.model.IPObjectList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class BrokenLinkedWorkItemsRepairerTest {

    // --- scan() tests ---

    @Test
    void testScanEntityNotWorkItemReturnsEmpty() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkflowObject entity = mock(IWorkflowObject.class);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanNoLinksReturnsEmpty() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getType()).thenReturn(mock(ITypeOpt.class));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(new ArrayList<>());

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanValidLinkNoIssue() {
        BrokenLinkedWorkItemsRepairer repairer = spy(new BrokenLinkedWorkItemsRepairer());

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getType()).thenReturn(mock(ITypeOpt.class));
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");
        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, "relates", false);
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(new ArrayList<>(List.of(link)));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-100", null)).thenReturn(true);
        // Mockito's RETURNS_DEFAULTS yields an empty list for getRules(), which would flag every link as a violation.
        // Use a roleOpt with rules == null to express "no rule-based restrictions" (no violation).
        doReturn(mockRoleOpt("relates", null)).when(repairer).getRoleOpt(any(IWorkItem.class), eq("relates"), any(ScanContext.class));
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanUnresolvableLinkedItemCreatesIssue() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getType()).thenReturn(mock(ITypeOpt.class));
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");
        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, "relates", true);
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(new ArrayList<>(List.of(link)));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertEquals(1, issues.size());
        assertEquals("Linked work item 'elibrary/EL-100' does not exist",
                issues.getFirst().getDescription());
        assertEquals("LINK_UNRESOLVABLE", issues.getFirst().getRawMetaInfo().get("issueType"));
        // isWorkItemExists must not be called — short-circuit on isUnresolvable()
        verify(polarionService, never()).isWorkItemExists(anyString(), anyString(), any());
    }

    @Test
    void testScanLinkWithNullRoleCreatesNoRoleIssue() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getType()).thenReturn(mock(ITypeOpt.class));
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");
        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, null, false);
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(new ArrayList<>(List.of(link)));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        // Item must be resolvable so the null-role branch is reached, not LINK_UNRESOLVABLE
        when(polarionService.isWorkItemExists("elibrary", "EL-100", null)).thenReturn(true);
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertEquals(1, issues.size());
        assertEquals("No link role specified for 'elibrary/EL-100'",
                issues.getFirst().getDescription());
        assertEquals("LINK_ROLE_MISSING", issues.getFirst().getRawMetaInfo().get("issueType"));
    }

    @Test
    void testScanWorkItemDoesNotExistCreatesIssueWithRevision() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getType()).thenReturn(mock(ITypeOpt.class));
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");
        ILinkedWorkItemStruct link = mockLink("drivepilot", "EL-100", "42", "relates", false);
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(new ArrayList<>(List.of(link)));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("drivepilot", "EL-100", "42")).thenReturn(false);
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertEquals(1, issues.size());
        assertEquals("Linked work item 'drivepilot/EL-100:42' does not exist",
                issues.getFirst().getDescription());
        assertEquals("LINK_UNRESOLVABLE", issues.getFirst().getRawMetaInfo().get("issueType"));
    }

    @Test
    void testScanUnknownLinkRoleCreatesIssue() {
        BrokenLinkedWorkItemsRepairer repairer = spy(new BrokenLinkedWorkItemsRepairer());

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getType()).thenReturn(mock(ITypeOpt.class));
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");
        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, "unknown-role", false);
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(new ArrayList<>(List.of(link)));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-100", null)).thenReturn(true);
        doReturn(null).when(repairer).getRoleOpt(any(IWorkItem.class), eq("unknown-role"), any(ScanContext.class));
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertEquals(1, issues.size());
        assertEquals("Unknown 'unknown-role' role specified for 'elibrary/EL-100'", issues.getFirst().getDescription());
        assertEquals("UNKNOWN_LINK_ROLE_ID", issues.getFirst().getRawMetaInfo().get("issueType"));
    }

    @Test
    void testScanMixedValidAndInvalidLinks() {
        BrokenLinkedWorkItemsRepairer repairer = spy(new BrokenLinkedWorkItemsRepairer());

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getType()).thenReturn(mock(ITypeOpt.class));
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");
        ILinkedWorkItemStruct validLink = mockLink("elibrary", "EL-1", null, "relates", false);
        ILinkedWorkItemStruct brokenLink = mockLink("drivepilot", "EL-2", null, "relates", false);
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(new ArrayList<>(List.of(validLink, brokenLink)));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-1", null)).thenReturn(true);
        when(polarionService.isWorkItemExists("drivepilot", "EL-2", null)).thenReturn(false);
        // rules == null => no rule-based restrictions; only the broken-link issue is expected.
        doReturn(mockRoleOpt("relates", null)).when(repairer).getRoleOpt(any(IWorkItem.class), eq("relates"), any(ScanContext.class));
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains("EL-2"));
    }

    @Test
    void testScanSkipsUnresolvableEntity() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.isUnresolvable()).thenReturn(true);
        when(entity.getType()).thenReturn(mock(ITypeOpt.class));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertTrue(issues.isEmpty());
        verify(entity, never()).getLinkedWorkItemsStructsDirect();
    }

    @Test
    void testScanSkipsEntityWithNullType() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getType()).thenReturn(null);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertTrue(issues.isEmpty());
        verify(entity, never()).getLinkedWorkItemsStructsDirect();
    }

    // --- repair() tests ---

    @Test
    void testRepairLinkNotFoundThrowsIllegalState() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(new ArrayList<>());

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("drivepilot");
        when(metaInfo.getString("linkRole")).thenReturn("relates");
        when(metaInfo.getString("linkRevision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.serialize()).thenReturn("serialized");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        assertThrows(IllegalStateException.class, () -> repairer.repair(entity, context));
    }

    @Test
    void testRepairFilterRejectsLinksWithMismatchingFields() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        // Four links: each mismatches on a different field; metaInfo matches none of them.
        ILinkedWorkItemStruct mismatchProject = mockLink("other-project", "EL-100", "42", "relates", false);
        ILinkedWorkItemStruct mismatchRole = mockLink("drivepilot", "EL-100", "42", "other-role", false);
        ILinkedWorkItemStruct mismatchRevision = mockLink("drivepilot", "EL-100", "99", "relates", false);
        ILinkedWorkItemStruct mismatchId = mockLink("drivepilot", "OTHER-1", "42", "relates", false);
        when(entity.getLinkedWorkItemsStructsDirect())
                .thenReturn(new ArrayList<>(List.of(mismatchProject, mismatchRole, mismatchRevision, mismatchId)));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("drivepilot");
        when(metaInfo.getString("linkRole")).thenReturn("relates");
        when(metaInfo.getString("linkRevision")).thenReturn("42");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.serialize()).thenReturn("serialized");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        assertThrows(IllegalStateException.class, () -> repairer.repair(entity, context));
    }

    @Test
    void testRepairFindsItemInCurrentProjectWithRevision() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        ILinkedWorkItemStruct link = mockLink("drivepilot", "EL-100", "42", "relates", false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        IWorkItem properItem = mock(IWorkItem.class);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-100", "42")).thenReturn(true);
        when(polarionService.getWorkItem("elibrary", "EL-100", "42")).thenReturn(properItem);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("drivepilot");
        when(metaInfo.getString("linkRole")).thenReturn("relates");
        when(metaInfo.getString("linkRevision")).thenReturn("42");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());
        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        verify(entity).addLinkedItem(properItem, link.getLinkRole(), "42", false);
        assertFalse(links.contains(link));
    }

    @Test
    void testRepairFindsItemInCurrentProjectWithoutRevision() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        // original link had a revision but item does not exist with it; exists with null revision
        ILinkedWorkItemStruct link = mockLink("drivepilot", "EL-100", "42", "relates", false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        IWorkItem properItem = mock(IWorkItem.class);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-100", "42")).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "EL-100", null)).thenReturn(true);
        when(polarionService.getWorkItem("elibrary", "EL-100", null)).thenReturn(properItem);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("drivepilot");
        when(metaInfo.getString("linkRole")).thenReturn("relates");
        when(metaInfo.getString("linkRevision")).thenReturn("42");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());
        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        verify(entity).addLinkedItem(properItem, link.getLinkRole(), null, false);
        assertFalse(links.contains(link));
    }

    @Test
    void testRepairGlobalSearchTwoResultsProducesWarning() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");

        ILinkedWorkItemStruct link = mockLink("", "EL-100", "", "relates", false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        IWorkItem found1 = mock(IWorkItem.class);
        when(found1.getProjectId()).thenReturn("projA");
        IWorkItem found2 = mock(IWorkItem.class);
        when(found2.getProjectId()).thenReturn("projB");

        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(2);
        when(searchResults.get(0)).thenReturn(found1);
        when(searchResults.get(1)).thenReturn(found2);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"EL-100\""), isNull(), eq(2))).thenReturn(searchResults);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("");
        when(metaInfo.getString("linkRole")).thenReturn("relates");
        when(metaInfo.getString("linkRevision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());
        RepairResult result = repairer.repair(entity, context);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        String warning = result.getWarnings().iterator().next();
        assertTrue(warning.contains("EL-100"));
        assertTrue(warning.contains("projA"));
        assertTrue(warning.contains("projB"));
        // link should remain — repair was not successful
        assertTrue(links.contains(link));
    }

    @Test
    void testRepairGlobalSearchOneResultReusesRevision() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");

        ILinkedWorkItemStruct link = mockLink("", "EL-100", "42", "relates", false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        IWorkItem foundItem = mock(IWorkItem.class);
        when(foundItem.getProjectId()).thenReturn("projA");
        when(foundItem.getId()).thenReturn("EL-100");

        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(1);
        when(searchResults.getFirst()).thenReturn(foundItem);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"EL-100\""), isNull(), eq(2))).thenReturn(searchResults);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        // branches 1 and 2 must fail so we reach the global search
        when(polarionService.isWorkItemExists("elibrary", "EL-100", "42")).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "EL-100", null)).thenReturn(false);
        // revision matches in the other project
        when(polarionService.isWorkItemExists("projA", "EL-100", "42")).thenReturn(true);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("");
        when(metaInfo.getString("linkRole")).thenReturn("relates");
        when(metaInfo.getString("linkRevision")).thenReturn("42");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());
        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        verify(entity).addLinkedItem(foundItem, link.getLinkRole(), "42", false);
        assertFalse(links.contains(link));
    }

    @Test
    void testRepairGlobalSearchOneResultFallsBackToNullRevision() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");

        // empty revision → branch 2 short-circuits, and the "reuse revision" check in
        // the global-search branch also short-circuits → falls back to null revision
        ILinkedWorkItemStruct link = mockLink("", "EL-100", "", "relates", false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        IWorkItem foundItem = mock(IWorkItem.class);

        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(1);
        when(searchResults.getFirst()).thenReturn(foundItem);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"EL-100\""), isNull(), eq(2))).thenReturn(searchResults);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("");
        when(metaInfo.getString("linkRole")).thenReturn("relates");
        when(metaInfo.getString("linkRevision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());
        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        verify(entity).addLinkedItem(foundItem, link.getLinkRole(), null, false);
        assertFalse(links.contains(link));
    }

    @Test
    void testRepairGlobalSearchZeroResultsDeleteUnresolvableFalseProducesWarning() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");

        ILinkedWorkItemStruct link = mockLink("", "EL-100", "", "relates", false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(0);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"EL-100\""), isNull(), eq(2))).thenReturn(searchResults);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("");
        when(metaInfo.getString("linkRole")).thenReturn("relates");
        when(metaInfo.getString("linkRevision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());
        RepairResult result = repairer.repair(entity, context);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Delete unresolvable links"));
        // link must NOT be removed
        assertTrue(links.contains(link));
    }

    @Test
    void testRepairGlobalSearchZeroResultsDeleteUnresolvableTrueRemovesLink() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");

        ILinkedWorkItemStruct link = mockLink("", "EL-100", "", "relates", false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(0);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"EL-100\""), isNull(), eq(2))).thenReturn(searchResults);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        UserConfigs configs = new UserConfigs();
        configs.put("BrokenLinkedWorkItemsRepairer", Map.of("deleteUnresolvable", true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("");
        when(metaInfo.getString("linkRole")).thenReturn("relates");
        when(metaInfo.getString("linkRevision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, configs, new Cache());
        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().isEmpty());
        // link must be removed
        assertFalse(links.contains(link));
        // no addLinkedItem: we are just deleting
        verify(entity, never()).addLinkedItem(any(), any(), any(), anyBoolean());
    }

    private static Stream<Arguments> warnsWhenDeleteUnresolvableFalseCases() {
        return Stream.of(
                Arguments.of("LINK_ROLE_MISSING", null, ""),
                Arguments.of("UNKNOWN_LINK_ROLE_ID", "bad-role", "bad-role"),
                Arguments.of("LINK_ROLE_RULE_VIOLATED", "relates", "relates")
        );
    }

    @ParameterizedTest
    @MethodSource("warnsWhenDeleteUnresolvableFalseCases")
    void testRepairWarnsWhenDeleteUnresolvableFalse(String issueType, String linkRoleId, String metaLinkRole) {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, linkRoleId, false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("elibrary");
        when(metaInfo.getString("linkRole")).thenReturn(metaLinkRole);
        when(metaInfo.getString("linkRevision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn(issueType);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());
        RepairResult result = repairer.repair(entity, context);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Cannot repair automatically"));
        // link must NOT be removed, no re-link attempted
        assertTrue(links.contains(link));
        verify(entity, never()).addLinkedItem(any(), any(), any(), anyBoolean());
    }

    @Test
    void testRepairLinkRoleMissingRemovesLinkWhenDeleteUnresolvableTrue() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, null, false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        UserConfigs configs = new UserConfigs();
        configs.put("BrokenLinkedWorkItemsRepairer", Map.of("deleteUnresolvable", true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("elibrary");
        when(metaInfo.getString("linkRole")).thenReturn("");
        when(metaInfo.getString("linkRevision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_ROLE_MISSING");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, configs, new Cache());
        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().isEmpty());
        assertFalse(links.contains(link));
        verify(entity, never()).addLinkedItem(any(), any(), any(), anyBoolean());
    }

    @Test
    void testRepairUnknownLinkRoleRemovesLinkWhenDeleteUnresolvableTrue() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, "bad-role", false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        UserConfigs configs = new UserConfigs();
        configs.put("BrokenLinkedWorkItemsRepairer", Map.of("deleteUnresolvable", true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("elibrary");
        when(metaInfo.getString("linkRole")).thenReturn("bad-role");
        when(metaInfo.getString("linkRevision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("UNKNOWN_LINK_ROLE_ID");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, configs, new Cache());
        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().isEmpty());
        assertFalse(links.contains(link));
        verify(entity, never()).addLinkedItem(any(), any(), any(), anyBoolean());
    }

    // --- getRoleOpt() tests ---

    @Test
    void testGetRoleOptReturnsMatchingRole() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("elibrary");
        ITypeOpt typeOpt = mock(ITypeOpt.class);
        when(typeOpt.getId()).thenReturn("task");
        when(workItem.getType()).thenReturn(typeOpt);

        ITrackerProject trackerProject = mock(ITrackerProject.class);
        when(polarionService.getTrackerProject("elibrary")).thenReturn(trackerProject);
        IEnumeration<ILinkRoleOpt> roleEnum = mock(IEnumeration.class);
        when(trackerProject.getWorkItemLinkRoleEnum()).thenReturn(roleEnum);
        ILinkRoleOpt roleOpt = mock(ILinkRoleOpt.class);
        when(roleOpt.getId()).thenReturn("relates");
        when(roleEnum.getAvailableOptions("task")).thenReturn(List.of(roleOpt));

        ScanContext context = createScanContext(polarionService);

        ILinkRoleOpt result = repairer.getRoleOpt(workItem, "relates", context);

        assertSame(roleOpt, result);
    }

    @Test
    void testGetRoleOptReturnsNullWhenNoMatch() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("elibrary");
        ITypeOpt typeOpt = mock(ITypeOpt.class);
        when(typeOpt.getId()).thenReturn("task");
        when(workItem.getType()).thenReturn(typeOpt);

        ITrackerProject trackerProject = mock(ITrackerProject.class);
        when(polarionService.getTrackerProject("elibrary")).thenReturn(trackerProject);
        IEnumeration<ILinkRoleOpt> roleEnum = mock(IEnumeration.class);
        when(trackerProject.getWorkItemLinkRoleEnum()).thenReturn(roleEnum);
        when(roleEnum.getAvailableOptions("task")).thenReturn(List.of());

        ScanContext context = createScanContext(polarionService);

        ILinkRoleOpt result = repairer.getRoleOpt(workItem, "unknown-role", context);

        assertNull(result);
    }

    @Test
    void testGetRoleOptCachesEnumPerProject() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("elibrary");
        ITypeOpt typeOpt = mock(ITypeOpt.class);
        when(typeOpt.getId()).thenReturn("task");
        when(workItem.getType()).thenReturn(typeOpt);

        ITrackerProject trackerProject = mock(ITrackerProject.class);
        when(polarionService.getTrackerProject("elibrary")).thenReturn(trackerProject);
        IEnumeration<ILinkRoleOpt> roleEnum = mock(IEnumeration.class);
        when(trackerProject.getWorkItemLinkRoleEnum()).thenReturn(roleEnum);
        when(roleEnum.getAvailableOptions("task")).thenReturn(List.of());

        ScanContext context = createScanContext(polarionService);

        repairer.getRoleOpt(workItem, "relates", context);
        repairer.getRoleOpt(workItem, "relates", context);

        // enum fetched only once — ScanContext caches by project key
        verify(trackerProject, times(1)).getWorkItemLinkRoleEnum();
    }

    // --- Metadata tests ---

    @Test
    void testGetDisplayName() {
        assertEquals("Broken Work Item Links", new BrokenLinkedWorkItemsRepairer().getDisplayName());
    }

    @Test
    void testGetDescription() {
        assertTrue(new BrokenLinkedWorkItemsRepairer().getDescription().contains("broken work item links"));
    }

    @Test
    void testGetRepairerId() {
        assertEquals("BrokenLinkedWorkItemsRepairer", new BrokenLinkedWorkItemsRepairer().getRepairerId());
    }

    @Test
    void testGetConfigs() {
        List<RepairerConfigMeta> configs = new BrokenLinkedWorkItemsRepairer().getConfigs();

        assertEquals(1, configs.size());
        RepairerConfigMeta config = configs.getFirst();
        assertEquals("deleteUnresolvable", config.getKey());
        assertEquals("Delete broken items", config.getDescription());
        assertEquals("Delete items with a wrong link role or unresolvable link (linked item cannot be found by the specified data in any available project)", config.getHint());
        assertEquals(RepairerConfigType.BOOLEAN, config.getType());
        assertEquals(false, config.getDefaultValue());
    }

    // --- linkViolatesRules() tests ---
    // linkViolatesRules delegates compliance to Polarion's ILinkRoleOpt.IRule#isAllowed.
    // These tests cover:
    //  - structureLinkRole bypass: identity comparison by (projectId, moduleFolder, moduleName, revision)
    //    where the target side reads revision from the link itself (not the target module)
    //  - null-rules guard, vacuous-truth on empty list, and noneMatch semantics
    //  - Optional.orElse("") fallback when source/target type is null

    @Test
    void testLinkViolatesRulesNullRulesReturnsFalse() {
        IWorkItem src = mockWorkItem("task", null);
        IWorkItem target = mockWorkItem("task", null);
        ILinkRoleOpt roleOpt = mockRoleOpt("relates", null);

        assertFalse(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
    }

    @Test
    void testLinkViolatesRulesEmptyListReturnsTrue() {
        // No rules to comply with -> violation
        IWorkItem src = mockWorkItem("task", null);
        IWorkItem target = mockWorkItem("task", null);
        ILinkRoleOpt roleOpt = mockRoleOpt("relates", List.of());

        assertTrue(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
    }

    @Test
    void testLinkViolatesRulesSingleAllowingRuleReturnsFalse() {
        IWorkItem src = mockWorkItem("requirement", null);
        IWorkItem target = mockWorkItem("task", null);
        ILinkRoleOpt.IRule rule = mock(ILinkRoleOpt.IRule.class);
        when(rule.isAllowed("requirement", "task")).thenReturn(true);
        ILinkRoleOpt roleOpt = mockRoleOpt("relates", List.of(rule));

        assertFalse(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
        verify(rule).isAllowed("requirement", "task");
    }

    @Test
    void testLinkViolatesRulesSingleDisallowingRuleReturnsTrue() {
        IWorkItem src = mockWorkItem("requirement", null);
        IWorkItem target = mockWorkItem("task", null);
        ILinkRoleOpt.IRule rule = mock(ILinkRoleOpt.IRule.class);
        when(rule.isAllowed(anyString(), anyString())).thenReturn(false);
        ILinkRoleOpt roleOpt = mockRoleOpt("relates", List.of(rule));

        assertTrue(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
        verify(rule).isAllowed("requirement", "task");
    }

    @Test
    void testLinkViolatesRulesSecondRuleAllowsReturnsFalse() {
        IWorkItem src = mockWorkItem("requirement", null);
        IWorkItem target = mockWorkItem("task", null);
        ILinkRoleOpt.IRule rule1 = mock(ILinkRoleOpt.IRule.class);
        when(rule1.isAllowed(anyString(), anyString())).thenReturn(false);
        ILinkRoleOpt.IRule rule2 = mock(ILinkRoleOpt.IRule.class);
        when(rule2.isAllowed("requirement", "task")).thenReturn(true);
        ILinkRoleOpt roleOpt = mockRoleOpt("relates", List.of(rule1, rule2));

        assertFalse(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
        verify(rule1).isAllowed("requirement", "task");
        verify(rule2).isAllowed("requirement", "task");
    }

    @Test
    void testLinkViolatesRulesFirstRuleAllowsShortCircuits() {
        IWorkItem src = mockWorkItem("requirement", null);
        IWorkItem target = mockWorkItem("task", null);
        ILinkRoleOpt.IRule rule1 = mock(ILinkRoleOpt.IRule.class);
        when(rule1.isAllowed("requirement", "task")).thenReturn(true);
        ILinkRoleOpt.IRule rule2 = mock(ILinkRoleOpt.IRule.class);
        ILinkRoleOpt roleOpt = mockRoleOpt("relates", List.of(rule1, rule2));

        assertFalse(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
        // noneMatch is short-circuiting — rule2 must not be consulted once rule1 allows
        verify(rule1).isAllowed("requirement", "task");
        verify(rule2, never()).isAllowed(anyString(), anyString());
    }

    @Test
    void testLinkViolatesRulesAllRulesDisallowReturnsTrue() {
        IWorkItem src = mockWorkItem("requirement", null);
        IWorkItem target = mockWorkItem("task", null);
        ILinkRoleOpt.IRule rule1 = mock(ILinkRoleOpt.IRule.class);
        when(rule1.isAllowed(anyString(), anyString())).thenReturn(false);
        ILinkRoleOpt.IRule rule2 = mock(ILinkRoleOpt.IRule.class);
        when(rule2.isAllowed(anyString(), anyString())).thenReturn(false);
        ILinkRoleOpt roleOpt = mockRoleOpt("relates", List.of(rule1, rule2));

        assertTrue(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
        verify(rule1).isAllowed("requirement", "task");
        verify(rule2).isAllowed("requirement", "task");
    }

    // --- structureLinkRole bypass tests ---
    // Polarion auto-links every work item in a document to its closest heading/parent via the module's
    // structureLinkRole without consulting link-role rules (see XMLStructuredDocument.createModuleStructureLinks).
    // Such links must not be flagged as violations.

    @Test
    void testLinkViolatesRulesStructureLinkInSameModuleBypassesRules() {
        IModule module = mockModule();
        IWorkItem src = mockWorkItem("requirement", module);
        IWorkItem target = mockWorkItem("task", module);
        ILinkRoleOpt.IRule rule = mock(ILinkRoleOpt.IRule.class);
        ILinkRoleOpt roleOpt = mockRoleOpt("parent", List.of(rule));

        assertFalse(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
        // Rules must not even be consulted
        verifyNoInteractions(rule);
    }

    @Test
    void testLinkViolatesRulesBypassWorksAcrossDistinctModuleInstancesWithSameIdentity() {
        // Bypass relies on field-by-field identity (projectId, folder, name, revision), not Polarion's
        // IModule.equals() — two distinct wrappers for the same document must still trigger bypass.
        IModule srcModule = mockModule();
        IModule targetModule = mockModule(); // distinct mock instance, same default identity fields
        IWorkItem src = mockWorkItem("requirement", srcModule);
        IWorkItem target = mockWorkItem("task", targetModule);
        ILinkRoleOpt.IRule rule = mock(ILinkRoleOpt.IRule.class);
        ILinkRoleOpt roleOpt = mockRoleOpt("parent", List.of(rule));

        assertFalse(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
        verifyNoInteractions(rule);
    }

    @Test
    void testLinkViolatesRulesStructureLinkBypassWorksWithNullRules() {
        // Bypass takes effect regardless of nullness of rules
        IModule module = mockModule();
        IWorkItem src = mockWorkItem("requirement", module);
        IWorkItem target = mockWorkItem("task", module);
        ILinkRoleOpt roleOpt = mockRoleOpt("parent", null);

        assertFalse(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
    }

    @Test
    void testLinkViolatesRulesNoBypassWhenSrcModuleNull() {
        IModule targetModule = mockModule();
        IWorkItem src = mockWorkItem("requirement", null);
        IWorkItem target = mockWorkItem("task", targetModule);
        ILinkRoleOpt.IRule rule = mock(ILinkRoleOpt.IRule.class);
        when(rule.isAllowed("requirement", "task")).thenReturn(false);
        ILinkRoleOpt roleOpt = mockRoleOpt("parent", List.of(rule));

        assertTrue(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
        verify(rule).isAllowed("requirement", "task");
    }

    @Test
    void testLinkViolatesRulesNoBypassWhenTargetModuleNull() {
        IModule srcModule = mockModule();
        IWorkItem src = mockWorkItem("requirement", srcModule);
        IWorkItem target = mockWorkItem("task", null);
        ILinkRoleOpt.IRule rule = mock(ILinkRoleOpt.IRule.class);
        when(rule.isAllowed("requirement", "task")).thenReturn(false);
        ILinkRoleOpt roleOpt = mockRoleOpt("parent", List.of(rule));

        assertTrue(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
    }

    private static Stream<Arguments> noBypassScenarios() {
        // Each scenario varies exactly one input vs the bypass-triggering baseline.
        // Covers every AND-condition in the bypass check.
        return Stream.of(
                Arguments.of("src module unresolvable", "srcUnresolvable"),
                Arguments.of("target module unresolvable", "targetUnresolvable"),
                Arguments.of("different projectId", "project"),
                Arguments.of("different moduleFolder", "folder"),
                Arguments.of("different moduleName", "name"),
                Arguments.of("different revision (link vs src module)", "revision")
        );
    }

    @ParameterizedTest(name = "no bypass when {0}")
    @MethodSource("noBypassScenarios")
    void testLinkViolatesRulesNoBypassScenarios(String scenario, String diff) {
        IModule srcModule = mockModule();
        IModule targetModule = mockModule();
        String linkRevision = null;
        switch (diff) {
            case "srcUnresolvable" -> when(srcModule.isUnresolvable()).thenReturn(true);
            case "targetUnresolvable" -> when(targetModule.isUnresolvable()).thenReturn(true);
            case "project" -> when(targetModule.getProjectId()).thenReturn("other-project");
            case "folder" -> when(targetModule.getModuleFolder()).thenReturn("OtherFolder");
            case "name" -> when(targetModule.getModuleName()).thenReturn("OtherDoc");
            // src module sees HEAD (null), link points to a specific revision => structure-link bypass must not fire.
            case "revision" -> linkRevision = "42";
            default -> throw new IllegalArgumentException("Unknown diff: " + diff);
        }
        IWorkItem src = mockWorkItem("requirement", srcModule);
        IWorkItem target = mockWorkItem("task", targetModule);
        ILinkRoleOpt.IRule rule = mock(ILinkRoleOpt.IRule.class);
        when(rule.isAllowed("requirement", "task")).thenReturn(false);
        ILinkRoleOpt roleOpt = mockRoleOpt("parent", List.of(rule));

        assertTrue(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, linkRevision), roleOpt));
    }

    @Test
    void testLinkViolatesRulesNoBypassWhenRoleIsNotStructureLinkRole() {
        IModule module = mockModule();
        IWorkItem src = mockWorkItem("requirement", module);
        IWorkItem target = mockWorkItem("task", module);
        ILinkRoleOpt.IRule rule = mock(ILinkRoleOpt.IRule.class);
        when(rule.isAllowed("requirement", "task")).thenReturn(false);
        // The role being validated ("relates") is not the module's structureLinkRole ("parent")
        ILinkRoleOpt roleOpt = mockRoleOpt("relates", List.of(rule));

        assertTrue(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
    }

    @Test
    void testLinkViolatesRulesNullSrcTypeUsesEmptyString() {
        // Optional.orElse("") branch for null source type
        IWorkItem src = mockWorkItem(null, null);
        IWorkItem target = mockWorkItem("task", null);
        ILinkRoleOpt.IRule rule = mock(ILinkRoleOpt.IRule.class);
        when(rule.isAllowed("", "task")).thenReturn(true);
        ILinkRoleOpt roleOpt = mockRoleOpt("relates", List.of(rule));

        assertFalse(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
        verify(rule).isAllowed("", "task");
    }

    @Test
    void testLinkViolatesRulesNullTargetTypeUsesEmptyString() {
        // Optional.orElse("") branch for null target type
        IWorkItem src = mockWorkItem("requirement", null);
        IWorkItem target = mockWorkItem(null, null);
        ILinkRoleOpt.IRule rule = mock(ILinkRoleOpt.IRule.class);
        when(rule.isAllowed("requirement", "")).thenReturn(true);
        ILinkRoleOpt roleOpt = mockRoleOpt("relates", List.of(rule));

        assertFalse(new BrokenLinkedWorkItemsRepairer().linkViolatesRules(src, mockLinkOf(target, null), roleOpt));
        verify(rule).isAllowed("requirement", "");
    }

    // --- scan() + LINK_ROLE_RULE_VIOLATED tests ---

    @Test
    void testScanLinkedItemTypeNullStillInvokesRuleCheck() {
        // The old inline guard `link.getLinkedItem().getType() != null` in scan() was removed.
        // The rule check is now always invoked when the role resolves; null target type is handled
        // internally via Optional.orElse("") in typesPairViolatesLinkRules.
        BrokenLinkedWorkItemsRepairer repairer = spy(new BrokenLinkedWorkItemsRepairer());

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getType()).thenReturn(mock(ITypeOpt.class));
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");
        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, "relates", false);
        // linked item type intentionally not stubbed — getType() returns null
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(new ArrayList<>(List.of(link)));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-100", null)).thenReturn(true);
        doReturn(mock(ILinkRoleOpt.class)).when(repairer).getRoleOpt(any(IWorkItem.class), eq("relates"), any(ScanContext.class));
        doReturn(false).when(repairer).linkViolatesRules(any(IWorkItem.class), any(ILinkedWorkItemStruct.class), any(ILinkRoleOpt.class));
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertTrue(issues.isEmpty());
        // The method IS invoked now — the inline null-type guard at scan() level was removed.
        verify(repairer).linkViolatesRules(any(IWorkItem.class), any(ILinkedWorkItemStruct.class), any(ILinkRoleOpt.class));
    }

    @Test
    void testScanLinkRoleRuleViolatedCreatesIssue() {
        BrokenLinkedWorkItemsRepairer repairer = spy(new BrokenLinkedWorkItemsRepairer());

        IWorkItem entity = mock(IWorkItem.class);
        ITypeOpt entityType = mock(ITypeOpt.class);
        when(entityType.getId()).thenReturn("requirement");
        when(entity.getType()).thenReturn(entityType);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");

        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, "relates", false);
        ITypeOpt linkedItemType = mock(ITypeOpt.class);
        when(linkedItemType.getId()).thenReturn("task");
        when(link.getLinkedItem().getType()).thenReturn(linkedItemType);
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(new ArrayList<>(List.of(link)));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-100", null)).thenReturn(true);

        ILinkRoleOpt roleOpt = mock(ILinkRoleOpt.class);
        doReturn(roleOpt).when(repairer).getRoleOpt(any(IWorkItem.class), eq("relates"), any(ScanContext.class));
        doReturn(true).when(repairer).linkViolatesRules(any(IWorkItem.class), any(ILinkedWorkItemStruct.class), any(ILinkRoleOpt.class));
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertEquals(1, issues.size());
        assertEquals("Link role 'relates' rule violated for 'elibrary/EL-100'", issues.getFirst().getDescription());
        assertEquals("LINK_ROLE_RULE_VIOLATED", issues.getFirst().getRawMetaInfo().get("issueType"));
    }

    @Test
    void testScanLinkRoleRuleNotViolatedNoIssue() {
        BrokenLinkedWorkItemsRepairer repairer = spy(new BrokenLinkedWorkItemsRepairer());

        IWorkItem entity = mock(IWorkItem.class);
        ITypeOpt entityType = mock(ITypeOpt.class);
        when(entityType.getId()).thenReturn("requirement");
        when(entity.getType()).thenReturn(entityType);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");

        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, "relates", false);
        ITypeOpt linkedItemType = mock(ITypeOpt.class);
        when(linkedItemType.getId()).thenReturn("task");
        when(link.getLinkedItem().getType()).thenReturn(linkedItemType);
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(new ArrayList<>(List.of(link)));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-100", null)).thenReturn(true);

        ILinkRoleOpt roleOpt = mock(ILinkRoleOpt.class);
        doReturn(roleOpt).when(repairer).getRoleOpt(any(IWorkItem.class), eq("relates"), any(ScanContext.class));
        doReturn(false).when(repairer).linkViolatesRules(any(IWorkItem.class), any(ILinkedWorkItemStruct.class), any(ILinkRoleOpt.class));
        ScanContext context = createScanContext(polarionService);

        List<Issue> issues = repairer.scan(entity, context);

        assertTrue(issues.isEmpty());
    }

    // --- repair() + LINK_ROLE_RULE_VIOLATED tests ---

    @Test
    void testRepairLinkRoleRuleViolatedRemovesLinkWhenDeleteUnresolvableTrue() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, "relates", false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        UserConfigs configs = new UserConfigs();
        configs.put("BrokenLinkedWorkItemsRepairer", Map.of("deleteUnresolvable", true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("elibrary");
        when(metaInfo.getString("linkRole")).thenReturn("relates");
        when(metaInfo.getString("linkRevision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_ROLE_RULE_VIOLATED");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, configs, new Cache());
        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().isEmpty());
        assertFalse(links.contains(link));
        verify(entity, never()).addLinkedItem(any(), any(), any(), anyBoolean());
    }

    private ILinkedWorkItemStruct mockLink(String projectId, String itemId, String revision, String roleId, boolean unresolvable) {
        ILinkedWorkItemStruct link = mock(ILinkedWorkItemStruct.class);
        IWorkItem linkedItem = mock(IWorkItem.class);
        when(link.getLinkedItem()).thenReturn(linkedItem);
        when(linkedItem.isUnresolvable()).thenReturn(unresolvable);
        when(linkedItem.getProjectId()).thenReturn(projectId);
        when(linkedItem.getId()).thenReturn(itemId);
        when(link.getRevision()).thenReturn(revision);
        if (roleId == null) {
            when(link.getLinkRole()).thenReturn(null);
        } else {
            ILinkRoleOpt role = mock(ILinkRoleOpt.class);
            when(role.getId()).thenReturn(roleId);
            when(link.getLinkRole()).thenReturn(role);
        }
        return link;
    }

    private static IWorkItem mockWorkItem(String typeId, IModule module) {
        IWorkItem wi = mock(IWorkItem.class);
        if (typeId != null) {
            ITypeOpt type = mock(ITypeOpt.class);
            when(type.getId()).thenReturn(typeId);
            when(wi.getType()).thenReturn(type);
        }
        when(wi.getModule()).thenReturn(module);
        return wi;
    }

    private static IModule mockModule() {
        IModule module = mock(IModule.class);
        // Default identity fields — two distinct mockModule() mocks share the same
        // (projectId, folder, name, HEAD revision), so the bypass treats them as the same module.
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleFolder()).thenReturn("Spec");
        when(module.getModuleName()).thenReturn("MyDoc");
        // getRevision() left unstubbed → null (HEAD); matches link.getRevision() default of null.
        ILinkRoleOpt structureRole = mock(ILinkRoleOpt.class);
        when(structureRole.getId()).thenReturn("parent");
        when(module.getStructureLinkRole()).thenReturn(structureRole);
        return module;
    }

    private static ILinkedWorkItemStruct mockLinkOf(IWorkItem linkedItem, String revision) {
        ILinkedWorkItemStruct link = mock(ILinkedWorkItemStruct.class);
        when(link.getLinkedItem()).thenReturn(linkedItem);
        when(link.getRevision()).thenReturn(revision);
        return link;
    }

    private static ILinkRoleOpt mockRoleOpt(String roleId, List<ILinkRoleOpt.IRule> rules) {
        ILinkRoleOpt roleOpt = mock(ILinkRoleOpt.class);
        when(roleOpt.getId()).thenReturn(roleId);
        when(roleOpt.getRules()).thenReturn(rules);
        return roleOpt;
    }
}
