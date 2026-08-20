package ch.sbb.polarion.extension.xml_repair;

import com.polarion.alm.ui.server.navigation.NavigationExtenderNode;
import com.polarion.subterra.base.data.identification.IContextId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The "Purge outdated data" child of the XML-Repair navigation node: finds attributes which are filled on
 * entities but no longer defined in their custom fields configuration, and clears them.
 * <p>
 * {@link #NODE_ID} doubles as the {@code ?feature=} id of the React page, so it must stay equal to the
 * matching entry of {@code ui/src/features.tsx}.
 */
public class PurgeOutdatedDataNode extends NavigationExtenderNode {

    public static final String NODE_ID = "purge-outdated-data";

    @NotNull
    @Override
    public String getId() {
        return NODE_ID;
    }

    @NotNull
    @Override
    public String getLabel() {
        return "Purge outdated data";
    }

    @Nullable
    @Override
    public String getIconUrl() {
         return "/polarion/xml-repair-app/ui/images/menu/16x16/purge.svg";
    }

    @Nullable
    @Override
    public String getPageUrl(IContextId contextId) {
        return XmlRepairNavigationExtender.navPageUrl(NODE_ID, contextId);
    }

    @Override
    public boolean requiresToken() {
        return false;
    }

    @NotNull
    @Override
    public List<NavigationExtenderNode> getChildren() {
        return List.of();
    }
}
