package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ModuleMissingTitleHeadingRepairer extends BaseHeadingsRepairer {

    public static final String NAME = "Document content: Missing title-heading";

    @Override
    public List<Issue> scan(IModule module, ScanContext context) {
        return hasTitleHeading(module) ? List.of() : List.of(new Issue(IssueMetaInfo.create(module), this,
                "Document '%s' has missing title-heading.".formatted(module.getModuleName())));
    }

    @Override
    public @NotNull RepairResult repair(IModule module, RepairContext context) {
        if (!hasTitleHeading(module)) {
            IWorkItem newHeading = module.createWorkItem(TYPE_HEADING);
            newHeading.setTitle(module.getModuleName());
            moveHeadingToProperPosition(module);
            newHeading.save();
            return new RepairResult(context.issueMetaInfo(), true);
        } else {
            return new RepairResult(context.issueMetaInfo(), false, "Issue was already fixed.");
        }
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Checks if the document has a title-heading defined and adds it if missing. A missing title-heading may cause severe issues, e.g. during import/export roundtrips.";
    }

}
