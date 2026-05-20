package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.ILinkedWorkItemStruct;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.StringUtils;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.model.IPObjectList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BrokenLinkedWorkItemsRepairer extends BaseLinksRepairer {

    public static final String NAME = "Broken Work Item Links";
    private static final String LINK_PROJECT_ID = "linkProjectId";
    private static final String LINK_ROLE = "linkRole";
    private static final String LINK_ID = "linkId";
    private static final String LINK_REVISION = "linkRevision";
    private static final String ISSUE_TYPE = "issueType";
    private static final String DELETE_UNRESOLVABLE = "deleteUnresolvable";
    private static final String CACHE_LINK_ROLES_KEY_TEMPLATE = "BROKEN_LWI_PROJECT_%s_LINK_ROLES";

    @Override
    @SuppressWarnings("java:S3776") // Ignore cognitive complexity warning, refactoring would make the code less readable
    public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        if (entity instanceof IWorkItem workItem && !entity.isUnresolvable() && entity.getType() != null) {
            workItem.getLinkedWorkItemsStructsDirect().forEach(link -> {
                String linkRoleId = link.getLinkRole() == null ? "" : link.getLinkRole().getId();
                String projectId = StringUtils.getEmptyIfNull(link.getLinkedItem().getProjectId());
                String workItemId = link.getLinkedItem().getId();
                String revision = link.getRevision();
                String message = null;
                IssueMetaInfo metaInfo = IssueMetaInfo.create(entity).set(LINK_ROLE, linkRoleId).set(LINK_PROJECT_ID, projectId)
                        .set(LINK_ID, workItemId).set(LINK_REVISION, StringUtils.getEmptyIfNull(revision));

                // Just calling isUnresolvable isn't enough, in case if (bad) revision provided polarion will implicitly take the HEAD revision
                if (link.getLinkedItem().isUnresolvable() || !context.polarionService().isWorkItemExists(projectId, workItemId, revision)) {
                    metaInfo.set(ISSUE_TYPE, IssueType.LINK_UNRESOLVABLE.name());
                    message = String.format("Linked work item '%s/%s' does not exist",
                            projectId, workItemId + (StringUtils.isEmpty(revision) ? "" : (":" + revision)));
                } else if (StringUtils.isEmpty(linkRoleId)) {
                    metaInfo.set(ISSUE_TYPE, IssueType.LINK_ROLE_MISSING.name());
                    message = "No link role specified for '%s/%s'".formatted(projectId, workItemId);
                } else {
                    ILinkRoleOpt roleOpt = getRoleOpt(workItem, linkRoleId, context);
                    if (roleOpt == null) {
                        metaInfo.set(ISSUE_TYPE, IssueType.UNKNOWN_LINK_ROLE_ID.name());
                        message = "Unknown '%s' role specified for '%s/%s'".formatted(linkRoleId, projectId, workItemId);
                    } else if (linkViolatesRules(workItem, link, roleOpt)) {
                        metaInfo.set(ISSUE_TYPE, IssueType.LINK_ROLE_RULE_VIOLATED.name());
                        message = "Link role '%s' rule violated for '%s/%s'".formatted(linkRoleId, projectId, workItemId);
                    }
                }
                if (message != null) {
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
                        && Objects.equals(context.issueMetaInfo().getString(LINK_REVISION), StringUtils.getEmptyIfNull(link.getRevision()))
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
        if (List.of(IssueType.LINK_ROLE_MISSING, IssueType.UNKNOWN_LINK_ROLE_ID, IssueType.LINK_ROLE_RULE_VIOLATED).contains(IssueType.valueOf((String) context.issueMetaInfo().get(ISSUE_TYPE)))) {
            return deleteUnresolvable ? null : "Cannot repair automatically. Either fix manually or use 'Delete unresolvable links' feature to remove items like this.";
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
                new RepairerConfigMeta(DELETE_UNRESOLVABLE, "Delete broken items",
                        "Delete items with a wrong link role or unresolvable link (linked item cannot be found by the specified data in any available project)", RepairerConfigType.BOOLEAN, false)
        );
    }

    private enum IssueType {
        LINK_ROLE_MISSING,
        LINK_ROLE_RULE_VIOLATED,
        UNKNOWN_LINK_ROLE_ID,
        LINK_UNRESOLVABLE
    }

    @VisibleForTesting
    ILinkRoleOpt getRoleOpt(@NotNull IWorkItem workItem, @NotNull String linkRoleId, @NotNull ScanContext context) {
        IEnumeration<ILinkRoleOpt> roleEnum = context.getAndCache(CACHE_LINK_ROLES_KEY_TEMPLATE.formatted(workItem.getProjectId()), () ->
                context.polarionService().getTrackerProject(workItem.getProjectId()).getWorkItemLinkRoleEnum());
        List<ILinkRoleOpt> availableOptions = roleEnum.getAvailableOptions(Objects.requireNonNull(workItem.getType()).getId());
        return availableOptions.stream().filter(o -> Objects.equals(o.getId(), linkRoleId)).findFirst().orElse(null);
    }

    @VisibleForTesting
    boolean linkViolatesRules(@NotNull IWorkItem srcWorkItem, @NotNull ILinkedWorkItemStruct link, @NotNull ILinkRoleOpt roleOpt) {
        // Polarion auto-links every work item in a document to its closest heading or parent via the module's structureLinkRole
        // without consulting link-role rules (see XMLStructuredDocument.createModuleStructureLinks).
        // Treat structureLinkRole links as allowed to avoid flagging these Polarion-managed structure links.
        IWorkItem targetWorkItem = link.getLinkedItem();
        IModule srcModule = srcWorkItem.getModule();
        IModule targetModule = targetWorkItem.getModule();
        if (srcModule != null && !srcModule.isUnresolvable() && targetModule != null && !targetModule.isUnresolvable()
                && Objects.equals(srcModule.getProjectId(), targetModule.getProjectId())
                && Objects.equals(srcModule.getModuleFolder(), targetModule.getModuleFolder())
                && Objects.equals(srcModule.getModuleName(), targetModule.getModuleName())
                && Objects.equals(srcModule.getRevision(), link.getRevision())
                && srcModule.getStructureLinkRole().getId().equals(roleOpt.getId())) {
            return false;
        }
        List<ILinkRoleOpt.IRule> rules = roleOpt.getRules();
        return rules != null && rules.stream().noneMatch(rule ->
                rule.isAllowed(Optional.ofNullable(srcWorkItem.getType()).map(IEnumOption::getId).orElse(""), Optional.ofNullable(targetWorkItem.getType()).map(IEnumOption::getId).orElse("")));
    }

}
