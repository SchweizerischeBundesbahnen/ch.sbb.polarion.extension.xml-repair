package ch.sbb.polarion.extension.xml_repair;

import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurgeOutdatedDataNodeTest {

    private final PurgeOutdatedDataNode node = new PurgeOutdatedDataNode();

    @Test
    void testGetId() {
        assertEquals("purge-outdated-data", node.getId());
    }

    @Test
    void testGetLabel() {
        assertEquals("Purge outdated data", node.getLabel());
    }

    @Test
    void testGetIconUrl() {
        assertEquals("/polarion/xml-repair-app/ui/images/menu/16x16/purge.svg", node.getIconUrl());
    }

    @Test
    void testGetPageUrl() {
        IContextId contextId = mock(IContextId.class);
        when(contextId.getContextName()).thenReturn("myProject");

        assertEquals("/polarion/xml-repair-app/ui/app/index.html?feature=purge-outdated-data&embedded=true&projectId=myProject",
                node.getPageUrl(contextId));
    }

    @Test
    void testRequiresToken() {
        assertFalse(node.requiresToken());
    }

    @Test
    void testGetChildren() {
        assertTrue(node.getChildren().isEmpty());
    }
}
