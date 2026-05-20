package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.IModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(PlatformContextMockExtension.class)
class ModuleStandardStructureLinkRoleRepairerTest {

    // --- metadata ---

    @Test
    void testGetDisplayName() {
        assertEquals("Standard structure link role", ModuleStandardStructureLinkRoleRepairer.NAME);
        assertEquals(ModuleStandardStructureLinkRoleRepairer.NAME,
                new ModuleStandardStructureLinkRoleRepairer().getDisplayName());
    }

    @Test
    void testGetDescription() {
        String description = new ModuleStandardStructureLinkRoleRepairer().getDescription();
        assertNotNull(description);
        // Mentions the allowed role id and the no-automatic-repair nature
        assertTrue(description.contains("'%s'".formatted(ModuleStandardStructureLinkRoleRepairer.ALLOWED_STRUCTURE_LINK_ROLE)));
        assertTrue(description.toLowerCase().contains("manual"));
    }

    @Test
    void testGetRepairerId() {
        assertEquals("ModuleStandardStructureLinkRoleRepairer",
                new ModuleStandardStructureLinkRoleRepairer().getRepairerId());
    }

    @Test
    void testGetConfigsIsEmpty() {
        // This repairer exposes no configurable settings
        assertTrue(new ModuleStandardStructureLinkRoleRepairer().getConfigs().isEmpty());
    }

    @Test
    void testAllowedStructureLinkRoleConstant() {
        // Lock the contract: the "standard" role id is "parent"
        assertEquals("parent", ModuleStandardStructureLinkRoleRepairer.ALLOWED_STRUCTURE_LINK_ROLE);
    }

    // --- scan() ---

    @Test
    void testScanModuleWithParentRoleReturnsNoIssue() {
        ModuleStandardStructureLinkRoleRepairer repairer = new ModuleStandardStructureLinkRoleRepairer();
        IModule module = mockModule("Spec/MyDoc", "MyDoc",
                ModuleStandardStructureLinkRoleRepairer.ALLOWED_STRUCTURE_LINK_ROLE);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanModuleWithNonParentRoleCreatesSingleIssue() {
        ModuleStandardStructureLinkRoleRepairer repairer = new ModuleStandardStructureLinkRoleRepairer();
        IModule module = mockModule("Spec/MyDoc", "MyDoc", "child");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertEquals(1, issues.size());
        Issue issue = issues.getFirst();
        assertEquals("Document 'MyDoc' has non-standard structure link role 'child'", issue.getDescription());
        assertEquals("ModuleStandardStructureLinkRoleRepairer", issue.getRepairer());
    }

    @Test
    void testScanIssueMetaInfoIsCreatedFromModule() {
        ModuleStandardStructureLinkRoleRepairer repairer = new ModuleStandardStructureLinkRoleRepairer();
        IModule module = mockModule("Spec/MyDoc", "MyDoc", "default");
        when(module.getRevision()).thenReturn("42");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertEquals(1, issues.size());
        IssueMetaInfo metaInfo = issues.getFirst().getRawMetaInfo();
        assertEquals("elibrary", metaInfo.get(IssueMetaInfo.PROJECT_ID));
        assertEquals("Spec/MyDoc", metaInfo.get(IssueMetaInfo.MODULE_PATH));
        assertEquals("42", metaInfo.get(IssueMetaInfo.REVISION));
        // The Issue constructor stamps the repairer id into the meta info
        assertEquals("ModuleStandardStructureLinkRoleRepairer", metaInfo.get(IssueMetaInfo.REPAIRER));
    }

    @Test
    void testScanModuleWithNullStructureLinkRoleIdCreatesIssue() {
        // Objects.equals(null, "parent") → false → issue is reported with "null" rendered into the message
        ModuleStandardStructureLinkRoleRepairer repairer = new ModuleStandardStructureLinkRoleRepairer();
        IModule module = mockModule("Spec/MyDoc", "MyDoc", null);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertEquals(1, issues.size());
        assertEquals("Document 'MyDoc' has non-standard structure link role 'null'", issues.getFirst().getDescription());
    }

    @Test
    void testScanIssueDescriptionIncludesActualModuleName() {
        // Lock the module-name placeholder substitution
        ModuleStandardStructureLinkRoleRepairer repairer = new ModuleStandardStructureLinkRoleRepairer();
        IModule module = mockModule("Spec/Other", "Some Other Document", "related");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertEquals(1, issues.size());
        String description = issues.getFirst().getDescription();
        assertTrue(description.contains("Some Other Document"));
        assertTrue(description.contains("related"));
    }

    // --- repair() ---

    @Test
    void testRepairAlwaysReturnsUnsuccessfulResult() {
        ModuleStandardStructureLinkRoleRepairer repairer = new ModuleStandardStructureLinkRoleRepairer();
        IModule module = mock(IModule.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairContext repairContext = new RepairContext(metaInfo,
                mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertFalse(result.isSuccess());
    }

    @Test
    void testRepairResultCarriesNoAutomaticRepairWarning() {
        ModuleStandardStructureLinkRoleRepairer repairer = new ModuleStandardStructureLinkRoleRepairer();
        IModule module = mock(IModule.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairContext repairContext = new RepairContext(metaInfo,
                mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertEquals(1, result.getWarnings().size());
        assertEquals("Automatic repair is not possible.", result.getWarnings().iterator().next());
    }

    @Test
    void testRepairResultPropagatesIssueMetaInfoFromContext() {
        ModuleStandardStructureLinkRoleRepairer repairer = new ModuleStandardStructureLinkRoleRepairer();
        IModule module = mock(IModule.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized-meta");
        RepairContext repairContext = new RepairContext(metaInfo,
                mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertSame(metaInfo, result.getRawIssueMetaInfo());
        assertEquals("serialized-meta", result.getIssueMetaInfo());
    }

    @Test
    void testRepairIgnoresModuleArgument() {
        // The method does not touch the module — it just emits a non-repairable result.
        ModuleStandardStructureLinkRoleRepairer repairer = new ModuleStandardStructureLinkRoleRepairer();
        IModule module = mock(IModule.class);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairContext repairContext = new RepairContext(metaInfo,
                mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(module, repairContext);

        assertFalse(result.isSuccess());
        // No interactions with the module — repair makes no inspection or mutation.
        org.mockito.Mockito.verifyNoInteractions(module);
    }

    private static IModule mockModule(String relativePath, String moduleName, String structureLinkRoleId) {
        IModule module = mock(IModule.class);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getRelativePath()).thenReturn(relativePath);
        when(module.getModuleName()).thenReturn(moduleName);
        ILinkRoleOpt role = mock(ILinkRoleOpt.class);
        when(role.getId()).thenReturn(structureLinkRoleId);
        when(module.getStructureLinkRole()).thenReturn(role);
        return module;
    }
}
