package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.LayoutUtils;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;
import java.util.stream.IntStream;

public class ModuleDuplicateLayoutDeclarationRepairer extends BaseRepairer {

    public static final String NAME = "Document content: Duplicate layout declarations";

    @Override
    public List<Issue> scan(IModule module, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        Set<String> duplicatedTypes = findDuplicateDeclarations(module);

        if (!duplicatedTypes.isEmpty()) {
            Issue issue = new Issue(IssueMetaInfo.create(module), this,
                    "Module '%s' has duplicate '%s' type%s declarations"
                            .formatted(module.getModuleName(), String.join("'/'", duplicatedTypes), duplicatedTypes.size() > 1 ? "s" : ""));
            issues.add(issue);
        }

        return issues;
    }

    @Override
    protected @NotNull RepairResult repair(IModule module, RepairContext context) {
        RepairResult result = new RepairResult(context.issueMetaInfo(), false);

        Set<String> duplicatedTypes = findDuplicateDeclarations(module);
        if (!duplicatedTypes.isEmpty()) {
            for (String duplicatedType : duplicatedTypes) {
                streamModuleWorkItems(module).filter(w -> Objects.requireNonNull(w.getType()).getId().equals(duplicatedType))
                        .forEach(workItem -> fixLayoutIndex(module, workItem, duplicatedType));
                removeLayoutDuplicates(module, duplicatedType);
            }
            result.setSuccess(true);
        }

        return result;
    }

    private Set<String> findDuplicateDeclarations(IModule module) {
        List<IModule.IRenderingLayoutStruct> declaredLayouts = module.getRenderingLayouts();
        return new LinkedHashSet<>(declaredLayouts.stream()
                .map(IModule.IRenderingLayoutStruct::getType)
                .filter(Objects::nonNull)
                .filter(type -> declaredLayouts.stream().filter(l -> Objects.equals(l.getType(), type)).count() > 1)
                .toList());
    }

    @VisibleForTesting
    void fixLayoutIndex(IModule module, IWorkItem workItem, String typeId) {
        int firstTypeEntryIndex = IntStream.range(0, module.getRenderingLayouts().size())
                .filter(i -> module.getRenderingLayouts().get(i).getType().equals(typeId))
                .findFirst().orElse(0);
        if (module.getStructureNodeOfWI(workItem).getLayout() != firstTypeEntryIndex) {
            LayoutUtils.switchLayoutIndex(module, workItem, firstTypeEntryIndex);
        }
    }

    @VisibleForTesting
    void removeLayoutDuplicates(IModule module, String typeId) {
        List<IModule.IRenderingLayoutStruct> allForThisType = new ArrayList<>(module.getRenderingLayouts().stream().filter(l -> l.getType().equals(typeId)).toList());
        allForThisType.removeFirst();
        module.getRenderingLayouts().removeAll(allForThisType);
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Checks if the document has several declarations of the same layout type. After repair only the first declaration will be kept and used for all items of the given type.";
    }

}
