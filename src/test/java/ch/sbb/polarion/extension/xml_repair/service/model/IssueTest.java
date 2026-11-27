package ch.sbb.polarion.extension.xml_repair.service.model;

import ch.sbb.polarion.extension.xml_repair.repairers.BaseRepairer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueTest {

    @Test
    void testConstructor() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        BaseRepairer repairer = mock(BaseRepairer.class);
        when(repairer.getRepairerId()).thenReturn("TestRepairer");

        Issue issue = new Issue(metaInfo, repairer, "desc");

        assertEquals("serialized", issue.getMetaInfo());
        assertEquals("TestRepairer", issue.getRepairer());
        assertEquals("desc", issue.getDescription());
        assertNotNull(issue.getWarnings());
        assertTrue(issue.getWarnings().isEmpty());
    }

    @Test
    void testConstructorSetsRepairerInMetaInfo() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        BaseRepairer repairer = mock(BaseRepairer.class);
        when(repairer.getRepairerId()).thenReturn("TestRepairer");

        new Issue(metaInfo, repairer, "desc");

        verify(metaInfo).set(IssueMetaInfo.REPAIRER, "TestRepairer");
    }

    @Test
    void testGetMetaInfoDelegatesToSerialize() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("base64encoded");
        BaseRepairer repairer = mock(BaseRepairer.class);
        when(repairer.getRepairerId()).thenReturn("TestRepairer");

        Issue issue = new Issue(metaInfo, repairer, "desc");

        assertEquals("base64encoded", issue.getMetaInfo());
        verify(metaInfo).serialize();
    }

    @Test
    void testGetRawMetaInfo() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        BaseRepairer repairer = mock(BaseRepairer.class);
        when(repairer.getRepairerId()).thenReturn("TestRepairer");

        Issue issue = new Issue(metaInfo, repairer, "desc");

        assertSame(metaInfo, issue.getRawMetaInfo());
    }

    @Test
    void testAddWarningKeepsOrder() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        BaseRepairer repairer = mock(BaseRepairer.class);
        when(repairer.getRepairerId()).thenReturn("TestRepairer");

        Issue issue = new Issue(metaInfo, repairer, "desc");

        issue.addWarning("warning1");
        issue.addWarning("warning2");

        assertEquals(List.of("warning1", "warning2"), issue.getWarnings());
    }

    @Test
    void testWarningsViewIsUnmodifiable() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        BaseRepairer repairer = mock(BaseRepairer.class);
        when(repairer.getRepairerId()).thenReturn("TestRepairer");

        Issue issue = new Issue(metaInfo, repairer, "desc");
        List<String> warnings = issue.getWarnings();
        issue.addWarning("test");

        assertThrows(UnsupportedOperationException.class, () -> warnings.add("other"));
        assertEquals(List.of("test"), warnings);
    }
}
