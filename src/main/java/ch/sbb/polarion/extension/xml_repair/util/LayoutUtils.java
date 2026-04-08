package ch.sbb.polarion.extension.xml_repair.util;

import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.types.Text;
import lombok.experimental.UtilityClass;

@UtilityClass
public class LayoutUtils {

    /**
     * Usually it's enough to use {@code module.getStructureNodeOfWI(workItem).updateWorkItemLayout(layoutIndex)} to set layout index
     * but was noticed that sometimes entries may have attribute value like {@code layout_workitem_type=text} which prevents
     * {@code module.getStructureNodeOfWI(workItem).updateWorkItemLayout(layoutIndex)} from working properly. This happens because of
     * bug in Polarion's {@code ModulePageModifier.insertWorkItemSnippet()}.
     * So in such cases we need to fix the content first before updating layout index - we can do this by inserting surrogate {@code layout=0} entry.
     */
    public void switchLayoutIndex(IModule module, IWorkItem workItem, int layoutIndex) {
        String originalContent = module.getHomePageContent().getContent();
        String fixedContent = RegexMatcher.get("<div(?![^>]*\\|layout=)[^>]*params=id=%s\\|[^>]*layout_workitem_type=[^>]*>".formatted(workItem.getId()))
                .useJavaUtil().replace(originalContent, regexEngine ->
                        regexEngine.group().replace("|layout_workitem_type=", "|layout=0|layout_workitem_type="));
        if (!fixedContent.equals(originalContent)) {
            module.setHomePageContent(Text.html(fixedContent));
        }

        module.getStructureNodeOfWI(workItem).updateWorkItemLayout(layoutIndex);
    }

}
