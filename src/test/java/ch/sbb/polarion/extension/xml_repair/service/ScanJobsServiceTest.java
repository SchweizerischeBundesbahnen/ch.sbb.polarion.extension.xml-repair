package ch.sbb.polarion.extension.xml_repair.service;

import ch.sbb.polarion.extension.generic.jobs.JobControl;
import ch.sbb.polarion.extension.generic.jobs.JobsRegistry;
import ch.sbb.polarion.extension.generic.rest.model.jobs.JobStatus;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanControl;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanResult;
import com.polarion.alm.shared.api.transaction.ReadOnlyTransaction;
import com.polarion.alm.shared.api.transaction.RunnableInReadOnlyTransaction;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.platform.security.ISecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.security.auth.Subject;
import java.security.PrivilegedAction;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// the platform must be mocked before Mockito mocks the polarion service, whose superclass looks it up
@ExtendWith({PlatformContextMockExtension.class, MockitoExtension.class})
class ScanJobsServiceTest {

    private static final String TEST_USER = "testUser";

    @Mock
    private XmlRepairPolarionService polarionService;

    @Mock
    private ISecurityService securityService;

    @Mock
    private Subject subject;

    private JobsRegistry<ScanJobsService.JobPayload, ScanResult> registry;
    private ScanJobsService scanJobsService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        scanJobsService = jobsService(TimeUnit.MINUTES);
        lenient().when(securityService.getCurrentUser()).thenReturn(TEST_USER);
        lenient().when(securityService.getCurrentSubject()).thenReturn(subject);
        lenient().when(securityService.doAsUser(eq(subject), any(PrivilegedAction.class)))
                .thenAnswer(invocation -> ((PrivilegedAction<?>) invocation.getArgument(1)).run());
    }

    @AfterEach
    void tearDown() {
        registry.clear();
        registry.shutdown();
    }

    /**
     * A spy over a registry of its own, so that no test leaves jobs behind and no scan opens a real transaction.
     */
    private ScanJobsService jobsService(TimeUnit timeoutUnit) {
        if (registry != null) {
            registry.shutdown();
        }
        registry = ScanJobsService.registryBuilder().timeoutUnit(timeoutUnit).build();
        return spy(new ScanJobsService(polarionService, securityService, registry));
    }

    @Test
    void testJobsProperties() {
        assertEquals(60, ScanJobsService.jobsProperties().getInProgressJobTimeout());
        assertEquals(30, ScanJobsService.jobsProperties().getFinishedJobTimeout());
    }

    @Test
    void testSuccessfulScan() {
        ScanResult expected = new ScanResult();
        doAnswer(invocation -> {
            invocation.<ScanControl>getArgument(1).reportProgress("1 item scanned, 1 with issues");
            return expected;
        }).when(scanJobsService).runScan(any(), any());

        String jobId = scanJobsService.startJob(new ScanParams());

        waitUntilOver(jobId);
        assertEquals(JobStatus.SUCCESSFULLY_FINISHED, scanJobsService.getJobState(jobId).status());
        assertEquals("1 item scanned, 1 with issues", scanJobsService.getJobState(jobId).progressMessage());
        assertEquals(Optional.of(expected), scanJobsService.getJobResult(jobId));
    }

    @Test
    void testFailedScan() {
        doAnswer(invocation -> {
            throw new IllegalStateException("Broken index");
        }).when(scanJobsService).runScan(any(), any());

        String jobId = scanJobsService.startJob(new ScanParams());

        waitUntilOver(jobId);
        assertEquals(JobStatus.FAILED, scanJobsService.getJobState(jobId).status());
        assertEquals("Broken index", scanJobsService.getJobState(jobId).errorMessage());
        assertThrows(IllegalStateException.class, () -> scanJobsService.getJobResult(jobId));
    }

    @Test
    void testStoppedScanKeepsItsResult() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        doAnswer(invocation -> runUntilStopped(invocation.getArgument(1), started)).when(scanJobsService).runScan(any(), any());

        String jobId = scanJobsService.startJob(new ScanParams());
        assertTrue(started.await(10, TimeUnit.SECONDS));
        scanJobsService.cancelJob(jobId);

        waitUntilOver(jobId);
        assertEquals(JobStatus.SUCCESSFULLY_FINISHED, scanJobsService.getJobState(jobId).status());
        assertEquals(ScanJobsService.STOPPED_BY_USER_WARNING, scanJobsService.getJobResult(jobId).orElseThrow().getReport());
    }

    @Test
    void testScanOutOfTimeKeepsItsResult() {
        scanJobsService = jobsService(TimeUnit.MILLISECONDS);
        doAnswer(invocation -> runUntilStopped(invocation.getArgument(1), new CountDownLatch(1))).when(scanJobsService).runScan(any(), any());

        String jobId = scanJobsService.startJob(new ScanParams(), 50);

        waitUntilOver(jobId);
        assertEquals(JobStatus.SUCCESSFULLY_FINISHED, scanJobsService.getJobState(jobId).status());
        assertEquals(ScanJobsService.JOB_TIMEOUT_WARNING.formatted(50), scanJobsService.getJobResult(jobId).orElseThrow().getReport());
    }

    @Test
    void testStopOfUnknownJob() {
        assertThrows(NoSuchElementException.class, () -> scanJobsService.cancelJob("unknown"));
    }

    @Test
    void testScanControl() {
        JobControl control = mock(JobControl.class);
        ScanJobsService.JobPayload payload = new ScanJobsService.JobPayload(new AtomicBoolean());
        ScanControl scanControl = ScanJobsService.scanControl(control, payload, 15);

        assertNull(scanControl.stopReason());

        when(control.isAbortRequested()).thenReturn(true);
        assertEquals(ScanJobsService.JOB_TIMEOUT_WARNING.formatted(15), scanControl.stopReason());

        payload.stoppedByUser().set(true);
        assertEquals(ScanJobsService.STOPPED_BY_USER_WARNING, scanControl.stopReason());

        scanControl.reportProgress("progress");
        verify(control).reportProgress("progress");
    }

    private static ScanResult runUntilStopped(ScanControl control, CountDownLatch started) {
        started.countDown();
        String stopReason = control.stopReason();
        while (stopReason == null) {
            Thread.onSpinWait();
            stopReason = control.stopReason();
        }
        ScanResult result = new ScanResult();
        result.setReport(stopReason);
        return result;
    }

    @Test
    void testServiceOfTheExtension() {
        assertNotNull(new ScanJobsService(polarionService, securityService));
        assertDoesNotThrow(ScanJobsService::startCleaner);
    }

    @Test
    void testRunScanOpensAReadOnlyTransaction() {
        ScanParams params = new ScanParams();
        ScanResult expected = new ScanResult();
        when(polarionService.scan(params, ScanControl.NONE)).thenReturn(expected);

        try (MockedStatic<TransactionalExecutor> executor = mockStatic(TransactionalExecutor.class)) {
            executor.when(() -> TransactionalExecutor.executeInReadOnlyTransaction(any()))
                    .thenAnswer(invocation -> invocation.<RunnableInReadOnlyTransaction<?>>getArgument(0).run(mock(ReadOnlyTransaction.class)));

            assertSame(expected, scanJobsService.runScan(params, ScanControl.NONE));
        }
    }

    private void waitUntilOver(String jobId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (scanJobsService.getJobState(jobId).status() == JobStatus.IN_PROGRESS) {
            assertTrue(System.nanoTime() < deadline, "Scan job is not over in time");
            Thread.onSpinWait();
        }
    }
}
