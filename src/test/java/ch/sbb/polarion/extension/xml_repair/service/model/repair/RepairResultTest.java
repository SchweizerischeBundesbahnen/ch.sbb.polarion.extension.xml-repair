package ch.sbb.polarion.extension.xml_repair.service.model.repair;

import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import com.polarion.subterra.base.SubterraURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

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
    void testAddWarning() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairResult result = new RepairResult(metaInfo, true);
        result.addWarning("added later");

        assertEquals(Set.of("added later"), result.getWarnings());
    }

    @Test
    void testWarningsViewIsUnmodifiable() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairResult result = new RepairResult(metaInfo, true, "warn");
        Set<String> warnings = result.getWarnings();

        assertThrows(UnsupportedOperationException.class, () -> warnings.add("other"));
        assertEquals(Set.of("warn"), result.getWarnings());
    }

    // SubterraURI cannot be loaded or mocked here (its static initializer fails), so these tests use null elements.
    @Test
    void testAddStaleCacheUris() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairResult result = new RepairResult(metaInfo, true);
        result.addStaleCacheUris(Collections.singleton(null));

        assertEquals(1, result.getStaleCacheUris().size());
        assertTrue(result.getStaleCacheUris().contains(null));
    }

    @Test
    void testStaleCacheUrisViewIsUnmodifiable() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");

        RepairResult result = new RepairResult(metaInfo, true);
        Set<SubterraURI> staleCacheUris = result.getStaleCacheUris();

        assertThrows(UnsupportedOperationException.class, () -> staleCacheUris.add(null));
        assertTrue(result.getStaleCacheUris().isEmpty());
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
