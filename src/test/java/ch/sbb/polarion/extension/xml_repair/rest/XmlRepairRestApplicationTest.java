package ch.sbb.polarion.extension.xml_repair.rest;

import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class, CurrentContextExtension.class})
class XmlRepairRestApplicationTest {

    @Test
    void testAppInstantiation() {
        try {
            XmlRepairRestApplication application = new XmlRepairRestApplication();
            assertDoesNotThrow(application::getExtensionControllerClasses);
        } finally {
            NamedSettingsRegistry.INSTANCE.getAll().clear();
        }
    }

}
