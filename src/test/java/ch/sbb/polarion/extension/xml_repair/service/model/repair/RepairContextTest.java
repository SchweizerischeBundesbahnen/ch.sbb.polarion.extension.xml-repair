package ch.sbb.polarion.extension.xml_repair.service.model.repair;

import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
class RepairContextTest {

    @Test
    void testAccessors() {
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        UserConfigs configs = new UserConfigs();

        RepairContext context = new RepairContext(metaInfo, polarionService, configs, new Cache());

        assertSame(metaInfo, context.issueMetaInfo());
        assertSame(polarionService, context.polarionService());
        assertSame(configs, context.configs());
    }

    @Test
    void testGetAndCacheReturnsValue() {
        RepairContext context = new RepairContext(mock(IssueMetaInfo.class), mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());

        String result = context.getAndCache("key", () -> "value");

        assertEquals("value", result);
    }

    @Test
    void testGetAndCacheCallsCallableOnlyOnce() {
        RepairContext context = new RepairContext(mock(IssueMetaInfo.class), mock(XmlRepairPolarionService.class), new UserConfigs(), new Cache());
        AtomicInteger callCount = new AtomicInteger(0);

        context.getAndCache("key", () -> { callCount.incrementAndGet(); return "value"; });
        context.getAndCache("key", () -> { callCount.incrementAndGet(); return "value"; });

        assertEquals(1, callCount.get());
    }

    @Test
    void testGetAndCacheSharesExternalCache() {
        Cache cache = new Cache();
        RepairContext first = new RepairContext(mock(IssueMetaInfo.class), mock(XmlRepairPolarionService.class), new UserConfigs(), cache);
        RepairContext second = new RepairContext(mock(IssueMetaInfo.class), mock(XmlRepairPolarionService.class), new UserConfigs(), cache);
        AtomicInteger callCount = new AtomicInteger(0);

        String firstResult = first.getAndCache("key", () -> { callCount.incrementAndGet(); return "value"; });
        String secondResult = second.getAndCache("key", () -> { callCount.incrementAndGet(); return "other"; });

        assertEquals("value", firstResult);
        assertEquals("value", secondResult);
        assertEquals(1, callCount.get());
    }
}
