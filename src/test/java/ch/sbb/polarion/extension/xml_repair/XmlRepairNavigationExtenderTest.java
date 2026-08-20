package ch.sbb.polarion.extension.xml_repair;

import com.polarion.alm.ui.server.navigation.NavigationExtenderNode;
import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XmlRepairNavigationExtenderTest {

    @Test
    void testGetId() {
        XmlRepairNavigationExtender extender = new XmlRepairNavigationExtender();
        assertEquals("xml-repair", extender.getId());
    }

    @Test
    void testGetLabel() {
        XmlRepairNavigationExtender extender = new XmlRepairNavigationExtender();
        assertEquals("XML-Repair", extender.getLabel());
    }

    @Test
    void testGetIconUrl() {
        XmlRepairNavigationExtender extender = new XmlRepairNavigationExtender();
        assertEquals("/polarion/xml-repair-app/ui/images/menu/30x30/_parent.svg", extender.getIconUrl());
    }

    @Test
    void testGetPageUrl() {
        XmlRepairNavigationExtender extender = new XmlRepairNavigationExtender();

        assertEquals("/polarion/xml-repair-app/ui/app/index.html?feature=home&embedded=true&projectId=myProject",
                extender.getPageUrl(contextId("myProject")));
    }

    @Test
    void testNavPageUrlLeavesProjectIdEmptyOutsideProjectScope() {
        assertEquals("/polarion/xml-repair-app/ui/app/index.html?feature=home&embedded=true&projectId=",
                XmlRepairNavigationExtender.navPageUrl("home", null));
        assertEquals("/polarion/xml-repair-app/ui/app/index.html?feature=home&embedded=true&projectId=",
                XmlRepairNavigationExtender.navPageUrl("home", contextId(null)));
        // Polarion prefixes a project group name with a dash, and a group is not a project id.
        assertEquals("/polarion/xml-repair-app/ui/app/index.html?feature=home&embedded=true&projectId=",
                XmlRepairNavigationExtender.navPageUrl("home", contextId("-myGroup")));
    }

    @Test
    void testRequiresToken() {
        XmlRepairNavigationExtender extender = new XmlRepairNavigationExtender();
        assertFalse(extender.requiresToken());
    }

    @Test
    void testGetRootNodes() {
        XmlRepairNavigationExtender extender = new XmlRepairNavigationExtender();

        List<NavigationExtenderNode> rootNodes = extender.getRootNodes(contextId("myProject"));

        assertEquals(List.of("general-checks", "purge-outdated-data"), rootNodes.stream().map(NavigationExtenderNode::getId).toList());
    }

    private IContextId contextId(String contextName) {
        IContextId contextId = mock(IContextId.class);
        when(contextId.getContextName()).thenReturn(contextName);
        return contextId;
    }
}
