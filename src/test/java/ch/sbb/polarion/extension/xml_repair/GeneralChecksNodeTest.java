package ch.sbb.polarion.extension.xml_repair;

import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneralChecksNodeTest {

    private final GeneralChecksNode node = new GeneralChecksNode();

    @Test
    void testGetId() {
        assertEquals("general-checks", node.getId());
    }

    @Test
    void testGetLabel() {
        assertEquals("General checks", node.getLabel());
    }

    @Test
    void testGetIconUrl() {
        assertEquals("/polarion/xml-repair-app/ui/images/menu/16x16/general_checks.svg", node.getIconUrl());
    }

    @Test
    void testGetPageUrl() {
        IContextId contextId = mock(IContextId.class);
        when(contextId.getContextName()).thenReturn("myProject");

        assertEquals("/polarion/xml-repair-app/ui/app/index.html?feature=general-checks&embedded=true&projectId=myProject",
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
