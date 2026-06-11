package ch.sbb.polarion.extension.xml_repair.service;

import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.server.rt.parts.Renderer;
import com.polarion.alm.shared.api.model.ModelObject;
import com.polarion.alm.shared.api.model.wi.WorkItemRenderer;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.RichTextRenderTarget;
import com.polarion.alm.shared.rt.RichTextRenderingContext;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.ui.shared.FieldRenderType;
import com.polarion.core.util.logging.Logger;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class FieldRenderer {

    public static final String KEY_SELF_LINK = "$_self";
    private static final String KEY_LABEL = "label";

    private final Logger logger = Logger.getLogger(FieldRenderer.class);

    private final ITrackerService trackerService;
    private final InternalReadOnlyTransaction transaction;
    private final RichTextRenderTarget renderTarget;
    private final RichTextRenderingContext renderingContext;

    public FieldRenderer(InternalReadOnlyTransaction transaction, ITrackerService trackerService) {
        this.transaction = transaction;
        this.trackerService = trackerService;
        this.renderTarget = RichTextRenderTarget.PDF_EXPORT;

        renderingContext = new RichTextRenderingContext(transaction.context(), renderTarget);
        renderingContext.setTransaction(transaction);
        renderingContext.setDocumentOutlineNumber(true);
    }

    public Map<String, String> render(@NotNull ModelObject object, @NotNull String fieldId) {

        IUniqueObject entity = (IUniqueObject) object.getOldApi();

        // create a temporary work item instance just to satisfy needs of Renderer constructor
        // this instance won't be saved because we do not call save() on it
        IWorkItem workItem = entity instanceof IWorkItem iWorkItem ? iWorkItem : trackerService.createWorkItem(entity.getProject());

        renderingContext.setMainObjectReference(object.getReference()); //optional but sometimes it's important (e.g. links rendering inside rich texts)

        HtmlFragmentBuilder fragmentBuilder = renderTarget.selectBuilderTarget(transaction.context().createHtmlFragmentBuilderFor());

        try {
            if (KEY_SELF_LINK.equals(fieldId)) {
                // render entity link
                com.polarion.alm.shared.api.model.Renderer<? extends com.polarion.alm.shared.api.model.Renderer<?>> renderer = object.render()
                        .withLinks(true)
                        .withIcon(true);
                if (renderer instanceof WorkItemRenderer workItemRenderer) {
                    renderer = workItemRenderer.withTitle(true);
                }
                renderer.htmlTo(fragmentBuilder);
            } else {
                Renderer renderer = new Renderer(fragmentBuilder.html(""), renderingContext, workItem,
                        IWorkItem.KEY_LINKED_WORK_ITEMS.equals(fieldId) ? FieldRenderType.LINKED_WI_IN_DOC : FieldRenderType.IMGTXT, KEY_LABEL, "fieldId", false);
                renderer.renderValue(entity.getValue(fieldId));
            }
            return Map.of(KEY_LABEL, entity.getFieldLabel(fieldId), "renderedValue", fragmentBuilder.toString());
        } catch (Exception e) {
            logger.debug("Error while rendering field '%s' of entity %s %s".formatted(fieldId, entity.getClass().getSimpleName(), object.getReference()), e);
            return Map.of(KEY_LABEL, fieldId, "renderedValue", "n/a");
        }
    }

}
