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
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.types.Text;
import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.mockFields;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class RichTextDocumentLinksRepairerTest {

    private static final String PROJECT = "elibrary";
    private static final String FIELD = "description";

    private final RichTextDocumentLinksRepairer repairer = new RichTextDocumentLinksRepairer();
    private XmlRepairPolarionService polarionService;
    private IWorkflowObject entity;

    @BeforeEach
    void setUp() {
        polarionService = mock(XmlRepairPolarionService.class);
        entity = mock(IWorkItem.class, RETURNS_DEEP_STUBS);
        when(entity.getPrototype().getName()).thenReturn(IWorkItem.PROTO);
        when(entity.getProjectId()).thenReturn(PROJECT);
        IContextId contextId = mock(IContextId.class);
        when(entity.getContextId()).thenReturn(contextId);
        Set<FieldMetadata> fields = mockFields(FieldType.TEXT, FIELD);
        when(polarionService.getAllFields(eq(IWorkItem.PROTO), any(), any(), eq(false),
                eq(FieldType.TEXT.getType()), eq(FieldType.RICH.getType()))).thenReturn(fields);
    }

    private static String link(String attributes) {
        return "<span class=\"polarion-rte-link\" data-type=\"document\" id=\"fake\" %s data-option-id=\"default\">".formatted(attributes);
    }

    private void content(String html) {
        when(entity.getValue(FIELD)).thenReturn(Text.html(html));
    }

    private void exists(String project, String space, String name) {
        when(polarionService.isDocumentExists(project, space, name, null)).thenReturn(true);
    }

    private List<Issue> scan() {
        return repairer.scan(entity, createScanContext(polarionService));
    }

    private RepairResult repair(String linkToFix) {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("fieldId")).thenReturn(FIELD);
        when(metaInfo.getString("link")).thenReturn(linkToFix);
        when(metaInfo.serialize()).thenReturn("serialized");
        return repairer.repair(entity, new RepairContext(metaInfo, polarionService, new UserConfigs(), new Cache()));
    }

    @Test
    void scanIgnoresLinkToExistingDocument() {
        content("<p>" + link("data-item-name=\"Spec\" data-space-name=\"Design\"") + "</span></p>");
        exists(PROJECT, "Design", "Spec");

        assertTrue(scan().isEmpty());
        verify(polarionService, never()).findDocumentSpaces(anyString(), anyString());
    }

    @Test
    void scanKeepsDocumentsWithSimilarNamesApart() {
        // joined by '_', both links would make the same cache key, and the second would get the answer of the first
        String broken = link("data-item-name=\"A_Spec\" data-space-name=\"Design\"");
        content(link("data-item-name=\"Spec\" data-space-name=\"Design_A\"") + "</span>" + broken + "</span>");
        exists(PROJECT, "Design_A", "Spec");

        List<Issue> issues = scan();

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains("document 'A_Spec' does not exist in the space 'Design'"));
    }

    @Test
    void scanIgnoresLinksOfOtherTypes() {
        content("<span class=\"polarion-rte-link\" data-type=\"workItem\" id=\"fake\" data-item-id=\"EL-1\" data-option-id=\"long\"></span>");

        assertTrue(scan().isEmpty());
        verify(polarionService, never()).isDocumentExists(anyString(), anyString(), anyString(), any());
    }

    @Test
    void scanReportsNewLocationOfMovedDocument() {
        String broken = link("data-item-name=\"Spec\" data-space-name=\"Design\"");
        content(broken + "</span>");
        when(polarionService.findDocumentSpaces(PROJECT, "Spec")).thenReturn(List.of("Archive"));

        List<Issue> issues = scan();

        assertEquals(1, issues.size());
        assertEquals("Broken link found: document 'Spec' does not exist in the space 'Design' of the project 'elibrary'. " +
                "The document was found in the space 'Archive', the link can be fixed.", issues.getFirst().getDescription());
        assertEquals(broken, issues.getFirst().getRawMetaInfo().getString("link"));
        assertEquals(FIELD, issues.getFirst().getRawMetaInfo().getString("fieldId"));
    }

    @Test
    void scanReportsDocumentFoundNowhere() {
        content(link("data-item-name=\"Spec\" data-space-name=\"Design\""));
        when(polarionService.findDocumentSpaces(PROJECT, "Spec")).thenReturn(List.of());

        assertEquals("Broken link found: document 'Spec' does not exist in the space 'Design' of the project 'elibrary'. " +
                "The link cannot be fixed: document 'Spec' was not found in any other space of the project.", scan().getFirst().getDescription());
    }

    @Test
    void scanReportsDocumentFoundInSeveralSpaces() {
        content(link("data-item-name=\"Spec\" data-space-name=\"Design\""));
        when(polarionService.findDocumentSpaces(PROJECT, "Spec")).thenReturn(List.of("Archive", "Drafts"));

        assertTrue(scan().getFirst().getDescription().endsWith(
                "The link cannot be fixed: document 'Spec' exists in several spaces ('Archive', 'Drafts')."));
    }

    @Test
    void scanUsesTheProjectAndRevisionOfTheLinkAndDecodesItsAttributes() {
        content(link("data-item-name=\"R&amp;D Spec\" data-space-name=\"Design\" data-scope=\"drivepilot\" data-revision=\"42\""));
        when(polarionService.findDocumentSpaces("drivepilot", "R&D Spec")).thenReturn(List.of("Archive", "Drafts"));
        // at the revision of the link the document was in one of them only
        when(polarionService.isDocumentExists("drivepilot", "Archive", "R&D Spec", "42")).thenReturn(true);

        assertEquals("Broken link found: document 'R&D Spec:42' does not exist in the space 'Design' of the project 'drivepilot'. " +
                "The document was found in the space 'Archive', the link can be fixed.", scan().getFirst().getDescription());
    }

    @Test
    void scanTreatsMissingSpaceAsDefaultSpace() {
        content(link("data-item-name=\"Spec\""));
        exists(PROJECT, "_default", "Spec");

        assertTrue(scan().isEmpty());
    }

    @Test
    void scanCoversTheBodyOfADocument() {
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getPrototype().getName()).thenReturn(IModule.PROTO);
        when(module.getProjectId()).thenReturn(PROJECT);
        IContextId contextId = mock(IContextId.class);
        when(module.getContextId()).thenReturn(contextId);
        // the field listing does not return the body
        when(polarionService.getAllFields(eq(IModule.PROTO), any(), any(), eq(false),
                eq(FieldType.TEXT.getType()), eq(FieldType.RICH.getType()))).thenReturn(Set.of());
        when(module.getValue(IModule.KEY_HOMEPAGECONTENT)).thenReturn(Text.html(link("data-item-name=\"Spec\" data-space-name=\"Design\"")));
        when(polarionService.findDocumentSpaces(PROJECT, "Spec")).thenReturn(List.of("Archive"));

        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertEquals(1, issues.size());
        assertEquals(IModule.KEY_HOMEPAGECONTENT, issues.getFirst().getRawMetaInfo().getString("fieldId"));
    }

    @Test
    void scanReportsEachOccurrence() {
        String broken = link("data-item-name=\"Spec\" data-space-name=\"Design\"") + "</span>";
        content(broken + " and " + broken);
        when(polarionService.findDocumentSpaces(PROJECT, "Spec")).thenReturn(List.of("Archive"));

        assertEquals(2, scan().size());
        // one lookup serves both
        verify(polarionService, times(1)).findDocumentSpaces(PROJECT, "Spec");
    }

    @Test
    void repairPointsTheLinkToTheNewSpace() {
        String broken = link("data-item-name=\"Spec\" data-space-name=\"Design\"");
        content("<p>" + broken + "</span></p>");
        when(polarionService.findDocumentSpaces(PROJECT, "Spec")).thenReturn(List.of("R&D"));

        RepairResult result = repair(broken);

        assertTrue(result.isSuccess());
        verify(entity).setValue(FIELD, Text.html("<p>" + link("data-item-name=\"Spec\" data-space-name=\"R&amp;D\"") + "</span></p>"));
    }

    @Test
    void repairAddsTheSpaceWhenTheLinkHasNone() {
        String broken = link("data-item-name=\"Spec\"");
        content(broken);
        when(polarionService.findDocumentSpaces(PROJECT, "Spec")).thenReturn(List.of("Archive"));

        assertTrue(repair(broken).isSuccess());
        verify(entity).setValue(FIELD, Text.html(link("data-space-name=\"Archive\" data-item-name=\"Spec\"")));
    }

    @Test
    void repairFixesOneOccurrencePerIssue() {
        String broken = link("data-item-name=\"Spec\" data-space-name=\"Design\"");
        content(broken + broken);
        when(polarionService.findDocumentSpaces(PROJECT, "Spec")).thenReturn(List.of("Archive"));

        assertTrue(repair(broken).isSuccess());
        verify(entity).setValue(FIELD, Text.html(link("data-item-name=\"Spec\" data-space-name=\"Archive\"") + broken));
    }

    @Test
    void repairLeavesAmbiguousLinkAlone() {
        String broken = link("data-item-name=\"Spec\" data-space-name=\"Design\"");
        content(broken);
        when(polarionService.findDocumentSpaces(PROJECT, "Spec")).thenReturn(List.of("Archive", "Drafts"));

        RepairResult result = repair(broken);

        assertFalse(result.isSuccess());
        assertEquals(Set.of("The link cannot be fixed: document 'Spec' exists in several spaces ('Archive', 'Drafts')."), result.getWarnings());
        verify(entity, never()).setValue(anyString(), any());
    }

    @Test
    void repairLeavesUnresolvableLinkAlone() {
        String broken = link("data-item-name=\"Spec\" data-space-name=\"Design\"");
        content(broken);
        when(polarionService.findDocumentSpaces(PROJECT, "Spec")).thenReturn(List.of());

        RepairResult result = repair(broken);

        assertFalse(result.isSuccess());
        assertEquals(Set.of("The link cannot be fixed: document 'Spec' was not found in any other space of the project."), result.getWarnings());
        verify(entity, never()).setValue(anyString(), any());
    }

    @Test
    void repairDoesNothingWhenTheDocumentIsBack() {
        String broken = link("data-item-name=\"Spec\" data-space-name=\"Design\"");
        content(broken);
        exists(PROJECT, "Design", "Spec");

        RepairResult result = repair(broken);

        assertFalse(result.isSuccess());
        assertEquals(Set.of("The link is not broken any more: document 'Spec' exists in the space 'Design'."), result.getWarnings());
        verify(entity, never()).setValue(anyString(), any());
    }

    @Test
    void repairReportsLinkGoneFromContent() {
        content("<p>changed</p>");

        RepairResult result = repair(link("data-item-name=\"Spec\" data-space-name=\"Design\""));

        assertFalse(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
    }

    @Test
    void repairReportsFieldWhichIsNotText() {
        when(entity.getValue(FIELD)).thenReturn(42);

        RepairResult result = repair("any");

        assertFalse(result.isSuccess());
        assertEquals(Set.of("Field 'description' is not of type Text."), result.getWarnings());
    }

    @Test
    void describesItself() {
        assertEquals("Rich text: Broken Links to Documents", repairer.getDisplayName());
        assertFalse(repairer.getDescription().isEmpty());
        assertTrue(repairer.getConfigs().isEmpty());
    }

}
