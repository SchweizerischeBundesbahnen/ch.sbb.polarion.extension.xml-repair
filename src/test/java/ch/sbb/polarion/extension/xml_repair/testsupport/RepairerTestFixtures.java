package ch.sbb.polarion.extension.xml_repair.testsupport;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.EntityRenderer;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

public final class RepairerTestFixtures {

    private RepairerTestFixtures() {
    }

    public static Set<FieldMetadata> mockFields(FieldType fieldType, String... ids) {
        return Stream.of(ids).map(id -> {
            FieldMetadata meta = mock(FieldMetadata.class);
            lenient().when(meta.getId()).thenReturn(id);
            lenient().when(meta.getType()).thenReturn(fieldType.getType());
            return meta;
        }).collect(Collectors.toSet());
    }

    public static ScanContext createScanContext(XmlRepairPolarionService polarionService) {
        return createScanContext(polarionService, List.of(), new UserConfigs(), new Report());
    }

    /** For tests that need control over what the cache hands back (e.g. a cached null). */
    public static ScanContext createScanContext(XmlRepairPolarionService polarionService, Cache cache) {
        return createScanContext(polarionService, List.of(), new UserConfigs(), new Report(), cache);
    }

    public static ScanContext createScanContext(XmlRepairPolarionService polarionService, List<String> repairers, UserConfigs configs, Report report) {
        return createScanContext(polarionService, repairers, configs, report, new Cache());
    }

    public static ScanContext createScanContext(XmlRepairPolarionService polarionService, List<String> repairers, UserConfigs configs, Report report, Cache cache) {
        lenient().when(polarionService.getTrackerService()).thenReturn(mock(ITrackerService.class));
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored = mockConstruction(EntityRenderer.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(mock(InternalReadOnlyTransaction.class));
            return new ScanContext(polarionService, repairers, configs, report, cache);
        }
    }
}
