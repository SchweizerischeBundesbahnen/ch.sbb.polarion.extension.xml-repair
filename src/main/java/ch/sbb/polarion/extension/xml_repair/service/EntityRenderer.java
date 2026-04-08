package ch.sbb.polarion.extension.xml_repair.service;

import com.polarion.alm.shared.api.model.ModelObject;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EntityRenderer {
    private final FieldRenderer fieldRenderer;

    public EntityRenderer(InternalReadOnlyTransaction transaction, ITrackerService trackerService) {
        fieldRenderer = new FieldRenderer(transaction, trackerService);
    }

    public Map<String, Map<String, String>> renderEntity(ModelObject object) {
        List<String> fieldIds = switch (object.getOldApi()) {
            case IWorkItem ignored -> List.of(FieldRenderer.KEY_SELF_LINK, IWorkflowObject.KEY_TYPE);
            case IModule ignored -> List.of(FieldRenderer.KEY_SELF_LINK, IWorkflowObject.KEY_TYPE);
            case IBaselineCollection ignored -> List.of(FieldRenderer.KEY_SELF_LINK);
            default ->
                    throw new IllegalArgumentException("Unsupported object type: " + object.getOldApi().getClass().getName());
        };

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (String fieldId : fieldIds) {
            result.put(fieldId, fieldRenderer.render(object, fieldId));
        }
        return result;
    }

}
