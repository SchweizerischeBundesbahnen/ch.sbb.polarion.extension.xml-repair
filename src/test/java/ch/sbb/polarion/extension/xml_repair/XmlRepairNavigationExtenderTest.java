package ch.sbb.polarion.extension.xml_repair;

import com.polarion.alm.ui.server.navigation.NavigationExtenderNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.polarion.subterra.base.data.identification.IContextId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XmlRepairNavigationExtenderTest {

    private final XmlRepairNavigationExtender extender = new XmlRepairNavigationExtender();

    @Test
    void testGetId() {
        assertEquals("xml-repair", extender.getId());
    }

    @Test
    void testGetLabel() {
        assertEquals("XML-Repair", extender.getLabel());
    }

    @Test
    void testGetIconUrl() {
        assertEquals("/polarion/xml-repair-app/ui/images/menu/30x30/_parent.svg", extender.getIconUrl());
    }

    @Test
    void testGetPageUrl() {
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
        assertFalse(extender.requiresToken());
    }

    @Test
    void testGetRootNodes() {
        List<NavigationExtenderNode> rootNodes = extender.getRootNodes(contextId("myProject"));

        assertEquals(List.of("general-checks", "purge-outdated-data"), rootNodes.stream().map(NavigationExtenderNode::getId).toList());
    }

    /**
     * The node ids also address the React pages, so they are asserted literally rather than through the
     * constants - a rename has to fail here and be carried over to ui/src/navigation.ts.
     */
    @ParameterizedTest
    @CsvSource({
            "0, general-checks, General checks, general_checks.svg",
            "1, purge-outdated-data, Purge outdated data, purge.svg"
    })
    void testRootNode(int index, String expectedId, String expectedLabel, String expectedIconFile) {
        NavigationExtenderNode node = extender.getRootNodes(contextId("myProject")).get(index);

        assertEquals(expectedId, node.getId());
        assertEquals(expectedLabel, node.getLabel());
        assertEquals("/polarion/xml-repair-app/ui/images/menu/16x16/" + expectedIconFile, node.getIconUrl());
        assertEquals("/polarion/xml-repair-app/ui/app/index.html?feature=%s&embedded=true&projectId=myProject".formatted(expectedId),
                node.getPageUrl(contextId("myProject")));
        assertFalse(node.requiresToken());
        assertTrue(node.getChildren().isEmpty());
    }

    private IContextId contextId(String contextName) {
        IContextId contextId = mock(IContextId.class);
        when(contextId.getContextName()).thenReturn(contextName);
        return contextId;
    }
}
