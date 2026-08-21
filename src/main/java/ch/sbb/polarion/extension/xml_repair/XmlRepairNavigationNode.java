package ch.sbb.polarion.extension.xml_repair;

import com.polarion.alm.ui.server.navigation.NavigationExtenderNode;
import com.polarion.subterra.base.data.identification.IContextId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A leaf page below the XML-Repair navigation node. Such pages differ only in their id, label and icon, so they
 * share this one implementation rather than a class each.
 * <p>
 * The id doubles as the {@code ?feature=} id of the React page, so it must stay equal to the matching entry of
 * {@code ui/src/navigation.ts}. {@link XmlRepairNavigationExtender#getRootNodes} names the ones in use.
 */
public class XmlRepairNavigationNode extends NavigationExtenderNode {

    private final @NotNull String id;
    private final @NotNull String label;
    private final @NotNull String iconUrl;

    public XmlRepairNavigationNode(@NotNull String id, @NotNull String label, @NotNull String iconUrl) {
        this.id = id;
        this.label = label;
        this.iconUrl = iconUrl;
    }

    @NotNull
    @Override
    public String getId() {
        return id;
    }

    @NotNull
    @Override
    public String getLabel() {
        return label;
    }

    @Nullable
    @Override
    public String getIconUrl() {
        return iconUrl;
    }

    @Nullable
    @Override
    public String getPageUrl(IContextId contextId) {
        return XmlRepairNavigationExtender.navPageUrl(id, contextId);
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
