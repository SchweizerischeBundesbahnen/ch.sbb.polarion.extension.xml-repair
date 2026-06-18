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
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import java.util.List;
import java.util.Map;

import static ch.sbb.polarion.extension.xml_repair.repairers.BaseLinksRepairer.ADJUST_WORK_ITEM_PREFIX;
import static ch.sbb.polarion.extension.xml_repair.repairers.BaseLinksRepairer.CONVERT_TO_PLAIN_TEXT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
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

    @Test
    void testScanLinksInHtmlValidLink() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-1", null)).thenReturn(true);

        String html = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-custom-label=\"EL-1\" data-option-id=\"long\"></span>";
        List<Issue> issues = repairer.scanLinksInHtml(html, entity, "content", polarionService);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanLinksInHtmlBrokenLinkWithScope() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        when(entity.getId()).thenReturn("WI-1");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("otherProject", "EL-1", null)).thenReturn(false);

        String html = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"otherProject\" data-option-id=\"long\"></span>";
        List<Issue> issues = repairer.scanLinksInHtml(html, entity, "content", polarionService);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().getDescription().contains("EL-1"));
        assertTrue(issues.getFirst().getDescription().contains("otherProject"));
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

    @Test
    void testScanLinksInHtmlScopeMatchesEntityProject() {
        TestableLinksRepairer repairer = new TestableLinksRepairer();
        IWorkItem entity = mock(IWorkItem.class);
        when(entity.getProjectId()).thenReturn("elibrary");
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists("elibrary", "EL-1", null)).thenReturn(true);

        String html = "<span class=\"polarion-rte-link\" data-type=\"workItem\" data-item-id=\"EL-1\" data-scope=\"elibrary\" data-option-id=\"long\"></span>";
        List<Issue> issues = repairer.scanLinksInHtml(html, entity, "content", polarionService);

        assertTrue(issues.isEmpty());
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
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"EL-1\""), isNull(), eq(2))).thenReturn(searchResults);

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
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"EL-1\""), isNull(), eq(2))).thenReturn(searchResults);

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
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"EL-1\""), isNull(), eq(2))).thenReturn(searchResults);

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
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"EL-1\""), isNull(), eq(2))).thenReturn(searchResults);

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
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"EL-1\""), isNull(), eq(2))).thenReturn(searchResults);

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
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"EL-1\""), isNull(), eq(2))).thenReturn(searchResults);

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
        verify(entity).setValue(eq("content"), argThat(t -> t instanceof Text
                && ((Text) t).getContent().contains("data-item-id=\"NEW-1\"")
                && !((Text) t).getContent().contains("data-item-id=\"OLD-1\"")
                && !((Text) t).getContent().contains("data-scope=\"otherProject\"")));
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
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"OLD-1\""), isNull(), eq(2))).thenReturn(searchResults);

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
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"NODASH\""), isNull(), eq(2))).thenReturn(searchResults);

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
        when(entity.getDataSvc().searchInstances(eq(IWorkItem.PROTO), eq("id:\"OLD-1\""), isNull(), eq(2))).thenReturn(searchResults);

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
