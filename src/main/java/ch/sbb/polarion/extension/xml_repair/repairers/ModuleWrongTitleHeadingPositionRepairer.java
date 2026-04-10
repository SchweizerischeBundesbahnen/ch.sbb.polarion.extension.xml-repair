package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.ModuleUtils;
import com.polarion.alm.tracker.internal.ModulePagePart;
import com.polarion.alm.tracker.model.IModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

public class ModuleWrongTitleHeadingPositionRepairer extends BaseHeadingsRepairer {

    public static final String NAME = "Document content: Wrong title-heading position";

    @Override
    public List<Issue> scan(IModule module, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        scanOrRepair(module, issues, null);
        return issues;
    }

    @Override
    protected @NotNull RepairResult repair(IModule module, RepairContext context) {
        RepairResult result = new RepairResult(context.issueMetaInfo(), false);
        scanOrRepair(module, null, result);
        return result;
    }

    private void scanOrRepair(IModule module, List<Issue> issues, RepairResult repairResult) {
        if (hasTitleHeading(module)) {

            List<ModulePagePart> parts = getContentParts(module);

            if (!isHeadingAtProperPosition(parts)) {
                if (repairResult != null) {
                    moveHeadingToProperPosition(module);
                    repairResult.setSuccess(true);
                } else {
                    Issue issue = new Issue(IssueMetaInfo.create(module), this,
                            "Document '%s' has wrong title-heading position.".formatted(module.getModuleName()));
                    issues.add(issue);
                }
            }
        }
    }

    @VisibleForTesting
    List<ModulePagePart> getContentParts(IModule module) {
        String content = module.getHomePageContent().convertToHTML().getContent();
        return ModuleUtils.getContentPartsNew(content, module.getProjectId());
    }

    @VisibleForTesting
    boolean isHeadingAtProperPosition(List<ModulePagePart> parts) {
        boolean macroFound = false;
        boolean pageBreakFound = false;
        for (ModulePagePart part : parts) {
            if (part.isHeadingTitle()) {
                return true;
            } else if (isPageBreak(part)) {
                if (!pageBreakFound && macroFound) {
                    pageBreakFound = true;
                } else {
                    return false;
                }
            } else if (isMacro(part)) {
                macroFound = true;
            } else if (!isEmptyParagraph(part)) {
                return false;
            }
        }
        return false;
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Checks if the document has wrong title-heading position. Title-heading must be the first element in the document body, " +
                "but if the first page contains macros and empty strings only - title-heading can be moved to the beginning of the second page.";
    }

}
