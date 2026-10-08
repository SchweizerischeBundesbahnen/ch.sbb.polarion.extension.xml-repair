package ch.sbb.polarion.extension.xml_repair.service.model.scan;

import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollectionElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class ScanContextTest {

    @Test
    void testAccessors() {
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        UserConfigs configs = new UserConfigs();
        Report report = new Report();
        List<String> repairers = List.of("r1", "r2");

        ScanContext context = createScanContext(polarionService, repairers, configs, report);

        assertSame(polarionService, context.polarionService());
        assertEquals(repairers, context.repairers());
        assertSame(configs, context.configs());
        assertSame(report, context.report());
        assertNotNull(context.entityRenderer());
    }

    @Test
    void testStopNotRequestedWithoutControl() {
        ScanContext context = createScanContext(mock(XmlRepairPolarionService.class), List.of(), new UserConfigs(), new Report());

        assertFalse(context.stopRequested());
    }

    @Test
    void testStopRequestedByControl() {
        ScanContext context = createScanContext(mock(XmlRepairPolarionService.class), List.of(), new UserConfigs(), new Report());
        ScanControl control = mock(ScanControl.class);

        assertSame(context, context.control(control));
        assertFalse(context.stopRequested());

        when(control.stopReason()).thenReturn("Stopped");
        assertTrue(context.stopRequested());
    }

    @Test
    void testCollectionDocumentsLazyLoading() {
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), new Report());

        IBaselineCollection collection = mock(IBaselineCollection.class);
        IBaselineCollectionElement moduleElement = mock(IBaselineCollectionElement.class);
        IBaselineCollectionElement nonModuleElement = mock(IBaselineCollectionElement.class);
        IModule module = mock(IModule.class);
        Object nonModule = mock(IBaselineCollection.class);

        when(collection.getElements()).thenReturn(List.of(moduleElement, nonModuleElement));
        when(moduleElement.getObjectWithRevision()).thenReturn(module);
        when(nonModuleElement.getObjectWithRevision()).thenReturn(nonModule);

        List<IModule> result = context.collectionDocuments(collection);

        assertEquals(1, result.size());
        assertSame(module, result.getFirst());
    }

    @Test
    void testCollectionDocumentsCaching() {
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), new Report());

        IBaselineCollection collection = mock(IBaselineCollection.class);
        IBaselineCollectionElement element = mock(IBaselineCollectionElement.class);
        IModule module = mock(IModule.class);

        when(collection.getElements()).thenReturn(List.of(element));
        when(element.getObjectWithRevision()).thenReturn(module);

        List<IModule> first = context.collectionDocuments(collection);
        List<IModule> second = context.collectionDocuments(collection);

        assertSame(first, second);
        // collection.getElements() should only be called once due to caching
        verify(collection, times(1)).getElements();
    }

    @Test
    void testCollectionDocumentsEmptyCollection() {
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), new Report());

        IBaselineCollection collection = mock(IBaselineCollection.class);
        when(collection.getElements()).thenReturn(List.of());

        List<IModule> result = context.collectionDocuments(collection);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAndCacheReturnsValue() {
        ScanContext context = createScanContext(mock(XmlRepairPolarionService.class), List.of(), new UserConfigs(), new Report());

        String result = context.getAndCache("key", () -> "value");

        assertEquals("value", result);
    }

    @Test
    void testGetAndCacheCallsCallableOnlyOnce() {
        ScanContext context = createScanContext(mock(XmlRepairPolarionService.class), List.of(), new UserConfigs(), new Report());
        AtomicInteger callCount = new AtomicInteger(0);

        context.getAndCache("key", () -> { callCount.incrementAndGet(); return "value"; });
        context.getAndCache("key", () -> { callCount.incrementAndGet(); return "value"; });

        assertEquals(1, callCount.get());
    }

    @Test
    void testGetAndCacheCachesNullValue() {
        ScanContext context = createScanContext(mock(XmlRepairPolarionService.class), List.of(), new UserConfigs(), new Report());
        AtomicInteger callCount = new AtomicInteger(0);

        Object first = context.getAndCache("key", () -> { callCount.incrementAndGet(); return null; });
        Object second = context.getAndCache("key", () -> { callCount.incrementAndGet(); return null; });

        assertNull(first);
        assertNull(second);
        assertEquals(1, callCount.get());
    }

    @Test
    void testGetAndCacheIsolatesKeys() {
        ScanContext context = createScanContext(mock(XmlRepairPolarionService.class), List.of(), new UserConfigs(), new Report());

        String a = context.getAndCache("keyA", () -> "alpha");
        String b = context.getAndCache("keyB", () -> "beta");

        assertEquals("alpha", a);
        assertEquals("beta", b);
    }
}
