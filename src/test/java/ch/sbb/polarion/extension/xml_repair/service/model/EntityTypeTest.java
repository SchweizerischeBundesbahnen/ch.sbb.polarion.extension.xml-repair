package ch.sbb.polarion.extension.xml_repair.service.model;

import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;
import com.polarion.platform.persistence.model.IPrototype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeast;

class EntityTypeTest {

    @Test
    void testEnumValues() {
        EntityType[] values = EntityType.values();
        assertEquals(3, values.length);
        assertEquals(EntityType.COLLECTION, EntityType.valueOf("COLLECTION"));
        assertEquals(EntityType.DOCUMENT, EntityType.valueOf("DOCUMENT"));
        assertEquals(EntityType.WORKITEM, EntityType.valueOf("WORKITEM"));
    }

    @Test
    void testFromPrototypeWithCollectionPrototype() {
        IPrototype collectionPrototype = mock(IPrototype.class);
        when(collectionPrototype.getName()).thenReturn(IBaselineCollection.PROTO);

        EntityType result = EntityType.fromPrototype(collectionPrototype);

        assertEquals(EntityType.COLLECTION, result);
        verify(collectionPrototype).getName();
    }

    @Test
    void testFromPrototypeWithModulePrototype() {
        IPrototype modulePrototype = mock(IPrototype.class);
        when(modulePrototype.getName()).thenReturn(IModule.PROTO);

        EntityType result = EntityType.fromPrototype(modulePrototype);

        assertEquals(EntityType.DOCUMENT, result);
        verify(modulePrototype).getName();
    }

    @Test
    void testFromPrototypeWithWorkItemPrototype() {
        IPrototype workItemPrototype = mock(IPrototype.class);
        when(workItemPrototype.getName()).thenReturn(IWorkItem.PROTO);

        EntityType result = EntityType.fromPrototype(workItemPrototype);

        assertEquals(EntityType.WORKITEM, result);
        verify(workItemPrototype).getName();
    }

    @Test
    void testFromPrototypeWithUnknownPrototype() {
        IPrototype unknownPrototype = mock(IPrototype.class);
        String unknownProtoName = "UnknownPrototype";
        when(unknownPrototype.getName()).thenReturn(unknownProtoName);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> EntityType.fromPrototype(unknownPrototype));

        assertEquals("Unknown entity prototype: " + unknownProtoName, exception.getMessage());
        verify(unknownPrototype, atLeast(1)).getName();
    }

    @Test
    void testFromPrototypeWithNullPrototypeName() {
        IPrototype nullPrototype = mock(IPrototype.class);
        when(nullPrototype.getName()).thenReturn(null);

        NullPointerException exception = assertThrows(NullPointerException.class,
            () -> EntityType.fromPrototype(nullPrototype));

        assertNotNull(exception);
        verify(nullPrototype, atLeast(1)).getName();
    }

    @Test
    void testFromPrototypeWithEmptyPrototypeName() {
        IPrototype emptyPrototype = mock(IPrototype.class);
        when(emptyPrototype.getName()).thenReturn("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> EntityType.fromPrototype(emptyPrototype));

        assertEquals("Unknown entity prototype: ", exception.getMessage());
        verify(emptyPrototype, atLeast(1)).getName();
    }
}
