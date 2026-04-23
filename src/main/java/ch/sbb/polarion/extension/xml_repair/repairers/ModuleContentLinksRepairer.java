package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.core.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ModuleContentLinksRepairer extends BaseLinksRepairer {

    public static final String NAME = "Document content: Broken Links";

    @Override
    public List<Issue> scan(IModule module, ScanContext context) {
        String content = StringUtils.getEmptyIfNull(module.getHomePageContent().getContent());
        return scanLinksInHtml(content, module, IModule.KEY_HOMEPAGECONTENT, context.polarionService());
    }

    @Override
    protected @NotNull RepairResult repair(IModule module, RepairContext context) {
        return repairLinksInHtml(module.getHomePageContent(), module, context.polarionService(), context.issueMetaInfo(), context.configs());
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Finds links to work items in the document body and checks if the referenced work item exists. " +
                "If the link points to a work item in another project and that work item does not exist, " +
                "the project part (data-scope) of the link is removed (if the work item exists in the current project).";
    }

    @Override
    public List<RepairerConfigMeta> getConfigs() {
        return List.of(
                new RepairerConfigMeta(BaseLinksRepairer.CONVERT_TO_PLAIN_TEXT, "Convert unresolvable links to plain text",
                        "Replace items which cannot be found by the specified ID in any available project with a plain text", RepairerConfigType.BOOLEAN, false)
        );
    }

}
