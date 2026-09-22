package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.IWorkflowObject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class FieldsFormattingSymbolsRepairer extends BaseRepairer {

    public static final String NAME = "String fields: Formatting Symbols";
    private static final String FORMATTING_SYMBOLS = "\n\r\t";
    // A single quantifier with nothing around it cannot backtrack. Describing "whitespace run containing a
    // formatting symbol" in the pattern instead needs a leading quantifier, which scans in quadratic time.
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    @Override
    public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        String proto = entity.getPrototype().getName();

        for (FieldMetadata meta : getAllFieldsUsingCache(context, proto, entity.getContextId(),
                Objects.requireNonNull(entity.getType()).getId(), false, FieldType.STRING.getType())) {
            Object value = entity.getValue(meta.getId());
            if (value instanceof String stringValue && containsFormattingSymbols(stringValue)) {
                issues.add(new Issue(IssueMetaInfo.create(entity).set(FIELD_ID, meta.getId()), this,
                        "String field contains formatting symbols."));
            }
        }
        return issues;
    }

    @Override
    protected @NotNull RepairResult repair(IWorkflowObject entity, RepairContext context) {
        String fieldId = context.issueMetaInfo().getString(FIELD_ID);
        Object value = entity.getValue(fieldId);
        if (value instanceof String stringValue && containsFormattingSymbols(stringValue)) {
            entity.setValue(fieldId, collapseFormattingSymbols(stringValue).trim());
            return new RepairResult(context.issueMetaInfo(), true);
        } else {
            return new RepairResult(context.issueMetaInfo(), false, "Issue does not exist anymore, possibly it was already fixed or the content was changed since the scan.");
        }
    }

    public static boolean containsFormattingSymbols(@NotNull String value) {
        return value.chars().anyMatch(character -> FORMATTING_SYMBOLS.indexOf(character) >= 0);
    }

    // Collapses every whitespace run which contains a formatting symbol into a single space, leaving the others as they are.
    public static @NotNull String collapseFormattingSymbols(@NotNull String value) {
        return WHITESPACE_RUN.matcher(value).replaceAll(match -> containsFormattingSymbols(match.group()) ? " " : match.group());
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Removes formatting symbols (new lines, tabs, etc.) in plain string fields.";
    }
}
