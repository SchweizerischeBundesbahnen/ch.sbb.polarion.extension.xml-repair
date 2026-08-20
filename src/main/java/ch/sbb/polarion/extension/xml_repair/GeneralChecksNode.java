package ch.sbb.polarion.extension.xml_repair;

import com.polarion.alm.ui.server.navigation.NavigationExtenderNode;
import com.polarion.subterra.base.data.identification.IContextId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The "General checks" child of the XML-Repair navigation node: the scan and repair page.
 * <p>
 * {@link #NODE_ID} doubles as the {@code ?feature=} id of the React page, so it must stay equal to the
 * matching entry of {@code ui/src/features.tsx}.
 */
public class GeneralChecksNode extends NavigationExtenderNode {

    public static final String NODE_ID = "general-checks";

    @NotNull
    @Override
    public String getId() {
        return NODE_ID;
    }

    @NotNull
    @Override
    public String getLabel() {
        return "General checks";
    }

    @Nullable
    @Override
    public String getIconUrl() {
        return "/polarion/xml-repair-app/ui/images/menu/16x16/general_checks.svg";
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
