package ch.sbb.polarion.extension.xml_repair.service.model.repair;

import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairResultTest {

    @Test
    void testConstructorSuccess() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized123");

        RepairResult result = new RepairResult(metaInfo, true);

        assertTrue(result.isSuccess());
        assertEquals("serialized123", result.getIssueMetaInfo());
        assertSame(metaInfo, result.getRawIssueMetaInfo());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void testConstructorFailure() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairResult result = new RepairResult(metaInfo, false);

        assertFalse(result.isSuccess());
    }

    @Test
    void testConstructorWithWarnings() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairResult result = new RepairResult(metaInfo, false, "warn1", "warn2");

        assertEquals(2, result.getWarnings().size());
        assertTrue(result.getWarnings().contains("warn1"));
        assertTrue(result.getWarnings().contains("warn2"));
    }

    @Test
    void testConstructorWithNoVarargs() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairResult result = new RepairResult(metaInfo, true);

        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void testSetSuccess() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairResult result = new RepairResult(metaInfo, false);
        assertFalse(result.isSuccess());

        result.setSuccess(true);
        assertTrue(result.isSuccess());
    }

    @Test
    void testWarningsSetIsMutable() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairResult result = new RepairResult(metaInfo, true);
        result.getWarnings().add("added later");

        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().contains("added later"));
    }

    @Test
    void testWarningsDeduplicates() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairResult result = new RepairResult(metaInfo, true, "dup", "dup");

        assertEquals(1, result.getWarnings().size());
    }

    @Test
    void testIssueMetaInfoIsSerialized() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("base64data");

        RepairResult result = new RepairResult(metaInfo, true);

        assertEquals("base64data", result.getIssueMetaInfo());
        verify(metaInfo).serialize();
    }

    @Test
    void testRawIssueMetaInfoPreservesOriginal() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairResult result = new RepairResult(metaInfo, true);

        assertSame(metaInfo, result.getRawIssueMetaInfo());
    }
}
