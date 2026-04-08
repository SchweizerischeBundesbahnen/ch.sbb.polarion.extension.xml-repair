package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.tracker.ModuleUtils;
import com.polarion.alm.tracker.internal.ModulePagePart;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.types.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class BaseRepairer implements IRepairer {

    protected static final String FIELD_ID = "fieldId";
    protected static final String ISSUE_DESCRIPTION = "issueDescription";
    protected static final String TYPE_HEADING = "heading";

    @Override
    public RepairResult repair(IUniqueObject entity, RepairContext context) {
        RepairResult repairResult;
        IUniqueObject entityToSave = entity;
        if (entity instanceof IModule module) {
            String revision = module.getRevision();
            String headRevisionTakenWarning = null;
            if (revision != null) {
                module = context.polarionService().getModule(module.getProject(), module.getModuleLocation().removeRevision());
                if (module.isUnresolvable()) {
                    return new RepairResult(context.issueMetaInfo(), false, "'%s' is unresolvable in HEAD, possibly because it was deleted.".formatted(module.getModuleName()));
                } else {
                    headRevisionTakenWarning = "'%s' HEAD revision was loaded instead of rev.%s".formatted(module.getModuleName(), revision);
                }
                entityToSave = module;
            }
            repairResult = repair(module, context);
            if (headRevisionTakenWarning != null) {
                repairResult.getWarnings().add(headRevisionTakenWarning);
            }
        } else {
            repairResult = repair((IWorkflowObject) entity, context);
        }
        if (repairResult.isSuccess()) {
            entityToSave.save();
        }
        return repairResult;
    }

    protected @NotNull RepairResult repair(IModule entity, RepairContext context) {
        return repair((IWorkflowObject) entity, context);
    }

    @SuppressWarnings("unused")
    protected @NotNull RepairResult repair(IWorkflowObject entity, RepairContext context) {
        throw new IllegalArgumentException("Repairer '%s' does not support entity of type '%s'".formatted(
                getClass().getSimpleName(), entity.getClass().getSimpleName()));
    }

    @Override
    public List<Issue> scan(IUniqueObject entity, ScanContext context) {
        if (entity instanceof IModule module) {
            String revision = module.getRevision();
            String headRevisionTakenWarning = null;
            if (revision != null) {
                module = context.polarionService().getModule(module.getProject(), module.getModuleLocation().removeRevision());
                if (module.isUnresolvable()) {
                    String unresolvableMessage = "'%s' is unresolvable in HEAD, possibly because it was deleted.".formatted(module.getModuleName());
                    context.report().warn(unresolvableMessage);
                    context.globalWarnings().add(unresolvableMessage);
                    return List.of();
                } else {
                    headRevisionTakenWarning = "'%s' HEAD revision was loaded instead of rev.%s".formatted(module.getModuleName(), revision);
                    context.report().warn(headRevisionTakenWarning);
                }
            }
            List<Issue> issues = scan(module, context);
            if (headRevisionTakenWarning != null) {
                for (Issue issue : issues) {
                    issue.getWarnings().add(headRevisionTakenWarning);
                }
            }
            return issues;
        } else {
            return scan((IWorkflowObject) entity, context);
        }
    }

    protected List<Issue> scan(IModule entity, ScanContext context) {
        return scan((IWorkflowObject) entity, context);
    }

    @SuppressWarnings("unused")
    public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
        throw new IllegalArgumentException("Repairer '%s' does not support entity of type '%s'".formatted(
                getClass().getSimpleName(), entity.getClass().getSimpleName()));
    }

    void moveHeadingToProperPosition(IModule module) {
        String content = module.getHomePageContent().convertToHTML().getContent();
        List<ModulePagePart> parts = ModuleUtils.getContentPartsNew(content, module.getProjectId());

        int desiredPosition = findDesiredHeadingPosition(parts);
        reorderHeadingToPosition(parts, desiredPosition);

        StringBuilder sb = new StringBuilder();
        parts.forEach(part -> part.append(sb));
        module.setHomePageContent(Text.html(sb.toString()));
    }

    @VisibleForTesting
    @SuppressWarnings("java:S135") // readability is better with break in this case
    int findDesiredHeadingPosition(List<ModulePagePart> parts) {
        // find proper position, usually it's the most top, but if the first page contains macros and empty <p> only - we must skip it and put the heading after it
        Integer secondPagePosition = null;
        boolean macroFound = false;
        for (int i = 0; i < parts.size(); i++) {
            ModulePagePart part = parts.get(i);
            if (isPageBreak(part)) {
                if (macroFound) {
                    secondPagePosition = i + 1;
                }
                break;
            } else if (isMacro(part)) {
                macroFound = true;
            } else if (!isEmptyParagraph(part)) {
                break;
            }
        }
        return Optional.ofNullable(secondPagePosition).orElse(0);
    }

    @VisibleForTesting
    void reorderHeadingToPosition(List<ModulePagePart> parts, int desiredPosition) {
        IntStream.range(0, parts.size())
                .filter(i -> parts.get(i).isHeading())
                .findFirst()
                .ifPresent(i -> parts.add(desiredPosition, parts.remove(i)));
    }

    boolean isEmptyParagraph(ModulePagePart part) {
        return part.getElementHtml().matches("(?s)<p[^>]*>\\s*</p>");
    }

    boolean isMacro(ModulePagePart part) {
        return part.getElementHtml().matches("(?s)<div[^>]+polarion-dle-wiki-block.*");
    }

    @SuppressWarnings("java:S5852") // Input is a single ModulePagePart element, not user-controlled.
    boolean isPageBreak(ModulePagePart part) {
        return part.getElementHtml().matches("(?s)<div[^>]+name=page_break.*?</div>");
    }

    protected Stream<IWorkItem> streamModuleWorkItems(IModule module) {
        return module.getContainedWorkItems().stream().filter(w -> !w.isUnresolvable() && w.getType() != null && !w.getType().getId().equals(TYPE_HEADING));
    }

}
