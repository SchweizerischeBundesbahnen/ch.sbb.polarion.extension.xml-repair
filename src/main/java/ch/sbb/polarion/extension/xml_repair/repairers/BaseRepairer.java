package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.data.model.IType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public abstract class BaseRepairer implements IRepairer {

    protected static final String FIELD_ID = "fieldId";
    protected static final String ISSUE_DESCRIPTION = "issueDescription";
    protected static final String TYPE_HEADING = "heading";
    private static final String CACHE_ALL_FIELDS_KEY_TEMPLATE = "ALL_FIELDS_%s_%s_%s_%s_%s";

    @Override
    public RepairResult repair(IUniqueObject entity, RepairContext context) {
        RepairResult repairResult;
        if (entity instanceof IModule module) {
            String revision = module.getRevision();
            if (revision != null) {
                return new RepairResult(context.issueMetaInfo(), false, "Cannot fix '%s' rev.%s - HEAD revision entity may be repaired only".formatted(module.getModuleName(), revision));
            } else {
                repairResult = repair(module, context);
            }
        } else {
            repairResult = repair((IWorkflowObject) entity, context);
        }
        if (repairResult.isSuccess()) {
            entity.save();
        }
        return repairResult;
    }

    protected @NotNull RepairResult repair(IModule entity, RepairContext context) {
        return repair((IWorkflowObject) entity, context);
    }

    @SuppressWarnings("unused")
    protected @NotNull RepairResult repair(IWorkflowObject entity, RepairContext context) {
        throw new IllegalArgumentException("Repairer '%s' does not support entity of type '%s'".formatted(
                getClass().getSimpleName(), entity.getClass().getSimpleName()));
    }

    @Override
    public List<Issue> scan(IUniqueObject entity, ScanContext context) {
        if (entity instanceof IModule module) {
            return scan(module, context);
        } else {
            return scan((IWorkflowObject) entity, context);
        }
    }

    protected List<Issue> scan(IModule entity, ScanContext context) {
        return scan((IWorkflowObject) entity, context);
    }

    @SuppressWarnings("unused")
    public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
        throw new IllegalArgumentException("Repairer '%s' does not support entity of type '%s'".formatted(
                getClass().getSimpleName(), entity.getClass().getSimpleName()));
    }

    protected Stream<IWorkItem> streamModuleWorkItems(IModule module) {
        return module.getContainedWorkItems().stream().filter(w -> !w.isUnresolvable() && w.getType() != null && !w.getType().getId().equals(TYPE_HEADING));
    }

    Set<FieldMetadata> getAllFieldsUsingCache(@NotNull IContext context, @NotNull String proto, @NotNull IContextId contextId, @NotNull String typeId, boolean compareTypeClass, @NotNull IType... fieldTypes) {
        String key = CACHE_ALL_FIELDS_KEY_TEMPLATE.formatted(proto, contextId, typeId, compareTypeClass, Arrays.toString(fieldTypes));
        return context.getAndCache(key, () -> context.polarionService().getAllFields(proto, contextId, typeId, compareTypeClass, fieldTypes));
    }

}
