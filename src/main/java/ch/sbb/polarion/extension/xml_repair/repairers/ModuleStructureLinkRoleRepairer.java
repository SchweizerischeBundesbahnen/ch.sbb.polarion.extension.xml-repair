package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.rest.exception.UnauthorizedException;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.ILinkedWorkItemStruct;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.spi.LowLevelPObjectAccessor;
import com.polarion.subterra.base.SubterraURI;
import com.polarion.subterra.base.data.model.internal.EnumType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Changes the structure link role of a document to the role the user selected on the "Structural link" page.
 * <p>
 * Polarion does not persist the structural links themselves. It derives one link per node from the document
 * tree plus the document's {@code structureLinkRole} when a work item is loaded, and strips every link of that
 * role again when the work item is stored. Setting the field is therefore the whole repair: Polarion rebuilds
 * the links under the new role by itself.
 * <p>
 * That same rule is why the links a document already holds under the target role matter. Polarion strips by
 * role alone, never comparing the link against the document tree, so after the switch every such link is
 * dropped the next time its work item is stored - silently, and long after the switch. The scan reports them,
 * and {@link #EXISTING_LINKS} decides what the repair does about them.
 */
public class ModuleStructureLinkRoleRepairer extends BaseRepairer {

    public static final String NAME = "Structural link role";
    /** Config key and meta info key of the role the document must end up with. */
    public static final String TARGET_ROLE = "targetRole";
    /** The role Polarion itself uses unless the document was created with another one. */
    public static final String DEFAULT_TARGET_ROLE = "parent";
    /** The Polarion enumeration holding the work item link roles. Equal to {@code IWorkItem.ENUM_ID_LINK_ROLE}. */
    public static final String LINK_ROLE_ENUM_ID = "wi-link-role";

    /** Config key of what to do with the links a document already holds under the target role. */
    public static final String EXISTING_LINKS = "existingLinks";
    /** Config key of the role those links move to. Read for {@link #EXISTING_LINKS_CHANGE} alone. */
    public static final String EXISTING_LINKS_ROLE = "existingLinksRole";

    /** Leave the document as it is and report why. The default: the other two answers lose or move a link. */
    public static final String EXISTING_LINKS_INTERRUPT = "INTERRUPT";
    /** Move those links to {@link #EXISTING_LINKS_ROLE}, then switch the document. */
    public static final String EXISTING_LINKS_CHANGE = "CHANGE";
    /** Switch the document and leave those links untouched, accepting that Polarion drops them later. */
    public static final String EXISTING_LINKS_IGNORE = "IGNORE";
    /** Remove those links, then switch the document. */
    public static final String EXISTING_LINKS_DELETE = "DELETE";

    /** Upper bound of the colliding links a single warning names, so a large document cannot flood the UI. */
    private static final int MAX_REPORTED_COLLISIONS = 10;

    /**
     * One link standing in the way of the switch, captured as the scan found it. The revision and the suspect
     * flag are kept so moving the link to another role preserves them.
     */
    @VisibleForTesting
    record CollidingLink(@NotNull IWorkItem source, @NotNull IWorkItem target, @NotNull ILinkRoleOpt role,
                         @Nullable String revision, boolean suspect) {

        @NotNull
        String describe() {
            return "%s -> %s".formatted(source.getId(), target.getId());
        }

        /**
         * Moves this link to {@code replacement}, or removes it when that is null. The new link is added first:
         * {@code addLinkedItem} refuses only a duplicate or a self-link, so a refusal here leaves the original
         * untouched rather than losing it.
         */
        void moveTo(@Nullable ILinkRoleOpt replacement) {
            if (replacement != null) {
                source.addLinkedItem(target, replacement, revision, suspect);
            }
            source.removeLinkedItem(target, role);
            source.save();
        }
    }

    @Override
    public List<Issue> scan(IModule module, ScanContext context) {
        String targetRole = targetRole(context.configs());
        String usedRole = usedRole(module);
        if (Objects.equals(usedRole, targetRole)) {
            return List.of();
        }

        // The label feeds the role column of the results list, where a count would always say one.
        Issue issue = new Issue(IssueMetaInfo.create(module).set(TARGET_ROLE, targetRole), this,
                "Document '%s' uses structure link role '%s' instead of '%s'"
                        .formatted(module.getModuleName(), Objects.requireNonNullElse(usedRole, "none"), targetRole),
                null, Objects.requireNonNullElse(usedRole, "none"));
        List<CollidingLink> collisions = collidingLinks(module, targetRole);
        if (!collisions.isEmpty()) {
            issue.getWarnings().add(collisionWarning(targetRole, collisions));
        }
        return List.of(issue);
    }

    @Override
    protected @NotNull RepairResult repair(IModule module, RepairContext context) {
        RepairResult result = new RepairResult(context.issueMetaInfo(), false);
        // The scan stamps the role into the meta info, so a repair applies what the user saw. The configs stay
        // as the fallback for a request built without a preceding scan of this page.
        String targetRole = Objects.requireNonNullElse(
                context.issueMetaInfo().getString(TARGET_ROLE), targetRole(context.configs()));

        String usedRole = usedRole(module);
        if (Objects.equals(usedRole, targetRole)) {
            result.getWarnings().add("Document already uses structure link role '%s'.".formatted(targetRole));
            return result;
        }

        // Resolved before anything is written, so an unknown role cannot leave the links already changed.
        ILinkRoleOpt role = resolveRole(module, targetRole);
        if (role == null || role.isPhantom()) {
            result.getWarnings().add("Link role '%s' does not exist in this project.".formatted(targetRole));
            return result;
        }

        // Settled before anything is written. The check inside setStructureLinkRole throws, the service catches
        // per item and carries on, and the write transaction still commits - so a refusal there would leave the
        // link changes below persisted against a document whose role never moved.
        if (!module.can().modifyKey(IModule.KEY_STRUCTURELINKROLE)) {
            result.getWarnings().add(XmlRepairPolarionService.MSG_NO_PERMISSIONS);
            return result;
        }

        List<CollidingLink> collisions = collidingLinks(module, targetRole);
        if (!collisions.isEmpty() && !resolveCollisions(module, targetRole, collisions, context.configs(), result)) {
            return result;
        }

        setStructureLinkRole(module, role);
        result.getStaleCacheUris().addAll(staleCacheUris(module));
        result.setSuccess(true);
        return result;
    }

    /**
     * The objects whose cached copy the switch leaves behind.
     * <p>
     * Polarion injects the structural link into a work item when it is deserialized, so every work item read
     * before the switch still carries a link of the old role. Writing the module invalidates the module alone,
     * and nothing about those work items changed, so nothing evicts them. That is not only a display problem:
     * their injected link no longer matches the document's role, so the next save of such a work item writes
     * it into storage as a real link. The caller drops them from the caches once the transaction is committed.
     */
    @VisibleForTesting
    @NotNull
    Set<SubterraURI> staleCacheUris(@NotNull IModule module) {
        Set<SubterraURI> uris = new HashSet<>();
        uris.add(module.getUri());
        for (IWorkItem workItem : module.getContainedWorkItems()) {
            if (!workItem.isUnresolvable()) {
                uris.add(workItem.getUri());
            }
        }
        return uris;
    }

    /**
     * Applies the "Existing links" answer to the links standing in the way.
     *
     * @return true when the document may be switched now, false when the repair must stop and report.
     */
    @VisibleForTesting
    boolean resolveCollisions(@NotNull IModule module, @NotNull String targetRole,
                              @NotNull List<CollidingLink> collisions, @NotNull UserConfigs configs,
                              @NotNull RepairResult result) {
        String policy = existingLinksPolicy(configs);
        if (EXISTING_LINKS_INTERRUPT.equals(policy)) {
            result.getWarnings().add(collisionWarning(targetRole, collisions));
            return false;
        }

        if (EXISTING_LINKS_IGNORE.equals(policy)) {
            // Still reported, because the switch is what seals their fate and this is the only record of it.
            result.getWarnings().add("Left %d %s of role '%s' in place: %s. Polarion drops such a link when its work item is next saved."
                    .formatted(collisions.size(), collisions.size() == 1 ? "link" : "links", targetRole, listed(collisions)));
            return true;
        }

        ILinkRoleOpt replacement = null;
        if (EXISTING_LINKS_CHANGE.equals(policy)) {
            String replacementRole = configs.getString(getClass(), EXISTING_LINKS_ROLE);
            if (replacementRole == null || replacementRole.isBlank() || replacementRole.equals(targetRole)) {
                result.getWarnings().add("No link role was chosen to move those links to.");
                return false;
            }
            if (replacementRole.equals(usedRole(module))) {
                // The links move before the switch, so a save now still strips the document's current role.
                result.getWarnings().add(("Link role '%s' is the role this document structures its content with, "
                        + "so a link moved to it would be stripped as its work item is saved.").formatted(replacementRole));
                return false;
            }
            replacement = resolveRole(module, replacementRole);
            if (replacement == null || replacement.isPhantom()) {
                result.getWarnings().add("Link role '%s' does not exist in this project.".formatted(replacementRole));
                return false;
            }
        }

        // Every work item is checked before any of them is written, so a refusal halfway cannot leave the
        // document with some links moved and its role unchanged.
        for (CollidingLink collision : collisions) {
            if (!collision.source().can().modify()) {
                result.getWarnings().add("Cannot modify work item '%s', which holds one of those links."
                        .formatted(collision.source().getId()));
                return false;
            }
        }

        for (CollidingLink collision : collisions) {
            collision.moveTo(replacement);
        }
        result.getWarnings().add(replacement == null
                ? "Deleted %d link(s) of role '%s': %s.".formatted(collisions.size(), targetRole, listed(collisions))
                : "Moved %d link(s) from role '%s' to '%s': %s."
                        .formatted(collisions.size(), targetRole, replacement.getId(), listed(collisions)));
        return true;
    }

    /** Anything unrecognized falls back to the answer that writes nothing. */
    @VisibleForTesting
    @NotNull
    String existingLinksPolicy(@NotNull UserConfigs configs) {
        String configured = configs.getString(getClass(), EXISTING_LINKS);
        return EXISTING_LINKS_CHANGE.equals(configured) || EXISTING_LINKS_DELETE.equals(configured)
                || EXISTING_LINKS_IGNORE.equals(configured) ? configured : EXISTING_LINKS_INTERRUPT;
    }

    /**
     * Writes the structure link role of a document that already exists.
     * <p>
     * {@code IPObject.setValue} refuses that key: the Module prototype declares it read only as soon as the
     * document is persisted, which is why ModuleManager can only set it while creating one, and why the role
     * cannot be changed from the UI at all. The low-level object performs the same write without that guard.
     * It unwraps the option, records the change so {@code save()} persists it, and runs the per-key permission
     * check handed to it - the very check {@code IPObject.setValue} would have run. Polarion reaches for that
     * object the same way in ModuleMerger.
     */
    @VisibleForTesting
    void setStructureLinkRole(@NotNull IModule module, @NotNull ILinkRoleOpt role) {
        LowLevelPObjectAccessor.getFor(module).setValue(IModule.KEY_STRUCTURELINKROLE, role, (key, value) -> {
            if (!module.can().modifyKey(key)) {
                throw new UnauthorizedException(XmlRepairPolarionService.MSG_NO_PERMISSIONS);
            }
        });
    }

    @VisibleForTesting
    @Nullable
    String usedRole(@NotNull IModule module) {
        ILinkRoleOpt role = module.getStructureLinkRole();
        return role == null ? null : role.getId();
    }

    @VisibleForTesting
    @NotNull
    String targetRole(@NotNull UserConfigs configs) {
        String configured = configs.getString(getClass(), TARGET_ROLE);
        return configured == null || configured.isBlank() ? DEFAULT_TARGET_ROLE : configured;
    }

    /**
     * The links of the target role the work items of this document already hold. With another role in place as
     * the structural one, every such link is a real one a user created.
     * <p>
     * Outgoing links alone: Polarion strips them while storing a work item that sits in the document, using
     * that document's role. A work item outside the document linking into it is stored under its own
     * document's role, so it is none of our business.
     */
    @VisibleForTesting
    @NotNull
    List<CollidingLink> collidingLinks(@NotNull IModule module, @NotNull String targetRole) {
        List<CollidingLink> collisions = new ArrayList<>();
        for (IWorkItem workItem : module.getContainedWorkItems()) {
            if (workItem.isUnresolvable()) {
                continue;
            }
            for (ILinkedWorkItemStruct link : workItem.getLinkedWorkItemsStructsDirect()) {
                ILinkRoleOpt role = link.getLinkRole();
                IWorkItem target = link.getLinkedItem();
                if (role != null && Objects.equals(role.getId(), targetRole) && target != null) {
                    collisions.add(new CollidingLink(workItem, target, role, link.getRevision(), link.isSuspect()));
                }
            }
        }
        return collisions;
    }

    /**
     * The one warning the collisions produce, on the scan and on a repair that stopped at them. It deliberately
     * says nothing about losing those links: the "Existing links" answer decides what happens to them, so the
     * warning would be threatening something the default never carries out.
     */
    @VisibleForTesting
    @NotNull
    String collisionWarning(@NotNull String targetRole, @NotNull List<CollidingLink> collisions) {
        boolean single = collisions.size() == 1;
        return "Link role '%s' is already used by %d %s in this document: %s. Choose what happens to %s under 'Existing %s links'."
                .formatted(targetRole, collisions.size(), single ? "link" : "links", listed(collisions),
                        single ? "it" : "them", targetRole);
    }

    @NotNull
    private String listed(@NotNull List<CollidingLink> collisions) {
        String names = collisions.stream().limit(MAX_REPORTED_COLLISIONS)
                .map(CollidingLink::describe).reduce((a, b) -> a + ", " + b).orElse("");
        return collisions.size() > MAX_REPORTED_COLLISIONS
                ? names + ", and %d more".formatted(collisions.size() - MAX_REPORTED_COLLISIONS) : names;
    }

    @SuppressWarnings("unchecked")
    @VisibleForTesting
    @Nullable
    ILinkRoleOpt resolveRole(@NotNull IModule module, @NotNull String roleId) {
        IEnumeration<ILinkRoleOpt> roles = module.getDataSvc()
                .getEnumerationForEnumId(new EnumType(LINK_ROLE_ENUM_ID), module.getContextId());
        return roles.wrapOption(roleId);
    }

    @Override
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Check which link role a document uses to structure its content, and change it to the selected one.";
    }

}
