package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.fields.model.Option;
import ch.sbb.polarion.extension.generic.test_extensions.CustomExtensionMock;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.data.model.IType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class FieldsInvalidUserValueRepairerTest {

    @CustomExtensionMock
    IProjectService projectService;

    private FieldsInvalidUserValueRepairer repairer;
    private IWorkflowObject entity;
    private XmlRepairPolarionService polarionService;
    private ScanContext contextNoFix;
    private IContextId contextId;

    @BeforeEach
    void setUp() {
        repairer = new FieldsInvalidUserValueRepairer();

        entity = mock(IWorkItem.class, RETURNS_DEEP_STUBS);
        lenient().when(entity.getPrototype().getName()).thenReturn("WorkItem");
        lenient().when(Objects.requireNonNull(entity.getType()).getId()).thenReturn("task");
        lenient().when(entity.getProjectId()).thenReturn("testProject");
        lenient().when(entity.getId()).thenReturn("WI-001");
        lenient().when(entity.getLastRevision()).thenReturn("123");
        contextId = mock(IContextId.class);
        lenient().when(entity.getContextId()).thenReturn(contextId);

        polarionService = mock(XmlRepairPolarionService.class);
        contextNoFix = createScanContext(polarionService);
    }

    // --- isInvalidEnumOption tests for user enums ---

    @Test
    void testIsInvalidEnumOptionUserEnumValidUser() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("@user");
        when(option.getId()).thenReturn("john");

        IUser user = mock(IUser.class);
        when(user.getId()).thenReturn("john");
        IPObjectList<IUser> userList = mockUserList(user);
        when(projectService.getUsers()).thenReturn(userList);

        FieldMetadata meta = FieldMetadata.builder().id("userField").options(Set.of()).build();

        assertFalse(repairer.isInvalidEnumOption(option, meta, contextNoFix));
    }

    @Test
    void testIsInvalidEnumOptionUserEnumInvalidUser() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("@user");
        when(option.getId()).thenReturn("disabled_user");

        IUser user = mock(IUser.class);
        when(user.getId()).thenReturn("john");
        IPObjectList<IUser> userList = mockUserList(user);
        when(projectService.getUsers()).thenReturn(userList);

        FieldMetadata meta = FieldMetadata.builder().id("userField").options(Set.of()).build();

        assertTrue(repairer.isInvalidEnumOption(option, meta, contextNoFix));
    }

    @Test
    void testIsInvalidEnumOptionUserEnumNoUsers() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("@user");

        IPObjectList<IUser> userList = mockUserList();
        when(projectService.getUsers()).thenReturn(userList);

        FieldMetadata meta = FieldMetadata.builder().id("userField").options(Set.of()).build();

        assertTrue(repairer.isInvalidEnumOption(option, meta, contextNoFix));
    }

    @Test
    void testIsInvalidEnumOptionNonUserEnumIsSkipped() {
        // non-user enumerations are handled by FieldsInvalidEnumerationValueRepairer
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("status-enum");
        when(option.getId()).thenReturn("deleted");

        FieldMetadata meta = FieldMetadata.builder()
                .id("status").options(Set.of(new Option("open", "Open"))).build();

        assertFalse(repairer.isInvalidEnumOption(option, meta, contextNoFix));
        verify(projectService, never()).getUsers();
    }

    // --- users list is fetched only once per run ---

    @Test
    void testUsersAreFetchedOnlyOncePerContext() {
        IEnumOption firstOption = mock(IEnumOption.class);
        when(firstOption.getEnumId()).thenReturn("@user");
        when(firstOption.getId()).thenReturn("disabled_user");

        IEnumOption secondOption = mock(IEnumOption.class);
        when(secondOption.getEnumId()).thenReturn("@user");
        when(secondOption.getId()).thenReturn("another_disabled_user");

        IUser user = mock(IUser.class);
        when(user.getId()).thenReturn("john");
        IPObjectList<IUser> userList = mockUserList(user);
        when(projectService.getUsers()).thenReturn(userList);

        FieldMetadata meta = FieldMetadata.builder().id("userField").options(Set.of()).build();

        assertTrue(repairer.isInvalidEnumOption(firstOption, meta, contextNoFix));
        assertTrue(repairer.isInvalidEnumOption(secondOption, meta, contextNoFix));

        verify(projectService, times(1)).getUsers();
    }

    // --- scan() with user enum ---

    @Test
    void testScanUserEnumValidUserNoIssue() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("@user");
        when(option.getId()).thenReturn("john");

        IUser user = mock(IUser.class);
        when(user.getId()).thenReturn("john");
        IPObjectList<IUser> userList = mockUserList(user);
        when(projectService.getUsers()).thenReturn(userList);

        FieldMetadata meta = buildUserFieldField(true);

        setupScanFields(meta);
        when(entity.getValue("userField")).thenReturn(option);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanUserEnumInvalidUserDetectsIssue() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("@user");
        when(option.getId()).thenReturn("disabled_user");

        IUser user = mock(IUser.class);
        when(user.getId()).thenReturn("john");
        IPObjectList<IUser> userList = mockUserList(user);
        when(projectService.getUsers()).thenReturn(userList);

        FieldMetadata meta = buildUserFieldField(true);

        setupScanFields(meta);
        when(entity.getValue("userField")).thenReturn(option);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains("disabled_user"));
    }

    // --- repair() with user enum ---

    @Test
    void testRepairUserEnumInvalidUserWithRemoval() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("@user");
        when(option.getId()).thenReturn("disabled_user");

        IPObjectList<IUser> userList = mockUserList();
        when(projectService.getUsers()).thenReturn(userList);

        FieldMetadata meta = buildUserFieldField(false);

        setupRepairFields(meta);
        when(entity.getValue("userField")).thenReturn(option);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidUserValueRepairer",
                Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("userField");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id 'disabled_user' for the field 'UserField'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        verify(entity).setValue("userField", null);
    }

    @SuppressWarnings("unchecked")
    private IPObjectList<IUser> mockUserList(IUser... users) {
        IPObjectList<IUser> list = mock(IPObjectList.class);
        // answer instead of a fixed return value, so each call gets a fresh (non-consumed) stream
        when(list.stream()).thenAnswer(invocation -> Stream.of(users));
        return list;
    }

    /**
     * The optional 'userField' user field every scan/repair test here works on - only the type varies:
     * for compareTypeClass=true matching, the type's class must match FieldType.ENUM or FieldType.LIST,
     * so a real ENUM type makes the field discoverable and a mocked subclass makes it invisible.
     */
    private FieldMetadata buildUserFieldField(boolean useRealEnumType) {
        IType type = useRealEnumType ? FieldType.ENUM.getType() : mock(FieldType.ENUM.getType().getClass());
        return FieldMetadata.builder()
                .id("userField").label("UserField").type(type).required(false).options(Set.of()).build();
    }

    private void setupScanFields(FieldMetadata... fields) {
        when(polarionService.getAllFields("WorkItem", contextId, "task", true,
                FieldType.LIST.getType(), FieldType.ENUM.getType())).thenReturn(Set.of(fields));
    }

    private void setupRepairFields(FieldMetadata... fields) {
        when(polarionService.getAllFields("WorkItem", contextId, "task", true,
                FieldType.LIST.getType(), FieldType.ENUM.getType())).thenReturn(Set.of(fields));
    }
}
