package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
import ch.sbb.polarion.extension.xml_repair.service.EntityRenderer;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
class ModuleContentLinksRepairerTest {

    private ScanContext createScanContext(XmlRepairPolarionService polarionService) {
        lenient().when(polarionService.getTrackerService()).thenReturn(mock(ITrackerService.class));
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored = mockConstruction(EntityRenderer.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(mock(InternalReadOnlyTransaction.class));
            return new ScanContext(polarionService, List.of(), new UserConfigs(), new Report());
        }
    }

    @Test
    void testScanFindsBrokenLinks() {
        ModuleContentLinksRepairer repairer = new ModuleContentLinksRepairer();

        IModule entity = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        //language=HTML
        when(entity.getHomePageContent().getContent()).thenReturn("""
                <span class="polarion-rte-link" data-type="workItem" data-item-id="EL-1" data-custom-label="EL-1" data-scope="drivepilot" data-option-id="long"></span>
                <span class="polarion-rte-link" data-type="workItem" data-scope="drivepilot" data-custom-label="EL-2" data-item-id="EL-2" data-option-id="long"></span>
                <span class="polarion-rte-link" data-type="workItem" data-item-id="EL-3" data-custom-label="EL-3" data-scope="elibrary" data-option-id="long"></span>
                <span class="polarion-rte-link" data-type="workItem" data-item-id="EL-4" data-custom-label="EL-4" data-option-id="long"></span>
                """);
        when(entity.getContextId()).thenReturn(mock(IContextId.class));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(eq("drivepilot"), anyString(), isNull())).thenReturn(false);
        when(polarionService.isWorkItemExists(eq("elibrary"), anyString(), isNull())).thenReturn(true);

        ScanContext scanContext = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(entity, scanContext);

        assertEquals(2, issues.size());
        assertTrue(issues.stream().map(Issue::getDescription).toList().containsAll(List.of(
                "Broken link found: workitem 'EL-1' does not exist in the project 'drivepilot'.",
                "Broken link found: workitem 'EL-2' does not exist in the project 'drivepilot'."
        )));
    }

    @Test
    void testScanNoIssuesWhenLinksValid() {
        ModuleContentLinksRepairer repairer = new ModuleContentLinksRepairer();

        IModule entity = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(entity.getProjectId()).thenReturn("elibrary");
        //language=HTML
        when(entity.getHomePageContent().getContent()).thenReturn("""
                <span class="polarion-rte-link" data-type="workItem" data-item-id="EL-1" data-custom-label="EL-1" data-scope="elibrary" data-option-id="long"></span>
                <span class="polarion-rte-link" data-type="workItem" data-item-id="EL-2" data-custom-label="EL-2" data-option-id="long"></span>
                """);
        when(entity.getContextId()).thenReturn(mock(IContextId.class));

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        when(polarionService.isWorkItemExists(eq("elibrary"), anyString(), isNull())).thenReturn(true);

        ScanContext scanContext = createScanContext(polarionService);
        List<Issue> issues = repairer.scan(entity, scanContext);

        assertTrue(issues.isEmpty());
    }

    @Test
    void testGetConfigs() {
        ModuleContentLinksRepairer repairer = new ModuleContentLinksRepairer();
        List<RepairerConfigMeta> configs = repairer.getConfigs();

        assertEquals(1, configs.size());
        RepairerConfigMeta config = configs.get(0);
        assertEquals(BaseLinksRepairer.CONVERT_TO_PLAIN_TEXT, config.getKey());
        assertEquals("Convert unresolvable links to plain text", config.getDescription());
        assertEquals(RepairerConfigType.BOOLEAN, config.getType());
        assertEquals(false, config.getDefaultValue());
    }
}
