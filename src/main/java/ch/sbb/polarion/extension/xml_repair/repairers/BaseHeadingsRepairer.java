package ch.sbb.polarion.extension.xml_repair.repairers;

import com.polarion.alm.tracker.ModuleUtils;
import com.polarion.alm.tracker.internal.ModulePagePart;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.core.util.types.Text;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public abstract class BaseHeadingsRepairer extends BaseRepairer {

    @VisibleForTesting
    boolean hasTitleHeading(IModule module) {
        String html = Optional.ofNullable(module.getHomePageContent()).orElse(Text.html("")).convertToHTML().getContent();
        return ModuleUtils.getContentPartsNew(html, module.getProjectId()).stream().anyMatch(ModulePagePart::isHeadingTitle);
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
    @SuppressWarnings("java:S135")
        // readability is better with break in this case
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

    @SuppressWarnings("java:S5852")
        // Input is a single ModulePagePart element, not user-controlled.
    boolean isPageBreak(ModulePagePart part) {
        return part.getElementHtml().matches("(?s)<div[^>]+name=page_break.*?</div>");
    }

}
