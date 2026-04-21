package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.ILinkedWorkItemStruct;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.StringUtils;
import com.polarion.platform.persistence.model.IPObjectList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class BrokenLinkedWorkItemsRepairer extends BaseLinksRepairer {

    public static final String NAME = "Broken Work Item Links";
    private static final String LINK_PROJECT_ID = "linkProjectId";
    private static final String LINK_ROLE = "linkRole";
    private static final String LINK_ID = "linkId";
    private static final String REVISION = "revision";
    private static final String DELETE_UNRESOLVABLE = "deleteUnresolvable";

    @Override
    public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        if (entity instanceof IWorkItem workItem) {
            workItem.getLinkedWorkItemsStructsDirect().forEach(link -> {
                String projectId = StringUtils.getEmptyIfNull(link.getLinkedItem().getProjectId());
                String workItemId = link.getLinkedItem().getId();
                String revision = link.getRevision();
                // just calling isUnresolvable() isn't enough, in case if (bad) revision provided polarion will implicitly take the HEAD revision
                if (link.getLinkedItem().isUnresolvable() || link.getLinkRole() == null || !context.polarionService().isWorkItemExists(projectId, workItemId, link.getRevision())) {
                    IssueMetaInfo metaInfo = IssueMetaInfo.create(entity).set(LINK_PROJECT_ID, projectId).set(LINK_ID, workItemId).set(REVISION, revision);
                    String message;
                    if (link.getLinkRole() == null) {
                        metaInfo.set(LINK_ROLE, "");
                        message = "Broken work item link found: no link role specified for '%s/%s'".formatted(projectId, workItemId);
                    } else {
                        metaInfo.set(LINK_ROLE, link.getLinkRole().getId());
                        message = String.format("Broken work item link found: linked work item '%s/%s' does not exist.",
                                projectId, workItemId + (StringUtils.isEmpty(revision) ? "" : (":" + revision)));
                    }
                    issues.add(new Issue(metaInfo, this, message));
                }
            });
        }
        return issues;
    }

    @Override
    protected @NotNull RepairResult repair(IWorkflowObject entity, RepairContext context) {
        RepairResult result = new RepairResult(context.issueMetaInfo(), false);

        IWorkItem workItem = (IWorkItem) entity;
        Collection<ILinkedWorkItemStruct> links = workItem.getLinkedWorkItemsStructsDirect();
        ILinkedWorkItemStruct linkToRepair = links.stream().filter(link -> Objects.equals(context.issueMetaInfo().getString(LINK_PROJECT_ID), StringUtils.getEmptyIfNull(link.getLinkedItem().getProjectId()))
                        && Objects.equals(context.issueMetaInfo().getString(LINK_ROLE), link.getLinkRole() == null ? "" : link.getLinkRole().getId())
                        && Objects.equals(context.issueMetaInfo().getString(REVISION), StringUtils.getEmptyIfNull(link.getRevision()))
                        && Objects.equals(context.issueMetaInfo().getString(LINK_ID), link.getLinkedItem().getId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Issue not found, possibly it was already repaired or the content was changed since the scan."));

        String warning = repairLink(linkToRepair, workItem, context);
        if (StringUtils.isEmpty(warning)) {
            links.remove(linkToRepair);
            result.setSuccess(true);
        } else {
            result.getWarnings().add(warning);
        }

        return result;
    }

    @VisibleForTesting
    @SuppressWarnings({"unchecked", "java:S3776"}) // Ignore cognitive complexity warning, refactoring would make the code less readable
    String repairLink(ILinkedWorkItemStruct link, IWorkItem workItem, RepairContext context) {
        boolean deleteUnresolvable = context.configs().getBoolean(getClass(), DELETE_UNRESOLVABLE);
        if (link.getLinkRole() == null) {
            return deleteUnresolvable ? null : "Link role is not specified for the link. Use 'Delete unresolvable links' feature to remove items like this.";
        }

        String currentProjectId = workItem.getProjectId();
        String projectId = link.getLinkedItem().getProjectId();
        String workItemId = link.getLinkedItem().getId();
        String revision = link.getRevision();
        String warning = null;

        // attempt to find work item in the current project
        if (!Objects.equals(projectId, currentProjectId) && context.polarionService().isWorkItemExists(currentProjectId, workItemId, link.getRevision())) {
            IWorkItem properItem = context.polarionService().getWorkItem(currentProjectId, workItemId, link.getRevision());
            workItem.addLinkedItem(properItem, link.getLinkRole(), link.getRevision(), false);
        } else if (!StringUtils.isEmpty(revision) && context.polarionService().isWorkItemExists(currentProjectId, workItemId, null)) {
            // next, attempt to find omitting revision
            IWorkItem properItem = context.polarionService().getWorkItem(currentProjectId, workItemId, null);
            workItem.addLinkedItem(properItem, link.getLinkRole(), null, false);
        } else {
            // otherwise try to search globally
            IPObjectList<IWorkItem> itemsFound = workItem.getDataSvc().searchInstances(IWorkItem.PROTO, "id:\"%s\"".formatted(workItemId), null, 2);
            if (itemsFound.size() > 1) {
                warning = "Work item '%s' found at least in 2 projects: '%s' and '%s'".formatted(workItemId, itemsFound.get(0).getProjectId(), itemsFound.get(1).getProjectId());
            } else if (itemsFound.size() == 1) {
                // attempt to reuse same version, if applicable
                if (!StringUtils.isEmpty(revision) && context.polarionService().isWorkItemExists(itemsFound.getFirst().getProjectId(), itemsFound.getFirst().getId(), revision)) {
                    workItem.addLinkedItem(itemsFound.getFirst(), link.getLinkRole(), revision, false);
                } else {
                    workItem.addLinkedItem(itemsFound.getFirst(), link.getLinkRole(), null, false);
                }
            } else if (!deleteUnresolvable) {
                // do not remove unresolvable items until explicit user demand
                warning = "Cannot find replacement for the current link. Use 'Delete unresolvable links' feature to remove items like this.";
            }
        }
        return warning;
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Finds broken work item links. Repairer attempts to find and rewrite work items by ID.";
    }

    @Override
    public List<RepairerConfigMeta> getConfigs() {
        return List.of(
                new RepairerConfigMeta(DELETE_UNRESOLVABLE, "Delete unresolvable links",
                        "Delete items which cannot be found by the specified ID in any available project", RepairerConfigType.BOOLEAN, false)
        );
    }

}
