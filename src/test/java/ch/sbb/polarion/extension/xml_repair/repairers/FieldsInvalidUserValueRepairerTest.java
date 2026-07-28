package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.fields.model.Option;
import ch.sbb.polarion.extension.generic.test_extensions.CustomExtensionMock;
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
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.persistence.spi.CustomTypedList;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.data.model.internal.EnumType;
import com.polarion.subterra.base.data.model.internal.ListType;
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

    @Test
    void testDisplayNameAndDescription() {
        assertEquals("User fields: Invalid value", repairer.getDisplayName());
        assertNotNull(repairer.getDescription());
        assertTrue(repairer.getDescription().contains("user fields"));
    }

    @Test
    void testRepairerId() {
        assertEquals("FieldsInvalidUserValueRepairer", repairer.getRepairerId());
    }

    @Test
    void testGetConfigs() {
        List<RepairerConfigMeta> configs = repairer.getConfigs();

        assertEquals(1, configs.size());
        RepairerConfigMeta config = configs.getFirst();
        assertEquals(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_VALUES, config.getKey());
        assertEquals("Remove invalid values", config.getDescription());
        assertEquals("Clear/remove value if the user isn't found", config.getHint());
        assertEquals(RepairerConfigType.BOOLEAN, config.getType());
        assertEquals(false, config.getDefaultValue());
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
    void testIsInvalidEnumOptionUserEnumReportsNothingWhenUserListUnavailable() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("@user");

        // The cache may hand back a cached null (see Cache#getOrCompute): with no user list at hand a
        // disabled user cannot be told apart from a valid one, so nothing must be reported - flagging every
        // user value instead would mean a flood of false issues.
        Cache emptyCache = mock(Cache.class);
        doReturn(null).when(emptyCache).getOrCompute(anyString(), any());
        ScanContext contextWithoutUserList = createScanContext(polarionService, emptyCache);

        FieldMetadata meta = FieldMetadata.builder().id("userField").options(Set.of()).build();

        assertFalse(repairer.isInvalidEnumOption(option, meta, contextWithoutUserList));
    }

    @Test
    void testIsInvalidEnumOptionNonUserEnumIsValidatedAgainstFieldOptions() {
        // Non-user enumerations are excluded per field (see shouldFixSpecificEnum), not per option, so on option
        // level a non-user value is validated against the field options here too - fields of other enumerations
        // just never reach this point in this repairer, see testScanNonUserEnumFieldIsSkippedWithoutReadingValue.
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("status-enum");
        when(option.getId()).thenReturn("deleted");

        FieldMetadata meta = FieldMetadata.builder()
                .id("status").options(Set.of(new Option("open", "Open"))).build();

        assertTrue(repairer.isInvalidEnumOption(option, meta, contextNoFix));
        verify(projectService, never()).getUsers();
    }

    // --- shouldFixSpecificEnum: which fields this repairer looks at in the first place ---

    @Test
    void testShouldFixSpecificEnumHandlesOnlyUserEnumeration() {
        // other enumerations are handled by FieldsInvalidEnumerationValueRepairer
        assertTrue(repairer.shouldFixSpecificEnum(FieldsInvalidEnumerationValueRepairer.USER_ENUM_ID));
        assertFalse(repairer.shouldFixSpecificEnum("status-enum"));
        assertFalse(repairer.shouldFixSpecificEnum("work-item-type"));
    }

    @Test
    void testScanNonUserEnumFieldIsSkippedWithoutReadingValue() {
        IEnumOption option = mock(IEnumOption.class);
        lenient().when(option.getEnumId()).thenReturn("status-enum");
        lenient().when(option.getId()).thenReturn("deleted");

        FieldMetadata meta = FieldMetadata.builder().id("status").label("Status")
                .type(new EnumType("status-enum")).required(false).options(Set.of(new Option("open", "Open"))).build();

        setupScanFields(meta);
        lenient().when(entity.getValue("status")).thenReturn(option);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
        verify(entity, never()).getValue("status");
        verify(projectService, never()).getUsers();
    }

    @Test
    void testScanNonEnumFieldIsSkippedWithoutReadingValue() {
        FieldMetadata meta = FieldMetadata.builder().id("title").label("Title")
                .type(FieldType.STRING.getType()).required(false).options(Set.of()).build();

        setupScanFields(meta);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
        verify(entity, never()).getValue("title");
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

        FieldMetadata meta = buildUserField();

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

        FieldMetadata meta = buildUserField();

        setupScanFields(meta);
        when(entity.getValue("userField")).thenReturn(option);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains("disabled_user"));
    }

    @Test
    void testScanMultiUserFieldDetectsInvalidUser() {
        IEnumOption validOption = mock(IEnumOption.class);
        when(validOption.getEnumId()).thenReturn("@user");
        when(validOption.getId()).thenReturn("john");

        IEnumOption invalidOption = mock(IEnumOption.class);
        when(invalidOption.getEnumId()).thenReturn("@user");
        when(invalidOption.getId()).thenReturn("disabled_user");

        IUser user = mock(IUser.class);
        when(user.getId()).thenReturn("john");
        IPObjectList<IUser> userList = mockUserList(user);
        when(projectService.getUsers()).thenReturn(userList);

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(validOption, invalidOption));

        FieldMetadata meta = buildMultiUserField();

        setupScanFields(meta);
        when(entity.getValue("userField")).thenReturn(list);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertEquals(1, issues.size());
        assertEquals("Invalid enumeration id(s) 'disabled_user' for the field 'UserField'.", issues.getFirst().getDescription());
    }

    // --- repair() with user enum ---

    @Test
    void testRepairUserEnumInvalidUserWithRemoval() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("@user");
        when(option.getId()).thenReturn("disabled_user");

        IPObjectList<IUser> userList = mockUserList();
        when(projectService.getUsers()).thenReturn(userList);

        FieldMetadata meta = buildUserField();

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

    @Test
    void testRepairNonUserEnumFieldIsSkippedWithoutReadingValue() {
        IEnumOption option = mock(IEnumOption.class);
        lenient().when(option.getEnumId()).thenReturn("status-enum");
        lenient().when(option.getId()).thenReturn("deleted");

        FieldMetadata meta = FieldMetadata.builder().id("status").label("Status")
                .type(new EnumType("status-enum")).required(false).options(Set.of(new Option("open", "Open"))).build();

        setupRepairFields(meta);
        lenient().when(entity.getValue("status")).thenReturn(option);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidUserValueRepairer",
                Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("status");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id 'deleted' for the field 'Status'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().isEmpty());
        verify(entity, never()).getValue("status");
        verify(entity, never()).setValue(any(), any());
    }

    @SuppressWarnings("unchecked")
    private IPObjectList<IUser> mockUserList(IUser... users) {
        IPObjectList<IUser> list = mock(IPObjectList.class);
        // answer instead of a fixed return value, so each call gets a fresh (non-consumed) stream
        when(list.stream()).thenAnswer(invocation -> Stream.of(users));
        return list;
    }

    /**
     * The optional 'userField' user field every scan/repair test here works on. Its type must carry the
     * {@code @user} enumeration id, that's what makes this repairer consider the field at all.
     */
    private FieldMetadata buildUserField() {
        return FieldMetadata.builder().id("userField").label("UserField")
                .type(new EnumType(FieldsInvalidEnumerationValueRepairer.USER_ENUM_ID)).required(false).options(Set.of()).build();
    }

    /** Multi-value counterpart of {@link #buildUserField()}, e.g. a custom 'list of users' field. */
    private FieldMetadata buildMultiUserField() {
        return FieldMetadata.builder().id("userField").label("UserField")
                .type(new ListType("user-list", new EnumType(FieldsInvalidEnumerationValueRepairer.USER_ENUM_ID)))
                .required(false).multi(true).options(Set.of()).build();
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
