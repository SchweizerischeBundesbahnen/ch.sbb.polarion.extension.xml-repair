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
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseLinksRepairer extends BaseRepairer {

    public static final String CONVERT_TO_PLAIN_TEXT = "convertToPlainText";
    public static final String ADJUST_WORK_ITEM_PREFIX = "adjustWorkItemPrefix";
    private static final String LINK = "link";
    // named capture groups of LINK_REGEX
    private static final String GROUP_WORK_ITEM_ID = "workItemId";
    private static final String GROUP_SELECTION_ID = "selectionId";
    private static final String GROUP_PROJECT_ID = "workItemProjectId";
    private static final String GROUP_REVISION = "workItemRevision";
    private static final String DATA_CUSTOM_LABEL_TEMPLATE = "data-custom-label=\"%s\"";
    private static final String DATA_ITEM_ID_TEMPLATE = "data-item-id=\"%s\"";
    private static final String DATA_SCOPE_TEMPLATE = "data-scope=\"%s\"";
    private static final String DATA_ITEM_ID_ANCHOR = "data-item-id=";
    // 'polarion' links carry the id in 'data-url' instead of 'data-item-id'; these anchor/rewrite the 'selection' param
    private static final String DATA_URL_ANCHOR = "data-url=";
    private static final String SELECTION_PARAM_TEMPLATE = "selection=%s";

    // Matches all three kinds of 'polarion-rte-link' spans: 'workItem' and 'crossReference' carry the id in
    // 'data-item-id', while 'polarion' (wiki/document) links carry it inside 'data-url' as the 'selection' query
    // parameter and have no 'data-item-id'. Consequently both id sources are optional; callers must fall back from
    // 'workItemId' to 'selectionId'.
    private static final String LINK_REGEX = "<span\\s+" +
            "(?=[^>]*class=\"polarion-rte-link\")" +
            "(?=[^>]*data-type=\"(?<linkType>workItem|crossReference|polarion)\")" +
            "(?=[^>]*data-custom-label=\"(?<customLabel>[^\"]+?)\")?" +
            "(?=[^>]*data-item-id=\"(?<workItemId>[^\"]+?)\")?" +
            "(?=[^>]*data-scope=\"(?<workItemProjectId>[^\"]+)\")?" +
            "(?=[^>]*data-revision=\"(?<workItemRevision>[^\"]+)\")?" +
            "(?=[^>]*data-url=\"[^\"]*selection=(?<selectionId>[^\"&]+))?" +
            "[^>]*?></span>";

    public List<Issue> scanLinksInHtml(String html, IWorkflowObject entity, String fieldId, XmlRepairPolarionService polarionService) {
        List<Issue> issues = new ArrayList<>();
        RegexMatcher.get(LINK_REGEX).useJavaUtil().processEntry(html, regexEngine -> {
            String link = regexEngine.group();
            String providedProjectId = regexEngine.group(GROUP_PROJECT_ID);
            String workItemRevision = regexEngine.group(GROUP_REVISION);
            // 'polarion' links have no 'data-item-id'; their id lives in the 'data-url' 'selection' parameter
            String workItemId = regexEngine.group(GROUP_WORK_ITEM_ID) != null ? regexEngine.group(GROUP_WORK_ITEM_ID) : regexEngine.group(GROUP_SELECTION_ID);
            if (workItemId == null) {
                return; // a 'polarion-rte-link' span without an identifiable work item id - nothing to check
            }

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
        // The scan reports one issue per occurrence, so a field referencing the same work item several times
        // yields several issues carrying identical link markup. Repair only the first occurrence: fixing all of
        // them at once would leave the remaining issues reporting a failure although the content is correct.
        AtomicBoolean occurrenceHandled = new AtomicBoolean(false);
        String fixedHtml = RegexMatcher.get(LINK_REGEX).useJavaUtil().replace(html, regexEngine -> {
            String link = regexEngine.group();
            if (!Objects.equals(link, linkToFix) || occurrenceHandled.getAndSet(true)) {
                return link;
            }
            String providedProjectId = regexEngine.group(GROUP_PROJECT_ID);
            String workItemRevision = regexEngine.group(GROUP_REVISION);
            String dataItemId = regexEngine.group(GROUP_WORK_ITEM_ID);
            // 'polarion' links have no 'data-item-id'; their id lives in the 'data-url' 'selection' parameter
            String workItemId = dataItemId != null ? dataItemId : regexEngine.group(GROUP_SELECTION_ID);
            String customLabel = regexEngine.group("customLabel");

            String effectiveProjectId = StringUtils.isEmpty(providedProjectId) ? entity.getProjectId() : providedProjectId;
            if (workItemId == null || polarionService.isWorkItemExists(effectiveProjectId, workItemId, workItemRevision)) {
                return link;
            }

            // 'polarion' (wiki/document) links have no 'data-item-id' - the id lives in the 'data-url' 'selection' param.
            // The repair flow below mirrors work-item links but rewrites 'selection'/anchors on 'data-url' for them.
            boolean urlBasedId = dataItemId == null;

            // first, attempt to find work item in the current project
            if (providedProjectId != null && polarionService.isWorkItemExists(entity.getProjectId(), workItemId, workItemRevision)) {
                result.setSuccess(true);
                return link.replace(DATA_SCOPE_TEMPLATE.formatted(providedProjectId), "");
            } else if (userConfigs.getBoolean(getClass(), ADJUST_WORK_ITEM_PREFIX)) {
                String adjustedWorkItemId = replaceWorkItemPrefix(workItemId, entity.getProject().getTrackerPrefix());
                if (!Objects.equals(workItemId, adjustedWorkItemId) && polarionService.isWorkItemExists(entity.getProjectId(), adjustedWorkItemId, workItemRevision)) {
                    result.setSuccess(true);
                    String adjustedCustomLabel = customLabel == null ? "" : customLabel.replace(workItemId, adjustedWorkItemId);
                    String adjusted = link.replace(DATA_SCOPE_TEMPLATE.formatted(providedProjectId), "")
                            .replace(DATA_CUSTOM_LABEL_TEMPLATE.formatted(customLabel), DATA_CUSTOM_LABEL_TEMPLATE.formatted(adjustedCustomLabel));
                    return urlBasedId
                            ? adjusted.replace(SELECTION_PARAM_TEMPLATE.formatted(workItemId), SELECTION_PARAM_TEMPLATE.formatted(adjustedWorkItemId))
                            : adjusted.replace(DATA_ITEM_ID_TEMPLATE.formatted(workItemId), DATA_ITEM_ID_TEMPLATE.formatted(adjustedWorkItemId));
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
                    // there was no project ID provided - we must insert it before the id-bearing attribute
                    String anchor = urlBasedId ? DATA_URL_ANCHOR : DATA_ITEM_ID_ANCHOR;
                    return link.replace(anchor, "data-scope=\"%s\" %s".formatted(itemsFound.getFirst().getProjectId(), anchor));
                } else {
                    return link.replace(DATA_SCOPE_TEMPLATE.formatted(providedProjectId), DATA_SCOPE_TEMPLATE.formatted(itemsFound.getFirst().getProjectId()));
                }
            } else {
                return convertToPlainTextOrKeep(link, workItemId, customLabel, entity, userConfigs, result);
            }
        });
        if (!Objects.equals(fixedHtml, html)) {
            entity.setValue(issueMetaInfo.getString(FIELD_ID), text.isPlain() ? Text.plain(fixedHtml) : Text.html(fixedHtml));
        }
        return result;
    }

    private String convertToPlainTextOrKeep(String link, String workItemId, String customLabel, IWorkflowObject entity, UserConfigs userConfigs, RepairResult result) {
        if (userConfigs.getBoolean(getClass(), CONVERT_TO_PLAIN_TEXT)) {
            result.setSuccess(true);
            return "<span class=\"xml-repair-replaced-link\">%s</span>".formatted(StringUtils.isEmpty(customLabel) ? workItemId : customLabel);
        } else {
            result.getWarnings().add("Work item '%s' does not exist in the project '%s'".formatted(workItemId, entity.getProjectId()));
            return link;
        }
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
