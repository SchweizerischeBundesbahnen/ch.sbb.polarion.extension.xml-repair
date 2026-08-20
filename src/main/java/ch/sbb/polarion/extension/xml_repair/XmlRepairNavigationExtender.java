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

    /** The React page behind the root node: links to the two children below. */
    static final String HOME_FEATURE = "home";

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
        return "/polarion/xml-repair-app/ui/images/menu/30x30/_parent.svg";
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
        return List.of(new GeneralChecksNode(), new PurgeOutdatedDataNode());
    }
}
