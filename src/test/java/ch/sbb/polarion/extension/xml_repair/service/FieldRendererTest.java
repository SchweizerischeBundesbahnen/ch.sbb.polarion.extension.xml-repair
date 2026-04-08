package ch.sbb.polarion.extension.xml_repair.service;

import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.server.rt.parts.Renderer;
import com.polarion.alm.shared.api.model.ModelObject;
import com.polarion.alm.shared.api.model.wi.WorkItemRenderer;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.shared.api.utils.html.HtmlContentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.RichTextRenderTarget;
import com.polarion.alm.shared.rt.RichTextRenderingContext;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FieldRendererTest {

    private FieldRenderer createFieldRenderer(InternalReadOnlyTransaction transaction,
                                              ITrackerService trackerService,
                                              RichTextRenderTarget renderTarget) throws Exception {
        FieldRenderer fieldRenderer = mock(FieldRenderer.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        RichTextRenderingContext renderingContext = new ObjenesisStd().newInstance(RichTextRenderingContext.class);

        setField(fieldRenderer, "transaction", transaction);
        setField(fieldRenderer, "trackerService", trackerService);
        setField(fieldRenderer, "renderTarget", renderTarget);
        setField(fieldRenderer, "renderingContext", renderingContext);
        setField(fieldRenderer, "logger", mock(Logger.class));

        return fieldRenderer;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = FieldRenderer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private HtmlFragmentBuilder setupFragmentBuilder(RichTextRenderTarget renderTarget, String returnValue) {
        HtmlFragmentBuilder fragmentBuilder = mock(HtmlFragmentBuilder.class);
        when(fragmentBuilder.toString()).thenReturn(returnValue);
        when(renderTarget.selectBuilderTarget(any())).thenReturn(fragmentBuilder);
        return fragmentBuilder;
    }

    @Test
    void testRenderSelfLinkWithWorkItemRenderer() throws Exception {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        ITrackerService trackerService = mock(ITrackerService.class);
        RichTextRenderTarget renderTarget = mock(RichTextRenderTarget.class);

        HtmlFragmentBuilder fragmentBuilder = setupFragmentBuilder(renderTarget,"<rendered>");

        ModelObject modelObject = mock(ModelObject.class, RETURNS_DEEP_STUBS);
        IWorkItem workItem = mock(IWorkItem.class);
        when(modelObject.getOldApi()).thenReturn(workItem);
        when(workItem.getFieldLabel(FieldRenderer.KEY_SELF_LINK)).thenReturn("Self Link");

        // Get the deep stub intermediate and override withIcon to return a WorkItemRenderer
        var withLinksResult = modelObject.render().withLinks(true);
        WorkItemRenderer workItemRenderer = mock(WorkItemRenderer.class);
        when(workItemRenderer.withTitle(true)).thenReturn(workItemRenderer);
        doReturn(workItemRenderer).when(withLinksResult).withIcon(true);

        FieldRenderer fieldRenderer = createFieldRenderer(transaction, trackerService, renderTarget);
        Map<String, String> result = fieldRenderer.render(modelObject, FieldRenderer.KEY_SELF_LINK);

        assertEquals("Self Link", result.get("label"));
        assertEquals("<rendered>", result.get("renderedValue"));
        verify(workItemRenderer).withTitle(true);
        verify(workItemRenderer).htmlTo(fragmentBuilder);
    }

    @Test
    void testRenderSelfLinkWithNonWorkItemRenderer() throws Exception {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        ITrackerService trackerService = mock(ITrackerService.class);
        RichTextRenderTarget renderTarget = mock(RichTextRenderTarget.class);

        setupFragmentBuilder(renderTarget,"<rendered>");

        ModelObject modelObject = mock(ModelObject.class, RETURNS_DEEP_STUBS);
        IWorkItem workItem = mock(IWorkItem.class);
        when(modelObject.getOldApi()).thenReturn(workItem);
        when(workItem.getFieldLabel(FieldRenderer.KEY_SELF_LINK)).thenReturn("Self Link");

        // Default RETURNS_DEEP_STUBS returns a generic Renderer (not WorkItemRenderer)

        FieldRenderer fieldRenderer = createFieldRenderer(transaction, trackerService, renderTarget);
        Map<String, String> result = fieldRenderer.render(modelObject, FieldRenderer.KEY_SELF_LINK);

        assertEquals("Self Link", result.get("label"));
        assertEquals("<rendered>", result.get("renderedValue"));
    }

    @Test
    void testRenderRegularField() throws Exception {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        ITrackerService trackerService = mock(ITrackerService.class);
        RichTextRenderTarget renderTarget = mock(RichTextRenderTarget.class);

        HtmlFragmentBuilder fragmentBuilder = setupFragmentBuilder(renderTarget,"<field-html>");
        HtmlContentBuilder htmlContentBuilder = mock(HtmlContentBuilder.class);
        when(fragmentBuilder.html("")).thenReturn(htmlContentBuilder);

        ModelObject modelObject = mock(ModelObject.class, RETURNS_DEEP_STUBS);
        IWorkItem workItem = mock(IWorkItem.class);
        when(modelObject.getOldApi()).thenReturn(workItem);
        when(workItem.getValue("description")).thenReturn("some value");
        when(workItem.getFieldLabel("description")).thenReturn("Description");

        try (MockedConstruction<Renderer> rendererConstruction = mockConstruction(Renderer.class)) {
            FieldRenderer fieldRenderer = createFieldRenderer(transaction, trackerService, renderTarget);
            Map<String, String> result = fieldRenderer.render(modelObject, "description");

            assertEquals("Description", result.get("label"));
            assertEquals("<field-html>", result.get("renderedValue"));
            assertEquals(1, rendererConstruction.constructed().size());
            verify(rendererConstruction.constructed().getFirst()).renderValue("some value");
        }
    }

    @Test
    void testRenderLinkedWorkItemsField() throws Exception {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        ITrackerService trackerService = mock(ITrackerService.class);
        RichTextRenderTarget renderTarget = mock(RichTextRenderTarget.class);

        HtmlFragmentBuilder fragmentBuilder = setupFragmentBuilder(renderTarget,"<linked>");
        HtmlContentBuilder htmlContentBuilder = mock(HtmlContentBuilder.class);
        when(fragmentBuilder.html("")).thenReturn(htmlContentBuilder);

        ModelObject modelObject = mock(ModelObject.class, RETURNS_DEEP_STUBS);
        IWorkItem workItem = mock(IWorkItem.class);
        when(modelObject.getOldApi()).thenReturn(workItem);
        when(workItem.getValue(IWorkItem.KEY_LINKED_WORK_ITEMS)).thenReturn("linked-value");
        when(workItem.getFieldLabel(IWorkItem.KEY_LINKED_WORK_ITEMS)).thenReturn("Linked WIs");

        try (MockedConstruction<Renderer> rendererConstruction = mockConstruction(Renderer.class)) {
            FieldRenderer fieldRenderer = createFieldRenderer(transaction, trackerService, renderTarget);
            Map<String, String> result = fieldRenderer.render(modelObject, IWorkItem.KEY_LINKED_WORK_ITEMS);

            assertEquals("Linked WIs", result.get("label"));
            assertEquals("<linked>", result.get("renderedValue"));
            assertEquals(1, rendererConstruction.constructed().size());
            verify(rendererConstruction.constructed().getFirst()).renderValue("linked-value");
        }
    }

    @Test
    void testRenderEntityNotWorkItemCreatesTemporary() throws Exception {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        ITrackerService trackerService = mock(ITrackerService.class);
        RichTextRenderTarget renderTarget = mock(RichTextRenderTarget.class);

        IWorkItem tempWorkItem = mock(IWorkItem.class);
        IProject project = mock(IProject.class);

        com.polarion.alm.projects.model.IUniqueObject nonWiEntity = mock(com.polarion.alm.projects.model.IUniqueObject.class);
        when(nonWiEntity.getProject()).thenReturn(project);
        when(nonWiEntity.getValue("someField")).thenReturn("val");
        when(nonWiEntity.getFieldLabel("someField")).thenReturn("Some Field");
        when(trackerService.createWorkItem(project)).thenReturn(tempWorkItem);

        HtmlFragmentBuilder fragmentBuilder = setupFragmentBuilder(renderTarget,"<non-wi>");
        HtmlContentBuilder htmlContentBuilder = mock(HtmlContentBuilder.class);
        when(fragmentBuilder.html("")).thenReturn(htmlContentBuilder);

        ModelObject modelObject = mock(ModelObject.class, RETURNS_DEEP_STUBS);
        when(modelObject.getOldApi()).thenReturn(nonWiEntity);

        try (MockedConstruction<Renderer> ignored = mockConstruction(Renderer.class)) {
            FieldRenderer fieldRenderer = createFieldRenderer(transaction, trackerService, renderTarget);
            Map<String, String> result = fieldRenderer.render(modelObject, "someField");

            assertEquals("Some Field", result.get("label"));
            verify(trackerService).createWorkItem(project);
        }
    }

    @Test
    void testRenderExceptionInsideTryReturnsFallback() throws Exception {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        ITrackerService trackerService = mock(ITrackerService.class);
        RichTextRenderTarget renderTarget = mock(RichTextRenderTarget.class);

        HtmlFragmentBuilder fragmentBuilder = setupFragmentBuilder(renderTarget,"<unused>");
        HtmlContentBuilder htmlContentBuilder = mock(HtmlContentBuilder.class);
        when(fragmentBuilder.html("")).thenReturn(htmlContentBuilder);

        ModelObject modelObject = mock(ModelObject.class, RETURNS_DEEP_STUBS);
        IWorkItem workItem = mock(IWorkItem.class);
        when(modelObject.getOldApi()).thenReturn(workItem);

        // Trigger exception inside the try block (line 64-66) via Renderer construction
        try (MockedConstruction<Renderer> ignored = mockConstruction(Renderer.class,
                (mock, ctx) -> doThrow(new RuntimeException("render error")).when(mock).renderValue(any()))) {
            FieldRenderer fieldRenderer = createFieldRenderer(transaction, trackerService, renderTarget);
            Map<String, String> result = fieldRenderer.render(modelObject, "description");

            assertEquals("description", result.get("label"));
            assertEquals("n/a", result.get("renderedValue"));
        }
    }

    @Test
    void testRenderExceptionInSelfLinkReturnsFallback() throws Exception {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class, RETURNS_DEEP_STUBS);
        ITrackerService trackerService = mock(ITrackerService.class);
        RichTextRenderTarget renderTarget = mock(RichTextRenderTarget.class);

        setupFragmentBuilder(renderTarget,"<unused>");

        ModelObject modelObject = mock(ModelObject.class, RETURNS_DEEP_STUBS);
        IWorkItem workItem = mock(IWorkItem.class);
        when(modelObject.getOldApi()).thenReturn(workItem);
        // Make the render chain throw inside the try block
        when(modelObject.render()).thenThrow(new RuntimeException("render chain error"));

        FieldRenderer fieldRenderer = createFieldRenderer(transaction, trackerService, renderTarget);
        Map<String, String> result = fieldRenderer.render(modelObject, FieldRenderer.KEY_SELF_LINK);

        assertEquals(FieldRenderer.KEY_SELF_LINK, result.get("label"));
        assertEquals("n/a", result.get("renderedValue"));
    }

    @Test
    void testKeySelfLinkConstant() {
        assertEquals("$_self", FieldRenderer.KEY_SELF_LINK);
    }
}
