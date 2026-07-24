package ch.sbb.polarion.extension.xml_repair;

import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.Test;

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
        assertEquals("/polarion/xml-repair-admin/ui/images/menu/30x30/_parent.svg", extender.getIconUrl());
    }

    @Test
    void testGetPageUrl() {
        XmlRepairNavigationExtender extender = new XmlRepairNavigationExtender();
        IContextId contextId = mock(IContextId.class);
        when(contextId.getContextName()).thenReturn("myProject");

        assertEquals("/polarion/xml-repair-app/ui/app/index.html?feature=repair&embedded=true&projectId=myProject", extender.getPageUrl(contextId));
    }

    @Test
    void testRequiresToken() {
        XmlRepairNavigationExtender extender = new XmlRepairNavigationExtender();
        assertFalse(extender.requiresToken());
    }

    @Test
    void testGetRootNodes() {
        XmlRepairNavigationExtender extender = new XmlRepairNavigationExtender();
        IContextId contextId = mock(IContextId.class);
        assertTrue(extender.getRootNodes(contextId).isEmpty());
    }
}
