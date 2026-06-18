package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.types.Text;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FieldsRichTextLinksRepairer extends BaseLinksRepairer {

    public static final String NAME = "Rich text fields: Broken Links";

    @Override
    public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        String proto = entity.getPrototype().getName();

        for (FieldMetadata meta : getAllFieldsUsingCache(context, proto, entity.getContextId(),
                Objects.requireNonNull(entity.getType()).getId(), false, FieldType.TEXT.getType(), FieldType.RICH.getType())) {
            if (proto.equals(IModule.PROTO) && meta.getId().equals(IModule.KEY_HOMEPAGECONTENT)) {
                // homePageContent is handled in ModuleContentLinksRepairer
                continue;
            }
            if (entity.getValue(meta.getId()) instanceof Text textValue) {
                String html = textValue.getContent();
                issues.addAll(scanLinksInHtml(html, entity, meta.getId(), context.polarionService()));
            }
        }
        return issues;
    }

    @Override
    protected @NotNull RepairResult repair(IWorkflowObject entity, RepairContext context) {
        String fieldId = context.issueMetaInfo().getString("fieldId");
        if (entity.getValue(fieldId) instanceof Text textValue) {
            return repairLinksInHtml(textValue, entity, context.polarionService(), context.issueMetaInfo(), context.configs());
        } else {
            return new RepairResult(context.issueMetaInfo(), false, "Field '%s' is not of type Text.".formatted(fieldId));
        }
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Finds links to work items in rich text fields and checks if the referenced work item exists. " +
                "If the link points to a work item in another project and that work item does not exist, " +
                "the project part (data-scope) of the link is removed (if the work item exists in the current project).";
    }

    @Override
    public List<RepairerConfigMeta> getConfigs() {
        return List.of(
                new RepairerConfigMeta(BaseLinksRepairer.CONVERT_TO_PLAIN_TEXT, "Convert unresolvable links to plain text",
                        "Replace items which cannot be found by the specified ID in any available project with a plain text", RepairerConfigType.BOOLEAN, false),
                new RepairerConfigMeta(BaseLinksRepairer.ADJUST_WORK_ITEM_PREFIX, "Adjust workitem-prefix",
                        "Replace workitem prefix if a workitem with the given number exists in the current project", RepairerConfigType.BOOLEAN, false)
        );
    }

}
