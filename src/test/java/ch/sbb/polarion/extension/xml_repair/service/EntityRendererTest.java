package ch.sbb.polarion.extension.xml_repair.service;

import com.polarion.alm.shared.api.model.ModelObject;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EntityRendererTest {

    @Test
    void testRenderEntityWorkItem() {
        try (MockedConstruction<FieldRenderer> fieldRendererConstruction = mockConstruction(FieldRenderer.class,
                (mock, ctx) -> when(mock.render(any(), any())).thenReturn(Map.of("label", "lbl", "renderedValue", "val")))) {

            InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class);
            ITrackerService trackerService = mock(ITrackerService.class);

            EntityRenderer entityRenderer = new EntityRenderer(transaction, trackerService);

            ModelObject modelObject = mock(ModelObject.class);
            when(modelObject.getOldApi()).thenReturn(mock(IWorkItem.class));

            Map<String, Map<String, String>> result = entityRenderer.renderEntity(modelObject);

            assertEquals(2, result.size());
            assertTrue(result.containsKey(FieldRenderer.KEY_SELF_LINK));
            assertTrue(result.containsKey(IWorkflowObject.KEY_TYPE));

            FieldRenderer fieldRenderer = fieldRendererConstruction.constructed().getFirst();
            verify(fieldRenderer).render(modelObject, FieldRenderer.KEY_SELF_LINK);
            verify(fieldRenderer).render(modelObject, IWorkflowObject.KEY_TYPE);
        }
    }

    @Test
    void testRenderEntityModule() {
        try (MockedConstruction<FieldRenderer> fieldRendererConstruction = mockConstruction(FieldRenderer.class,
                (mock, ctx) -> when(mock.render(any(), any())).thenReturn(Map.of("label", "lbl", "renderedValue", "val")))) {

            InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class);
            ITrackerService trackerService = mock(ITrackerService.class);

            EntityRenderer entityRenderer = new EntityRenderer(transaction, trackerService);

            ModelObject modelObject = mock(ModelObject.class);
            when(modelObject.getOldApi()).thenReturn(mock(IModule.class));

            Map<String, Map<String, String>> result = entityRenderer.renderEntity(modelObject);

            assertEquals(2, result.size());
            assertTrue(result.containsKey(FieldRenderer.KEY_SELF_LINK));
            assertTrue(result.containsKey(IWorkflowObject.KEY_TYPE));

            FieldRenderer fieldRenderer = fieldRendererConstruction.constructed().getFirst();
            verify(fieldRenderer).render(modelObject, FieldRenderer.KEY_SELF_LINK);
            verify(fieldRenderer).render(modelObject, IWorkflowObject.KEY_TYPE);
        }
    }

    @Test
    void testRenderEntityBaselineCollection() {
        try (MockedConstruction<FieldRenderer> fieldRendererConstruction = mockConstruction(FieldRenderer.class,
                (mock, ctx) -> when(mock.render(any(), any())).thenReturn(Map.of("label", "lbl", "renderedValue", "val")))) {

            InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class);
            ITrackerService trackerService = mock(ITrackerService.class);

            EntityRenderer entityRenderer = new EntityRenderer(transaction, trackerService);

            ModelObject modelObject = mock(ModelObject.class);
            when(modelObject.getOldApi()).thenReturn(mock(IBaselineCollection.class));

            Map<String, Map<String, String>> result = entityRenderer.renderEntity(modelObject);

            assertEquals(1, result.size());
            assertTrue(result.containsKey(FieldRenderer.KEY_SELF_LINK));
            assertFalse(result.containsKey(IWorkflowObject.KEY_TYPE));

            FieldRenderer fieldRenderer = fieldRendererConstruction.constructed().getFirst();
            verify(fieldRenderer).render(modelObject, FieldRenderer.KEY_SELF_LINK);
            verify(fieldRenderer, never()).render(modelObject, IWorkflowObject.KEY_TYPE);
        }
    }

    @Test
    void testRenderEntityUnsupportedType() {
        try (MockedConstruction<FieldRenderer> ignored = mockConstruction(FieldRenderer.class)) {

            InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class);
            ITrackerService trackerService = mock(ITrackerService.class);

            EntityRenderer entityRenderer = new EntityRenderer(transaction, trackerService);

            ModelObject modelObject = mock(ModelObject.class);
            // Use a generic IUniqueObject that is none of the supported types
            com.polarion.alm.projects.model.IUniqueObject unsupported = mock(com.polarion.alm.projects.model.IUniqueObject.class);
            when(modelObject.getOldApi()).thenReturn(unsupported);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> entityRenderer.renderEntity(modelObject));
            assertTrue(exception.getMessage().contains("Unsupported object type"));
        }
    }
}
