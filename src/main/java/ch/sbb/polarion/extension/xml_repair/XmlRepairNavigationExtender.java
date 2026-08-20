package ch.sbb.polarion.extension.xml_repair;

import com.polarion.alm.ui.server.navigation.NavigationExtender;
import com.polarion.alm.ui.server.navigation.NavigationExtenderNode;
import com.polarion.subterra.base.data.identification.IContextId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class XmlRepairNavigationExtender extends NavigationExtender {

    public static final String ID = "xml-repair";
    public static final String LABEL = "XML-Repair";

    /** The React page behind the root node: links to the two pages below it. */
    static final String HOME_FEATURE = "home";

    /**
     * The ids of the pages below the root node. Each one is both a {@code ?feature=} value and the id of a
     * {@link XmlRepairNavigationNode}, and must stay equal to the matching entry of
     * {@code ui/src/navigation.ts}: the node puts its own id into the URL, and the React home page appends it
     * to the portal's topic path to select the node in the tree.
     */
    public static final String GENERAL_CHECKS = "general-checks";
    public static final String PURGE_OUTDATED_DATA = "purge-outdated-data";

    private static final String MENU_ICONS = "/polarion/xml-repair-app/ui/images/menu/";

    /**
     * The React page of one navigation node. There is a single index.html and bundle; which page it renders
     * comes from {@code ?feature=}, whose values are the node ids - the same arrangement the admin pages in
     * hivemodule.xml use.
     * <p>
     * {@code projectId} stays empty outside a project scope, where the context name is either null or a
     * project group name, which Polarion prefixes with a dash.
     */
    @NotNull
    static String navPageUrl(@NotNull String feature, @Nullable IContextId contextId) {
        String contextName = contextId == null ? null : contextId.getContextName();
        String projectId = contextName == null || contextName.startsWith("-") ? "" : contextName;
        return "/polarion/xml-repair-app/ui/app/index.html?feature=%s&embedded=true&projectId=%s".formatted(feature, projectId);
    }

    @NotNull
    @Override
    public String getId() {
        return ID;
    }

    @NotNull
    @Override
    public String getLabel() {
        return LABEL;
    }

    @Nullable
    @Override
    public String getIconUrl() {
        return MENU_ICONS + "30x30/_parent.svg";
    }

    @Nullable
    @Override
    public String getPageUrl(@NotNull IContextId contextId) {
        return navPageUrl(HOME_FEATURE, contextId);
    }

    @Override
    public boolean requiresToken() {
        return false;
    }

    @NotNull
    @Override
    public List<NavigationExtenderNode> getRootNodes(@NotNull IContextId contextId) {
        return List.of(
                new XmlRepairNavigationNode(GENERAL_CHECKS, "General checks", MENU_ICONS + "16x16/general_checks.svg"),
                new XmlRepairNavigationNode(PURGE_OUTDATED_DATA, "Purge outdated data", MENU_ICONS + "16x16/purge.svg")
        );
    }
}
