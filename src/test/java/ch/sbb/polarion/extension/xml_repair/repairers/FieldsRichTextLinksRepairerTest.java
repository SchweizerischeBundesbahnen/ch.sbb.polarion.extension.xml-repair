package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
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
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.types.Text;
import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class FieldsRichTextLinksRepairerTest {

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

    @Test
    void testScanFindsBrokenLinks() {
        FieldsRichTextLinksRepairer repairer = new FieldsRichTextLinksRepairer();

        IWorkflowObject entity = mock(IWorkItem.class, RETURNS_DEEP_STUBS);
        when(entity.getPrototype().getName()).thenReturn(IModule.PROTO);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getContextId()).thenReturn(mock(IContextId.class));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        Set<FieldMetadata> fields = mockFields(FieldType.TEXT, "description");
        fields.addAll(mockFields(FieldType.RICH, "custom1"));
        when(polarionService.getAllFields(eq(IModule.PROTO), any(), any(), eq(false),
                eq(FieldType.TEXT.getType()), eq(FieldType.RICH.getType()))).thenReturn(fields);

        //language=HTML
        when(entity.getValue("description")).thenReturn(Text.plain(
                "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-custom-label=\"EL-1\" data-scope=\"drivepilot\" data-option-id=\"long\"></span>"
        ));
        //language=HTML
        when(entity.getValue("custom1")).thenReturn(Text.html(
                "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-2\" data-custom-label=\"EL-2\" data-scope=\"drivepilot\" data-option-id=\"long\"></span>"
        ));

        when(polarionService.isWorkItemExists(eq("drivepilot"), anyString(), isNull())).thenReturn(false);
        when(polarionService.isWorkItemExists(eq("elibrary"), anyString(), isNull())).thenReturn(true);

        ScanContext context = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(entity, context);

        assertEquals(2, issues.size());
        assertTrue(issues.stream().map(Issue::getDescription).toList().containsAll(List.of(
                "Broken link found: workitem 'EL-1' does not exist in the project 'drivepilot'.",
                "Broken link found: workitem 'EL-2' does not exist in the project 'drivepilot'."
        )));
    }

    @Test
    void testScanSkipsNonTextValues() {
        FieldsRichTextLinksRepairer repairer = new FieldsRichTextLinksRepairer();

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getPrototype().getName()).thenReturn(IModule.PROTO);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getContextId()).thenReturn(mock(IContextId.class));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        Set<FieldMetadata> fields = mockFields(FieldType.RICH, "custom1");
        when(polarionService.getAllFields(eq(IModule.PROTO), any(), any(), eq(false),
                eq(FieldType.TEXT.getType()), eq(FieldType.RICH.getType()))).thenReturn(fields);

        when(entity.getValue("custom1")).thenReturn(42);

        ScanContext context = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(entity, context);

        assertTrue(issues.isEmpty());
        verify(polarionService, never()).isWorkItemExists(anyString(), anyString(), any());
    }

    @Test
    void testRepairFixesBrokenLink() {
        FieldsRichTextLinksRepairer repairer = new FieldsRichTextLinksRepairer();

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");

        //language=HTML
        String brokenLink = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-custom-label=\"EL-1\" data-scope=\"drivepilot\" data-option-id=\"long\"></span>";
        when(entity.getValue("description")).thenReturn(Text.plain(brokenLink));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(eq("drivepilot"), eq("EL-1"), isNull())).thenReturn(false);
        when(polarionService.isWorkItemExists(eq("elibrary"), eq("EL-1"), isNull())).thenReturn(true);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("fieldId")).thenReturn("description");
        when(metaInfo.getString("link")).thenReturn(brokenLink);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());
        RepairResult result = repairer.repair(entity, context);

        assertTrue(result.isSuccess());
        //language=HTML
        verify(entity).setValue("description", Text.plain(
                "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-custom-label=\"EL-1\"  data-option-id=\"long\"></span>"
        ));
    }

    @Test
    void testRepairFieldNotText() {
        FieldsRichTextLinksRepairer repairer = new FieldsRichTextLinksRepairer();

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getValue("description")).thenReturn(42);

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("fieldId")).thenReturn("description");
        when(metaInfo.serialize()).thenReturn("serialized");

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        RepairContext context = new RepairContext(metaInfo, polarionService, new UserConfigs());
        RepairResult result = repairer.repair(entity, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("not of type Text")));
    }

    @Test
    void testGetConfigs() {
        FieldsRichTextLinksRepairer repairer = new FieldsRichTextLinksRepairer();
        List<RepairerConfigMeta> configs = repairer.getConfigs();

        assertEquals(1, configs.size());
        RepairerConfigMeta config = configs.get(0);
        assertEquals(BaseLinksRepairer.CONVERT_TO_PLAIN_TEXT, config.getKey());
        assertEquals("Convert unresolvable links to plain text", config.getDescription());
        assertEquals(RepairerConfigType.BOOLEAN, config.getType());
        assertEquals(false, config.getDefaultValue());
    }
}
