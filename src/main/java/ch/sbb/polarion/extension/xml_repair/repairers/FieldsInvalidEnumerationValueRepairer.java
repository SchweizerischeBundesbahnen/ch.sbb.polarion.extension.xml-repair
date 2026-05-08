package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.fields.model.Option;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.tracker.model.IPriorityOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.logging.Logger;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.UnresolvableObjectException;
import com.polarion.platform.persistence.spi.CustomTypedList;
import com.polarion.platform.persistence.spi.PObject;
import com.polarion.platform.persistence.spi.ValueHelper;
import com.polarion.subterra.base.data.model.internal.EnumType;
import com.polarion.subterra.base.data.model.internal.ListType;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class FieldsInvalidEnumerationValueRepairer extends BaseRepairer {

    public static final String REMOVE_INVALID_ENUM_VALUES = "removeInvalidEnumValues";
    public static final String NAME = "Enumeration fields: Invalid value";
    private static final String USER_ENUM_ID = "@user";
    private static final String WORK_ITEM_TYPE_ENUM_ID = "work-item-type";
    private final Logger logger = Logger.getLogger(FieldsInvalidEnumerationValueRepairer.class);
    private final IProjectService projectService = PlatformContext.getPlatform().lookupService(IProjectService.class);

    @Override
    public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        String proto = entity.getPrototype().getName();

        for (FieldMetadata meta : context.polarionService().getAllFields(proto, entity.getContextId(),
                Objects.requireNonNull(entity.getType()).getId(), true,
                FieldType.LIST.getType(), FieldType.ENUM.getType())) {
            scanOrRepair(entity, meta, context.configs(), issues, null);
        }
        return issues;
    }

    @Override
    public @NotNull RepairResult repair(IWorkflowObject entity, RepairContext context) {
        String fieldId = context.issueMetaInfo().getString(FIELD_ID);
        String proto = entity.getPrototype().getName();

        RepairResult result = new RepairResult(context.issueMetaInfo(), false);

        FieldMetadata meta = context.polarionService().getAllFields(proto, entity.getContextId(),
                        Objects.requireNonNull(entity.getType()).getId(), true,
                        FieldType.LIST.getType(), FieldType.ENUM.getType()).stream()
                .filter(m -> Objects.equals(m.getId(), fieldId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Field with id '%s' not found on entity '%s'".formatted(fieldId, entity.getId())));

        scanOrRepair(entity, meta, context.configs(), null, result);

        return result;
    }

    @SuppressWarnings({"java:S3776", "java:S1166"}) // ignore cognitive complexity and "log or rethrow this exception" complaints
    private void scanOrRepair(IWorkflowObject entity, FieldMetadata meta, UserConfigs configs, List<Issue> issues, RepairResult repairResult) {
        Object value = null;
        try {
            value = entity.getValue(meta.getId());
        } catch (UnresolvableObjectException e) {
            logger.debug("Failed to resolve enumeration value(s) in field '%s' of entity '%s'".formatted(meta.getLabel(), entity.getId()));
            handleUnresolvableObjectException(entity, meta, issues, configs, repairResult, null);
        }

        // skip 'priority' enum, as it has special handling in Polarion
        if (value instanceof IEnumOption option && !(option instanceof IPriorityOpt) && isInvalidEnumOption(option, meta)) {
            Issue issue = createIssue(entity, meta, "Invalid enumeration id '%s' for the field '%s'".formatted(option.getId(), meta.getLabel()));
            if (repairResult != null && issue.getDescription().equals(repairResult.getRawIssueMetaInfo().getString(ISSUE_DESCRIPTION))) {
                IEnumOption similarValue = findSimilarOption(entity, option, meta);
                if (similarValue != null) {
                    entity.setValue(meta.getId(), similarValue);
                    repairResult.setSuccess(true);
                } else if (!configs.getBoolean(getClass(), REMOVE_INVALID_ENUM_VALUES)) {
                    warnRepairTurnedOff(repairResult, false);
                } else {
                    clearFieldValue(entity, meta, repairResult);
                }
            } else if (issues != null) {
                issues.add(issue);
            }
        } else if (value instanceof CustomTypedList list && meta.getType() instanceof ListType listType
                && listType.getItemType() instanceof EnumType enumType && !(Objects.equals(enumType.getEnumerationId(), IWorkItem.ENUM_ID_PRIORITY))) {
            try {
                List<IEnumOption> invalidOptions = list.stream().filter(v -> v instanceof IEnumOption e && isInvalidEnumOption(e, meta)).toList();
                handleInvalidOptions(entity, meta, issues, configs, repairResult, list, invalidOptions);
            } catch (UnresolvableObjectException e) {
                logger.debug("Failed to resolve enumeration value(s) in field '%s' of entity '%s'".formatted(meta.getLabel(), entity.getId()));
                handleUnresolvableObjectException(entity, meta, issues, configs, repairResult, listType);
            }
        }
    }

    private Issue createIssue(IWorkflowObject entity, FieldMetadata meta, String description) {
        return new Issue(IssueMetaInfo.create(entity).set(FIELD_ID, meta.getId()).set(ISSUE_DESCRIPTION, description), this, description);
    }


    private void handleInvalidOptions(IWorkflowObject entity, FieldMetadata meta, List<Issue> issues, UserConfigs configs, RepairResult repairResult, CustomTypedList list, List<IEnumOption> invalidOptions) {
        if (!invalidOptions.isEmpty()) {
            String invalidIds = invalidOptions.stream().map(IEnumOption::getId).collect(Collectors.joining("', '", "'", "'"));
            Issue issue = createIssue(entity, meta, "Invalid enumeration id(s) %s for the field '%s'.".formatted(invalidIds, meta.getLabel()));
            if (repairResult != null && issue.getDescription().equals(repairResult.getRawIssueMetaInfo().getString(ISSUE_DESCRIPTION))) {
                List<IEnumOption> similarOptions = invalidOptions.stream().map(o -> findSimilarOption(entity, o, meta)).filter(Objects::nonNull).toList();
                // Fix automatically only when similar item found for every invalid.
                // Otherwise, we can end up in a situation that only some of the invalid values are repaired, and the rest are still invalid,
                // so user will need to run repair multiple times and it can be confusing. So even if one of N items may be fixed
                // by deletion we require REMOVE_INVALID_ENUM_VALUES option is turned on.
                if (meta.isRequired() && invalidOptions.size() == list.size() && similarOptions.isEmpty()) {
                    repairResult.getWarnings().add("Can't remove all values of required enumeration field '%s'.".formatted(meta.getLabel()));
                } else if (!configs.getBoolean(getClass(), REMOVE_INVALID_ENUM_VALUES) && similarOptions.size() != invalidOptions.size()) {
                    warnRepairTurnedOff(repairResult, invalidOptions.size() > 1);
                } else {
                    // We fix either case when we found all similar items or when REMOVE_INVALID_ENUM_VALUES option is turned on.
                    // We want to prevent situation that only some of the invalid values are repaired, and the rest are still invalid,
                    // so user will need to run repair multiple times which is confusing.
                    list.removeAll(invalidOptions);
                    list.addAll(similarOptions);
                    entity.setValue(meta.getId(), list);
                    repairResult.setSuccess(true);
                }
            } else if (issues != null) {
                issues.add(issue);
            }
        }
    }

    /**
     * Handle the case when the value of enumeration field can't be resolved at all (e.g. because of the type was changed from 'string' to 'enum/document').
     */
    @SuppressWarnings({"rawtypes", "java:S3776", "java:S1166"}) // ignore cognitive complexity and "log or rethrow this exception" complaints
    private void handleUnresolvableObjectException(IWorkflowObject entity, FieldMetadata meta, List<Issue> issues, UserConfigs configs, RepairResult repairResult, ListType listType) {
        List<Object> badItems = new ArrayList<>();
        Object customValue = Objects.requireNonNull(((PObject) entity).getData()).getCustomValue(meta.getId());
        if (listType != null && customValue instanceof List<?> customList) {
            for (Object item : customList) {
                try {
                    ValueHelper.wrapCustomField(entity, null, listType.getItemType(), item);
                } catch (UnresolvableObjectException e) {
                    badItems.add(item);
                }
            }
        } else {
            badItems.add(customValue);
        }

        if (!badItems.isEmpty()) {
            Issue issue = createIssue(entity, meta, "Invalid enumeration id(s) %s for the field '%s'.".formatted(badItems.stream().map(String::valueOf).toList(), meta.getLabel()));
            if (repairResult != null && issue.getDescription().equals(repairResult.getRawIssueMetaInfo().getString(ISSUE_DESCRIPTION))) {
                if (!configs.getBoolean(getClass(), REMOVE_INVALID_ENUM_VALUES)) {
                    warnRepairTurnedOff(repairResult, badItems.size() > 1);
                } else if (!meta.isMulti()) {
                    clearFieldValue(entity, meta, repairResult);
                } else {
                    if (listType != null && customValue instanceof List customList) {
                        if (meta.isRequired() && badItems.size() == customList.size()) {
                            repairResult.getWarnings().add("Can't remove all values of required enumeration field '%s'.".formatted(meta.getLabel()));
                        } else {
                            List newList = new CustomTypedList(entity, listType, false, new ArrayList<>());
                            customList.stream().filter(v -> !badItems.contains(v)).forEach(v -> {
                                try {
                                    newList.add(ValueHelper.wrapCustomField(entity, null, listType.getItemType(), v));
                                } catch (UnresolvableObjectException e) {
                                    // should not happen, as we already checked these values, but just in case
                                    logger.debug("Failed to resolve enumeration value '%s' in field '%s' of entity '%s'".formatted(v, meta.getLabel(), entity.getId()));
                                }
                            });
                            entity.setValue(meta.getId(), newList);
                            repairResult.setSuccess(true);
                        }
                    } else {
                        clearFieldValue(entity, meta, repairResult);
                    }
                }
            } else if (issues != null) {
                issues.add(issue);
            }
        }
    }

    void warnRepairTurnedOff(RepairResult repairResult, boolean multipleEntries) {
        repairResult.getWarnings().add("Cannot repair %s automatically. Enable option 'Remove invalid enumeration values' to remove invalid value".formatted(multipleEntries ? "all values" : "value"));
    }

    @VisibleForTesting
    boolean isInvalidEnumOption(IEnumOption option, FieldMetadata meta) {
        String enumId = option.getEnumId();
        if (enumId.equals(USER_ENUM_ID)) {// some users may be not presented in the user enumeration (e.g. disabled users) so we need to check them separately
            return projectService.getUsers().stream().noneMatch(u -> u.getId().equals(option.getId()));
        } else if (enumId.equals(WORK_ITEM_TYPE_ENUM_ID) && TYPE_HEADING.equals(option.getId())) {
            return false; // heading type isn't presented in the options list but is still valid
        }
        return meta.getOptions().stream().noneMatch(o -> o.getKey().equals(option.getId()));
    }

    private IEnumOption findSimilarOption(IWorkflowObject entity, IEnumOption option, FieldMetadata meta) {
        // attempt 1: find option with exact name
        Option match = meta.getOptions().stream().filter(o -> Objects.equals(o.getName(), option.getId())).findFirst()
                // attempt 2: by id case-insensitive
                .or(() -> meta.getOptions().stream().filter(o -> Strings.CI.equals(o.getKey(), option.getId())).findFirst())
                // attempt 3: by name case-insensitive
                .or(() -> meta.getOptions().stream().filter(o -> Strings.CI.equals(o.getName(), option.getId())).findFirst())
                .orElse(null);
        if (match == null) {
            return null;
        }
        // Use the IEnumeration registered for this enum id so the returned option is the proper
        // concrete type (e.g. IStatusOpt for status, ITypeOpt for type). Polarion casts on read,
        // so a plain EnumOption would fail with ClassCastException for fields backed by typed
        // IEnumOption subtypes. Resolve via enum id (not field key) so custom fields work too.
        IEnumeration<?> enumeration = entity.getDataSvc().getEnumerationForEnumId(
                new EnumType(option.getEnumId()), entity.getContextId());
        return enumeration.wrapOption(match.getKey());
    }

    private void clearFieldValue(IWorkflowObject entity, FieldMetadata meta, RepairResult repairResult) {
        if (meta.isRequired()) {
            repairResult.getWarnings().add("Can't remove the only value of required enumeration field '%s'.".formatted(meta.getLabel()));
        } else {
            entity.setValue(meta.getId(), null);
            repairResult.setSuccess(true);
        }
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Finds fields with invalid enumeration values. Repair should removed invalid value, if an empty value is possible (or it is only one value from multiple).";
    }

    @Override
    public List<RepairerConfigMeta> getConfigs() {
        return List.of(
                new RepairerConfigMeta(REMOVE_INVALID_ENUM_VALUES, "Remove invalid enumeration values",
                        "Clear/remove value if it is not defined in the specified enumeration", RepairerConfigType.BOOLEAN, false)
        );
    }

}
