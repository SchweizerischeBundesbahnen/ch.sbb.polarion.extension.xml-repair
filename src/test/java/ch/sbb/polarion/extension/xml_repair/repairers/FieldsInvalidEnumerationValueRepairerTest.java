package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.fields.model.Option;
import ch.sbb.polarion.extension.generic.test_extensions.CustomExtensionMock;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.tracker.model.IPriorityOpt;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.UnresolvableObjectException;
import com.polarion.platform.persistence.spi.CustomTypedList;
import com.polarion.platform.persistence.spi.PObject;
import com.polarion.platform.persistence.spi.ValueHelper;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.data.model.IType;
import com.polarion.subterra.base.data.object.IDataObject;
import com.polarion.subterra.base.data.model.internal.EnumType;
import com.polarion.subterra.base.data.model.internal.ListType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"unchecked", "unused", "java:S125"}) // suppress false-positive commented-out lines of code
class FieldsInvalidEnumerationValueRepairerTest {

    @CustomExtensionMock
    private IProjectService projectService;

    private FieldsInvalidEnumerationValueRepairer repairer;
    private IWorkflowObject entity;
    private XmlRepairPolarionService polarionService;
    private ScanContext contextNoFix;
    private IContextId contextId;

    @BeforeEach
    void setUp() {
        repairer = new FieldsInvalidEnumerationValueRepairer();

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
        assertEquals("Enumeration fields: Invalid value", repairer.getDisplayName());
        assertNotNull(repairer.getDescription());
        assertTrue(repairer.getDescription().contains("invalid enumeration"));
    }

    @Test
    void testRepairerId() {
        assertEquals("FieldsInvalidEnumerationValueRepairer", repairer.getRepairerId());
    }

    // --- isInvalidEnumOption tests ---

    @Test
    void testIsInvalidEnumOptionValid() {
        IEnumOption option = mockEnumOption("open");

        FieldMetadata meta = FieldMetadata.builder()
                .id("status").options(Set.of(new Option("open", "Open"), new Option("closed", "Closed"))).build();

        assertFalse(repairer.isInvalidEnumOption(option, meta));
    }

    @Test
    void testIsInvalidEnumOptionInvalid() {
        IEnumOption option = mockEnumOption("deleted");

        FieldMetadata meta = FieldMetadata.builder()
                .id("status").options(Set.of(new Option("open", "Open"), new Option("closed", "Closed"))).build();

        assertTrue(repairer.isInvalidEnumOption(option, meta));
    }

    @Test
    void testIsInvalidEnumOptionEmptyOptions() {
        IEnumOption option = mockEnumOption("any");

        FieldMetadata meta = FieldMetadata.builder().id("status").options(Set.of()).build();

        assertTrue(repairer.isInvalidEnumOption(option, meta));
    }

    // --- isInvalidEnumOption tests for work-item-type enums ---

    @Test
    void testIsInvalidEnumOptionWorkItemTypeHeadingIsValid() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("work-item-type");
        when(option.getId()).thenReturn("heading");

        FieldMetadata meta = FieldMetadata.builder().id("type").options(Set.of()).build();

        assertFalse(repairer.isInvalidEnumOption(option, meta));
    }

    @Test
    void testIsInvalidEnumOptionWorkItemTypeNonHeadingFallsThrough() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("work-item-type");
        when(option.getId()).thenReturn("task");

        FieldMetadata meta = FieldMetadata.builder()
                .id("type").options(Set.of(new Option("task", "Task"))).build();

        assertFalse(repairer.isInvalidEnumOption(option, meta));
    }

    @Test
    void testIsInvalidEnumOptionWorkItemTypeNonHeadingInvalid() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("work-item-type");
        when(option.getId()).thenReturn("deleted_type");

        FieldMetadata meta = FieldMetadata.builder()
                .id("type").options(Set.of(new Option("task", "Task"))).build();

        assertTrue(repairer.isInvalidEnumOption(option, meta));
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

        FieldMetadata meta = FieldMetadata.builder().id("assignee").options(Set.of()).build();

        assertFalse(repairer.isInvalidEnumOption(option, meta));
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

        FieldMetadata meta = FieldMetadata.builder().id("assignee").options(Set.of()).build();

        assertTrue(repairer.isInvalidEnumOption(option, meta));
    }

    @Test
    void testIsInvalidEnumOptionUserEnumNoUsers() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("@user");

        IPObjectList<IUser> userList = mockUserList();
        when(projectService.getUsers()).thenReturn(userList);

        FieldMetadata meta = FieldMetadata.builder().id("assignee").options(Set.of()).build();

        assertTrue(repairer.isInvalidEnumOption(option, meta));
    }

    // --- scan() with single IEnumOption values ---

    @Test
    void testScanNoFields() {
        when(polarionService.getAllFields("WorkItem", contextId, "task", true,
                FieldType.LIST.getType(), FieldType.ENUM.getType())).thenReturn(Set.of());

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanValidEnumValueNoIssue() {
        IEnumOption option = mockEnumOption("open");

        FieldMetadata meta = buildEnumField("status", "Status", false, true,
                Set.of(new Option("open", "Open"), new Option("closed", "Closed")));

        setupScanFields(meta);
        when(entity.getValue("status")).thenReturn(option);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanInvalidEnumValueDetectsIssue() {
        IEnumOption option = mockEnumOption("deleted");

        FieldMetadata meta = buildEnumField("status", "Status", false, true,
                Set.of(new Option("open", "Open")));

        setupScanFields(meta);
        when(entity.getValue("status")).thenReturn(option);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertEquals(1, issues.size());
        Issue issue = issues.getFirst();
        assertTrue(issue.getDescription().contains("deleted"));
        verify(entity, never()).setValue(any(), any());
    }

    @Test
    void testRepairInvalidEnumValueNotRequiredWithRemoval() {
        IEnumOption option = mockEnumOption("deleted");

        FieldMetadata meta = buildEnumField("status", "Status", false, false,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("status")).thenReturn(option);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("status");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id 'deleted' for the field 'Status'");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        verify(entity).setValue("status", null);
    }

    @Test
    void testRepairInvalidEnumValueRequiredWithRemoval() {
        IEnumOption option = mockEnumOption("deleted");

        FieldMetadata meta = buildEnumField("status", "Status", true, false,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("status")).thenReturn(option);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("status");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id 'deleted' for the field 'Status'");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        // required field: setValue should NOT be called, warning added instead
        verify(entity, never()).setValue(eq("status"), any());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Status"));
    }

    @Test
    void testScanSkipsPriorityEnum() {
        // IPriorityOpt is a special subtype that should be skipped
        IPriorityOpt priorityOption = mock(IPriorityOpt.class);

        FieldMetadata meta = buildEnumField("priority", "Priority", false, true,
                Set.of(new Option("high", "High")));

        setupScanFields(meta);
        when(entity.getValue("priority")).thenReturn(priorityOption);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanValidEnumValueNoSetValue() {
        // Valid value in no-fix mode: no issue, no setValue
        IEnumOption option = mockEnumOption("open");

        FieldMetadata meta = buildEnumField("status", "Status", false, false,
                Set.of(new Option("open", "Open")));

        setupScanFields(meta);
        when(entity.getValue("status")).thenReturn(option);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
        verify(entity, never()).setValue(any(), any());
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

        FieldMetadata meta = buildEnumField("assignee", "Assignee", false, true, Set.of());

        setupScanFields(meta);
        when(entity.getValue("assignee")).thenReturn(option);

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

        FieldMetadata meta = buildEnumField("assignee", "Assignee", false, true, Set.of());

        setupScanFields(meta);
        when(entity.getValue("assignee")).thenReturn(option);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains("disabled_user"));
    }

    @Test
    void testRepairUserEnumInvalidUserWithRemoval() {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getEnumId()).thenReturn("@user");
        when(option.getId()).thenReturn("disabled_user");

        IPObjectList<IUser> userList = mockUserList();
        when(projectService.getUsers()).thenReturn(userList);

        FieldMetadata meta = buildEnumField("assignee", "Assignee", false, false, Set.of());

        setupRepairFields(meta);
        when(entity.getValue("assignee")).thenReturn(option);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("assignee");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id 'disabled_user' for the field 'Assignee'");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        verify(entity).setValue("assignee", null);
    }

    // --- scan() with CustomTypedList (multi-value enum) ---

    @Test
    void testScanListWithInvalidOptions() {
        IEnumOption validOption = mockEnumOption("open");
        IEnumOption invalidOption = mockEnumOption("deleted");

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(validOption, invalidOption));

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", false, listType,
                Set.of(new Option("open", "Open")));

        setupScanFields(meta);
        when(entity.getValue("categories")).thenReturn(list);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertEquals(1, issues.size());
        assertEquals("Invalid enumeration id(s) 'deleted' for the field 'Categories'.", issues.getFirst().getDescription());
        verify(entity, never()).setValue(any(), any());
    }

    @Test
    void testScanListWithMultipleInvalidOptionsCreatesOneIssue() {
        IEnumOption invalidOption1 = mockEnumOption("deleted");
        IEnumOption invalidOption2 = mockEnumOption("obsolete");

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(invalidOption1, invalidOption2));

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", false, listType, Set.of());

        setupScanFields(meta);
        when(entity.getValue("categories")).thenReturn(list);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertEquals(1, issues.size());
        assertEquals("Invalid enumeration id(s) 'deleted', 'obsolete' for the field 'Categories'.", issues.getFirst().getDescription());
    }

    @Test
    void testRepairListWithInvalidOptionsNotRequired() {
        IEnumOption validOption = mockEnumOption("open");
        IEnumOption invalidOption = mockEnumOption("deleted");

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(validOption, invalidOption));

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", false, listType,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("categories")).thenReturn(list);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("categories");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) 'deleted' for the field 'Categories'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        verify(list).removeAll(List.of(invalidOption));
        verify(entity).setValue("categories", list);
    }

    @Test
    void testRepairListWithInvalidOptionsRequiredPartialRemoval() {
        // Required field, but not all values are invalid -> can remove invalid ones
        IEnumOption validOption = mockEnumOption("open");
        IEnumOption invalidOption = mockEnumOption("deleted");

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(validOption, invalidOption));
        when(list.size()).thenReturn(2);

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", true, listType,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("categories")).thenReturn(list);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("categories");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) 'deleted' for the field 'Categories'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        // invalidOptions.size() (1) < list.size() (2), so fix is allowed even though required
        verify(list).removeAll(List.of(invalidOption));
        verify(entity).setValue("categories", list);
    }

    @Test
    void testRepairListAllInvalidRequiredFieldCannotRemove() {
        // Required field where ALL values are invalid -> cannot remove (would empty a required field)
        IEnumOption invalidOption = mockEnumOption("deleted");

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(invalidOption));
        when(list.size()).thenReturn(1);

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", true, listType,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("categories")).thenReturn(list);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("categories");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) 'deleted' for the field 'Categories'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        // required and invalidOptions.size() == list.size(), so no removal - warning added instead
        verify(list, never()).removeAll(any());
        verify(entity, never()).setValue(eq("categories"), any());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Categories"));
    }

    @Test
    void testScanListSkipsPriorityEnumeration() {
        CustomTypedList list = mock(CustomTypedList.class);

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn(IWorkItem.ENUM_ID_PRIORITY);

        FieldMetadata meta = buildListField("priorities", "Priorities", false, listType,
                Set.of(new Option("high", "High")));

        setupScanFields(meta);
        when(entity.getValue("priorities")).thenReturn(list);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanListNoInvalidOptions() {
        IEnumOption validOption = mockEnumOption("open");

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(validOption));

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", false, listType,
                Set.of(new Option("open", "Open")));

        setupScanFields(meta);
        when(entity.getValue("categories")).thenReturn(list);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanListNoInvalidOptionsNoRemoval() {
        // All valid options: no issue, no removeAll/setValue
        IEnumOption validOption = mockEnumOption("open");

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(validOption));

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", false, listType,
                Set.of(new Option("open", "Open")));

        setupScanFields(meta);
        when(entity.getValue("categories")).thenReturn(list);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
        verify(list, never()).removeAll(any());
        verify(entity, never()).setValue(eq("categories"), any());
    }

    @Test
    void testScanListItemTypeNotEnumType() {
        // ListType but itemType is not EnumType -> should not enter the list branch
        CustomTypedList list = mock(CustomTypedList.class);

        ListType listType = mock(ListType.class);
        IType nonEnumType = mock(IType.class);
        when(listType.getItemType()).thenReturn(nonEnumType);

        FieldMetadata meta = buildListField("tags", "Tags", false, listType,
                Set.of(new Option("tag1", "Tag 1")));

        setupScanFields(meta);
        when(entity.getValue("tags")).thenReturn(list);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanFieldValueNotEnumOrList() {
        // Value is neither IEnumOption nor CustomTypedList -> skip
        FieldMetadata meta = buildEnumField("status", "Status", false, true,
                Set.of(new Option("open", "Open")));

        setupScanFields(meta);
        when(entity.getValue("status")).thenReturn("plainString");

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanMultipleFieldsMixed() {
        // Field 1: valid enum
        IEnumOption validOption = mockEnumOption("open");

        FieldMetadata meta1 = buildEnumField("status", "Status", false, true,
                Set.of(new Option("open", "Open")));

        // Field 2: invalid enum
        IEnumOption invalidOption = mockEnumOption("deleted");

        FieldMetadata meta2 = buildEnumField("severity", "Severity", false, true,
                Set.of(new Option("high", "High")));

        when(polarionService.getAllFields("WorkItem", contextId, "task", true,
                FieldType.LIST.getType(), FieldType.ENUM.getType())).thenReturn(Set.of(meta1, meta2));

        when(entity.getValue("status")).thenReturn(validOption);
        when(entity.getValue("severity")).thenReturn(invalidOption);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains("deleted"));
    }

    // --- scan() with UnresolvableObjectException on getValue() (listType=null path) ---

    @Test
    void testScanGetValueThrowsUnresolvable() {
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);
        when(dataObject.getCustomValue("status")).thenReturn("badValue");

        FieldMetadata meta = buildEnumField("status", "Status", false, true,
                Set.of(new Option("open", "Open")));

        when(pEntity.getValue("status")).thenThrow(new UnresolvableObjectException("test"));
        setupFieldsForEntity(meta);

        List<Issue> issues = repairer.scan((IWorkflowObject) pEntity, contextNoFix);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains("badValue"));
    }

    @Test
    void testRepairGetValueThrowsUnresolvableSingleNotRequired() {
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);
        when(dataObject.getCustomValue("status")).thenReturn("badValue");

        FieldMetadata meta = buildEnumField("status", "Status", false, false,
                Set.of(new Option("open", "Open")));

        when(pEntity.getValue("status")).thenThrow(new UnresolvableObjectException("test"));
        setupFieldsForEntity(meta);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("status");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) [badValue] for the field 'Status'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair((IWorkflowObject) pEntity, repairContext);

        assertTrue(result.isSuccess());
        verify(pEntity).setValue("status", null);
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void testRepairGetValueThrowsUnresolvableSingleRequired() {
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);
        when(dataObject.getCustomValue("status")).thenReturn("badValue");

        FieldMetadata meta = buildEnumField("status", "Status", true, false,
                Set.of(new Option("open", "Open")));

        when(pEntity.getValue("status")).thenThrow(new UnresolvableObjectException("test"));
        setupFieldsForEntity(meta);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("status");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) [badValue] for the field 'Status'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair((IWorkflowObject) pEntity, repairContext);

        verify(pEntity, never()).setValue(eq("status"), any());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Status"));
    }

    // --- repair() with UnresolvableObjectException on list.stream() (listType provided) ---

    @Test
    void testRepairListStreamThrowsUnresolvableSomeBadItems() {
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        Object goodItem = "goodItem";
        Object badItem = "badItem";
        List<Object> customList = new ArrayList<>(List.of(goodItem, badItem));
        when(dataObject.getCustomValue("categories")).thenReturn(customList);

        FieldMetadata meta = FieldMetadata.builder()
                .id("categories").label("Categories").type(listType).required(false).multi(true).options(Set.of(new Option("open", "Open"))).build();

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenThrow(new UnresolvableObjectException("test"));
        when(pEntity.getValue("categories")).thenReturn(list);
        setupFieldsForEntity(meta);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IEnumOption wrappedGood = mock(IEnumOption.class);
        try (MockedStatic<ValueHelper> valueHelperMock = mockStatic(ValueHelper.class)) {
            valueHelperMock.when(() -> ValueHelper.wrapCustomField(pEntity, null, enumType, goodItem)).thenReturn(wrappedGood);
            valueHelperMock.when(() -> ValueHelper.wrapCustomField(pEntity, null, enumType, badItem)).thenThrow(new UnresolvableObjectException("bad"));

            IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
            when(metaInfo.serialize()).thenReturn("serialized");
            when(metaInfo.getString("fieldId")).thenReturn("categories");
            when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) [badItem] for the field 'Categories'.");
            RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

            RepairResult result = repairer.repair((IWorkflowObject) pEntity, repairContext);

            assertTrue(result.isSuccess());
            assertTrue(result.getWarnings().isEmpty());
            verify(pEntity).setValue(eq("categories"), any());
        }
    }

    @Test
    void testRepairListStreamThrowsUnresolvableAllBadRequired() {
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        Object badItem1 = "badItem1";
        Object badItem2 = "badItem2";
        List<Object> customList = new ArrayList<>(List.of(badItem1, badItem2));
        when(dataObject.getCustomValue("categories")).thenReturn(customList);

        FieldMetadata meta = FieldMetadata.builder()
                .id("categories").label("Categories").type(listType).required(true).multi(true).options(Set.of(new Option("open", "Open"))).build();

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenThrow(new UnresolvableObjectException("test"));
        when(pEntity.getValue("categories")).thenReturn(list);
        setupFieldsForEntity(meta);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        try (MockedStatic<ValueHelper> valueHelperMock = mockStatic(ValueHelper.class)) {
            valueHelperMock.when(() -> ValueHelper.wrapCustomField(eq(pEntity), isNull(), eq(enumType), any())).thenThrow(new UnresolvableObjectException("bad"));

            IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
            when(metaInfo.serialize()).thenReturn("serialized");
            when(metaInfo.getString("fieldId")).thenReturn("categories");
            when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) [badItem1, badItem2] for the field 'Categories'.");
            RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

            RepairResult result = repairer.repair((IWorkflowObject) pEntity, repairContext);

            assertEquals(1, result.getWarnings().size());
            assertTrue(result.getWarnings().iterator().next().contains("Categories"));
            verify(pEntity, never()).setValue(eq("categories"), any());
        }
    }

    @Test
    void testScanListStreamThrowsUnresolvable() {
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        Object badItem = "badItem";
        List<Object> customList = new ArrayList<>(List.of(badItem));
        when(dataObject.getCustomValue("categories")).thenReturn(customList);

        FieldMetadata meta = FieldMetadata.builder()
                .id("categories").label("Categories").type(listType).required(false).multi(true).options(Set.of(new Option("open", "Open"))).build();

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenThrow(new UnresolvableObjectException("test"));
        when(pEntity.getValue("categories")).thenReturn(list);
        setupFieldsForEntity(meta);

        try (MockedStatic<ValueHelper> valueHelperMock = mockStatic(ValueHelper.class)) {
            valueHelperMock.when(() -> ValueHelper.wrapCustomField(eq(pEntity), isNull(), eq(enumType), any())).thenThrow(new UnresolvableObjectException("bad"));

            List<Issue> issues = repairer.scan((IWorkflowObject) pEntity, contextNoFix);

            assertEquals(1, issues.size());
            verify(pEntity, never()).setValue(eq("categories"), any());
        }
    }

    @Test
    void testRepairGetValueThrowsUnresolvableMultiNotListCustomValue() {
        // meta.isMulti() is true but customValue is not a List and listType is null
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);
        when(dataObject.getCustomValue("tags")).thenReturn("singleBadValue");

        FieldMetadata meta = FieldMetadata.builder()
                .id("tags").label("Tags").type(FieldType.ENUM.getType()).required(false).multi(true).options(Set.of(new Option("open", "Open"))).build();

        when(pEntity.getValue("tags")).thenThrow(new UnresolvableObjectException("test"));
        setupFieldsForEntity(meta);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("tags");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) [singleBadValue] for the field 'Tags'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair((IWorkflowObject) pEntity, repairContext);

        assertTrue(result.isSuccess());
        verify(pEntity).setValue("tags", null);
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void testRepairGetValueThrowsUnresolvableMultiNotListRequired() {
        // meta.isMulti() is true, required, customValue is not a List, listType is null
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);
        when(dataObject.getCustomValue("tags")).thenReturn("singleBadValue");

        FieldMetadata meta = FieldMetadata.builder()
                .id("tags").label("Tags").type(FieldType.ENUM.getType()).required(true).multi(true).options(Set.of(new Option("open", "Open"))).build();

        when(pEntity.getValue("tags")).thenThrow(new UnresolvableObjectException("test"));
        setupFieldsForEntity(meta);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("tags");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) [singleBadValue] for the field 'Tags'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair((IWorkflowObject) pEntity, repairContext);

        verify(pEntity, never()).setValue(eq("tags"), any());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Tags"));
    }

    @Test
    void testRepairListStreamThrowsUnresolvableAllBadNotRequired() {
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        Object badItem = "badItem";
        List<Object> customList = new ArrayList<>(List.of(badItem));
        when(dataObject.getCustomValue("categories")).thenReturn(customList);

        FieldMetadata meta = FieldMetadata.builder()
                .id("categories").label("Categories").type(listType).required(false).multi(true).options(Set.of(new Option("open", "Open"))).build();

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenThrow(new UnresolvableObjectException("test"));
        when(pEntity.getValue("categories")).thenReturn(list);
        setupFieldsForEntity(meta);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        try (MockedStatic<ValueHelper> valueHelperMock = mockStatic(ValueHelper.class)) {
            valueHelperMock.when(() -> ValueHelper.wrapCustomField(eq(pEntity), isNull(), eq(enumType), any())).thenThrow(new UnresolvableObjectException("bad"));

            IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
            when(metaInfo.serialize()).thenReturn("serialized");
            when(metaInfo.getString("fieldId")).thenReturn("categories");
            when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) [badItem] for the field 'Categories'.");
            RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

            RepairResult result = repairer.repair((IWorkflowObject) pEntity, repairContext);

            assertTrue(result.isSuccess());
            assertTrue(result.getWarnings().isEmpty());
            // setValue should be called with a new (empty) list
            verify(pEntity).setValue(eq("categories"), any());
        }
    }

    // --- Tests for warnRepairTurnedOff (REMOVE_INVALID_ENUM_VALUES config disabled) ---

    @Test
    void testRepairSingleEnumWarnRepairTurnedOff() {
        IEnumOption option = mockEnumOption("deleted");

        FieldMetadata meta = buildEnumField("status", "Status", false, false,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("status")).thenReturn(option);

        // empty UserConfigs -> REMOVE_INVALID_ENUM_VALUES defaults to false
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("status");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id 'deleted' for the field 'Status'");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Enable option 'Remove invalid enumeration values'"));
        verify(entity, never()).setValue(any(), any());
    }

    @Test
    void testRepairListWarnRepairTurnedOff() {
        IEnumOption invalidOption = mockEnumOption("deleted");

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(invalidOption));

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", false, listType,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("categories")).thenReturn(list);

        // empty UserConfigs -> REMOVE_INVALID_ENUM_VALUES defaults to false
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("categories");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) 'deleted' for the field 'Categories'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Enable option 'Remove invalid enumeration values'"));
        verify(list, never()).removeAll(any());
        verify(entity, never()).setValue(any(), any());
    }

    @Test
    void testRepairUnresolvableWarnRepairTurnedOff() {
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);
        when(dataObject.getCustomValue("status")).thenReturn("badValue");

        FieldMetadata meta = buildEnumField("status", "Status", false, false,
                Set.of(new Option("open", "Open")));

        when(pEntity.getValue("status")).thenThrow(new UnresolvableObjectException("test"));
        setupFieldsForEntity(meta);

        // empty UserConfigs -> REMOVE_INVALID_ENUM_VALUES defaults to false
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("status");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) [badValue] for the field 'Status'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair((IWorkflowObject) pEntity, repairContext);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Enable option 'Remove invalid enumeration values'"));
        verify(pEntity, never()).setValue(eq("status"), any());
    }

    // --- repair() field not found ---

    @Test
    void testRepairFieldNotFoundThrowsException() {
        when(polarionService.getAllFields("WorkItem", contextId, "task", true,
                FieldType.LIST.getType(), FieldType.ENUM.getType())).thenReturn(Set.of());

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("nonExistentField");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        assertThrows(IllegalArgumentException.class, () -> repairer.repair(entity, repairContext));
    }

    // --- scan() with null value ---

    @Test
    void testScanNullValueNoIssue() {
        FieldMetadata meta = buildEnumField("status", "Status", false, true,
                Set.of(new Option("open", "Open")));

        setupScanFields(meta);
        when(entity.getValue("status")).thenReturn(null);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertTrue(issues.isEmpty());
    }

    // --- scan() with UnresolvableObjectException on getValue() but empty badItems ---

    @Test
    void testScanGetValueThrowsUnresolvableNullCustomValue() {
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);
        when(dataObject.getCustomValue("status")).thenReturn(null);

        FieldMetadata meta = buildEnumField("status", "Status", false, true,
                Set.of(new Option("open", "Open")));

        when(pEntity.getValue("status")).thenThrow(new UnresolvableObjectException("test"));
        setupFieldsForEntity(meta);

        List<Issue> issues = repairer.scan((IWorkflowObject) pEntity, contextNoFix);

        // null customValue becomes a single badItem (null), so 1 issue
        assertEquals(1, issues.size());
    }

    // --- warnRepairTurnedOff direct test ---

    @Test
    void testWarnRepairTurnedOffAddsWarning() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairResult result = new RepairResult(metaInfo, false);

        repairer.warnRepairTurnedOff(result, false);

        assertEquals(1, result.getWarnings().size());
        String warning = result.getWarnings().iterator().next();
        assertTrue(warning.contains("Enable option 'Remove invalid enumeration values' to remove invalid value"));
        assertTrue(warning.contains("Cannot repair value automatically"));
    }

    @Test
    void testWarnRepairTurnedOffMultipleEntries() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairResult result = new RepairResult(metaInfo, false);

        repairer.warnRepairTurnedOff(result, true);

        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Cannot repair all values automatically"));
    }

    // --- findSimilarOption: single-value enum repair via similar option ---

    @Test
    void testRepairSingleEnumSimilarFoundByExactName() {
        // bad option id "Open" matches valid option's name "Open" (attempt 1)
        IEnumOption option = mockEnumOption("Open");

        FieldMetadata meta = buildEnumField("status", "Status", false, false,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("status")).thenReturn(option);
        IEnumOption expectedReplacement = stubWrapOption(entity, "open");

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("status");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id 'Open' for the field 'Status'");
        // config is OFF, but we still expect a fix because similar option is found
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(entity).setValue(eq("status"), captor.capture());
        assertSame(expectedReplacement, captor.getValue());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void testRepairSingleEnumSimilarFoundByCaseInsensitiveId() {
        // bad option id "OPEN" matches valid option's key "open" case-insensitively (attempt 2)
        IEnumOption option = mockEnumOption("OPEN");

        FieldMetadata meta = buildEnumField("status", "Status", false, false,
                Set.of(new Option("open", "Different")));

        setupRepairFields(meta);
        when(entity.getValue("status")).thenReturn(option);
        IEnumOption expectedReplacement = stubWrapOption(entity, "open");

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("status");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id 'OPEN' for the field 'Status'");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(entity).setValue(eq("status"), captor.capture());
        assertSame(expectedReplacement, captor.getValue());
    }

    @Test
    void testRepairSingleEnumSimilarFoundByCaseInsensitiveName() {
        // bad option id "OPEN" matches valid option's name "Open" case-insensitively (attempt 3)
        IEnumOption option = mockEnumOption("OPEN");

        FieldMetadata meta = buildEnumField("status", "Status", false, false,
                Set.of(new Option("st1", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("status")).thenReturn(option);
        IEnumOption expectedReplacement = stubWrapOption(entity, "st1");

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("status");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id 'OPEN' for the field 'Status'");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(entity).setValue(eq("status"), captor.capture());
        assertSame(expectedReplacement, captor.getValue());
    }

    @Test
    void testRepairSingleEnumSimilarFoundEvenForRequiredField() {
        // similar found path bypasses the "removal disabled" / required-field guards
        IEnumOption option = mockEnumOption("Open");

        FieldMetadata meta = buildEnumField("status", "Status", true, false,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("status")).thenReturn(option);
        IEnumOption expectedReplacement = stubWrapOption(entity, "open");

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("status");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id 'Open' for the field 'Status'");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        verify(entity).setValue("status", expectedReplacement);
        assertTrue(result.getWarnings().isEmpty());
    }

    // --- list repair via similar option(s) ---

    @Test
    void testRepairListAllInvalidHaveSimilarReplacementsFixesAll() {
        // similarOptions.size() == invalidOptions.size() branch
        IEnumOption invalidOption = mockEnumOption("Open"); // matches valid name "Open"

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(invalidOption));

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", false, listType,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("categories")).thenReturn(list);
        IEnumOption expectedReplacement = stubWrapOption(entity, "open");

        // config OFF: when similar found for every invalid, repair still proceeds
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("categories");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) 'Open' for the field 'Categories'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        verify(list).removeAll(List.of(invalidOption));
        ArgumentCaptor<Object> addCaptor = ArgumentCaptor.forClass(Object.class);
        verify(list).addAll((java.util.Collection<?>) addCaptor.capture());
        java.util.Collection<?> added = (java.util.Collection<?>) addCaptor.getValue();
        assertEquals(1, added.size());
        assertSame(expectedReplacement, added.iterator().next());
        verify(entity).setValue("categories", list);
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void testRepairListMixedSimilarsAndUnreplaceable() {
        // 2 invalids, only 1 has a similar replacement; config ON, not required -> branch 4
        IEnumOption replaceable = mockEnumOption("Open");      // similar: key="open"
        IEnumOption unreplaceable = mockEnumOption("garbage"); // no similar

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(replaceable, unreplaceable));
        when(list.size()).thenReturn(2);

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", false, listType,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("categories")).thenReturn(list);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("categories");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) 'Open', 'garbage' for the field 'Categories'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        verify(list).removeAll(List.of(replaceable, unreplaceable));
        ArgumentCaptor<Object> addCaptor = ArgumentCaptor.forClass(Object.class);
        verify(list).addAll((java.util.Collection<?>) addCaptor.capture());
        java.util.Collection<?> added = (java.util.Collection<?>) addCaptor.getValue();
        assertEquals(1, added.size()); // only the replaceable one is re-added
        verify(entity).setValue("categories", list);
    }

    @Test
    void testRepairListMultipleInvalidsConfigOffWarnsAllValues() {
        // multipleEntries=true variant of warnRepairTurnedOff
        IEnumOption invalid1 = mockEnumOption("deleted");
        IEnumOption invalid2 = mockEnumOption("obsolete");

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(invalid1, invalid2));

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", false, listType,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("categories")).thenReturn(list);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("categories");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) 'deleted', 'obsolete' for the field 'Categories'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Cannot repair all values automatically"));
        verify(list, never()).removeAll(any());
        verify(entity, never()).setValue(any(), any());
    }

    @Test
    void testRepairListRequiredAllInvalidNoSimilarsCannotRemove() {
        // explicitly covers: required && size match && similarOptions empty => warning, no fix
        IEnumOption invalid = mockEnumOption("garbage"); // no similar in options

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(invalid));
        when(list.size()).thenReturn(1);

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", true, listType,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("categories")).thenReturn(list);

        UserConfigs removalEnabledConfigs = new UserConfigs();
        removalEnabledConfigs.put("FieldsInvalidEnumerationValueRepairer",
                java.util.Map.of(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, true));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("categories");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) 'garbage' for the field 'Categories'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, removalEnabledConfigs, new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().iterator().next().contains("Can't remove all values of required enumeration field 'Categories'"));
        verify(list, never()).removeAll(any());
        verify(entity, never()).setValue(any(), any());
    }

    @Test
    void testRepairListRequiredAllInvalidWithSimilarsFixesAll() {
        // covers: required && size match && similarOptions NOT empty => fix proceeds (else branch).
        // Complements testRepairListRequiredAllInvalidNoSimilarsCannotRemove for full condition
        // coverage of the `meta.isRequired() && sizes==list.size() && similarOptions.isEmpty()` guard.
        IEnumOption invalid = mockEnumOption("Open"); // matches valid name "Open"

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenReturn(Stream.of(invalid));
        when(list.size()).thenReturn(1);

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        FieldMetadata meta = buildListField("categories", "Categories", true, listType,
                Set.of(new Option("open", "Open")));

        setupRepairFields(meta);
        when(entity.getValue("categories")).thenReturn(list);
        IEnumOption expectedReplacement = stubWrapOption(entity, "open");

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        when(metaInfo.getString("fieldId")).thenReturn("categories");
        when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) 'Open' for the field 'Categories'.");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        verify(list).removeAll(List.of(invalid));
        ArgumentCaptor<Object> addCaptor = ArgumentCaptor.forClass(Object.class);
        verify(list).addAll((java.util.Collection<?>) addCaptor.capture());
        java.util.Collection<?> added = (java.util.Collection<?>) addCaptor.getValue();
        assertEquals(1, added.size());
        assertSame(expectedReplacement, added.iterator().next());
        verify(entity).setValue("categories", list);
        assertTrue(result.getWarnings().isEmpty());
    }

    // --- handleUnresolvableObjectException with multiple bad items + config off ---

    @Test
    void testRepairUnresolvableMultipleBadItemsConfigOffWarnsAllValues() {
        PObject pEntity = createPObjectEntity();
        IDataObject dataObject = mock(IDataObject.class);
        when(pEntity.getData()).thenReturn(dataObject);

        ListType listType = mock(ListType.class);
        EnumType enumType = mock(EnumType.class);
        when(listType.getItemType()).thenReturn(enumType);
        when(enumType.getEnumerationId()).thenReturn("status-enum");

        Object bad1 = "bad1";
        Object bad2 = "bad2";
        when(dataObject.getCustomValue("categories")).thenReturn(new ArrayList<>(List.of(bad1, bad2)));

        FieldMetadata meta = FieldMetadata.builder()
                .id("categories").label("Categories").type(listType).required(false).multi(true)
                .options(Set.of(new Option("open", "Open"))).build();

        CustomTypedList list = mock(CustomTypedList.class);
        when(list.stream()).thenThrow(new UnresolvableObjectException("test"));
        when(pEntity.getValue("categories")).thenReturn(list);
        setupFieldsForEntity(meta);

        try (MockedStatic<ValueHelper> valueHelperMock = mockStatic(ValueHelper.class)) {
            valueHelperMock.when(() -> ValueHelper.wrapCustomField(eq(pEntity), isNull(), eq(enumType), any()))
                    .thenThrow(new UnresolvableObjectException("bad"));

            IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
            when(metaInfo.serialize()).thenReturn("serialized");
            when(metaInfo.getString("fieldId")).thenReturn("categories");
            when(metaInfo.getString("issueDescription")).thenReturn("Invalid enumeration id(s) [bad1, bad2] for the field 'Categories'.");
            // config OFF -> warning with multipleEntries=true
            RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

            RepairResult result = repairer.repair((IWorkflowObject) pEntity, repairContext);

            assertFalse(result.isSuccess());
            assertEquals(1, result.getWarnings().size());
            assertTrue(result.getWarnings().iterator().next().contains("Cannot repair all values automatically"));
            verify(pEntity, never()).setValue(eq("categories"), any());
        }
    }

    // --- scan() issue description format check (uses new "id" wording) ---

    @Test
    void testScanIssueDescriptionUsesEnumerationIdWording() {
        IEnumOption option = mockEnumOption("deleted");
        FieldMetadata meta = buildEnumField("status", "Status", false, true,
                Set.of(new Option("open", "Open")));

        setupScanFields(meta);
        when(entity.getValue("status")).thenReturn(option);

        List<Issue> issues = repairer.scan(entity, contextNoFix);

        assertEquals(1, issues.size());
        assertEquals("Invalid enumeration id 'deleted' for the field 'Status'", issues.getFirst().getDescription());
    }

    // --- Helper methods ---

    private IEnumOption mockEnumOption(String id) {
        IEnumOption option = mock(IEnumOption.class);
        when(option.getId()).thenReturn(id);
        when(option.getEnumId()).thenReturn("generic-enum");
        return option;
    }

    // Stubs the IEnumeration#wrapOption(key) chain so findSimilarOption returns a known mock.
    // Resolution is by enum id (not field key) so this works for custom fields too.
    // Enum id matches mockEnumOption()'s "generic-enum".
    private IEnumOption stubWrapOption(IWorkflowObject onEntity, String key) {
        IEnumOption wrapped = mock(IEnumOption.class);
        when(onEntity.getDataSvc()
                .getEnumerationForEnumId(new EnumType("generic-enum"), contextId)
                .wrapOption(key)).thenReturn(wrapped);
        return wrapped;
    }

    private FieldMetadata buildEnumField(String id, String label, boolean required, boolean useRealEnumType, Set<Option> options) {
        IType type = useRealEnumType ? FieldType.ENUM.getType() : mock(IType.class);
        // For compareTypeClass=true matching, the type's class must match FieldType.ENUM or FieldType.LIST
        if (!useRealEnumType) {
            // Make it match by class with ENUM type
            type = mock(FieldType.ENUM.getType().getClass());
        }
        return FieldMetadata.builder()
                .id(id).label(label).type(type).required(required).options(options).build();
    }

    private FieldMetadata buildListField(String id, String label, boolean required, ListType listType, Set<Option> options) {
        return FieldMetadata.builder()
                .id(id).label(label).type(listType).required(required).options(options).build();
    }

    @SuppressWarnings("unchecked")
    private IPObjectList<IUser> mockUserList(IUser... users) {
        IPObjectList<IUser> list = mock(IPObjectList.class);
        when(list.stream()).thenReturn(Stream.of(users));
        return list;
    }

    private void setupScanFields(FieldMetadata... fields) {
        when(polarionService.getAllFields("WorkItem", contextId, "task", true,
                FieldType.LIST.getType(), FieldType.ENUM.getType())).thenReturn(Set.of(fields));
    }

    private void setupRepairFields(FieldMetadata... fields) {
        when(polarionService.getAllFields("WorkItem", contextId, "task", true,
                FieldType.LIST.getType(), FieldType.ENUM.getType())).thenReturn(Set.of(fields));
    }

    private PObject createPObjectEntity() {
        PObject pEntity = mock(PObject.class, withSettings()
                .extraInterfaces(IWorkItem.class)
                .defaultAnswer(RETURNS_DEEP_STUBS));
        IWorkflowObject wfEntity = (IWorkflowObject) pEntity;
        lenient().when(wfEntity.getPrototype().getName()).thenReturn("WorkItem");
        lenient().when(Objects.requireNonNull(wfEntity.getType()).getId()).thenReturn("task");
        lenient().when(wfEntity.getProjectId()).thenReturn("testProject");
        lenient().when(wfEntity.getId()).thenReturn("WI-001");
        lenient().when(wfEntity.getLastRevision()).thenReturn("123");
        lenient().when(wfEntity.getContextId()).thenReturn(contextId);
        return pEntity;
    }

    private void setupFieldsForEntity(FieldMetadata... fields) {
        lenient().when(polarionService.getAllFields("WorkItem", contextId, "task", true,
                FieldType.LIST.getType(), FieldType.ENUM.getType())).thenReturn(Set.of(fields));
    }

    @Test
    void testGetConfigs() {
        List<RepairerConfigMeta> configs = repairer.getConfigs();

        assertEquals(1, configs.size());
        RepairerConfigMeta config = configs.getFirst();
        assertEquals(FieldsInvalidEnumerationValueRepairer.REMOVE_INVALID_ENUM_VALUES, config.getKey());
        assertEquals("Remove invalid enumeration values", config.getDescription());
        assertEquals("Clear/remove value if it is not defined in the specified enumeration", config.getHint());
        assertEquals(RepairerConfigType.BOOLEAN, config.getType());
        assertEquals(false, config.getDefaultValue());
    }
}
