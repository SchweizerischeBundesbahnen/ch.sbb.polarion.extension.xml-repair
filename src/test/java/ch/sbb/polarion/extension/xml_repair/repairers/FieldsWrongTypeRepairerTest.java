package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.service.EntityRenderer;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.types.Text;
import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(PlatformContextMockExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FieldsWrongTypeRepairerTest {

    private Set<FieldMetadata> mockFields(FieldType fieldType, String... ids) {
        return Stream.of(ids).map(id -> {
            FieldMetadata meta = mock(FieldMetadata.class);
            when(meta.getId()).thenReturn(id);
            when(meta.getType()).thenReturn(fieldType.getType());
            return meta;
        }).collect(Collectors.toSet());
    }

    private ScanContext createScanContext(XmlRepairPolarionService polarionService) {
        lenient().when(polarionService.getTrackerService()).thenReturn(mock(ITrackerService.class));
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored = mockConstruction(EntityRenderer.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(mock(InternalReadOnlyTransaction.class));
            return new ScanContext(polarionService, List.of(), new UserConfigs(), new Report());
        }
    }

    private IWorkflowObject createMockEntity() {
        IWorkflowObject entity = mock(IWorkItem.class, RETURNS_DEEP_STUBS);
        when(entity.getPrototype().getName()).thenReturn(IModule.PROTO);
        when(entity.getContextId()).thenReturn(mock(IContextId.class));
        ITypeOpt typeOpt = mock(ITypeOpt.class);
        when(typeOpt.getId()).thenReturn("testType");
        when(entity.getType()).thenReturn(typeOpt);
        return entity;
    }

    @Test
    void testScanFindsWrongTypeValues() {
        FieldsWrongTypeRepairer repairer = new FieldsWrongTypeRepairer();
        IWorkflowObject entity = createMockEntity();

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        Set<FieldMetadata> fields = mockFields(FieldType.STRING, "field1", "field2", "field3");
        when(polarionService.getAllFields(any(), any(), any(), eq(false), eq(FieldType.STRING.getType()))).thenReturn(fields);

        when(entity.getValue("field1")).thenReturn(Text.html("<b>HTML text</b>"));
        when(entity.getValue("field2")).thenReturn(Text.plain("Plain text"));
        when(entity.getValue("field3")).thenReturn(123);

        ScanContext context = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(entity, context);

        assertEquals(3, issues.size());
        verify(entity, never()).setValue(anyString(), any());
    }

    @Test
    void testScanIgnoresStringValues() {
        FieldsWrongTypeRepairer repairer = new FieldsWrongTypeRepairer();
        IWorkflowObject entity = createMockEntity();

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        Set<FieldMetadata> fields = mockFields(FieldType.STRING, "field1", "field2");
        when(polarionService.getAllFields(any(), any(), any(), eq(false), eq(FieldType.STRING.getType()))).thenReturn(fields);

        when(entity.getValue("field1")).thenReturn("normal string");
        when(entity.getValue("field2")).thenReturn("another string");

        ScanContext context = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(entity, context);

        assertEquals(0, issues.size());
    }

    @Test
    void testRepairConvertsHtmlText() {
        FieldsWrongTypeRepairer repairer = new FieldsWrongTypeRepairer();
        IWorkflowObject entity = createMockEntity();

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        FieldMetadata fieldMeta = mock(FieldMetadata.class);
        when(fieldMeta.getId()).thenReturn("custom1");
        when(fieldMeta.getType()).thenReturn(FieldType.STRING.getType());
        when(polarionService.getAllFields(any(), any(), any(), eq(false), eq(FieldType.STRING.getType()))).thenReturn(Set.of(fieldMeta));

        when(entity.getValue("custom1")).thenReturn(Text.html("<b>HTML text</b>"));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("fieldId")).thenReturn("custom1");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs());
        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        verify(entity).setValue("custom1", "HTML text");
    }

    @Test
    void testRepairConvertsPlainText() {
        FieldsWrongTypeRepairer repairer = new FieldsWrongTypeRepairer();
        IWorkflowObject entity = createMockEntity();

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        FieldMetadata fieldMeta = mock(FieldMetadata.class);
        when(fieldMeta.getId()).thenReturn("custom2");
        when(fieldMeta.getType()).thenReturn(FieldType.STRING.getType());
        when(polarionService.getAllFields(any(), any(), any(), eq(false), eq(FieldType.STRING.getType()))).thenReturn(Set.of(fieldMeta));

        when(entity.getValue("custom2")).thenReturn(Text.plain("Plain text"));

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("fieldId")).thenReturn("custom2");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs());
        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        verify(entity).setValue("custom2", "Plain text");
    }

    @Test
    void testRepairConvertsInteger() {
        FieldsWrongTypeRepairer repairer = new FieldsWrongTypeRepairer();
        IWorkflowObject entity = createMockEntity();

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        FieldMetadata fieldMeta = mock(FieldMetadata.class);
        when(fieldMeta.getId()).thenReturn("custom3");
        when(fieldMeta.getType()).thenReturn(FieldType.STRING.getType());
        when(polarionService.getAllFields(any(), any(), any(), eq(false), eq(FieldType.STRING.getType()))).thenReturn(Set.of(fieldMeta));

        when(entity.getValue("custom3")).thenReturn(123);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("fieldId")).thenReturn("custom3");
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs());
        RepairResult result = repairer.repair(entity, repairContext);

        assertTrue(result.isSuccess());
        verify(entity).setValue("custom3", "123");
    }
}
