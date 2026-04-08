package ch.sbb.polarion.extension.xml_repair.service.model;

import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueMetaInfoTest {

    @Test
    void testCreateFromWorkItem() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("elibrary");
        when(workItem.getId()).thenReturn("EL-123");

        IssueMetaInfo metaInfo = IssueMetaInfo.create(workItem);

        assertEquals("elibrary", metaInfo.getString(IssueMetaInfo.PROJECT_ID));
        assertEquals("EL-123", metaInfo.getString(IssueMetaInfo.ID));
        assertNull(metaInfo.getString(IssueMetaInfo.MODULE_PATH));
    }

    @Test
    void testCreateFromModule() {
        IModule module = mock(IModule.class);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getRelativePath()).thenReturn("Specification/MyDoc");

        IssueMetaInfo metaInfo = IssueMetaInfo.create(module);

        assertEquals("elibrary", metaInfo.getString(IssueMetaInfo.PROJECT_ID));
        assertEquals("Specification/MyDoc", metaInfo.getString(IssueMetaInfo.MODULE_PATH));
        assertNull(metaInfo.getString(IssueMetaInfo.ID));
    }

    @Test
    void testCreateFromUniqueObjectRoutesToWorkItem() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("proj");
        when(workItem.getId()).thenReturn("WI-1");

        IssueMetaInfo metaInfo = IssueMetaInfo.create((IUniqueObject) workItem);

        assertEquals("proj", metaInfo.getString(IssueMetaInfo.PROJECT_ID));
        assertEquals("WI-1", metaInfo.getString(IssueMetaInfo.ID));
    }

    @Test
    void testCreateFromUniqueObjectRoutesToModule() {
        IModule module = mock(IModule.class);
        when(module.getProjectId()).thenReturn("proj");
        when(module.getRelativePath()).thenReturn("path/doc");

        IssueMetaInfo metaInfo = IssueMetaInfo.create((IUniqueObject) module);

        assertEquals("proj", metaInfo.getString(IssueMetaInfo.PROJECT_ID));
        assertEquals("path/doc", metaInfo.getString(IssueMetaInfo.MODULE_PATH));
    }

    @Test
    void testCreateFromUniqueObjectThrowsForUnknownType() {
        IUniqueObject unknown = mock(IUniqueObject.class);

        assertThrows(IllegalArgumentException.class, () -> IssueMetaInfo.create(unknown));
    }

    @Test
    void testSetAndGet() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("proj");
        when(workItem.getId()).thenReturn("WI-1");

        IssueMetaInfo metaInfo = IssueMetaInfo.create(workItem);
        metaInfo.set("customKey", "customValue");

        assertEquals("customValue", metaInfo.get("customKey"));
        assertEquals("customValue", metaInfo.getString("customKey"));
    }

    @Test
    void testSetReturnsSelfForFluency() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("proj");
        when(workItem.getId()).thenReturn("WI-1");

        IssueMetaInfo metaInfo = IssueMetaInfo.create(workItem);
        IssueMetaInfo result = metaInfo.set("key", "value");

        assertSame(metaInfo, result);
    }

    @Test
    void testSetChaining() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("proj");
        when(workItem.getId()).thenReturn("WI-1");

        IssueMetaInfo metaInfo = IssueMetaInfo.create(workItem)
                .set(IssueMetaInfo.REPAIRER, "SomeRepairer")
                .set("extra", "data");

        assertEquals("SomeRepairer", metaInfo.getString(IssueMetaInfo.REPAIRER));
        assertEquals("data", metaInfo.getString("extra"));
    }

    @Test
    void testGetReturnsNullForMissingKey() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("proj");
        when(workItem.getId()).thenReturn("WI-1");

        IssueMetaInfo metaInfo = IssueMetaInfo.create(workItem);

        assertNull(metaInfo.get("nonexistent"));
        assertNull(metaInfo.getString("nonexistent"));
    }

    @Test
    void testGetReturnsNonStringValue() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("proj");
        when(workItem.getId()).thenReturn("WI-1");

        IssueMetaInfo metaInfo = IssueMetaInfo.create(workItem);
        metaInfo.set("number", 42);

        assertEquals(42, metaInfo.get("number"));
    }

    @Test
    void testSerializeAndFromStringRoundtrip() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("elibrary");
        when(workItem.getId()).thenReturn("EL-456");

        IssueMetaInfo original = IssueMetaInfo.create(workItem);
        original.set(IssueMetaInfo.REPAIRER, "TestRepairer");

        String serialized = original.serialize();
        assertNotNull(serialized);
        assertFalse(serialized.isEmpty());

        IssueMetaInfo deserialized = IssueMetaInfo.fromString(serialized);

        assertEquals("elibrary", deserialized.getString(IssueMetaInfo.PROJECT_ID));
        assertEquals("EL-456", deserialized.getString(IssueMetaInfo.ID));
        assertEquals("TestRepairer", deserialized.getString(IssueMetaInfo.REPAIRER));
    }

    @Test
    void testSerializeAndFromStringRoundtripForModule() {
        IModule module = mock(IModule.class);
        when(module.getProjectId()).thenReturn("proj");
        when(module.getRelativePath()).thenReturn("Spec/Doc");

        IssueMetaInfo original = IssueMetaInfo.create(module);
        String serialized = original.serialize();

        IssueMetaInfo deserialized = IssueMetaInfo.fromString(serialized);

        assertEquals("proj", deserialized.getString(IssueMetaInfo.PROJECT_ID));
        assertEquals("Spec/Doc", deserialized.getString(IssueMetaInfo.MODULE_PATH));
        assertNull(deserialized.getString(IssueMetaInfo.ID));
    }

    @Test
    void testSerializedValueDiffersForSameWorkItem() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("proj");
        when(workItem.getId()).thenReturn("WI-1");

        String serialized1 = IssueMetaInfo.create(workItem).serialize();
        String serialized2 = IssueMetaInfo.create(workItem).serialize();

        assertNotEquals(serialized1, serialized2);
    }

    @Test
    void testSerializedValueDiffersForSameModule() {
        IModule module = mock(IModule.class);
        when(module.getProjectId()).thenReturn("proj");
        when(module.getRelativePath()).thenReturn("Spec/Doc");

        String serialized1 = IssueMetaInfo.create(module).serialize();
        String serialized2 = IssueMetaInfo.create(module).serialize();

        assertNotEquals(serialized1, serialized2);
    }

    @Test
    void testSetOverwritesExistingValue() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("proj");
        when(workItem.getId()).thenReturn("WI-1");

        IssueMetaInfo metaInfo = IssueMetaInfo.create(workItem);
        metaInfo.set(IssueMetaInfo.PROJECT_ID, "newProject");

        assertEquals("newProject", metaInfo.getString(IssueMetaInfo.PROJECT_ID));
    }
}
