package ch.sbb.polarion.extension.xml_repair.rest;

import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.service.ScanJobsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mockStatic;

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

    @Test
    void testAppStartsWhenAJobsCleanerCannotStart() {
        try (MockedStatic<ScanJobsService> scanJobs = mockStatic(ScanJobsService.class)) {
            scanJobs.when(ScanJobsService::startCleaner).thenThrow(new IllegalStateException("Missing property"));

            assertDoesNotThrow(XmlRepairRestApplication::new);
        } finally {
            NamedSettingsRegistry.INSTANCE.getAll().clear();
        }
    }

}
