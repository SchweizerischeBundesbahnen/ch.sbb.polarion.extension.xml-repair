package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.core.util.StringUtils;
import com.polarion.core.util.types.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ModuleNonExistentWorkItemsRepairer extends BaseRepairer {

    public static final String NAME = "Document: Non-existent Work Items";

    private static final String LINK = "link";
    private static final String WI_REGEX = "<div\\s+id=\"polarion_wiki macro name=module-workitem;params=(?<params>[^\"]+?)\"[^>]*></div>";

    @Override
    public List<Issue> scan(IModule module, ScanContext context) {
        String content = StringUtils.getEmptyIfNull(module.getHomePageContent().getContent());
        List<Issue> issues = new ArrayList<>();
        RegexMatcher.get(WI_REGEX).useJavaUtil().processEntry(content, regexEngine -> {
            String link = regexEngine.group();
            Map<String, String> params = parseParams(regexEngine.group("params"));
            String workItemId = params.get("id");
            String projectId = params.get("project");

            String effectiveProjectId = StringUtils.isEmpty(projectId) ? module.getProjectId() : projectId;
            if (workItemId != null && !context.polarionService().isWorkItemExists(effectiveProjectId, workItemId, null)) {
                issues.add(new Issue(IssueMetaInfo.create(module).set(LINK, link), this,
                        String.format("Work item '%s' does not exist in the project '%s'.", workItemId, effectiveProjectId)));
            }
        });
        return issues;
    }

    @Override
    protected @NotNull RepairResult repair(IModule module, RepairContext context) {
        IssueMetaInfo issueMetaInfo = context.issueMetaInfo();
        String linkToFix = issueMetaInfo.getString(LINK);
        String html = module.getHomePageContent().getContent();
        if (!html.contains(linkToFix)) {
            return new RepairResult(issueMetaInfo, false, "Work item was not found in the content, possibly it was already fixed or the content was changed since the scan.");
        }

        RepairResult result = new RepairResult(issueMetaInfo, false);

        String fixedHtml = RegexMatcher.get(WI_REGEX).useJavaUtil().replace(html, regexEngine -> {
            String link = regexEngine.group();
            if (!Objects.equals(link, linkToFix)) {
                return link;
            }
            Map<String, String> params = parseParams(regexEngine.group("params"));
            String workItemId = params.get("id");
            String projectId = params.get("project");
            if (projectId != null && context.polarionService().isWorkItemExists(module.getProjectId(), workItemId, null)) {
                result.setSuccess(true);
                return link.replace("|project=" + projectId, "");
            } else {
                return link;
            }
        });
        if (!Objects.equals(fixedHtml, html)) {
            module.setHomePageContent(Text.html(fixedHtml));
        }
        if (!result.isSuccess()) {
            result.getWarnings().add("Issue cannot be repaired automatically.");
        }
        return result;
    }

    @VisibleForTesting
    Map<String, String> parseParams(String paramsString) {
        Map<String, String> parameters = new HashMap<>();
        String[] parts = paramsString.split("\\|");
        for (String part : parts) {
            int index = part.indexOf(61);
            if (index > 0 && index + 1 < part.length()) {
                String name = part.substring(0, index);
                String value = part.substring(index + 1);
                parameters.put(name, value);
            }
        }
        return parameters;
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Finds unresolvable work items in the document body.";
    }

}
