package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.service.model.IContext;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.platform.persistence.spi.LowLevelPObjectAccessor;
import com.polarion.platform.persistence.spi.PObject;
import com.polarion.subterra.base.data.object.IDataObject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds custom fields which hold a value on an entity but are no longer declared in the custom fields
 * configuration for that entity's type, and clears them.
 * <p>
 * This is what backs the "Purge outdated data" page, so it is registered in
 * {@code XmlRepairPolarionService.PURGE_REPAIRERS} rather than in {@code REPAIRERS} - the "Repairers" block of
 * the General checks page must not offer it.
 * <p>
 * Both the detection and the removal work on the persisted data below the model API, because a field with no
 * definition has no prototype for {@code setValue} to validate against. {@code getValidCustomFieldIds} is
 * evaluated per object, so it is type-aware: a field declared only for {@code requirement} but filled on a
 * {@code task} counts as outdated there.
 */
public class OutdatedCustomFieldsRepairer extends BaseRepairer {

    public static final String NAME = "Outdated Custom Fields";
    private static final String CACHE_VALID_CUSTOM_FIELDS_KEY_TEMPLATE = "VALID_CUSTOM_FIELDS_%s_%s_%s";

    @Override
    public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        for (String fieldId : findOutdatedFieldIds(entity, context)) {
            issues.add(new Issue(IssueMetaInfo.create(entity).set(FIELD_ID, fieldId), this,
                    "Attribute '%s' holds a value but is not defined in the custom fields configuration.".formatted(fieldId), fieldId));
        }
        return issues;
    }

    @Override
    protected @NotNull RepairResult repair(IWorkflowObject entity, RepairContext context) {
        String fieldId = context.issueMetaInfo().getString(FIELD_ID);
        if (fieldId == null || !findOutdatedFieldIds(entity, context).contains(fieldId)) {
            return new RepairResult(context.issueMetaInfo(), false, "Issue does not exist anymore, possibly it was already fixed or the attribute was defined again since the scan.");
        }
        PObject pObject = (PObject) entity;
        // Removes the key from the persisted custom values and from the incompatible ones, and drops the data
        // object's transient caches. BaseRepairer.repair saves the entity once this returns successfully.
        Objects.requireNonNull(pObject.getData()).removeCustomKey(fieldId);

        // Without this the save is a no-op. DataService.save returns early for a persisted, resolved object that
        // does not report itself modified, and only the field setters flip that flag - editing the persisted
        // data directly cannot, because the flag lives on the low level object rather than in the data. Which is
        // also why setCustomField is not usable here: it resolves the field's definition and reads its type, and
        // an attribute that is no longer defined has none.
        LowLevelPObjectAccessor.getFor(pObject).markChanged();
        // The removed key must not linger in the "which custom fields are set" caches of the object either.
        LowLevelPObjectAccessor.clearCustomSetCaches(pObject);
        return new RepairResult(context.issueMetaInfo(), true);
    }

    /**
     * The stored custom keys which no longer have a definition and still hold a value, sorted so that the
     * scan report and the attribute list of the page keep a stable order.
     */
    @NotNull
    private Collection<String> findOutdatedFieldIds(@NotNull IWorkflowObject entity, @NotNull IContext context) {
        if (!(entity instanceof PObject pObject) || !pObject.getPrototype().allowsCustomFields()) {
            return List.of();
        }
        IDataObject data = pObject.getData();
        if (data == null) {
            return List.of();
        }

        Set<String> fieldIds = new TreeSet<>(data.getCustomKeySet());
        // A stored value whose type stopped matching its definition lands in a separate map, and an
        // undefined field has no definition left to match, so both are candidates.
        fieldIds.addAll(data.getIncompatibleCustomKeySet());
        fieldIds.removeAll(getValidFieldIdsUsingCache(context, pObject, entity));
        fieldIds.removeIf(fieldId -> data.getCustomValue(fieldId) == null && data.getIncompatibleCustomValue(fieldId) == null);
        return fieldIds;
    }

    /**
     * Which custom fields are declared for this object. Cached per prototype, context and type, the same
     * granularity the definitions themselves have, so one scan resolves them once per type.
     */
    @NotNull
    private Collection<String> getValidFieldIdsUsingCache(@NotNull IContext context, @NotNull PObject pObject, @NotNull IWorkflowObject entity) {
        String key = CACHE_VALID_CUSTOM_FIELDS_KEY_TEMPLATE.formatted(pObject.getPrototype().getName(),
                pObject.getContextId(), Objects.requireNonNull(entity.getType()).getId());
        return context.getAndCache(key, () -> pObject.getDataSvc().getCustomFieldsService().getValidCustomFieldIds(pObject));
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Clears attributes which are filled on the entity but are not defined in its custom fields configuration.";
    }
}
