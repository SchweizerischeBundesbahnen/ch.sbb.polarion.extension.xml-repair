package ch.sbb.polarion.extension.xml_repair.service.model.scan;

import ch.sbb.polarion.extension.xml_repair.service.EntityRenderer;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollectionElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class ScanContextTest {

    private ScanContext createScanContext(XmlRepairPolarionService polarionService, List<String> repairers, UserConfigs configs, Report report) {
        InternalReadOnlyTransaction transaction = mock(InternalReadOnlyTransaction.class);
        ITrackerService trackerService = mock(ITrackerService.class);
        when(polarionService.getTrackerService()).thenReturn(trackerService);

        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored = mockConstruction(EntityRenderer.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(transaction);
            return new ScanContext(polarionService, repairers, configs, report);
        }
    }

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
        assertNotNull(context.globalWarnings());
        assertTrue(context.globalWarnings().isEmpty());
    }

    @Test
    void testTimeoutSetterReturnsSelf() {
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), new Report());

        ScanContext result = context.timeout(5000);

        assertSame(context, result);
    }

    @Test
    void testTimeoutNotReachedWhenTimeoutIsZero() {
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), new Report());

        // Default timeout is 0, so timeoutReached should return false
        assertFalse(context.timeoutReached());
    }

    @Test
    void testTimeoutNotReachedWhenTimeIsWithinLimit() {
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), new Report());

        // Set a very large timeout so it won't be reached
        context.timeout(Long.MAX_VALUE);

        assertFalse(context.timeoutReached());
    }

    @Test
    @SuppressWarnings("java:S2925") // allow Thread.sleep here
    void testTimeoutReachedWhenTimeExceedsLimit() {
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        Report report = new Report();
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), report);

        // Set timeout to 1ms - by the time we check, it will be exceeded
        context.timeout(1);

        // Wait a tiny bit to ensure stopwatch exceeds 1ms
        try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        assertTrue(context.timeoutReached());
        // Verify warning was added
        assertFalse(context.globalWarnings().isEmpty());
        assertTrue(context.globalWarnings().stream().anyMatch(w -> w.contains("timeout")));
        assertTrue(report.toString().contains("timeout"));
    }

    @Test
    @SuppressWarnings("java:S2925") // allow Thread.sleep here
    void testTimeoutReachedReturnsTrueOnSubsequentCalls() {
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        Report report = new Report();
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), report);

        context.timeout(1);
        try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // First call triggers the timeout
        assertTrue(context.timeoutReached());
        // Second call should still return true (via AtomicBoolean shortcut)
        assertTrue(context.timeoutReached());

        // Warning should only appear once (Set deduplicates)
        assertEquals(1, context.globalWarnings().size());
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

    @Test
    void testGlobalWarningsIsLinkedHashSet() {
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        ScanContext context = createScanContext(polarionService, List.of(), new UserConfigs(), new Report());

        Set<String> warnings = context.globalWarnings();
        warnings.addAll(List.of("warning1", "warning2", "warning1"));

        assertEquals(2, warnings.size());
        // LinkedHashSet preserves insertion order
        assertEquals(List.of("warning1", "warning2"), List.copyOf(warnings));
    }
}
