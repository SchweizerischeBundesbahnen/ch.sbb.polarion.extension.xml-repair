package ch.sbb.polarion.extension.xml_repair;

import ch.sbb.polarion.extension.generic.GenericUiServlet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class XmlRepairAppServletTest {

    @Test
    void instantiatesAsGenericUiServlet() {
        XmlRepairAppServlet servlet = new XmlRepairAppServlet();

        assertInstanceOf(GenericUiServlet.class, servlet);
    }
}
