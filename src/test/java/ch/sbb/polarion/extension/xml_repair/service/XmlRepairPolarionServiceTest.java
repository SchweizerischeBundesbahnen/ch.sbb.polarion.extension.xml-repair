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
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.model.ModelObject;
import com.polarion.alm.shared.api.model.PrototypeEnum;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.internal.model.UniqueObject;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollectionElement;
import com.polarion.platform.IPlatformService;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());

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

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());

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
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());

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
        assertEquals(4, polarionService.getRepairerMetas(EntityType.WORKITEM).size());
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

        assertEquals(4, repairers.size());
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
            ScanContext context = new ScanContext(polarionService, List.of("TestRepairer"), new UserConfigs(), new Report());

            doReturn(List.of(new TestRepairer(null))).when(polarionService).getRepairersForEntity(any());

            polarionService.scanEntity(scanEntity, context);

            assertTrue(scanEntity.getIssues().isEmpty()); // TestRepairer returns empty list
        }
    }

    @Test
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
            ScanContext context = new ScanContext(polarionService, List.of("TestRepairer"), new UserConfigs(), new Report());

            // submodule will trigger scanEntity recursively, which will hit the non-collection branch
            doReturn(List.of(new TestRepairer(null))).when(polarionService).getRepairersForEntity(any());
            when(((IWorkflowObject) module).getType()).thenReturn(mock(ITypeOpt.class));

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

        doReturn(List.of()).when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), anyInt(), anyInt());

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
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), anyInt(), anyInt());
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
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), anyInt(), anyInt());
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
            params.setTimeout(1L); // Very short timeout - will be exceeded
            params.setRepairers(List.of("TestRepairer"));

            doReturn(List.of(modelObject1, modelObject2))
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), anyInt(), anyInt());
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
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), anyInt(), anyInt());

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
                    .when(polarionService).queryEntities(anyString(), any(PrototypeEnum.class), isNull(), isNull(), isNull(), anyInt(), anyInt());

            // Make scanEntity throw so items are not considered "valid" (error != null means they're shown)
            doThrow(new RuntimeException("scan error")).when(polarionService).scanEntity(any(ScanEntity.class), any(ScanContext.class));

            ScanResult result = polarionService.scan(params);

            // With limit=1 and hideValid=true, should stop after collecting 1 item with error
            assertEquals(1, result.getItems().size());
            assertTrue(result.getReport().contains("Top items limit reached"));
        }
    }

    // ---- queryEntities tests ----

    @Test
    void testQueryEntitiesNoTransactionThrows() {
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(null);

            assertThrows(IllegalStateException.class, () ->
                    polarionService.queryEntities("proj", PrototypeEnum.WorkItem, null, null, null, null, null));
        }
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
            return new ScanContext(polarionService, repairers, new UserConfigs(), new Report());
        }
    }

    // ---- Helper classes ----

    private Set<FieldMetadata> mockFields(FieldType fieldType, String... ids) {
        return Stream.of(ids).map(id -> mockFieldWithType(id, fieldType))
                .collect(Collectors.toSet());
    }

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
