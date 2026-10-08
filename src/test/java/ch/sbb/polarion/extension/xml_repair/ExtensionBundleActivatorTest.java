package ch.sbb.polarion.extension.xml_repair;

import ch.sbb.polarion.extension.xml_repair.service.RepairJobsService;
import ch.sbb.polarion.extension.xml_repair.service.ScanJobsService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.osgi.framework.BundleContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class ExtensionBundleActivatorTest {

    @Test
    void testRegistersNoFormExtension() {
        assertTrue(new ExtensionBundleActivator().getExtensions().isEmpty());
    }

    @Test
    void testStopShutsTheJobsDown() {
        // mocked, so the registries the other tests run on stay alive
        try (MockedStatic<ScanJobsService> scanJobs = mockStatic(ScanJobsService.class);
             MockedStatic<RepairJobsService> repairJobs = mockStatic(RepairJobsService.class)) {
            new ExtensionBundleActivator().stop(mock(BundleContext.class));

            scanJobs.verify(ScanJobsService::shutdown);
            repairJobs.verify(RepairJobsService::shutdown);
        }
    }
}
