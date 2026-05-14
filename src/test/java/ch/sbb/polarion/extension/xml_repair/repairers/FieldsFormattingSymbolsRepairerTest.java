package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.types.Text;
import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.mockFields;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class FieldsFormattingSymbolsRepairerTest {

    @Test
    void testScanFindsFormattingSymbols() {
        FieldsFormattingSymbolsRepairer repairer = new FieldsFormattingSymbolsRepairer();

        IWorkflowObject entity = mock(IWorkItem.class, RETURNS_DEEP_STUBS);
        when(entity.getPrototype().getName()).thenReturn(IModule.PROTO);
        when(entity.getContextId()).thenReturn(mock(IContextId.class));
        when(Objects.requireNonNull(entity.getType()).getId()).thenReturn("testType");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        Set<FieldMetadata> fields = mockFields(FieldType.STRING, "description");
        when(polarionService.getAllFields(eq(IModule.PROTO), any(), eq("testType"), eq(false), eq(FieldType.STRING.getType()))).thenReturn(fields);

        String input = "This is a test with\n\tformatting symbols.";
        when(entity.getValue("description")).thenReturn(input);

        ScanContext context = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(entity, context);

        assertEquals(1, issues.size());
        assertEquals("String field contains formatting symbols.", issues.getFirst().getDescription());
    }

    @Test
    void testScanIgnoresNonStringAndCleanValues() {
        FieldsFormattingSymbolsRepairer repairer = new FieldsFormattingSymbolsRepairer();

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getPrototype().getName()).thenReturn(IModule.PROTO);
        when(entity.getContextId()).thenReturn(mock(IContextId.class));
        when(Objects.requireNonNull(entity.getType()).getId()).thenReturn("testType");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        Set<FieldMetadata> fields = mockFields(FieldType.STRING, "textField", "intField", "cleanField");
        when(polarionService.getAllFields(eq(IModule.PROTO), any(), eq("testType"), eq(false), eq(FieldType.STRING.getType()))).thenReturn(fields);

        when(entity.getValue("textField")).thenReturn(Text.plain("Some text"));
        when(entity.getValue("intField")).thenReturn(123);
        when(entity.getValue("cleanField")).thenReturn("Clean string without formatting");

        ScanContext context = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(entity, context);

        assertEquals(0, issues.size());
    }

    @Test
    void testRepairReplacesFormattingSymbols() {
        FieldsFormattingSymbolsRepairer repairer = new FieldsFormattingSymbolsRepairer();

        IWorkflowObject entity = mock(IWorkflowObject.class);
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("fieldId")).thenReturn("description");
        when(metaInfo.serialize()).thenReturn("serialized");

        String input = "This is a test with\n\tformatting symbols.";
        String expected = "This is a test with formatting symbols.";
        when(entity.getValue("description")).thenReturn(input);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        verify(entity).setValue("description", expected);
    }

    @Test
    void testRepairIssueAlreadyFixed() {
        FieldsFormattingSymbolsRepairer repairer = new FieldsFormattingSymbolsRepairer();

        IWorkflowObject entity = mock(IWorkflowObject.class);
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("fieldId")).thenReturn("description");
        when(metaInfo.serialize()).thenReturn("serialized");

        when(entity.getValue("description")).thenReturn("Clean string without formatting");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache());

        RepairResult result = repairer.repair(entity, context);

        assertFalse(result.isSuccess());
        verify(entity, never()).setValue(anyString(), any());
    }
}
