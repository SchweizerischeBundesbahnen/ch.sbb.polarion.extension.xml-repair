package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
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
    private static final String PROJECT_ID_PARAM = "projectIdParam";
    private static final String WORK_ITEM_ID_PARAM = "workItemIdParam";
    private static final String EXTERNAL_PARAM = "isExternalParam";
    private static final String WI_REGEX = "<div\\s+id=\"polarion_wiki macro name=module-workitem;params=(?<params>[^\"]+?)\"[^>]*></div>";

    @Override
    @SuppressWarnings("java:S3776") // Ignore cognitive complexity warning, refactoring would make the code less readable
    public List<Issue> scan(IModule module, ScanContext context) {
        String content = StringUtils.getEmptyIfNull(module.getHomePageContent().getContent());
        List<Issue> issues = new ArrayList<>();
        RegexMatcher.get(WI_REGEX).useJavaUtil().processEntry(content, regexEngine -> {
            String link = regexEngine.group();
            Map<String, String> params = parseParams(regexEngine.group("params"));
            String workItemId = StringUtils.getEmptyIfNull(params.get("id"));
            String projectId = StringUtils.getEmptyIfNull(params.get("project"));
            boolean external = Boolean.parseBoolean(params.get("external"));
            String issue = null;
            if (StringUtils.isEmpty(workItemId)) {
                issue = String.format("Invalid work item declaration in the document body: '%s'.", link);
            } else {
                String effectiveProjectId = StringUtils.isEmpty(projectId) ? module.getProjectId() : projectId;
                IWorkItem documentWorkItem = getModuleWorkItem(module, effectiveProjectId, workItemId);
                if (documentWorkItem == null || documentWorkItem.isUnresolvable() || (!external && !Objects.equals(documentWorkItem.getModule(), module))) {
                    issue = String.format("Work item '%s' doesn't exist or doesn't belong to the current document.",
                            StringUtils.isEmpty(projectId) ? workItemId : (projectId + "/" + workItemId));
                }
            }
            if (issue != null) {
                issues.add(new Issue(IssueMetaInfo.create(module)
                        .set(LINK, link)
                        .set(EXTERNAL_PARAM, external)
                        .set(WORK_ITEM_ID_PARAM, workItemId)
                        .set(PROJECT_ID_PARAM, projectId), this, issue));
            }
        });
        return issues;
    }

    @Override
    @SuppressWarnings("java:S3776") // Ignore cognitive complexity warning, refactoring would make the code less readable
    protected @NotNull RepairResult repair(IModule module, RepairContext context) {
        IssueMetaInfo issueMetaInfo = context.issueMetaInfo();
        String linkToFix = issueMetaInfo.getString(LINK);
        String html = StringUtils.getEmptyIfNull(module.getHomePageContent().getContent());
        if (!html.contains(linkToFix)) {
            return new RepairResult(issueMetaInfo, false, "Work item was not found in the content, possibly it was already fixed or the content was changed since the scan.");
        }

        String projectIdParam = issueMetaInfo.getString(PROJECT_ID_PARAM);
        String workItemId = issueMetaInfo.getString(WORK_ITEM_ID_PARAM);
        boolean external = issueMetaInfo.getBoolean(EXTERNAL_PARAM);
        String fixedHtml = html;

        // the only case we can attempt to fix - when occasionally some wrong project value was set but the work items itself is contained in the current document
        // in this case we basically clean project param
        if (!external && !StringUtils.isEmpty(workItemId) && !StringUtils.isEmpty(projectIdParam)) {
            IWorkItem moduleWorkItem = getModuleWorkItem(module, module.getProjectId(), workItemId);
            if (moduleWorkItem != null && !moduleWorkItem.isUnresolvable()) {
                String fixedLink = linkToFix.replace("|project=" + projectIdParam, "");
                if (!fixedLink.equals(linkToFix)) {
                    fixedHtml = html.replace(linkToFix, fixedLink);
                }
            }
        }

        RepairResult result = new RepairResult(issueMetaInfo, false);
        if (!Objects.equals(fixedHtml, html)) {
            module.setHomePageContent(Text.html(fixedHtml));
            result.setSuccess(true);
        } else {
            result.getWarnings().add("Issue cannot be repaired automatically.");
        }
        return result;
    }

    @VisibleForTesting
    IWorkItem getModuleWorkItem(IModule module, String projectId, String workItemId) {
        return module.getAllWorkItems().stream().filter(wi -> Objects.equals(wi.getId(), workItemId) && Objects.equals(wi.getProjectId(), projectId)).findFirst().orElse(null);
    }

    @VisibleForTesting
    Map<String, String> parseParams(String paramsString) {
        Map<String, String> parameters = new HashMap<>();
        String[] parts = paramsString.split("\\|");
        for (String part : parts) {
            int index = part.indexOf("=");
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
        return "Finds unresolvable/invalid work items in the document body.";
    }

}
