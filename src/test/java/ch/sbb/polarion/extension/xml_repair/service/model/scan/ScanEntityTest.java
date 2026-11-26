package ch.sbb.polarion.extension.xml_repair.service.model.scan;

import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import com.polarion.alm.tracker.internal.model.UniqueObject;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScanEntityTest {

    @Test
    void testAddIssues() {
        ScanEntity entity = createScanEntity("WI-1");
        Issue issue = mock(Issue.class);

        entity.addIssues(List.of(issue));

        assertEquals(List.of(issue), entity.getIssues());
    }

    @Test
    void testIssuesViewIsUnmodifiable() {
        ScanEntity entity = createScanEntity("WI-1");
        Issue issue = mock(Issue.class);
        List<Issue> issues = entity.getIssues();

        assertThrows(UnsupportedOperationException.class, () -> issues.add(issue));
        assertTrue(entity.getIssues().isEmpty());
    }

    @Test
    void testPutFields() {
        ScanEntity entity = createScanEntity("WI-1");
        Map<String, Map<String, String>> fields = Map.of("type", Map.of("label", "Type"));

        entity.putFields(fields);

        assertEquals(fields, entity.getFields());
    }

    @Test
    void testFieldsViewIsUnmodifiable() {
        ScanEntity entity = createScanEntity("WI-1");
        Map<String, Map<String, String>> fields = entity.getFields();
        Map<String, String> value = Map.of();

        assertThrows(UnsupportedOperationException.class, () -> fields.put("type", value));
        assertTrue(entity.getFields().isEmpty());
    }

    @Test
    void testAddSubitem() {
        ScanEntity entity = createScanEntity("COLL-1");
        ScanEntity subitem = createScanEntity("DOC-1");

        entity.addSubitem(subitem);

        assertEquals(List.of(subitem), entity.getSubitems());
    }

    @Test
    void testSubitemsViewIsUnmodifiable() {
        ScanEntity entity = createScanEntity("COLL-1");
        ScanEntity subitem = createScanEntity("DOC-1");
        List<ScanEntity> subitems = entity.getSubitems();

        assertThrows(UnsupportedOperationException.class, () -> subitems.add(subitem));
        assertTrue(entity.getSubitems().isEmpty());
    }

    @Test
    void testAddWarningKeepsOrderAndDeduplicates() {
        ScanEntity entity = createScanEntity("WI-1");

        entity.addWarning("first");
        entity.addWarning("second");
        entity.addWarning("first");

        assertEquals(List.of("first", "second"), List.copyOf(entity.getWarnings()));
    }

    @Test
    void testWarningsViewIsUnmodifiable() {
        ScanEntity entity = createScanEntity("WI-1");
        Set<String> warnings = entity.getWarnings();

        assertThrows(UnsupportedOperationException.class, () -> warnings.add("other"));
        assertTrue(entity.getWarnings().isEmpty());
    }

    static ScanEntity createScanEntity(String entityId) {
        var wi = mock(UniqueObject.class, withSettings()
                .extraInterfaces(IWorkItem.class, IWorkflowObject.class)
                .defaultAnswer(RETURNS_DEEP_STUBS));
        when(wi.getPrototype().getName()).thenReturn(IWorkItem.PROTO);
        when(wi.getProjectId()).thenReturn("proj");
        when(wi.getId()).thenReturn(entityId);
        return ScanEntity.from(wi);
    }
}
