package ch.sbb.polarion.extension.xml_repair.service;

import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.exception.ObjectNotFoundException;
import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.rest.exception.UnauthorizedException;
import ch.sbb.polarion.extension.generic.settings.NamedSettings;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.test_extensions.CustomExtensionMock;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.generic.test_extensions.TransactionalExecutorExtension;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.repairers.BaseRepairer;
import ch.sbb.polarion.extension.xml_repair.repairers.BrokenLinkedWorkItemsRepairer;
import ch.sbb.polarion.extension.xml_repair.repairers.IRepairer;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairParams;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanEntity;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanResult;
import ch.sbb.polarion.extension.xml_repair.settings.AuthorizationModel;
import ch.sbb.polarion.extension.xml_repair.settings.AuthorizationSettings;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.model.ModelObject;
import com.polarion.alm.shared.api.model.ModelObjectsSearch;
import com.polarion.alm.shared.api.model.PrototypeEnum;
import com.polarion.alm.shared.api.model.document.Document;
import com.polarion.alm.shared.api.model.document.DocumentSelector;
import com.polarion.alm.shared.api.impl.ScopeFactoryImpl;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.shared.api.utils.internal.InternalPolarionUtils;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.internal.model.UniqueObject;
import com.polarion.alm.tracker.model.IBaseline;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollectionElement;
import com.polarion.alm.tracker.model.ipi.IInternalBaselinesManager;
import com.polarion.platform.IPlatformService;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.service.repository.IRepositoryService;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.data.model.IType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;
import java.util.stream.Stream;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.mockFields;
import static ch.sbb.polarion.extension.xml_repair.util.RolesUtils.MSG_NOT_AUTHORIZED_BY_ADMIN;
import static ch.sbb.polarion.extension.xml_repair.util.RolesUtils.MSG_NO_PERMISSIONS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class, TransactionalExecutorExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
@CurrentContextConfig("xml-repair")
class XmlRepairPolarionServiceTest {

    @CustomExtensionMock
    protected ITrackerService trackerService;
    @CustomExtensionMock
    protected IProjectService projectService;
    @CustomExtensionMock
    protected ISecurityService securityService;
    @CustomExtensionMock
    protected IPlatformService platformService;
    @CustomExtensionMock
    protected IRepositoryService repositoryService;
    private XmlRepairPolarionService polarionService;

    @BeforeEach
    void beforeEach() {
        polarionService = spy(new XmlRepairPolarionService(trackerService, projectService, securityService, platformService, repositoryService));
    }

    // ---- repair(RepairParams) tests ----

    @Test
    void testRepairParamsWithWorkItem() {
        IWorkItem wi = mock(IWorkItem.class);
        when(wi.getProjectId()).thenReturn("proj");
        when(wi.getId()).thenReturn("WI-1");
        IssueMetaInfo metaInfo = IssueMetaInfo.create(wi);
        metaInfo.set(IssueMetaInfo.REPAIRER, "SomeRepairer");

        RepairParams params = new RepairParams();
        params.setIssueMetaInfos(List.of(metaInfo.serialize()));

        IWorkItem resolvedWi = mock(IWorkItem.class);
        doReturn(resolvedWi).when(polarionService).getWorkItem("proj", "WI-1", null);

        IssueMetaInfo resultMetaInfo = mock(IssueMetaInfo.class);
        when(resultMetaInfo.serialize()).thenReturn("serialized");
        RepairResult expectedResult = new RepairResult(resultMetaInfo, true);
        doReturn(expectedResult).when(polarionService).repairEntity(any(IUniqueObject.class), any(RepairContext.class));

        List<RepairResult> results = polarionService.repair(params);

        assertEquals(1, results.size());
        assertTrue(results.getFirst().isSuccess());
        verify(polarionService).getWorkItem("proj", "WI-1", null);
    }

    @Test
    void testRepairParamsWithModule() {
        IModule mod = mock(IModule.class);
        when(mod.getProjectId()).thenReturn("proj");
        when(mod.getRelativePath()).thenReturn("Spec/MyDoc");
        IssueMetaInfo metaInfo = IssueMetaInfo.create(mod);
        metaInfo.set(IssueMetaInfo.REPAIRER, "SomeRepairer");

        RepairParams params = new RepairParams();
        params.setIssueMetaInfos(List.of(metaInfo.serialize()));

        IProject project = mock(IProject.class);
        doReturn(project).when(polarionService).getProject("proj");
        IModule resolvedModule = mock(IModule.class);
        doReturn(resolvedModule).when(polarionService).getModule(any(IProject.class), any());

        IssueMetaInfo resultMetaInfo = mock(IssueMetaInfo.class);
        when(resultMetaInfo.serialize()).thenReturn("serialized");
        RepairResult expectedResult = new RepairResult(resultMetaInfo, true);
        doReturn(expectedResult).when(polarionService).repairEntity(any(IUniqueObject.class), any(RepairContext.class));

        List<RepairResult> results = polarionService.repair(params);

        assertEquals(1, results.size());
        verify(polarionService).getProject("proj");
    }

    @Test
    void testRepairParamsMultipleItems() {
        IWorkItem wi1 = mock(IWorkItem.class);
        when(wi1.getProjectId()).thenReturn("proj");
        when(wi1.getId()).thenReturn("WI-1");
        IssueMetaInfo meta1 = IssueMetaInfo.create(wi1);

        IWorkItem wi2 = mock(IWorkItem.class);
        when(wi2.getProjectId()).thenReturn("proj");
        when(wi2.getId()).thenReturn("WI-2");
        IssueMetaInfo meta2 = IssueMetaInfo.create(wi2);

        RepairParams params = new RepairParams();
        params.setIssueMetaInfos(List.of(meta1.serialize(), meta2.serialize()));

        doReturn(mock(IWorkItem.class)).when(polarionService).getWorkItem(anyString(), anyString(), isNull());

        IssueMetaInfo resultMetaInfo = mock(IssueMetaInfo.class);
        when(resultMetaInfo.serialize()).thenReturn("serialized");
        doReturn(new RepairResult(resultMetaInfo, true)).when(polarionService).repairEntity(any(IUniqueObject.class), any(RepairContext.class));

        List<RepairResult> results = polarionService.repair(params);

        assertEquals(2, results.size());
    }

    @Test
    void testRepairParamsFailedItemDoesNotStopOthers() {
        IWorkItem wi1 = mock(IWorkItem.class);
        when(wi1.getProjectId()).thenReturn("proj");
        when(wi1.getId()).thenReturn("WI-1");
        IssueMetaInfo meta1 = IssueMetaInfo.create(wi1);

        IWorkItem wi2 = mock(IWorkItem.class);
        when(wi2.getProjectId()).thenReturn("proj");
        when(wi2.getId()).thenReturn("WI-2");
        IssueMetaInfo meta2 = IssueMetaInfo.create(wi2);

        IWorkItem wi3 = mock(IWorkItem.class);
        when(wi3.getProjectId()).thenReturn("proj");
        when(wi3.getId()).thenReturn("WI-3");
        IssueMetaInfo meta3 = IssueMetaInfo.create(wi3);

        RepairParams params = new RepairParams();
        params.setIssueMetaInfos(List.of(meta1.serialize(), meta2.serialize(), meta3.serialize()));

        IWorkItem resolvedWi1 = mock(IWorkItem.class);
        IWorkItem resolvedWi3 = mock(IWorkItem.class);
        doReturn(resolvedWi1).when(polarionService).getWorkItem("proj", "WI-1", null);
        doThrow(new RuntimeException("Entity not found")).when(polarionService).getWorkItem("proj", "WI-2", null);
        doReturn(resolvedWi3).when(polarionService).getWorkItem("proj", "WI-3", null);

        IssueMetaInfo resultMetaInfo = mock(IssueMetaInfo.class);
        when(resultMetaInfo.serialize()).thenReturn("serialized");
        doReturn(new RepairResult(resultMetaInfo, true)).when(polarionService).repairEntity(any(IUniqueObject.class), any(RepairContext.class));

        List<RepairResult> results = polarionService.repair(params);

        assertEquals(3, results.size());
        assertTrue(results.get(0).isSuccess());
        assertFalse(results.get(1).isSuccess());
        assertTrue(results.get(1).getWarnings().stream().anyMatch(w -> w.contains("Entity not found")));
        assertTrue(results.get(2).isSuccess());
    }

    @Test
    void testRepairParamsFailsFastForWorkItemWithRevision() {
        IWorkItem wi = mock(IWorkItem.class);
        when(wi.getProjectId()).thenReturn("proj");
        when(wi.getId()).thenReturn("WI-1");
        when(wi.getRevision()).thenReturn("42");
        IssueMetaInfo metaInfo = IssueMetaInfo.create(wi);

        RepairParams params = new RepairParams();
        params.setIssueMetaInfos(List.of(metaInfo.serialize()));

        List<RepairResult> results = polarionService.repair(params);

        assertEquals(1, results.size());
        assertFalse(results.getFirst().isSuccess());
        assertTrue(results.getFirst().getWarnings().stream()
                .anyMatch(w -> w.contains("baseline/revision") && w.contains("switch to HEAD")));
        verify(polarionService, never()).getWorkItem(anyString(), anyString(), any());
        verify(polarionService, never()).getModule(any(), any());
        verify(polarionService, never()).repairEntity(any(IUniqueObject.class), any(RepairContext.class));
    }

    @Test
    void testRepairParamsFailsFastForModuleWithRevision() {
        IModule mod = mock(IModule.class);
        when(mod.getProjectId()).thenReturn("proj");
        when(mod.getRelativePath()).thenReturn("Spec/MyDoc");
        when(mod.getRevision()).thenReturn("100");
        IssueMetaInfo metaInfo = IssueMetaInfo.create(mod);

        RepairParams params = new RepairParams();
        params.setIssueMetaInfos(List.of(metaInfo.serialize()));

        List<RepairResult> results = polarionService.repair(params);

        assertEquals(1, results.size());
        assertFalse(results.getFirst().isSuccess());
        assertTrue(results.getFirst().getWarnings().stream()
                .anyMatch(w -> w.contains("baseline/revision") && w.contains("switch to HEAD")));
        verify(polarionService, never()).getProject(anyString());
        verify(polarionService, never()).getModule(any(), any());
        verify(polarionService, never()).repairEntity(any(IUniqueObject.class), any(RepairContext.class));
    }

    @Test
    void testRepairParamsForwardsConfigs() {
        IWorkItem wi = mock(IWorkItem.class);
        when(wi.getProjectId()).thenReturn("proj");
        when(wi.getId()).thenReturn("WI-1");
        IssueMetaInfo metaInfo = IssueMetaInfo.create(wi);

        UserConfigs configs = new UserConfigs();
        configs.put("key", "value");

        RepairParams params = new RepairParams();
        params.setIssueMetaInfos(List.of(metaInfo.serialize()));
        params.setConfigs(configs);

        doReturn(mock(IWorkItem.class)).when(polarionService).getWorkItem(anyString(), anyString(), isNull());

        IssueMetaInfo resultMetaInfo = mock(IssueMetaInfo.class);
        when(resultMetaInfo.serialize()).thenReturn("serialized");
        doReturn(new RepairResult(resultMetaInfo, true)).when(polarionService).repairEntity(any(IUniqueObject.class), any(RepairContext.class));

        polarionService.repair(params);

        verify(polarionService).repairEntity(any(IUniqueObject.class), argThat(ctx -> ctx.configs().containsKey("key")));
    }

    // ---- repair(IUniqueObject, RepairContext) tests ----

    @Test
    void testRepairEntityFindsRepairerAndDelegates() {
        doNothing().when(polarionService).checkAccess(any());

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString(IssueMetaInfo.REPAIRER)).thenReturn("TestRepairer");

        RepairResult expectedResult = new RepairResult(metaInfo, true);
        TestRepairer repairer = new TestRepairer(expectedResult);
        doReturn(List.of(repairer)).when(polarionService).getRepairersForEntity(any());

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = polarionService.repairEntity(entity, context);

        assertSame(expectedResult, result);
        verify(polarionService).checkAccess(entity);
    }

    @Test
    void testRepairEntityWithNullType() {
        doNothing().when(polarionService).checkAccess(any());

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        var entity = mock(UniqueObject.class,
                withSettings().extraInterfaces(IWorkItem.class, IWorkflowObject.class).defaultAnswer(RETURNS_DEEP_STUBS));
        when(((IWorkflowObject) entity).getType()).thenReturn(null);
        when(entity.getReferencePath()).thenReturn("elibrary/WI-1");
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = polarionService.repairEntity(entity, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("has no type")));
        verify(polarionService, never()).getRepairersForEntity(any());
    }

    @Test
    void testRepairEntityRepairerNotFound() {
        doNothing().when(polarionService).checkAccess(any());

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString(IssueMetaInfo.REPAIRER)).thenReturn("NonExistentRepairer");

        doReturn(List.of(new TestRepairer(null))).when(polarionService).getRepairersForEntity(any());

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        assertThrows(IllegalArgumentException.class, () -> polarionService.repairEntity(entity, context));
    }

    // ---- isWorkItemExists tests ----

    @Test
    void testIsWorkItemExists() {
        when(polarionService.getWorkItem("elibrary", "EL-1", null)).thenReturn(mock(IWorkItem.class));
        when(polarionService.getWorkItem("elibrary", "EL-2", null)).thenThrow(ObjectNotFoundException.class);
        assertTrue(polarionService.isWorkItemExists("elibrary", "EL-1", null));
        assertFalse(polarionService.isWorkItemExists("elibrary", "EL-2", null));
    }

    @Test
    void testIsWorkItemExistsWithRevision() {
        doReturn(mock(IWorkItem.class)).when(polarionService).getWorkItem("elibrary", "EL-1", "42");
        assertTrue(polarionService.isWorkItemExists("elibrary", "EL-1", "42"));
    }

    // ---- Authorization tests ----

    @Test
    void testUserNotAuthorizedForRepair() {
        try {
            AuthorizationSettings settingsMock = mock(AuthorizationSettings.class);
            when(settingsMock.getFeatureName()).thenReturn(AuthorizationSettings.FEATURE_NAME);
            AuthorizationModel authorizationModel = new AuthorizationModel();
            authorizationModel.setGlobalRoles("repairer");
            authorizationModel.setProjectRoles("project_repairer");
            when(settingsMock.read(ScopeUtils.getScopeFromProject("projectId"), SettingId.fromName(NamedSettings.DEFAULT_NAME), null)).thenReturn(authorizationModel);
            NamedSettingsRegistry.INSTANCE.register(List.of(settingsMock));

            when(securityService.getCurrentUser()).thenReturn("user");
            when(securityService.getRolesForUser("user")).thenReturn(List.of("role1", "role2"));

            IProject projectMock = mock(IProject.class);
            when(projectService.getProject("projectId")).thenReturn(projectMock);

            ITrackerProject trackerProjectMock = mock(ITrackerProject.class);
            when(trackerService.getTrackerProject((IProject) any())).thenReturn(trackerProjectMock);
            IContextId contextIdMock = mock(IContextId.class);
            when(trackerProjectMock.getContextId()).thenReturn(contextIdMock);
            when(securityService.getRolesForUser("user", contextIdMock)).thenReturn(List.of("role3", "role4"));

            assertFalse(polarionService.userAuthorizedForRepair("projectId"));
        } finally {
            NamedSettingsRegistry.INSTANCE.getAll().clear();
        }
    }

    @Test
    void testUserAuthorizedForRepairByGlobalRole() {
        try {
            AuthorizationSettings settingsMock = mock(AuthorizationSettings.class);
            when(settingsMock.getFeatureName()).thenReturn(AuthorizationSettings.FEATURE_NAME);
            AuthorizationModel authorizationModel = new AuthorizationModel();
            authorizationModel.setGlobalRoles("repairer");
            authorizationModel.setProjectRoles("project_repairer");
            when(settingsMock.read(ScopeUtils.getScopeFromProject("projectId"), SettingId.fromName(NamedSettings.DEFAULT_NAME), null)).thenReturn(authorizationModel);
            NamedSettingsRegistry.INSTANCE.register(List.of(settingsMock));

            when(securityService.getCurrentUser()).thenReturn("user");
            when(securityService.getRolesForUser("user")).thenReturn(List.of("repairer", "role2"));

            assertTrue(polarionService.userAuthorizedForRepair("projectId"));
        } finally {
            NamedSettingsRegistry.INSTANCE.getAll().clear();
        }
    }

    @Test
    void testUserAuthorizedForRepairByProjectRole() {
        try {
            AuthorizationSettings settingsMock = mock(AuthorizationSettings.class);
            when(settingsMock.getFeatureName()).thenReturn(AuthorizationSettings.FEATURE_NAME);
            AuthorizationModel authorizationModel = new AuthorizationModel();
            authorizationModel.setGlobalRoles("repairer");
            authorizationModel.setProjectRoles("project_repairer");
            when(settingsMock.read(ScopeUtils.getScopeFromProject("projectId"), SettingId.fromName(NamedSettings.DEFAULT_NAME), null)).thenReturn(authorizationModel);
            NamedSettingsRegistry.INSTANCE.register(List.of(settingsMock));

            when(securityService.getCurrentUser()).thenReturn("user");
            when(securityService.getRolesForUser("user")).thenReturn(List.of("role1", "role2"));

            IProject projectMock = mock(IProject.class);
            when(projectService.getProject("projectId")).thenReturn(projectMock);

            ITrackerProject trackerProjectMock = mock(ITrackerProject.class);
            when(trackerService.getTrackerProject((IProject) any())).thenReturn(trackerProjectMock);
            IContextId contextIdMock = mock(IContextId.class);
            when(trackerProjectMock.getContextId()).thenReturn(contextIdMock);
            when(securityService.getRolesForUser("user", contextIdMock)).thenReturn(List.of("role3", "project_repairer"));

            assertTrue(polarionService.userAuthorizedForRepair("projectId"));
        } finally {
            NamedSettingsRegistry.INSTANCE.getAll().clear();
        }
    }

    @Test
    void testCheckAccessNotAuthorizedByAdmin() {
        IWorkflowObject entity = mock(IWorkflowObject.class);
        when(entity.getProjectId()).thenReturn("projectId");
        doReturn(false).when(polarionService).userAuthorizedForRepair("projectId");

        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> polarionService.checkAccess(entity));
        assertEquals(MSG_NOT_AUTHORIZED_BY_ADMIN, exception.getMessage());
    }

    @Test
    void testCheckAccessNoModifyPermission() {
        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("projectId");
        doReturn(true).when(polarionService).userAuthorizedForRepair("projectId");
        when(entity.can().modify()).thenReturn(false);

        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> polarionService.checkAccess(entity));
        assertEquals(MSG_NO_PERMISSIONS, exception.getMessage());
    }

    @Test
    void testCheckAccessAllowed() {
        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("projectId");
        doReturn(true).when(polarionService).userAuthorizedForRepair("projectId");
        when(entity.can().modify()).thenReturn(true);

        assertDoesNotThrow(() -> polarionService.checkAccess(entity));
    }

    // ---- Repairer metadata tests ----

    @Test
    void testGetRepairerMetas() {
        assertEquals(10, polarionService.getRepairerMetas(EntityType.COLLECTION).size());
        assertEquals(10, polarionService.getRepairerMetas(EntityType.DOCUMENT).size());
        assertEquals(5, polarionService.getRepairerMetas(EntityType.WORKITEM).size());
    }

    @Test
    void testGetRepairersForEntityCollection() {
        var entity = mock(IBaselineCollection.class, RETURNS_DEEP_STUBS);
        when(entity.getPrototype().getName()).thenReturn(IBaselineCollection.PROTO);

        List<IRepairer> repairers = polarionService.getRepairersForEntity(entity);

        assertEquals(10, repairers.size());
    }

    @Test
    void testGetRepairersForEntityDocument() {
        var entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getPrototype().getName()).thenReturn(IModule.PROTO);

        List<IRepairer> repairers = polarionService.getRepairersForEntity(entity);

        assertEquals(10, repairers.size());
    }

    @Test
    void testGetRepairersForEntityWorkItem() {
        var entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getPrototype().getName()).thenReturn(IWorkItem.PROTO);

        List<IRepairer> repairers = polarionService.getRepairersForEntity(entity);

        assertEquals(5, repairers.size());
    }

    // ---- scanEntity validation tests ----

    @Test
    void testScanEntityThrowsWhenNoRepairersMatch() {
        var entity = mock(UniqueObject.class, withSettings()
                .extraInterfaces(IWorkItem.class, IWorkflowObject.class)
                .defaultAnswer(RETURNS_DEEP_STUBS));
        when(entity.getPrototype().getName()).thenReturn(IWorkItem.PROTO);
        when(entity.getReferencePath()).thenReturn("proj/WI-1");
        when(entity.getProjectId()).thenReturn("proj");
        when(entity.getId()).thenReturn("WI-1");

        ScanEntity scanEntity = ScanEntity.from(entity);
        ScanContext context = createTestScanContext(List.of("NonExistentRepairer"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> polarionService.scanEntity(scanEntity, context));
        assertTrue(exception.getMessage().contains("No repairers selected"));
        assertTrue(exception.getMessage().contains("WORKITEM"));
    }

    @Test
    void testScanEntityThrowsWhenRepairersListIsEmpty() {
        var entity = mock(UniqueObject.class, withSettings()
                .extraInterfaces(IWorkItem.class, IWorkflowObject.class)
                .defaultAnswer(RETURNS_DEEP_STUBS));
        when(entity.getPrototype().getName()).thenReturn(IWorkItem.PROTO);
        when(entity.getReferencePath()).thenReturn("proj/WI-1");
        when(entity.getProjectId()).thenReturn("proj");
        when(entity.getId()).thenReturn("WI-1");

        ScanEntity scanEntity = ScanEntity.from(entity);
        ScanContext context = createTestScanContext(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> polarionService.scanEntity(scanEntity, context));
    }

    // ---- getAllFields tests ----

    @Test
    void testGetAllFieldsWithoutTypeFilter() {
        IContextId contextId = mock(IContextId.class);
        Set<FieldMetadata> generalFields = mockFields(FieldType.STRING, "id", "title");
        Set<FieldMetadata> customFields = mockFields(FieldType.INTEGER, "custom1", "custom2");

        doReturn(generalFields).when(polarionService).getGeneralFields("TestProto", contextId, "TestType");
        doReturn(customFields).when(polarionService).getCustomFields("TestProto", contextId, "TestType");

        Set<FieldMetadata> result = polarionService.getAllFields("TestProto", contextId, "TestType", false);

        assertEquals(4, result.size());
        assertTrue(result.containsAll(generalFields));
        assertTrue(result.containsAll(customFields));
    }

    @Test
    void testGetAllFieldsWithSingleTypeFilter() {
        IContextId contextId = mock(IContextId.class);
        IType stringType = FieldType.STRING.getType();

        Set<FieldMetadata> generalFields = mockFields(FieldType.STRING, "id", "title");
        Set<FieldMetadata> customFields = Set.of(
                mockFieldWithType("custom1", FieldType.STRING),
                mockFieldWithType("custom2", FieldType.INTEGER)
        );

        doReturn(generalFields).when(polarionService).getGeneralFields("TestProto", contextId, "TestType");
        doReturn(customFields).when(polarionService).getCustomFields("TestProto", contextId, "TestType");

        Set<FieldMetadata> result = polarionService.getAllFields("TestProto", contextId, "TestType", false, stringType);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(f -> f.getType().equals(stringType)));
    }

    @Test
    void testGetAllFieldsWithNonMatchingTypeFilter() {
        IContextId contextId = mock(IContextId.class);
        IType richTextType = FieldType.RICH.getType();

        Set<FieldMetadata> generalFields = mockFields(FieldType.STRING, "id", "title");
        Set<FieldMetadata> customFields = mockFields(FieldType.INTEGER, "custom1");

        doReturn(generalFields).when(polarionService).getGeneralFields("TestProto", contextId, "TestType");
        doReturn(customFields).when(polarionService).getCustomFields("TestProto", contextId, "TestType");

        Set<FieldMetadata> result = polarionService.getAllFields("TestProto", contextId, "TestType", false, richTextType);

        assertEquals(0, result.size());
    }

    @Test
    void testGetAllFieldsCompareTypeClassTrue() {
        IContextId contextId = mock(IContextId.class);
        IType filterType = mock(IType.class);
        IType sameClassType = mock(IType.class);

        FieldMetadata matchingField = mock(FieldMetadata.class);
        when(matchingField.getType()).thenReturn(sameClassType);

        doReturn(Set.of(matchingField)).when(polarionService).getGeneralFields("TestProto", contextId, "TestType");
        doReturn(Set.of()).when(polarionService).getCustomFields("TestProto", contextId, "TestType");

        Set<FieldMetadata> result = polarionService.getAllFields("TestProto", contextId, "TestType", true, filterType);

        // Both filterType and sameClassType are mocks of IType, so their classes match
        assertEquals(1, result.size());
    }

    @Test
    void testGetAllFieldsCompareTypeClassFalse() {
        IContextId contextId = mock(IContextId.class);
        IType filterType = mock(IType.class);
        IType differentType = mock(IType.class);

        FieldMetadata field = mock(FieldMetadata.class);
        when(field.getType()).thenReturn(differentType);

        doReturn(Set.of(field)).when(polarionService).getGeneralFields("TestProto", contextId, "TestType");
        doReturn(Set.of()).when(polarionService).getCustomFields("TestProto", contextId, "TestType");

        Set<FieldMetadata> result = polarionService.getAllFields("TestProto", contextId, "TestType", false, filterType);

        // Different mock instances, equals() returns false
        assertEquals(0, result.size());
    }

    // ---- scanEntity tests (null type, non-collection, collection) ----

    @Test
    void testScanEntityWithNullTypeAddsWarning() {
        var entity = mock(UniqueObject.class, withSettings()
                .extraInterfaces(IWorkItem.class, IWorkflowObject.class)
                .defaultAnswer(RETURNS_DEEP_STUBS));
        when(entity.getPrototype().getName()).thenReturn(IWorkItem.PROTO);
        when(entity.getReferencePath()).thenReturn("proj/WI-1");
        when(entity.getProjectId()).thenReturn("proj");
        when(entity.getId()).thenReturn("WI-1");
        when(((IWorkflowObject) entity).getType()).thenReturn(null);

        ScanEntity scanEntity = ScanEntity.from(entity);
        ScanContext context = createTestScanContext(List.of("TestRepairer"));

        polarionService.scanEntity(scanEntity, context);

        assertTrue(scanEntity.getWarnings().stream().anyMatch(w -> w.contains("has no type")));
        assertTrue(scanEntity.getIssues().isEmpty());
    }

    @Test
    void testScanEntityNonCollectionScansWithRepairers() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored = mockConstruction(EntityRenderer.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            var entity = mock(UniqueObject.class, withSettings()
                    .extraInterfaces(IWorkItem.class, IWorkflowObject.class)
                    .defaultAnswer(RETURNS_DEEP_STUBS));
            when(entity.getPrototype().getName()).thenReturn(IWorkItem.PROTO);
            when(entity.getReferencePath()).thenReturn("proj/WI-1");
            when(entity.getProjectId()).thenReturn("proj");
            when(entity.getId()).thenReturn("WI-1");
            when(((IWorkflowObject) entity).getType()).thenReturn(mock(ITypeOpt.class));

            ScanEntity scanEntity = ScanEntity.from(entity);
            ScanContext context = new ScanContext(polarionService, List.of("TestRepairer"), new UserConfigs(), new Report(), new Cache());

            doReturn(List.of(new TestRepairer(null))).when(polarionService).getRepairersForEntity(any());

            polarionService.scanEntity(scanEntity, context);

            assertTrue(scanEntity.getIssues().isEmpty()); // TestRepairer returns empty list
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testScanEntityCollectionScansSubitems() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored = mockConstruction(EntityRenderer.class, (mock, ctx) ->
                     when(mock.renderEntity(any())).thenReturn(new LinkedHashMap<>()))) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            var collection = mock(UniqueObject.class, withSettings()
                    .extraInterfaces(IBaselineCollection.class)
                    .defaultAnswer(RETURNS_DEEP_STUBS));
            when(collection.getPrototype().getName()).thenReturn(IBaselineCollection.PROTO);
            when(collection.getProjectId()).thenReturn("proj");
            when(collection.getId()).thenReturn("COL-1");

            var module = mock(UniqueObject.class, withSettings()
                    .extraInterfaces(IModule.class, IWorkflowObject.class)
                    .defaultAnswer(RETURNS_DEEP_STUBS));
            when(module.getProjectId()).thenReturn("proj");
            when(((IModule) module).getModuleFolder()).thenReturn("Spec");
            when(((IModule) module).getModuleName()).thenReturn("MyDoc");
            when(module.getId()).thenReturn("MyDoc");
            when(module.getPrototype().getName()).thenReturn(IModule.PROTO);
            when(module.getReferencePath()).thenReturn("proj/Spec/MyDoc");

            IBaselineCollectionElement element = mock(IBaselineCollectionElement.class);
            when(element.getObjectWithRevision()).thenReturn(module);
            when(((IBaselineCollection) collection).getElements()).thenReturn(List.of(element));

            ScanEntity scanEntity = ScanEntity.from(collection);
            ScanContext context = new ScanContext(polarionService, List.of("TestRepairer"), new UserConfigs(), new Report(), new Cache());

            // submodule will trigger scanEntity recursively, which will hit the non-collection branch
            doReturn(List.of(new TestRepairer(null))).when(polarionService).getRepairersForEntity(any());
            when(((IWorkflowObject) module).getType()).thenReturn(mock(ITypeOpt.class));

            // DocumentSelector inherits revision() from ModelObjectSelector<T, S, R>; Mockito's deep-stub
            // cannot resolve S, so revision() returns a ModelObjectSelector mock and the bytecode cast
            // to DocumentSelector fails. Override the chain explicitly.
            DocumentSelector<Document> documentSelector = mock(DocumentSelector.class);
            Document documentMock = mock(Document.class);
            when(documentSelector.projectSpaceAndName(any(), any(), any())).thenReturn(documentMock);
            DocumentSelector<? extends Document> getBySelector = transaction.documents().getBy();
            doReturn(documentSelector).when(getBySelector).revision(any());

            polarionService.scanEntity(scanEntity, context);

            assertEquals(1, scanEntity.getSubitems().size());
            assertEquals("proj", scanEntity.getSubitems().getFirst().getProjectId());
        }
    }

    // ---- scan() method tests ----

    @Test
    void testScanEmptyQueryReturnsEmptyResult() {
        ScanParams params = new ScanParams();
        params.setProjectId("proj");
        params.setEntityType(EntityType.WORKITEM);
        params.setLimit(10);
        params.setTimeout(60000L);
        params.setRepairers(List.of("TestRepairer"));

        doReturn(List.of()).when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());

        ScanResult result = polarionService.scan(params);

        assertTrue(result.getItems().isEmpty());
        assertNotNull(result.getReport());
    }

    @Test
    void testScanBasicFlow() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored2 = mockConstruction(EntityRenderer.class, (mock, ctx) ->
                     when(mock.renderEntity(any())).thenReturn(new LinkedHashMap<>()))) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            ModelObject modelObject = createMockModelObject("WI-1");

            ScanParams params = new ScanParams();
            params.setProjectId("proj");
            params.setEntityType(EntityType.WORKITEM);
            params.setLimit(10);
            params.setTimeout(60000L);
            params.setRepairers(List.of("TestRepairer"));

            doReturn(List.of(modelObject)).doReturn(List.of())
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());
            doReturn(List.of(new TestRepairer(null))).when(polarionService).getRepairersForEntity(any());

            ScanResult result = polarionService.scan(params);

            assertEquals(1, result.getItems().size());
            assertNotNull(result.getReport());
        }
    }

    @Test
    void testScanHideValidFiltersCleanItems() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored2 = mockConstruction(EntityRenderer.class, (mock, ctx) ->
                     when(mock.renderEntity(any())).thenReturn(new LinkedHashMap<>()))) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            // Create two entities - both clean (no issues)
            ModelObject modelObject1 = createMockModelObject("WI-1");
            ModelObject modelObject2 = createMockModelObject("WI-2");

            ScanParams params = new ScanParams();
            params.setProjectId("proj");
            params.setEntityType(EntityType.WORKITEM);
            params.setLimit(10);
            params.setTimeout(60000L);
            params.setHideValid(true);
            params.setRepairers(List.of("TestRepairer"));

            doReturn(List.of(modelObject1, modelObject2)).doReturn(List.of())
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());
            doReturn(List.of(new TestRepairer(null))).when(polarionService).getRepairersForEntity(any());

            ScanResult result = polarionService.scan(params);

            // Both items are clean, hideValid=true, so they should be filtered out
            assertTrue(result.getItems().isEmpty());
        }
    }

    @Test
    void testScanTimeLimitReachedAddsWarning() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored2 = mockConstruction(EntityRenderer.class, (mock, ctx) ->
                     when(mock.renderEntity(any())).thenReturn(new LinkedHashMap<>()))) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            ModelObject modelObject1 = createMockModelObject("WI-1");
            ModelObject modelObject2 = createMockModelObject("WI-2");

            ScanParams params = new ScanParams();
            params.setProjectId("proj");
            params.setEntityType(EntityType.WORKITEM);
            params.setLimit(10);
            params.setTimeout(-1L); // Negative timeout - always exceeded
            params.setRepairers(List.of("TestRepairer"));

            doReturn(List.of(modelObject1, modelObject2))
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());
            doReturn(List.of(new TestRepairer(null))).when(polarionService).getRepairersForEntity(any());

            ScanResult result = polarionService.scan(params);

            // At least 2 items shown, and the report should contain the time limit warning
            assertEquals(2, result.getItems().size());
            assertTrue(result.getReport().contains(XmlRepairPolarionService.SCAN_TIME_LIMIT_REACHED_WARNING));
        }
    }

    @Test
    void testScanErrorDuringItemScanAddsWarning() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored2 = mockConstruction(EntityRenderer.class, (mock, ctx) ->
                     when(mock.renderEntity(any())).thenReturn(new LinkedHashMap<>()))) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            ModelObject modelObject = createMockModelObject("WI-1");

            ScanParams params = new ScanParams();
            params.setProjectId("proj");
            params.setEntityType(EntityType.WORKITEM);
            params.setLimit(10);
            params.setTimeout(60000L);
            params.setRepairers(List.of("TestRepairer"));

            doReturn(List.of(modelObject))
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());

            // Make scanEntity throw
            doThrow(new RuntimeException("scan failed")).when(polarionService).scanEntity(any(ScanEntity.class), any(ScanContext.class));

            ScanResult result = polarionService.scan(params);

            assertEquals(1, result.getItems().size());
            assertTrue(result.getItems().getFirst().getWarnings().stream().anyMatch(w -> w.contains("scan failed")));
        }
    }

    @Test
    void testScanHideValidWithLimitStopsProcessing() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored2 = mockConstruction(EntityRenderer.class, (mock, ctx) ->
                     when(mock.renderEntity(any())).thenReturn(new LinkedHashMap<>()))) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            // Create items that will have errors (so they won't be hidden)
            ModelObject modelObject1 = createMockModelObject("WI-1");
            ModelObject modelObject2 = createMockModelObject("WI-2");

            ScanParams params = new ScanParams();
            params.setProjectId("proj");
            params.setEntityType(EntityType.WORKITEM);
            params.setLimit(1); // Only allow 1 item
            params.setTimeout(60000L);
            params.setHideValid(true);
            params.setRepairers(List.of("TestRepairer"));

            doReturn(List.of(modelObject1, modelObject2))
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());

            // Make scanEntity throw so items are not considered "valid" (error != null means they're shown)
            doThrow(new RuntimeException("scan error")).when(polarionService).scanEntity(any(ScanEntity.class), any(ScanContext.class));

            ScanResult result = polarionService.scan(params);

            // With limit=1 and hideValid=true, should stop after collecting 1 item with error
            assertEquals(1, result.getItems().size());
            assertTrue(result.getReport().contains("Top items limit reached"));
        }
    }

    @Test
    void testScanHideValidKeepsCollectionWithSubitemIssues() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored2 = mockConstruction(EntityRenderer.class, (mock, ctx) ->
                     when(mock.renderEntity(any())).thenReturn(new LinkedHashMap<>()))) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            ModelObject modelObject = createMockCollectionModelObject();

            ScanParams params = new ScanParams();
            params.setProjectId("proj");
            params.setEntityType(EntityType.COLLECTION);
            params.setLimit(10);
            params.setTimeout(60000L);
            params.setHideValid(true);
            params.setRepairers(List.of("TestRepairer"));

            doReturn(List.of(modelObject)).doReturn(List.of())
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());

            // Simulate the collection scan populating a subitem with issues
            doAnswer(inv -> {
                ScanEntity entity = inv.getArgument(0);
                ScanEntity sub = createScanEntity("DOC-1");
                sub.getIssues().add(createIssueForRepairer("RepairerA"));
                entity.getSubitems().add(sub);
                return null;
            }).when(polarionService).scanEntity(any(ScanEntity.class), any(ScanContext.class));

            ScanResult result = polarionService.scan(params);

            assertEquals(1, result.getItems().size(), "Collection with subitem issues must remain visible");
            assertEquals(1, result.getItems().getFirst().getSubitems().size());
            assertEquals(1, result.getItems().getFirst().getSubitems().getFirst().getIssues().size());
        }
    }

    @Test
    void testScanHideValidHidesCollectionWithoutSubitemIssues() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored2 = mockConstruction(EntityRenderer.class, (mock, ctx) ->
                     when(mock.renderEntity(any())).thenReturn(new LinkedHashMap<>()))) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            ModelObject modelObject = createMockCollectionModelObject();

            ScanParams params = new ScanParams();
            params.setProjectId("proj");
            params.setEntityType(EntityType.COLLECTION);
            params.setLimit(10);
            params.setTimeout(60000L);
            params.setHideValid(true);
            params.setRepairers(List.of("TestRepairer"));

            doReturn(List.of(modelObject)).doReturn(List.of())
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());

            // Collection populated with subitems that have no issues
            doAnswer(inv -> {
                ScanEntity entity = inv.getArgument(0);
                entity.getSubitems().add(createScanEntity("DOC-1"));
                entity.getSubitems().add(createScanEntity("DOC-2"));
                return null;
            }).when(polarionService).scanEntity(any(ScanEntity.class), any(ScanContext.class));

            ScanResult result = polarionService.scan(params);

            assertTrue(result.getItems().isEmpty(), "Collection with no subitem issues must be hidden when hideValid=true");
        }
    }

    @Test
    void testScanHideValidKeepsCollectionWithMixedSubitems() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored2 = mockConstruction(EntityRenderer.class, (mock, ctx) ->
                     when(mock.renderEntity(any())).thenReturn(new LinkedHashMap<>()))) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            ModelObject modelObject = createMockCollectionModelObject();

            ScanParams params = new ScanParams();
            params.setProjectId("proj");
            params.setEntityType(EntityType.COLLECTION);
            params.setLimit(10);
            params.setTimeout(60000L);
            params.setHideValid(true);
            params.setRepairers(List.of("TestRepairer"));

            doReturn(List.of(modelObject)).doReturn(List.of())
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());

            // Mix: one subitem with issues, one without
            doAnswer(inv -> {
                ScanEntity entity = inv.getArgument(0);
                ScanEntity sub1 = createScanEntity("DOC-1");
                sub1.getIssues().add(createIssueForRepairer("RepairerA"));
                ScanEntity sub2 = createScanEntity("DOC-2");
                entity.getSubitems().add(sub1);
                entity.getSubitems().add(sub2);
                return null;
            }).when(polarionService).scanEntity(any(ScanEntity.class), any(ScanContext.class));

            ScanResult result = polarionService.scan(params);

            assertEquals(1, result.getItems().size(), "Collection with at least one issue-bearing subitem must remain visible");
            assertEquals(2, result.getItems().getFirst().getSubitems().size());
        }
    }

    @Test
    void testScanHideValidDisabledKeepsCollectionWithoutSubitemIssues() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored2 = mockConstruction(EntityRenderer.class, (mock, ctx) ->
                     when(mock.renderEntity(any())).thenReturn(new LinkedHashMap<>()))) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            ModelObject modelObject = createMockCollectionModelObject();

            ScanParams params = new ScanParams();
            params.setProjectId("proj");
            params.setEntityType(EntityType.COLLECTION);
            params.setLimit(10);
            params.setTimeout(60000L);
            params.setHideValid(false);
            params.setRepairers(List.of("TestRepairer"));

            doReturn(List.of(modelObject)).doReturn(List.of())
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());

            doAnswer(inv -> {
                ScanEntity entity = inv.getArgument(0);
                entity.getSubitems().add(createScanEntity("DOC-1"));
                return null;
            }).when(polarionService).scanEntity(any(ScanEntity.class), any(ScanContext.class));

            ScanResult result = polarionService.scan(params);

            assertEquals(1, result.getItems().size(), "hideValid=false must short-circuit the hide check");
        }
    }

    private ModelObject createMockCollectionModelObject() {
        ModelObject modelObject = mock(ModelObject.class);
        var entity = mock(UniqueObject.class, withSettings()
                .extraInterfaces(IBaselineCollection.class)
                .defaultAnswer(RETURNS_DEEP_STUBS));
        when(entity.getPrototype().getName()).thenReturn(IBaselineCollection.PROTO);
        when(entity.getProjectId()).thenReturn("proj");
        when(entity.getId()).thenReturn("COL-1");
        when(modelObject.getOldApi()).thenReturn(entity);
        return modelObject;
    }

    // ---- appendRepairerBreakdown tests ----

    @Test
    void testAppendRepairerBreakdownEmptyResultAppendsNothing() {
        ScanResult result = new ScanResult();
        Report report = new Report();

        polarionService.appendRepairerBreakdown(report, result);

        assertFalse(report.toString().contains("Issues by repairer"));
    }

    @Test
    void testAppendRepairerBreakdownItemsWithoutIssuesAppendNothing() {
        ScanResult result = new ScanResult();
        result.getItems().add(createScanEntity("WI-1"));
        result.getItems().add(createScanEntity("WI-2"));
        Report report = new Report();

        polarionService.appendRepairerBreakdown(report, result);

        assertFalse(report.toString().contains("Issues by repairer"));
    }

    @Test
    void testAppendRepairerBreakdownSingleRepairerCountsAllIssues() {
        ScanResult result = new ScanResult();
        ScanEntity entity = createScanEntity("WI-1");
        entity.getIssues().add(createIssueForRepairer("RepairerA"));
        entity.getIssues().add(createIssueForRepairer("RepairerA"));
        entity.getIssues().add(createIssueForRepairer("RepairerA"));
        result.getItems().add(entity);
        Report report = new Report();

        polarionService.appendRepairerBreakdown(report, result);

        String text = report.toString();
        assertTrue(text.contains("Issues by repairer:"));
        assertTrue(text.contains("RepairerA: 3"));
    }

    @Test
    void testAppendRepairerBreakdownSortsRepairersByCountDescending() {
        ScanResult result = new ScanResult();
        ScanEntity entity = createScanEntity("WI-1");
        entity.getIssues().add(createIssueForRepairer("RepairerLow"));
        entity.getIssues().add(createIssueForRepairer("RepairerHigh"));
        entity.getIssues().add(createIssueForRepairer("RepairerHigh"));
        entity.getIssues().add(createIssueForRepairer("RepairerHigh"));
        entity.getIssues().add(createIssueForRepairer("RepairerMid"));
        entity.getIssues().add(createIssueForRepairer("RepairerMid"));
        result.getItems().add(entity);
        Report report = new Report();

        polarionService.appendRepairerBreakdown(report, result);

        String text = report.toString();
        int highIdx = text.indexOf("RepairerHigh: 3");
        int midIdx = text.indexOf("RepairerMid: 2");
        int lowIdx = text.indexOf("RepairerLow: 1");
        assertTrue(highIdx >= 0 && midIdx >= 0 && lowIdx >= 0, "All three repairers must appear in the report");
        assertTrue(highIdx < midIdx, "Higher count must appear before mid count");
        assertTrue(midIdx < lowIdx, "Mid count must appear before lower count");
    }

    @Test
    void testAppendRepairerBreakdownUsesDisplayNameWhenRepairerIsKnown() {
        ScanResult result = new ScanResult();
        ScanEntity entity = createScanEntity("WI-1");
        entity.getIssues().add(createIssueWithRealRepairer(new BrokenLinkedWorkItemsRepairer()));
        result.getItems().add(entity);
        Report report = new Report();

        polarionService.appendRepairerBreakdown(report, result);

        String text = report.toString();
        assertTrue(text.contains(BrokenLinkedWorkItemsRepairer.NAME + ": 1"));
        assertFalse(text.contains("BrokenLinkedWorkItemsRepairer:"));
    }

    @Test
    void testAppendRepairerBreakdownFallsBackToIdWhenRepairerIsUnknown() {
        ScanResult result = new ScanResult();
        ScanEntity entity = createScanEntity("WI-1");
        entity.getIssues().add(createIssueForRepairer("NotInRepairersMap"));
        result.getItems().add(entity);
        Report report = new Report();

        polarionService.appendRepairerBreakdown(report, result);

        assertTrue(report.toString().contains("NotInRepairersMap: 1"));
    }

    @Test
    void testAppendRepairerBreakdownAggregatesIssuesFromSubitems() {
        ScanResult result = new ScanResult();
        ScanEntity collection = createScanEntity("COL-1");
        ScanEntity sub1 = createScanEntity("WI-1");
        sub1.getIssues().add(createIssueForRepairer("RepairerA"));
        sub1.getIssues().add(createIssueForRepairer("RepairerB"));
        ScanEntity sub2 = createScanEntity("WI-2");
        sub2.getIssues().add(createIssueForRepairer("RepairerA"));
        collection.getSubitems().add(sub1);
        collection.getSubitems().add(sub2);
        result.getItems().add(collection);
        Report report = new Report();

        polarionService.appendRepairerBreakdown(report, result);

        String text = report.toString();
        assertTrue(text.contains("RepairerA: 2"));
        assertTrue(text.contains("RepairerB: 1"));
    }

    @Test
    void testAppendRepairerBreakdownMergesItemAndSubitemIssuesUnderSameRepairer() {
        ScanResult result = new ScanResult();
        ScanEntity item = createScanEntity("DOC-1");
        item.getIssues().add(createIssueForRepairer("RepairerA"));
        ScanEntity sub = createScanEntity("WI-1");
        sub.getIssues().add(createIssueForRepairer("RepairerA"));
        item.getSubitems().add(sub);
        result.getItems().add(item);
        Report report = new Report();

        polarionService.appendRepairerBreakdown(report, result);

        assertTrue(report.toString().contains("RepairerA: 2"));
    }

    private ScanEntity createScanEntity(String entityId) {
        var wi = mock(UniqueObject.class, withSettings()
                .extraInterfaces(IWorkItem.class, IWorkflowObject.class)
                .defaultAnswer(RETURNS_DEEP_STUBS));
        when(wi.getPrototype().getName()).thenReturn(IWorkItem.PROTO);
        when(wi.getProjectId()).thenReturn("proj");
        when(wi.getId()).thenReturn(entityId);
        return ScanEntity.from(wi);
    }

    private Issue createIssueForRepairer(String repairerId) {
        BaseRepairer repairer = mock(BaseRepairer.class);
        when(repairer.getRepairerId()).thenReturn(repairerId);
        return createIssueWithRealRepairer(repairer);
    }

    private <T extends BaseRepairer> Issue createIssueWithRealRepairer(T repairer) {
        IWorkItem wi = mock(IWorkItem.class);
        when(wi.getProjectId()).thenReturn("proj");
        when(wi.getId()).thenReturn("WI-1");
        return new Issue(IssueMetaInfo.create(wi), repairer, "test issue");
    }

    // ---- queryEntities tests ----

    @Test
    void testQueryEntitiesNoTransactionThrows() {
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(null);

            assertThrows(IllegalStateException.class, () ->
                    polarionService.queryEntities("proj", PrototypeEnum.WorkItem, null, null, null, null, null, null));
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    void testQueryEntitiesAppliesBaselineRevision() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        InternalPolarionUtils utils = mock(InternalPolarionUtils.class);
        when(transaction.utils()).thenReturn(utils);
        when(utils.addScopeToLuceneQuery(any(), anyString())).thenReturn("scoped-query");

        ModelObjectsSearch search = transaction.byEnum(PrototypeEnum.WorkItem).search();
        when(search.query(anyString())).thenReturn(search);
        when(search.baseline(any())).thenReturn(search);
        when(search.sort(anyString())).thenReturn(search);
        when(search.limit(anyInt())).thenReturn(search);
        when(search.offset(anyInt())).thenReturn(search);
        when(search.toArrayList()).thenReturn(new ArrayList<>());

        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<ScopeFactoryImpl> ignored = mockConstruction(ScopeFactoryImpl.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            List<? extends ModelObject> result = polarionService.queryEntities(
                    "proj", PrototypeEnum.WorkItem, "requirement", "id:PRJ-1", "rev-42", "~updated", 5, 50);

            assertTrue(result.isEmpty());
            verify(search).baseline("rev-42");
            verify(search).sort("~updated");
            verify(search).limit(50);
            verify(search).offset(5);
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    void testQueryEntitiesDefaultsAndNullRevisionPassedThrough() {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        InternalPolarionUtils utils = mock(InternalPolarionUtils.class);
        when(transaction.utils()).thenReturn(utils);
        when(utils.addScopeToLuceneQuery(any(), anyString())).thenReturn("scoped-query");

        ModelObjectsSearch search = transaction.byEnum(PrototypeEnum.WorkItem).search();
        when(search.query(anyString())).thenReturn(search);
        when(search.baseline(any())).thenReturn(search);
        when(search.sort(anyString())).thenReturn(search);
        when(search.limit(anyInt())).thenReturn(search);
        when(search.offset(anyInt())).thenReturn(search);
        when(search.toArrayList()).thenReturn(new ArrayList<>());

        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<ScopeFactoryImpl> ignored = mockConstruction(ScopeFactoryImpl.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);

            polarionService.queryEntities("proj", PrototypeEnum.WorkItem, null, null, null, null, null, null);

            verify(search).baseline(null);
            verify(search).sort("created");
            verify(search).limit(100);
            verify(search).offset(0);
        }
    }

    // ---- getBaselines tests ----

    @Test
    @SuppressWarnings("unchecked")
    void testGetBaselinesReturnsListSortedByRevisionDescending() {
        ITrackerProject trackerProject = mock(ITrackerProject.class);
        when(trackerService.getTrackerProject("proj")).thenReturn(trackerProject);

        IInternalBaselinesManager baselinesManager = mock(IInternalBaselinesManager.class);
        when(trackerProject.getBaselinesManager()).thenReturn(baselinesManager);

        IBaseline older = mock(IBaseline.class);
        when(older.getBaseRevision()).thenReturn("100");
        when(older.getName()).thenReturn("Older");
        IBaseline newer = mock(IBaseline.class);
        when(newer.getBaseRevision()).thenReturn("200");
        when(newer.getName()).thenReturn("Newer");

        IPObjectList<IBaseline> baselineList = mock(IPObjectList.class);
        when(baselineList.stream()).thenReturn(Stream.of(older, newer));
        when(baselinesManager.getBaselines()).thenReturn(baselineList);

        List<BaselineInfo> result = polarionService.getBaselines("proj");

        assertEquals(2, result.size());
        assertEquals("200", result.get(0).revision());
        assertEquals("Newer", result.get(0).name());
        assertEquals("100", result.get(1).revision());
        assertEquals("Older", result.get(1).name());
    }

    // ---- Helper methods ----

    private ModelObject createMockModelObject(String id) {
        ModelObject modelObject = mock(ModelObject.class);
        var entity = mock(UniqueObject.class, withSettings()
                .extraInterfaces(IWorkItem.class, IWorkflowObject.class)
                .defaultAnswer(RETURNS_DEEP_STUBS));
        when(entity.getPrototype().getName()).thenReturn(IWorkItem.PROTO);
        when(entity.getReferencePath()).thenReturn("proj/" + id);
        when(entity.getProjectId()).thenReturn("proj");
        when(entity.getId()).thenReturn(id);
        when(((IWorkflowObject) entity).getType()).thenReturn(mock(ITypeOpt.class));
        when(modelObject.getOldApi()).thenReturn(entity);
        return modelObject;
    }

    private ScanContext createTestScanContext(List<String> repairers) {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class);
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored = mockConstruction(EntityRenderer.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);
            return new ScanContext(polarionService, repairers, new UserConfigs(), new Report(), new Cache());
        }
    }

    // ---- Helper classes ----

    private FieldMetadata mockFieldWithType(String id, FieldType fieldType) {
        FieldMetadata meta = mock(FieldMetadata.class, id);
        lenient().when(meta.getId()).thenReturn(id);
        lenient().when(meta.getType()).thenReturn(fieldType.getType());
        return meta;
    }

    private record TestRepairer(RepairResult resultToReturn) implements IRepairer {

        @Override
            public List<Issue> scan(IUniqueObject entity, ScanContext context) {
                return List.of();
            }

            @Override
            public RepairResult repair(IUniqueObject entity, RepairContext context) {
                return resultToReturn;
            }

            @Override
            public String getDisplayName() {
                return "Test Repairer";
            }

            @Override
            public String getDescription() {
                return "Test Repairer";
            }
        }
}
