package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.EntityRenderer;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.ILinkedWorkItemStruct;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.model.IPObjectList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class BrokenLinkedWorkItemsRepairerTest {

    private ScanContext createScanContext(XmlRepairPolarionService polarionService) {
        lenient().when(polarionService.getTrackerService()).thenReturn(mock(ITrackerService.class));
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored = mockConstruction(EntityRenderer.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(mock(InternalReadOnlyTransaction.class));
            return new ScanContext(polarionService, List.of(), new UserConfigs(), new Report());
        }
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
        doReturn(mock(ILinkRoleOpt.class)).when(repairer).getRoleOpt(any(IWorkItem.class), eq("relates"), any(ScanContext.class));
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
        assertEquals("Broken work item link found: linked work item 'elibrary/EL-100' does not exist.",
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
        assertEquals("Broken work item link found: no link role specified for 'elibrary/EL-100'",
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
        assertEquals("Broken work item link found: linked work item 'drivepilot/EL-100:42' does not exist.",
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
        assertTrue(issues.getFirst().getDescription().contains("unknown-role"));
        assertTrue(issues.getFirst().getDescription().contains("elibrary/EL-100"));
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
        doReturn(mock(ILinkRoleOpt.class)).when(repairer).getRoleOpt(any(IWorkItem.class), eq("relates"), any(ScanContext.class));
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
        when(metaInfo.getString("revision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.serialize()).thenReturn("serialized");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());

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
        when(metaInfo.getString("revision")).thenReturn("42");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());
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
        when(metaInfo.getString("revision")).thenReturn("42");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());
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
        when(metaInfo.getString("revision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());
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
        when(metaInfo.getString("revision")).thenReturn("42");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());
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
        when(metaInfo.getString("revision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());
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
        when(metaInfo.getString("revision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());
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
        when(metaInfo.getString("revision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_UNRESOLVABLE");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, configs);
        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().isEmpty());
        // link must be removed
        assertFalse(links.contains(link));
        // no addLinkedItem: we are just deleting
        verify(entity, never()).addLinkedItem(any(), any(), any(), anyBoolean());
    }

    @Test
    void testRepairLinkRoleMissingWarnsWhenDeleteUnresolvableFalse() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, null, false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("elibrary");
        when(metaInfo.getString("linkRole")).thenReturn("");
        when(metaInfo.getString("revision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_ROLE_MISSING");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());
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
        when(metaInfo.getString("revision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("LINK_ROLE_MISSING");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, configs);
        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().isEmpty());
        assertFalse(links.contains(link));
        verify(entity, never()).addLinkedItem(any(), any(), any(), anyBoolean());
    }

    @Test
    void testRepairUnknownLinkRoleWarnsWhenDeleteUnresolvableFalse() {
        BrokenLinkedWorkItemsRepairer repairer = new BrokenLinkedWorkItemsRepairer();

        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        ILinkedWorkItemStruct link = mockLink("elibrary", "EL-100", null, "bad-role", false);
        Collection<ILinkedWorkItemStruct> links = new ArrayList<>(List.of(link));
        when(entity.getLinkedWorkItemsStructsDirect()).thenReturn(links);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("linkProjectId")).thenReturn("elibrary");
        when(metaInfo.getString("linkRole")).thenReturn("bad-role");
        when(metaInfo.getString("revision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("UNKNOWN_LINK_ROLE_ID");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());
        RepairResult result = repairer.repair(entity, context);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Cannot repair automatically"));
        assertTrue(links.contains(link));
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
        when(metaInfo.getString("revision")).thenReturn("");
        when(metaInfo.getString("linkId")).thenReturn("EL-100");
        when(metaInfo.get("issueType")).thenReturn("UNKNOWN_LINK_ROLE_ID");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, configs);
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
}
