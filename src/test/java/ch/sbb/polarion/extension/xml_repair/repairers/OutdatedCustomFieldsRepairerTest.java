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
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.platform.persistence.ICustomFieldsService;
import com.polarion.platform.persistence.IDataService;
import com.polarion.platform.persistence.lowlevel.ILowLevelPObject;
import com.polarion.platform.persistence.spi.LowLevelPObjectAccessor;
import com.polarion.platform.persistence.spi.PObject;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.data.object.IDataObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class OutdatedCustomFieldsRepairerTest {

    private static final String OUTDATED = "outdatedField";
    private static final String DEFINED = "definedField";

    private OutdatedCustomFieldsRepairer repairer;
    private XmlRepairPolarionService polarionService;
    private IContextId contextId;
    private PObject pEntity;
    private IDataObject data;
    private ICustomFieldsService customFieldsService;

    @BeforeEach
    void setUp() {
        repairer = new OutdatedCustomFieldsRepairer();
        polarionService = mock(XmlRepairPolarionService.class);
        contextId = mock(IContextId.class);
        pEntity = createPObjectEntity();
        data = mock(IDataObject.class);
        customFieldsService = mock(ICustomFieldsService.class);

        IDataService dataService = mock(IDataService.class);
        lenient().when(dataService.getCustomFieldsService()).thenReturn(customFieldsService);
        lenient().when(pEntity.getDataSvc()).thenReturn(dataService);
        lenient().when(pEntity.getData()).thenReturn(data);
        lenient().when(pEntity.getPrototype().allowsCustomFields()).thenReturn(true);

        stored(DEFINED, OUTDATED);
        incompatible();
        valid(DEFINED);
        lenient().when(data.getCustomValue(anyString())).thenReturn("a value");
    }

    @Test
    void testScanFindsFilledUndefinedAttribute() {
        List<Issue> issues = repairer.scan(entity(), createScanContext(polarionService));

        assertEquals(1, issues.size());
        Issue issue = issues.getFirst();
        assertEquals(OUTDATED, issue.getGroup());
        assertEquals("Attribute 'outdatedField' holds a value but is not defined in the custom fields configuration.", issue.getDescription());
        assertEquals(OUTDATED, issue.getRawMetaInfo().getString("fieldId"));
        assertEquals("OutdatedCustomFieldsRepairer", issue.getRepairer());
    }

    @Test
    void testScanIgnoresDefinedAttributes() {
        valid(DEFINED, OUTDATED);

        assertTrue(repairer.scan(entity(), createScanContext(polarionService)).isEmpty());
    }

    @Test
    void testScanIgnoresUndefinedAttributeWithoutValue() {
        when(data.getCustomValue(OUTDATED)).thenReturn(null);
        when(data.getIncompatibleCustomValue(OUTDATED)).thenReturn(null);

        assertTrue(repairer.scan(entity(), createScanContext(polarionService)).isEmpty());
    }

    @Test
    void testScanFindsUndefinedAttributeStoredAsIncompatible() {
        stored(DEFINED);
        incompatible(OUTDATED);
        when(data.getCustomValue(OUTDATED)).thenReturn(null);
        when(data.getIncompatibleCustomValue(OUTDATED)).thenReturn("a value of the wrong type");

        List<Issue> issues = repairer.scan(entity(), createScanContext(polarionService));

        assertEquals(1, issues.size());
        assertEquals(OUTDATED, issues.getFirst().getGroup());
    }

    @Test
    void testScanSortsAttributesAlphabetically() {
        stored("zeta", "alpha", "middle");
        valid();

        List<Issue> issues = repairer.scan(entity(), createScanContext(polarionService));

        assertEquals(List.of("alpha", "middle", "zeta"), issues.stream().map(Issue::getGroup).toList());
    }

    @Test
    void testScanReturnsNothingWhenPrototypeDisallowsCustomFields() {
        when(pEntity.getPrototype().allowsCustomFields()).thenReturn(false);

        assertTrue(repairer.scan(entity(), createScanContext(polarionService)).isEmpty());
        verify(customFieldsService, never()).getValidCustomFieldIds(any());
    }

    @Test
    void testScanReturnsNothingWhenEntityIsNotAPersistedObject() {
        IWorkflowObject plainEntity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);

        assertTrue(repairer.scan(plainEntity, createScanContext(polarionService)).isEmpty());
    }

    @Test
    void testScanReturnsNothingWhenDataIsUnavailable() {
        when(pEntity.getData()).thenReturn(null);

        assertTrue(repairer.scan(entity(), createScanContext(polarionService)).isEmpty());
    }

    @Test
    void testScanResolvesDefinitionsOncePerTypeThroughTheCache() {
        ScanContext context = createScanContext(polarionService);

        repairer.scan(entity(), context);
        repairer.scan(entity(), context);

        verify(customFieldsService, times(1)).getValidCustomFieldIds(pEntity);
    }

    @Test
    void testRepairRemovesTheStoredAttributeAndSaves() {
        try (MockedStatic<LowLevelPObjectAccessor> accessor = mockLowLevelAccessor()) {
            RepairResult result = repairer.repair((IUniqueObject) pEntity, repairContext(OUTDATED));

            assertTrue(result.isSuccess());
            assertTrue(result.getWarnings().isEmpty());
            verify(data).removeCustomKey(OUTDATED);
            verify(pEntity).save();
            accessor.verify(() -> LowLevelPObjectAccessor.clearCustomSetCaches(pEntity));
        }
    }

    /**
     * The removal alone is silently dropped: DataService.save returns without writing anything for a persisted,
     * resolved object whose low level object does not report itself modified, and editing the persisted data
     * does not flip that flag. Marking the object changed is what makes the save take effect, so it is asserted
     * rather than left to a manual check against a live Polarion.
     */
    @Test
    void testRepairMarksTheObjectChangedSoThatSaveIsNotANoOp() {
        ILowLevelPObject lowLevelObject = mock(ILowLevelPObject.class);
        try (MockedStatic<LowLevelPObjectAccessor> accessor = mockLowLevelAccessor(lowLevelObject)) {
            repairer.repair((IUniqueObject) pEntity, repairContext(OUTDATED));

            verify(lowLevelObject).markChanged();
        }
    }

    @Test
    void testRepairLeavesTheObjectUntouchedWhenThereIsNothingToRemove() {
        valid(DEFINED, OUTDATED);
        ILowLevelPObject lowLevelObject = mock(ILowLevelPObject.class);
        try (MockedStatic<LowLevelPObjectAccessor> accessor = mockLowLevelAccessor(lowLevelObject)) {
            repairer.repair((IUniqueObject) pEntity, repairContext(OUTDATED));

            verify(lowLevelObject, never()).markChanged();
            accessor.verify(() -> LowLevelPObjectAccessor.clearCustomSetCaches(any()), never());
        }
    }

    @Test
    void testRepairFailsWhenTheAttributeIsDefinedAgain() {
        valid(DEFINED, OUTDATED);

        RepairResult result = repairer.repair((IUniqueObject) pEntity, repairContext(OUTDATED));

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("does not exist anymore")));
        verify(data, never()).removeCustomKey(anyString());
        verify(pEntity, never()).save();
    }

    @Test
    void testRepairFailsWhenTheMetaInfoCarriesNoAttribute() {
        RepairResult result = repairer.repair((IUniqueObject) pEntity, repairContext(null));

        assertFalse(result.isSuccess());
        verify(data, never()).removeCustomKey(anyString());
    }

    private MockedStatic<LowLevelPObjectAccessor> mockLowLevelAccessor() {
        return mockLowLevelAccessor(mock(ILowLevelPObject.class));
    }

    /** The accessor reaches the low level object through a field of PObject, which a mock does not have. */
    private MockedStatic<LowLevelPObjectAccessor> mockLowLevelAccessor(ILowLevelPObject lowLevelObject) {
        MockedStatic<LowLevelPObjectAccessor> accessor = mockStatic(LowLevelPObjectAccessor.class);
        accessor.when(() -> LowLevelPObjectAccessor.getFor(pEntity)).thenReturn(lowLevelObject);
        return accessor;
    }

    @Test
    void testMetadata() {
        assertEquals("Outdated Custom Fields", repairer.getDisplayName());
        assertEquals("OutdatedCustomFieldsRepairer", repairer.getRepairerId());
        assertTrue(repairer.getDescription().contains("not defined"));
        assertTrue(repairer.getConfigs().isEmpty());
    }

    private IWorkflowObject entity() {
        return (IWorkflowObject) pEntity;
    }

    private RepairContext repairContext(String fieldId) {
        IssueMetaInfo metaInfo = IssueMetaInfo.create(entity());
        if (fieldId != null) {
            metaInfo.set("fieldId", fieldId);
        }
        return new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());
    }

    private void stored(String... fieldIds) {
        lenient().when(data.getCustomKeySet()).thenReturn(Set.of(fieldIds));
    }

    private void incompatible(String... fieldIds) {
        lenient().when(data.getIncompatibleCustomKeySet()).thenReturn(Set.of(fieldIds));
    }

    private void valid(String... fieldIds) {
        Collection<String> validIds = List.of(fieldIds);
        lenient().when(customFieldsService.getValidCustomFieldIds(pEntity)).thenReturn(validIds);
    }

    private PObject createPObjectEntity() {
        PObject entity = mock(PObject.class, withSettings()
                .extraInterfaces(IWorkItem.class)
                .defaultAnswer(RETURNS_DEEP_STUBS));
        IWorkflowObject workflowObject = (IWorkflowObject) entity;
        lenient().when(workflowObject.getPrototype().getName()).thenReturn("WorkItem");
        lenient().when(Objects.requireNonNull(workflowObject.getType()).getId()).thenReturn("task");
        lenient().when(workflowObject.getProjectId()).thenReturn("testProject");
        lenient().when(workflowObject.getId()).thenReturn("WI-001");
        lenient().when(workflowObject.getContextId()).thenReturn(contextId);
        return entity;
    }
}
