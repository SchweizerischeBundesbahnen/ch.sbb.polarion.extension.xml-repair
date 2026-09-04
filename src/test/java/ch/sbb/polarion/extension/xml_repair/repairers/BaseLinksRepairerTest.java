package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.types.Text;
import com.polarion.platform.persistence.model.IPObjectList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static ch.sbb.polarion.extension.xml_repair.repairers.BaseLinksRepairer.ADJUST_WORK_ITEM_PREFIX;
import static ch.sbb.polarion.extension.xml_repair.repairers.BaseLinksRepairer.CONVERT_TO_PLAIN_TEXT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("java:S125") // suppress false-positive commented-out lines of code
class BaseLinksRepairerTest {

    private static class TestableLinksRepairer extends BaseLinksRepairer {
        @Override
        public String getDisplayName() {
            return "TestableLinksRepairer";
        }

        @Override
        public String getDescription() {
            return "TestableLinksRepairer";
        }
    }

    // ---- scanLinksInHtml tests ----

    @Test
    void testScanLinksInHtmlNoLinks() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        List<Issue> issues = repairer.scanLinksInHtml("<p>No links here</p>", entity, "content", polarionService);

        assertTrue(issues.isEmpty());
    }

    static Stream<Arguments> testScanLinksInHtmlValidLink() {
        return Stream.of(
                // workItem link without scope - validated against the entity's own project
                Arguments.of("workItem link without scope",
                        "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-custom-label=\"EL-1\" data-option-id=\"long\"></span>",
                        "elibrary", "EL-1"),
                // workItem link whose scope matches the entity's own project
                Arguments.of("workItem link with matching scope",
                        "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"elibrary\" data-option-id=\"long\"></span>",
                        "elibrary", "EL-1"),
                // polarion (wiki/document) link with the id in data-url's 'selection' parameter
                Arguments.of("polarion link with id in data-url",
                        "<span class=\"polarion-rte-link\" data-type=\"polarion\" id=\"fake\" data-custom-label=\"EL-266\" data-scope=\"docx_exporter_elibrary_st_st_103c721ee242\" data-url=\"/wiki/Testing/Link%20Role%20Direction%20Test?selection=EL-266\"></span>",
                        "docx_exporter_elibrary_st_st_103c721ee242", "EL-266"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testScanLinksInHtmlValidLink(String name, String html, String effectiveProjectId, String itemId) {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(effectiveProjectId, itemId, null)).thenReturn(true);

        List<Issue> issues = repairer.scanLinksInHtml(html, entity, "content", polarionService);

        assertTrue(issues.isEmpty());
    }

    static Stream<Arguments> testScanLinksInHtmlBrokenLink() {
        return Stream.of(
                // workItem and crossReference links carry the id in data-item-id and the project in data-scope
                Arguments.of("workItem link with scope",
                        "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>",
                        "EL-1", "otherProject"),
                Arguments.of("crossReference link with scope",
                        "<span class=\"polarion-rte-link\" data-type=\"crossReference\" data-item-id=\"EL-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>",
                        "EL-1", "otherProject"),
                // polarion (wiki/document) links have no data-item-id - the id is the 'selection' parameter of data-url
                Arguments.of("polarion link with id in data-url",
                        "<span class=\"polarion-rte-link\" data-type=\"polarion\" id=\"fake\" data-custom-label=\"EL-266\" data-scope=\"docx_exporter_elibrary_st_st_103c721ee242\" data-url=\"/wiki/Testing/Link%20Role%20Direction%20Test?selection=EL-266\"></span>",
                        "EL-266", "docx_exporter_elibrary_st_st_103c721ee242"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testScanLinksInHtmlBrokenLink(String name, String html, String expectedItemId, String expectedProjectId) {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(expectedProjectId, expectedItemId, null)).thenReturn(false);

        List<Issue> issues = repairer.scanLinksInHtml(html, entity, "content", polarionService);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains(expectedItemId));
        assertTrue(issues.getFirst().getDescription().contains(expectedProjectId));
    }

    @Test
    void testScanLinksInHtmlBrokenLinkWithoutScope() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-1", null)).thenReturn(false);

        String html = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-option-id=\"long\"></span>";
        List<Issue> issues = repairer.scanLinksInHtml(html, entity, "content", polarionService);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains("elibrary"));
    }

    @Test
    void testScanLinksInHtmlLinkWithRevision() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("otherProject", "EL-1", "42")).thenReturn(false);

        String html = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"otherProject\" data-revision=\"42\" data-option-id=\"long\"></span>";
        List<Issue> issues = repairer.scanLinksInHtml(html, entity, "content", polarionService);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains("EL-1:42"));
    }

    // ---- repairLinksInHtml tests ----

    @Test
    void testRepairLinkNotFoundInContent() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn("<span>some link</span>");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn("<p>No matching link</p>");

        IWorkflowObject entity = mock(IWorkflowObject.class);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("not found in the content")));
    }

    @Test
    void testRepairLinkStillValid() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-custom-label=\"EL-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("otherProject", "EL-1", null)).thenReturn(true);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertFalse(result.isSuccess());
        verify(entity, never()).setValue(anyString(), any());
    }

    @Test
    void testRepairBrokenLinkExistsInCurrentProject() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-custom-label=\"EL-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("otherProject", "EL-1", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "EL-1", null)).thenReturn(true);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertTrue(result.isSuccess());
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text && !((Text) t).getContent().contains("data-scope=\"otherProject\"")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRepairGlobalSearchFindsMultipleItems() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(anyString(), anyString(), isNull())).thenReturn(false);

        IWorkItem found1 = mock(IWorkItem.class);
        when(found1.getProjectId()).thenReturn("projectA");
        IWorkItem found2 = mock(IWorkItem.class);
        when(found2.getProjectId()).thenReturn("projectB");
        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(2);
        when(searchResults.get(0)).thenReturn(found1);
        when(searchResults.get(1)).thenReturn(found2);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:EL\\-1"), isNull(), eq(2))).thenReturn(searchResults);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("projectA") && w.contains("projectB")));
        verify(entity, never()).setValue(anyString(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRepairGlobalSearchFindsOneItemReplacesScope() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"wrongProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(anyString(), anyString(), isNull())).thenReturn(false);

        IWorkItem foundItem = mock(IWorkItem.class);
        when(foundItem.getProjectId()).thenReturn("correctProject");
        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(1);
        when(searchResults.getFirst()).thenReturn(foundItem);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:EL\\-1"), isNull(), eq(2))).thenReturn(searchResults);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertTrue(result.isSuccess());
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text && ((Text) t).getContent().contains("data-scope=\"correctProject\"")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRepairGlobalSearchFindsOneItemInsertsScope() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        // No data-scope in the link
        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-1", null)).thenReturn(false);

        IWorkItem foundItem = mock(IWorkItem.class);
        when(foundItem.getProjectId()).thenReturn("otherProject");
        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(1);
        when(searchResults.getFirst()).thenReturn(foundItem);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:EL\\-1"), isNull(), eq(2))).thenReturn(searchResults);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertTrue(result.isSuccess());
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text && ((Text) t).getContent().contains("data-scope=\"otherProject\" data-item-id=")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRepairConvertToPlainTextWithCustomLabel() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-custom-label=\"My Custom Label\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(anyString(), anyString(), isNull())).thenReturn(false);

        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(0);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:EL\\-1"), isNull(), eq(2))).thenReturn(searchResults);

        UserConfigs configs = new UserConfigs();
        configs.put("TestableLinksRepairer", Map.of(CONVERT_TO_PLAIN_TEXT, true));

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, configs);

        assertTrue(result.isSuccess());
        verify(entity).setValue(eq("content"), argThat(t ->
                t instanceof Text && ((Text) t).getContent().equals("<span class=\"xml-repair-replaced-link\">My Custom Label</span>")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRepairConvertToPlainTextNoCustomLabel() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(anyString(), anyString(), isNull())).thenReturn(false);

        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(0);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:EL\\-1"), isNull(), eq(2))).thenReturn(searchResults);

        UserConfigs configs = new UserConfigs();
        configs.put("TestableLinksRepairer", Map.of(CONVERT_TO_PLAIN_TEXT, true));

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, configs);

        assertTrue(result.isSuccess());
        verify(entity).setValue(eq("content"), argThat(t ->
                t instanceof Text && ((Text) t).getContent().equals("<span class=\"xml-repair-replaced-link\">EL-1</span>")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRepairNotFoundAnywhereConvertToPlainTextDisabled() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-custom-label=\"My Label\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(anyString(), anyString(), isNull())).thenReturn(false);

        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(0);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:EL\\-1"), isNull(), eq(2))).thenReturn(searchResults);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("does not exist")));
        verify(entity, never()).setValue(anyString(), any());
    }

    @Test
    void testRepairSetValueUsesPlainTextWhenTextIsPlain() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-custom-label=\"EL-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(true);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("otherProject", "EL-1", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "EL-1", null)).thenReturn(true);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertTrue(result.isSuccess());
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text && ((Text) t).isPlain()));
    }

    @Test
    void testRepairLinkWithRevision() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"otherProject\" data-revision=\"42\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("otherProject", "EL-1", "42")).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "EL-1", "42")).thenReturn(true);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertTrue(result.isSuccess());
        // Scope should be removed since item exists in current project
        verify(entity).setValue(eq("content"), argThat(t ->
                t instanceof Text && !((Text) t).getContent().contains("data-scope=\"otherProject\"")));
    }

    @Test
    void testRepairNonMatchingLinksArePreserved() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String targetLink = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        String otherLink = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-2\" data-scope=\"validProject\" data-option-id=\"long\"></span>";
        String fullHtml = otherLink + targetLink;

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(targetLink);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(fullHtml);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        // target link is broken
        when(polarionService.isWorkItemExists("otherProject", "EL-1", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "EL-1", null)).thenReturn(true);
        // other link is valid
        when(polarionService.isWorkItemExists("validProject", "EL-2", null)).thenReturn(true);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertTrue(result.isSuccess());
        // The other link should be preserved unchanged
        verify(entity).setValue(eq("content"), argThat(t ->
                t instanceof Text && ((Text) t).getContent().contains("data-scope=\"validProject\"")));
    }

    @Test
    void testRepairPolarionLinkConvertToPlainText() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"polarion\" id=\"fake\" data-custom-label=\"EL-266\" data-scope=\"docx_exporter_elibrary_st_st_103c721ee242\" data-url=\"/wiki/Testing/Link%20Role%20Direction%20Test?selection=EL-266\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("docx_exporter_elibrary_st_st_103c721ee242", "EL-266", null)).thenReturn(false);

        UserConfigs configs = new UserConfigs();
        configs.put("TestableLinksRepairer", Map.of(CONVERT_TO_PLAIN_TEXT, true));

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, configs);

        assertTrue(result.isSuccess());
        // the item is found nowhere, so the link is converted to plain text using the custom label
        verify(entity).setValue(eq("content"), argThat(t ->
                t instanceof Text && ((Text) t).getContent().equals("<span class=\"xml-repair-replaced-link\">EL-266</span>")));
    }

    @Test
    void testRepairPolarionLinkAdjustPrefixRewritesSelection() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"polarion\" id=\"fake\" data-custom-label=\"OLD-266\" data-scope=\"docx_exporter_elibrary_st_st_103c721ee242\" data-url=\"/wiki/Testing/Test?selection=OLD-266\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getProject().getTrackerPrefix()).thenReturn("NEW");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("docx_exporter_elibrary_st_st_103c721ee242", "OLD-266", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "OLD-266", null)).thenReturn(false);
        // the adjusted id exists in the current project
        when(polarionService.isWorkItemExists("elibrary", "NEW-266", null)).thenReturn(true);

        UserConfigs configs = new UserConfigs();
        configs.put("TestableLinksRepairer", Map.of(ADJUST_WORK_ITEM_PREFIX, true));

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, configs);

        assertTrue(result.isSuccess());
        // the id is rewritten inside data-url's 'selection' parameter (not data-item-id, which polarion links lack),
        // the custom label is adjusted, and the (document) scope is removed - mirroring the work-item repair
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text
                && ((Text) t).getContent().contains("selection=NEW-266")
                && !((Text) t).getContent().contains("selection=OLD-266")
                && ((Text) t).getContent().contains("data-custom-label=\"NEW-266\"")
                && !((Text) t).getContent().contains("data-scope=")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRepairPolarionLinkGlobalSearchInsertsScopeBeforeDataUrl() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        // no data-scope present -> the found project id must be inserted before the id-bearing attribute (data-url)
        String link = "<span class=\"polarion-rte-link\" data-type=\"polarion\" data-custom-label=\"EL-266\" data-url=\"/wiki/Testing/Test?selection=EL-266\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-266", null)).thenReturn(false);

        IWorkItem foundItem = mock(IWorkItem.class);
        when(foundItem.getProjectId()).thenReturn("otherProject");
        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(1);
        when(searchResults.getFirst()).thenReturn(foundItem);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:EL\\-266"), isNull(), eq(2))).thenReturn(searchResults);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertTrue(result.isSuccess());
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text
                && ((Text) t).getContent().contains("data-scope=\"otherProject\" data-url=")));
    }

    @Test
    void testRepairFixesOnlyFirstOfIdenticalLinks() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        // the same work item referenced twice produces two issues with identical markup; one repair call must
        // fix one occurrence only, so the second issue still has something to repair and is not reported as failed
        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        String fullHtml = link + "<p>text between</p>" + link;

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(fullHtml);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("otherProject", "EL-1", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "EL-1", null)).thenReturn(true);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertTrue(result.isSuccess());
        String fixedLink = link.replace("data-scope=\"otherProject\"", "");
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text
                && ((Text) t).getContent().equals(fixedLink + "<p>text between</p>" + link)));
    }

    @Test
    void testRepairIdenticalLinksRepairedOneByOne() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        // repairing both issues of a duplicated link must succeed twice and leave no broken occurrence behind
        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        String fixedLink = link.replace("data-scope=\"otherProject\"", "");

        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("otherProject", "EL-1", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "EL-1", null)).thenReturn(true);

        // the content as the entity holds it; each repair reads it and writes the updated value back
        String[] content = {link + link};
        doAnswer(invocation -> content[0] = ((Text) invocation.getArgument(1)).getContent())
                .when(entity).setValue(eq("content"), any());

        for (int i = 0; i < 2; i++) {
            Text text = mock(Text.class);
            when(text.getContent()).thenReturn(content[0]);
            when(text.isPlain()).thenReturn(false);
            assertTrue(repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs()).isSuccess());
        }

        assertEquals(fixedLink + fixedLink, content[0]);
    }

    // ---- adjustWorkItemPrefix config tests ----

    @Test
    void testRepairAdjustPrefixFindsItemInCurrentProject() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"OLD-1\" data-custom-label=\"OLD-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getProject().getTrackerPrefix()).thenReturn("NEW");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        // original id not found anywhere
        when(polarionService.isWorkItemExists("otherProject", "OLD-1", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "OLD-1", null)).thenReturn(false);
        // adjusted id exists in the current project
        when(polarionService.isWorkItemExists("elibrary", "NEW-1", null)).thenReturn(true);

        UserConfigs configs = new UserConfigs();
        configs.put("TestableLinksRepairer", Map.of(ADJUST_WORK_ITEM_PREFIX, true));

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, configs);

        assertTrue(result.isSuccess());
        // both the item id and the custom label (which equals the old id) must be adjusted, the scope removed
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text
                && ((Text) t).getContent().contains("data-item-id=\"NEW-1\"")
                && ((Text) t).getContent().contains("data-custom-label=\"NEW-1\"")
                && !((Text) t).getContent().contains("data-item-id=\"OLD-1\"")
                && !((Text) t).getContent().contains("data-custom-label=\"OLD-1\"")
                && !((Text) t).getContent().contains("data-scope=\"otherProject\"")));
    }

    @Test
    void testRepairAdjustPrefixCustomLabelWithoutId() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        // custom label does not contain the work item id -> label stays unchanged, only the id is adjusted
        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"OLD-1\" data-custom-label=\"My Label\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getProject().getTrackerPrefix()).thenReturn("NEW");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("otherProject", "OLD-1", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "OLD-1", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "NEW-1", null)).thenReturn(true);

        UserConfigs configs = new UserConfigs();
        configs.put("TestableLinksRepairer", Map.of(ADJUST_WORK_ITEM_PREFIX, true));

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, configs);

        assertTrue(result.isSuccess());
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text
                && ((Text) t).getContent().contains("data-item-id=\"NEW-1\"")
                && ((Text) t).getContent().contains("data-custom-label=\"My Label\"")));
    }

    @Test
    void testRepairAdjustPrefixWithoutCustomLabel() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        // no data-custom-label attribute at all -> customLabel is null, label replacement is a no-op
        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"OLD-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getProject().getTrackerPrefix()).thenReturn("NEW");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("otherProject", "OLD-1", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "OLD-1", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "NEW-1", null)).thenReturn(true);

        UserConfigs configs = new UserConfigs();
        configs.put("TestableLinksRepairer", Map.of(ADJUST_WORK_ITEM_PREFIX, true));

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, configs);

        assertTrue(result.isSuccess());
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text
                && ((Text) t).getContent().contains("data-item-id=\"NEW-1\"")
                && !((Text) t).getContent().contains("data-custom-label")));
    }

    @Test
    void testRepairAdjustPrefixWithoutScope() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        // No data-scope provided -> first 'if' branch is skipped, else-if (adjust) is reached
        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"OLD-1\" data-custom-label=\"OLD-1\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.getString("fieldId")).thenReturn("content");
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);
        when(text.isPlain()).thenReturn(false);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getProject().getTrackerPrefix()).thenReturn("NEW");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "OLD-1", null)).thenReturn(false);
        when(polarionService.isWorkItemExists("elibrary", "NEW-1", null)).thenReturn(true);

        UserConfigs configs = new UserConfigs();
        configs.put("TestableLinksRepairer", Map.of(ADJUST_WORK_ITEM_PREFIX, true));

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, configs);

        assertTrue(result.isSuccess());
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text
                && ((Text) t).getContent().contains("data-item-id=\"NEW-1\"")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRepairAdjustPrefixAdjustedItemNotFoundFallsBackToGlobalSearch() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"OLD-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getProject().getTrackerPrefix()).thenReturn("NEW");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        // neither original nor adjusted id is found
        when(polarionService.isWorkItemExists(anyString(), anyString(), isNull())).thenReturn(false);

        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(0);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:OLD\\-1"), isNull(), eq(2))).thenReturn(searchResults);

        UserConfigs configs = new UserConfigs();
        configs.put("TestableLinksRepairer", Map.of(ADJUST_WORK_ITEM_PREFIX, true));

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, configs);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("does not exist")));
        verify(entity, never()).setValue(anyString(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRepairAdjustPrefixUnchangedIdFallsBackToGlobalSearch() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        // work item id has no dash -> replaceWorkItemPrefix returns it unchanged -> adjust branch is a no-op
        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"NODASH\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getProject().getTrackerPrefix()).thenReturn("NEW");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(anyString(), anyString(), isNull())).thenReturn(false);

        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(0);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:NODASH"), isNull(), eq(2))).thenReturn(searchResults);

        UserConfigs configs = new UserConfigs();
        configs.put("TestableLinksRepairer", Map.of(ADJUST_WORK_ITEM_PREFIX, true));

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, configs);

        assertFalse(result.isSuccess());
        // adjusted id equals original, so it must never be queried with a different id
        verify(polarionService, never()).isWorkItemExists(eq("elibrary"), eq("NEW-NODASH"), isNull());
        verify(entity, never()).setValue(anyString(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRepairAdjustPrefixDisabledSkipsAdjustment() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();

        String link = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"OLD-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.getString("link")).thenReturn(link);
        when(metaInfo.serialize()).thenReturn("serialized");

        Text text = mock(Text.class);
        when(text.getContent()).thenReturn(link);

        IWorkflowObject entity = mock(IWorkflowObject.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(anyString(), anyString(), isNull())).thenReturn(false);
        // adjusted id would exist, but config is disabled so it must not be used
        when(polarionService.isWorkItemExists("elibrary", "NEW-1", null)).thenReturn(true);

        IPObjectList<IWorkItem> searchResults = mock(IPObjectList.class);
        when(searchResults.size()).thenReturn(0);
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:OLD\\-1"), isNull(), eq(2))).thenReturn(searchResults);

        RepairResult result = repairer.repairLinksInHtml(text, entity, polarionService, metaInfo, new UserConfigs());

        assertFalse(result.isSuccess());
        verify(entity, never()).getProject();
        verify(entity, never()).setValue(anyString(), any());
    }

    // ---- replaceWorkItemPrefix tests ----

    @Test
    void testReplaceWorkItemPrefixReplacesPrefix() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        assertEquals("NEW-1", repairer.replaceWorkItemPrefix("OLD-1", "NEW"));
    }

    @Test
    void testReplaceWorkItemPrefixKeepsRemainderAfterFirstDash() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        assertEquals("NEW-12-3", repairer.replaceWorkItemPrefix("OLD-12-3", "NEW"));
    }

    @Test
    void testReplaceWorkItemPrefixNoDash() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        assertEquals("NODASH", repairer.replaceWorkItemPrefix("NODASH", "NEW"));
    }

    @Test
    void testReplaceWorkItemPrefixDashAtStart() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        assertEquals("-1", repairer.replaceWorkItemPrefix("-1", "NEW"));
    }

    @Test
    void testReplaceWorkItemPrefixDashAtEnd() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        assertEquals("OLD-", repairer.replaceWorkItemPrefix("OLD-", "NEW"));
    }

    @Test
    void testReplaceWorkItemPrefixEmptyString() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        assertEquals("", repairer.replaceWorkItemPrefix("", "NEW"));
    }
}
