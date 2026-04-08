package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.types.Text;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ch.sbb.polarion.extension.xml_repair.repairers.FieldsFormattingSymbolsRepairer.FORMATTING_SYMBOLS_REGEX;

public class FieldsWrongTypeRepairer extends BaseRepairer {

    public static final String NAME = "Fields: Wrong value type";

    @Override
    public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        String proto = entity.getPrototype().getName();

        for (FieldMetadata meta : context.polarionService().getAllFields(proto, entity.getContextId(),
                Objects.requireNonNull(entity.getType()).getId(), false, FieldType.STRING.getType())) {
            Object value = entity.getValue(meta.getId());
            if (value != null && !(value instanceof String)) {
                issues.add(new Issue(IssueMetaInfo.create(entity).set(FIELD_ID, meta.getId()), this,
                        "Non-string (%s) value used in a plain string field.".formatted(value.getClass().getSimpleName())));
            }
        }
        return issues;
    }

    @Override
    protected @NotNull RepairResult repair(IWorkflowObject entity, RepairContext context) {
        RepairResult result = new RepairResult(context.issueMetaInfo(), false);

        String fieldId = context.issueMetaInfo().getString(FIELD_ID);
        FieldMetadata meta = context.polarionService().getAllFields(entity.getPrototype().getName(), entity.getContextId(),
                        Objects.requireNonNull(entity.getType()).getId(), false,
                        FieldType.STRING.getType()).stream()
                .filter(m -> Objects.equals(m.getId(), fieldId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Field with id '%s' not found on entity '%s'".formatted(fieldId, entity.getId())));

        Object value = entity.getValue(meta.getId());
        if (value != null && !(value instanceof String)) {
            String convertedValue = convertToString(value);
            entity.setValue(meta.getId(), convertedValue.replaceAll(FORMATTING_SYMBOLS_REGEX, " ").trim());
            result.setSuccess(true);
        }

        return result;
    }

    private String convertToString(Object value) {
        return value instanceof Text textValue ? textValue.convertToPlainText().getContent() : String.valueOf(value);
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Converts values to the fields specific format (e.g. plain string fields may contain 'Text' objects " +
                "left by changing type of the field from multi-line).";
    }
}
