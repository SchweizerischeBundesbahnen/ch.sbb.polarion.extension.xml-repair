package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.StringUtils;
import com.polarion.core.util.types.Text;
import com.polarion.platform.persistence.model.IPObjectList;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class BaseLinksRepairer extends BaseRepairer {

    public static final String CONVERT_TO_PLAIN_TEXT = "convertToPlainText";
    public static final String ADJUST_WORK_ITEM_PREFIX = "adjustWorkItemPrefix";
    private static final String LINK = "link";
    private static final String DATA_SCOPE_TEMPLATE = "data-scope=\"%s\"";
    private static final String DATA_ITEM_ID_TEMPLATE = "data-item-id=\"%s\"";

    private static final String LINK_REGEX = "<span\\s+" +
            "(?=[^>]*class=\"polarion-rte-link\")" +
            "(?=[^>]*data-type=\"workItem\")" +
            "(?=[^>]*data-custom-label=\"(?<customLabel>[^\"]+?)\")?" +
            "(?=[^>]*data-item-id=\"(?<workItemId>[^\"]+?)\")" +
            "(?=[^>]*data-scope=\"(?<workItemProjectId>[^\"]+)\")?" +
            "(?=[^>]*data-revision=\"(?<workItemRevision>[^\"]+)\")?" +
            "[^>]*?></span>";

    public List<Issue> scanLinksInHtml(String html, IWorkflowObject entity, String fieldId, XmlRepairPolarionService polarionService) {
        List<Issue> issues = new ArrayList<>();
        RegexMatcher.get(LINK_REGEX).useJavaUtil().processEntry(html, regexEngine -> {
            String link = regexEngine.group();
            String providedProjectId = regexEngine.group("workItemProjectId");
            String workItemRevision = regexEngine.group("workItemRevision");
            String workItemId = regexEngine.group("workItemId");

            String effectiveProjectId = StringUtils.isEmpty(providedProjectId) ? entity.getProjectId() : providedProjectId;
            if (!polarionService.isWorkItemExists(effectiveProjectId, workItemId, workItemRevision)) {
                issues.add(new Issue(IssueMetaInfo.create(entity).set(FIELD_ID, fieldId).set(LINK, link), this,
                        String.format("Broken link found: workitem '%s' does not exist in the project '%s'.",
                                workItemId + (StringUtils.isEmpty(workItemRevision) ? "" : (":" + workItemRevision)), effectiveProjectId)));
            }
        });
        return issues;
    }

    @SuppressWarnings({"unchecked", "java:S3776"}) // Ignore cognitive complexity warning, refactoring would make the code less readable
    public RepairResult repairLinksInHtml(Text text, IWorkflowObject entity, XmlRepairPolarionService polarionService, IssueMetaInfo issueMetaInfo, UserConfigs userConfigs) {
        String linkToFix = issueMetaInfo.getString(LINK);
        String html = text.getContent();
        if (!html.contains(linkToFix)) {
            return new RepairResult(issueMetaInfo, false, "The link to fix was not found in the content, possibly it was already fixed or the content was changed since the scan.");
        }

        RepairResult result = new RepairResult(issueMetaInfo, false);
        String fixedHtml = RegexMatcher.get(LINK_REGEX).useJavaUtil().replace(html, regexEngine -> {
            String link = regexEngine.group();
            if (!Objects.equals(link, linkToFix)) {
                return link;
            }
            String providedProjectId = regexEngine.group("workItemProjectId");
            String workItemRevision = regexEngine.group("workItemRevision");
            String workItemId = regexEngine.group("workItemId");
            String customLabel = regexEngine.group("customLabel");

            String effectiveProjectId = StringUtils.isEmpty(providedProjectId) ? entity.getProjectId() : providedProjectId;
            if (polarionService.isWorkItemExists(effectiveProjectId, workItemId, workItemRevision)) {
                return link;
            }

            // first, attempt to find work item in the current project
            if (providedProjectId != null && polarionService.isWorkItemExists(entity.getProjectId(), workItemId, workItemRevision)) {
                result.setSuccess(true);
                return link.replace(DATA_SCOPE_TEMPLATE.formatted(providedProjectId), "");
            } else if (userConfigs.getBoolean(getClass(), ADJUST_WORK_ITEM_PREFIX)) {
                String adjustedWorkItemId = replaceWorkItemPrefix(workItemId, entity.getProject().getTrackerPrefix());
                if (!Objects.equals(workItemId, adjustedWorkItemId) && polarionService.isWorkItemExists(entity.getProjectId(), adjustedWorkItemId, workItemRevision)) {
                    result.setSuccess(true);
                    return link.replace(DATA_SCOPE_TEMPLATE.formatted(providedProjectId), "").replace(DATA_ITEM_ID_TEMPLATE.formatted(workItemId), DATA_ITEM_ID_TEMPLATE.formatted(adjustedWorkItemId));
                }
            }

            // otherwise try to search globally
            IPObjectList<IWorkItem> itemsFound = entity.getDataSvc().searchInstances(
                    IWorkItem.PROTO, "id:\"%s\"".formatted(workItemId), null, 2);
            if (itemsFound.size() > 1) {
                result.getWarnings().add("Work item '%s' found at least in 2 projects: '%s' and '%s'".formatted(workItemId, itemsFound.get(0).getProjectId(), itemsFound.get(1).getProjectId()));
                return link;
            } else if (itemsFound.size() == 1) {
                result.setSuccess(true);
                if (providedProjectId == null) {
                    // there was no project ID provided - we must insert it
                    return link.replace("data-item-id=", "data-scope=\"%s\" data-item-id=".formatted(itemsFound.getFirst().getProjectId()));
                } else {
                    return link.replace(DATA_SCOPE_TEMPLATE.formatted(providedProjectId), DATA_SCOPE_TEMPLATE.formatted(itemsFound.getFirst().getProjectId()));
                }
            } else {
                if (userConfigs.getBoolean(getClass(), CONVERT_TO_PLAIN_TEXT)) {
                    result.setSuccess(true);
                    return "<span class=\"xml-repair-replaced-link\">%s</span>".formatted(StringUtils.isEmpty(customLabel) ? workItemId : customLabel);
                } else {
                    result.getWarnings().add("Work item '%s' does not exist in the project '%s'".formatted(workItemId, entity.getProjectId()));
                    return link;
                }
            }
        });
        if (!Objects.equals(fixedHtml, html)) {
            entity.setValue(issueMetaInfo.getString(FIELD_ID), text.isPlain() ? Text.plain(fixedHtml) : Text.html(fixedHtml));
        }
        return result;
    }

    @VisibleForTesting
    String replaceWorkItemPrefix(String workItemId, String newPrefix) {
        int dashPosition = workItemId.indexOf("-");
        if (dashPosition > 0 && dashPosition < workItemId.length() - 1) {
            return "%s-%s".formatted(newPrefix, workItemId.substring(dashPosition + 1));
        } else {
            return workItemId;
        }
    }

}
