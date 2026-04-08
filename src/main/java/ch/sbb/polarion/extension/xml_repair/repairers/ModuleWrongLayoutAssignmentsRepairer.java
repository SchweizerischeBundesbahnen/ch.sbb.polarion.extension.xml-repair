package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.LayoutUtils;
import com.polarion.alm.tracker.internal.model.module.Module;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.platform.persistence.spi.AbstractTypedList;
import com.polarion.subterra.base.data.model.IStructType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("rawtypes")
public class ModuleWrongLayoutAssignmentsRepairer extends BaseRepairer {

    public static final String NAME = "Document content: Wrong layout assignments";

    @Override
    public List<Issue> scan(IModule module, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        scanOrRepair(module, issues, null);
        return issues;
    }

    @Override
    protected @NotNull RepairResult repair(IModule module, RepairContext context) {
        RepairResult result = new RepairResult(context.issueMetaInfo(), false);
        scanOrRepair(module, null, result);
        return result;
    }

    @SuppressWarnings("java:S3776") // Ignore cognitive complexity warning, refactoring would make the code less readable
    private void scanOrRepair(IModule module, List<Issue> issues, RepairResult repairResult) {
        streamModuleWorkItems(module)
                .forEach(workItem -> {
                    String typeId = Objects.requireNonNull(workItem.getType()).getId();
                    int expectedLayoutIndex = getLayoutForWorkItem(module, typeId);
                    int currentLayoutIndex = module.getStructureNodeOfWI(workItem).getLayout();
                    if (currentLayoutIndex != expectedLayoutIndex) {
                        String layoutTypeId = getLayoutTypeId(module, currentLayoutIndex);
                        boolean requiredLayoutDeclared = expectedLayoutIndex != -1;
                        // here we check/fix 2 cases:
                        // 1) required layout declared but the index in work item is wrong (note that same type may be declared
                        //    several times in the single document - this is subject of another repairer)
                        // 2) layout isn't declared at all for the work item type - in this case we add new layout
                        //    declaration to the module structure and assign it to the work item
                        if (!typeId.equals(layoutTypeId)) {
                            String description = layoutTypeId == null ? "No layout declared for work item '%s' with type '%s'".formatted(workItem.getId(), typeId)
                                    : "Work item '%s' has wrong layout assigned in the module structure (expected '%s' but found '%s').".formatted(workItem.getId(), typeId, layoutTypeId);
                            Issue issue = new Issue(IssueMetaInfo.create(module).set(ISSUE_DESCRIPTION, description), this, description);
                            if (repairResult != null && description.equals(repairResult.getRawIssueMetaInfo().get(ISSUE_DESCRIPTION))) {
                                if (requiredLayoutDeclared) {
                                    LayoutUtils.switchLayoutIndex(module, workItem, expectedLayoutIndex);
                                    repairResult.setSuccess(true);
                                } else {
                                    if (module.getProject().getWorkItemTypeEnum().getAllOptions().stream().anyMatch(o -> Objects.equals(o.getId(), typeId))) {
                                        Map<String, Object> data = Map.of("label", workItem.getType().getName(),
                                                "layouter", "paragraph",
                                                "type", typeId, "properties",
                                                List.of(Map.of("key", "sidebarWorkitemFields", "value", "severity,status")));
                                        IStructType itemType = (IStructType) ((AbstractTypedList) module.getRenderingLayouts()).getPrototype().getItemType();
                                        module.getRenderingLayouts().add(new Module.ModuleRenderingLayout(module, itemType, false, data));
                                        LayoutUtils.switchLayoutIndex(module, workItem, module.getRenderingLayouts().size() - 1);
                                        repairResult.setSuccess(true);
                                    } else {
                                        repairResult.getWarnings().add("No such '%s' work item type declared in the project '%s'".formatted(typeId, module.getProjectId()));
                                    }
                                }
                            } else if (issues != null) {
                                issues.add(issue);
                            }
                        }
                    }
                });
    }

    private int getLayoutForWorkItem(IModule module, String typeId) {
        return module.getRenderingLayouts().stream()
                .filter(l -> l.getType().equals(typeId))
                .map(l -> module.getRenderingLayouts().indexOf(l))
                .findFirst().orElse(-1);
    }

    private String getLayoutTypeId(IModule module, int layoutIndex) {
        List<IModule.IRenderingLayoutStruct> layouts = module.getRenderingLayouts();
        return layoutIndex >= 0 && layoutIndex < layouts.size() ? layouts.get(layoutIndex).getType() : null;
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Checks if the assigned layout ID for a work item matches the layout declaration for its specific work item type. If the document doesn't contain declaration for a work item type it will be added.";
    }

}
