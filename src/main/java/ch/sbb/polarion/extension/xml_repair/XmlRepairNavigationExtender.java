package ch.sbb.polarion.extension.xml_repair;

import com.polarion.alm.ui.server.navigation.NavigationExtender;
import com.polarion.alm.ui.server.navigation.NavigationExtenderNode;
import com.polarion.subterra.base.data.identification.IContextId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class XmlRepairNavigationExtender extends NavigationExtender {

    public static final String ID = "xml-repair";
    public static final String LABEL = "XML-Repair";

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
        return "/polarion/xml-repair-admin/ui/images/menu/30x30/_parent.svg";
    }

    @Nullable
    @Override
    public String getPageUrl(@NotNull IContextId contextId) {
        return "/polarion/xml-repair-app/ui/app/index.html?feature=repair&embedded=true&projectId=" + contextId.getContextName();
    }

    @Override
    public boolean requiresToken() {
        return false;
    }

    @NotNull
    @Override
    public List<NavigationExtenderNode> getRootNodes(@NotNull IContextId contextId) {
        return Collections.emptyList();
    }
}
