package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.IModule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ModuleStandardStructureLinkRoleRepairer extends BaseLinksRepairer {

    public static final String NAME = "Standard structure link role";
    public static final String ALLOWED_STRUCTURE_LINK_ROLE = "parent";

    @Override
    public List<Issue> scan(IModule module, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        String usedRoleId = module.getStructureLinkRole().getId();
        if (!Objects.equals(usedRoleId, ALLOWED_STRUCTURE_LINK_ROLE)) {
            Issue issue = new Issue(IssueMetaInfo.create(module), this,
                    "Document '%s' has non-standard structure link role '%s'"
                            .formatted(module.getModuleName(), usedRoleId));
            issues.add(issue);
        }
        return issues;
    }

    @Override
    protected @NotNull RepairResult repair(IModule module, RepairContext context) {
        return new RepairResult(context.issueMetaInfo(), false, "Automatic repair is not possible.");
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Check whether standard ('parent') link role was chosen during document creation. No automatic repair possible - only manual actions are implied.";
    }

}
