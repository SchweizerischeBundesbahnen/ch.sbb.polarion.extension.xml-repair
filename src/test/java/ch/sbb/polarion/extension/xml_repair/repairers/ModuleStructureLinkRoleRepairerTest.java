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
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.ILinkedWorkItemStruct;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkItemPermissions;
import com.polarion.platform.persistence.IDataService;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.model.IPObjectList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(PlatformContextMockExtension.class)
class ModuleStructureLinkRoleRepairerTest {

    private final ModuleStructureLinkRoleRepairer repairer = new ModuleStructureLinkRoleRepairer();

    // --- metadata ---

    @Test
    void testMetadata() {
        assertEquals("Structural link role", repairer.getDisplayName());
        assertEquals("ModuleStructureLinkRoleRepairer", repairer.getRepairerId());
        assertEquals("parent", ModuleStructureLinkRoleRepairer.DEFAULT_TARGET_ROLE);
        assertTrue(repairer.getConfigs().isEmpty());
    }

    // --- target role resolution ---

    @Test
    void testTargetRoleFallsBackToParentWhenNotConfigured() {
        assertEquals("parent", repairer.targetRole(new UserConfigs()));
        assertEquals("parent", repairer.targetRole(configs("  ")));
    }

    @Test
    void testTargetRoleComesFromTheConfigs() {
        assertEquals("relates_to", repairer.targetRole(configs("relates_to")));
    }

    // --- scan() ---

    @Test
    void testScanReportsNoIssueWhenTheDocumentAlreadyUsesTheTargetRole() {
        IModule module = mockModule("MyDoc", "relates_to");

        List<Issue> issues = repairer.scan(module, scanContext("relates_to"));

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanReportsTheDifferingRole() {
        IModule module = mockModule("MyDoc", "relates_to");

        List<Issue> issues = repairer.scan(module, scanContext("parent"));

        assertEquals(1, issues.size());
        Issue issue = issues.getFirst();
        assertEquals("Document 'MyDoc' uses structure link role 'relates_to' instead of 'parent'", issue.getDescription());
        assertEquals("parent", issue.getRawMetaInfo().getString(ModuleStructureLinkRoleRepairer.TARGET_ROLE));
        // The role column of the results list reads the label, not the issue count.
        assertEquals("relates_to", issue.getLabel());
        assertTrue(issue.getWarnings().isEmpty());
    }

    @Test
    void testScanLabelsADocumentWithoutARoleAsNone() {
        IModule module = mockModule("MyDoc", null);

        List<Issue> issues = repairer.scan(module, scanContext("parent"));

        assertEquals(1, issues.size());
        assertEquals("none", issues.getFirst().getLabel());
    }

    @Test
    void testScanWarnsAboutLinksTheSwitchWouldDelete() {
        IModule module = mockModule("MyDoc", "relates_to");
        containWorkItems(module, link("PRJ-1", "PRJ-2", "parent"), link("PRJ-3", "PRJ-4", "verifies"));

        List<Issue> issues = repairer.scan(module, scanContext("parent"));

        assertEquals(1, issues.size());
        assertEquals(1, issues.getFirst().getWarnings().size());
        String warning = issues.getFirst().getWarnings().getFirst();
        assertTrue(warning.contains("PRJ-1 -> PRJ-2"), warning);
        assertFalse(warning.contains("PRJ-3"), warning);
    }

    // --- repair() ---

    /**
     * The repairer under test, with the two calls into Polarion's platform stubbed out: the write, which goes
     * through the low-level object and casts the module to a class a mock cannot be, and the URI collection,
     * because {@code SubterraURI} cannot even be loaded here - its static initializer needs Guava, which is
     * not on the test classpath. Whether the repair collects is asserted through the calls instead.
     */
    private static ModuleStructureLinkRoleRepairer writeStubbed() {
        ModuleStructureLinkRoleRepairer spy = spy(new ModuleStructureLinkRoleRepairer());
        doNothing().when(spy).setStructureLinkRole(any(), any());
        doReturn(Set.of()).when(spy).staleCacheUris(any());
        return spy;
    }

    @Test
    void testRepairSetsTheStructureLinkRole() {
        ModuleStructureLinkRoleRepairer spy = writeStubbed();
        IModule module = mockModule("MyDoc", "relates_to");
        ILinkRoleOpt parent = mockRole("parent");
        mockRoleEnumeration(module, parent);

        RepairResult result = spy.repair(module, repairContext("parent"));

        assertTrue(result.isSuccess());
        verify(spy).setStructureLinkRole(module, parent);
    }

    @Test
    void testRepairCollectsTheStaleCachesOfTheDocumentItSwitched() {
        ModuleStructureLinkRoleRepairer spy = writeStubbed();
        IModule module = mockModule("MyDoc", "relates_to");
        mockRoleEnumeration(module, mockRole("parent"));

        RepairResult result = spy.repair(module, repairContext("parent"));

        assertTrue(result.isSuccess());
        verify(spy).staleCacheUris(module);
    }

    @Test
    void testRepairCollectsNothingStaleWhenItChangesNothing() {
        ModuleStructureLinkRoleRepairer spy = writeStubbed();
        IModule module = mockModule("MyDoc", "parent");

        RepairResult result = spy.repair(module, repairContext("parent"));

        assertFalse(result.isSuccess());
        assertTrue(result.getStaleCacheUris().isEmpty());
        verify(spy, never()).staleCacheUris(any());
    }

    @Test
    void testRepairRefusesWhenTheTargetRoleIsAlreadyUsedByRealLinks() {
        ModuleStructureLinkRoleRepairer spy = writeStubbed();
        IModule module = mockModule("MyDoc", "relates_to");
        containWorkItems(module, link("PRJ-1", "PRJ-2", "parent"));
        mockRoleEnumeration(module, mockRole("parent"));

        RepairResult result = spy.repair(module, repairContext("parent"));

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Choose what happens to it under 'Existing parent links'")));
        assertEquals(1, result.getWarnings().size());
        verify(spy, never()).setStructureLinkRole(any(), any());
    }

    @Test
    void testRepairRefusesAnUnknownRole() {
        ModuleStructureLinkRoleRepairer spy = writeStubbed();
        IModule module = mockModule("MyDoc", "relates_to");
        ILinkRoleOpt phantom = mockRole("nope");
        when(phantom.isPhantom()).thenReturn(true);
        mockRoleEnumeration(module, phantom);

        RepairResult result = spy.repair(module, repairContext("nope"));

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("does not exist in this project")));
        verify(spy, never()).setStructureLinkRole(any(), any());
    }

    @Test
    void testExistingLinksPolicyFallsBackToInterrupt() {
        assertEquals("INTERRUPT", repairer.existingLinksPolicy(new UserConfigs()));
        assertEquals("INTERRUPT", repairer.existingLinksPolicy(existingLinksConfigs("NONSENSE", null)));
        assertEquals("CHANGE", repairer.existingLinksPolicy(existingLinksConfigs("CHANGE", "relates_to")));
        assertEquals("DELETE", repairer.existingLinksPolicy(existingLinksConfigs("DELETE", null)));
    }

    @Test
    void testRepairMovesCollidingLinksToTheChosenRole() {
        ModuleStructureLinkRoleRepairer spy = writeStubbed();
        IModule module = mockModule("MyDoc", "relates_to");
        IWorkItem source = link("PRJ-1", "PRJ-2", "parent");
        containWorkItems(module, source);
        ILinkRoleOpt parent = mockRole("parent");
        ILinkRoleOpt verifies = mockRole("verifies");
        mockRoleEnumeration(module, parent, verifies);

        RepairResult result = spy.repair(module, repairContext("parent", existingLinksConfigs("CHANGE", "verifies")));

        assertTrue(result.isSuccess());
        IWorkItem target = linkTargetOf(source);
        verify(source).addLinkedItem(eq(target), eq(verifies), any(), anyBoolean());
        verify(source).removeLinkedItem(eq(target), any());
        verify(source).save();
        verify(spy).setStructureLinkRole(module, parent);
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Moved 1 link(s)")), result.getWarnings().toString());
    }

    @Test
    void testRepairDeletesCollidingLinks() {
        ModuleStructureLinkRoleRepairer spy = writeStubbed();
        IModule module = mockModule("MyDoc", "relates_to");
        IWorkItem source = link("PRJ-1", "PRJ-2", "parent");
        containWorkItems(module, source);
        ILinkRoleOpt parent = mockRole("parent");
        mockRoleEnumeration(module, parent);

        RepairResult result = spy.repair(module, repairContext("parent", existingLinksConfigs("DELETE", null)));

        assertTrue(result.isSuccess());
        IWorkItem target = linkTargetOf(source);
        verify(source, never()).addLinkedItem(any(), any(), any(), anyBoolean());
        verify(source).removeLinkedItem(eq(target), any());
        verify(source).save();
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Deleted 1 link(s)")), result.getWarnings().toString());
    }

    @Test
    void testRepairRefusesToChangeWithoutAReplacementRole() {
        ModuleStructureLinkRoleRepairer spy = writeStubbed();
        IModule module = mockModule("MyDoc", "relates_to");
        IWorkItem source = link("PRJ-1", "PRJ-2", "parent");
        containWorkItems(module, source);
        mockRoleEnumeration(module, mockRole("parent"));

        RepairResult result = spy.repair(module, repairContext("parent", existingLinksConfigs("CHANGE", null)));

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("No link role was chosen")));
        verify(source, never()).save();
        verify(spy, never()).setStructureLinkRole(any(), any());
    }

    @Test
    void testRepairWritesNothingWhenAWorkItemCannotBeModified() {
        ModuleStructureLinkRoleRepairer spy = writeStubbed();
        IModule module = mockModule("MyDoc", "relates_to");
        IWorkItem source = link("PRJ-1", "PRJ-2", "parent");
        lenient().when(source.can().modify()).thenReturn(false);
        containWorkItems(module, source);
        mockRoleEnumeration(module, mockRole("parent"));

        RepairResult result = spy.repair(module, repairContext("parent", existingLinksConfigs("DELETE", null)));

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Cannot modify work item 'PRJ-1'")));
        verify(source, never()).save();
        verify(spy, never()).setStructureLinkRole(any(), any());
    }

    @Test
    void testRepairDoesNothingWhenTheRoleIsAlreadyTheTargetOne() {
        ModuleStructureLinkRoleRepairer spy = writeStubbed();
        IModule module = mockModule("MyDoc", "parent");

        RepairResult result = spy.repair(module, repairContext("parent"));

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("already uses")));
        verify(spy, never()).setStructureLinkRole(any(), any());
    }

    // --- fixtures ---

    private static UserConfigs configs(String targetRole) {
        UserConfigs configs = new UserConfigs();
        configs.put("ModuleStructureLinkRoleRepairer", Map.of(ModuleStructureLinkRoleRepairer.TARGET_ROLE, targetRole));
        return configs;
    }

    /** The configs of a repair, which additionally carry the "Existing links" answer. */
    private static UserConfigs existingLinksConfigs(String policy, String replacementRole) {
        Map<String, String> values = new HashMap<>();
        values.put(ModuleStructureLinkRoleRepairer.EXISTING_LINKS, policy);
        if (replacementRole != null) {
            values.put(ModuleStructureLinkRoleRepairer.EXISTING_LINKS_ROLE, replacementRole);
        }
        UserConfigs configs = new UserConfigs();
        configs.put("ModuleStructureLinkRoleRepairer", values);
        return configs;
    }

    private static RepairContext repairContext(String targetRole, UserConfigs configs) {
        IssueMetaInfo metaInfo = IssueMetaInfo.fromString(metaInfoWithRole(targetRole));
        return new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), configs, new Cache());
    }

    private static ScanContext scanContext(String targetRole) {
        return createScanContext(mock(XmlRepairPolarionService.class), List.of(), configs(targetRole), new Report());
    }

    private static RepairContext repairContext(String targetRole) {
        IssueMetaInfo metaInfo = IssueMetaInfo.fromString(metaInfoWithRole(targetRole));
        return new RepairContext(metaInfo, mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());
    }

    /** A serialized meta info carrying only the target role, which is what the repair reads back. */
    private static String metaInfoWithRole(String targetRole) {
        IModule module = mock(IModule.class);
        lenient().when(module.getProjectId()).thenReturn("elibrary");
        lenient().when(module.getRelativePath()).thenReturn("Spec/MyDoc");
        return IssueMetaInfo.create(module).set(ModuleStructureLinkRoleRepairer.TARGET_ROLE, targetRole).serialize();
    }

    private static IModule mockModule(String moduleName, String structureLinkRoleId) {
        IModule module = mock(IModule.class);
        lenient().when(module.getProjectId()).thenReturn("elibrary");
        lenient().when(module.getRelativePath()).thenReturn("Spec/" + moduleName);
        lenient().when(module.getModuleName()).thenReturn(moduleName);
        // The role mock is built first: creating a mock inside when(...) leaves Mockito with an unfinished stub.
        ILinkRoleOpt role = mockRole(structureLinkRoleId);
        lenient().when(module.getStructureLinkRole()).thenReturn(role);
        containWorkItems(module);
        return module;
    }

    private static ILinkRoleOpt mockRole(String id) {
        ILinkRoleOpt role = mock(ILinkRoleOpt.class);
        lenient().when(role.getId()).thenReturn(id);
        return role;
    }

    @SuppressWarnings("unchecked")
    private static void mockRoleEnumeration(IModule module, ILinkRoleOpt... roles) {
        IDataService dataService = mock(IDataService.class);
        IEnumeration<ILinkRoleOpt> enumeration = mock(IEnumeration.class);
        for (ILinkRoleOpt role : roles) {
            lenient().when(enumeration.wrapOption(role.getId())).thenReturn(role);
        }
        lenient().when(dataService.getEnumerationForEnumId(any(), any())).thenReturn(enumeration);
        lenient().when(module.getDataSvc()).thenReturn(dataService);
    }

    /** The work item the given source links to, read back from what {@link #link} stubbed. */
    private static IWorkItem linkTargetOf(IWorkItem source) {
        return source.getLinkedWorkItemsStructsDirect().iterator().next().getLinkedItem();
    }

    private static IWorkItem link(String sourceId, String targetId, String roleId) {
        IWorkItem source = mock(IWorkItem.class);
        lenient().when(source.getId()).thenReturn(sourceId);
        lenient().when(source.isUnresolvable()).thenReturn(false);

        IWorkItem target = mock(IWorkItem.class);
        lenient().when(target.getId()).thenReturn(targetId);

        ILinkRoleOpt role = mockRole(roleId);
        ILinkedWorkItemStruct struct = mock(ILinkedWorkItemStruct.class);
        lenient().when(struct.getLinkRole()).thenReturn(role);
        lenient().when(struct.getLinkedItem()).thenReturn(target);
        lenient().when(source.getLinkedWorkItemsStructsDirect()).thenReturn(List.of(struct));

        // Modifiable unless a test says otherwise: the repair refuses to touch a work item it cannot write.
        IWorkItemPermissions permissions = mock(IWorkItemPermissions.class);
        lenient().when(permissions.modify()).thenReturn(true);
        lenient().when(source.can()).thenReturn(permissions);
        return source;
    }

    @SuppressWarnings("unchecked")
    private static void containWorkItems(IModule module, IWorkItem... workItems) {
        IPObjectList<IWorkItem> list = mock(IPObjectList.class);
        List<IWorkItem> contained = new ArrayList<>(List.of(workItems));
        lenient().when(list.iterator()).thenAnswer(invocation -> contained.iterator());
        lenient().when(module.getContainedWorkItems()).thenReturn(list);
    }
}
