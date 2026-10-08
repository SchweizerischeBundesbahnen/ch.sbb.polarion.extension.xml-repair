package ch.sbb.polarion.extension.xml_repair.service;

import ch.sbb.polarion.extension.generic.jobs.JobControl;
import ch.sbb.polarion.extension.generic.jobs.JobsRegistry;
import ch.sbb.polarion.extension.generic.rest.model.jobs.JobStatus;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairParams;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import com.polarion.alm.shared.api.transaction.RunnableInWriteTransaction;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.shared.api.transaction.WriteTransaction;
import com.polarion.platform.security.ISecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.security.auth.Subject;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// the platform must be mocked before Mockito mocks the polarion service, whose superclass looks it up
@ExtendWith({PlatformContextMockExtension.class, MockitoExtension.class})
class RepairJobsServiceTest {

    private static final String TEST_USER = "testUser";

    @Mock
    private XmlRepairPolarionService polarionService;

    @Mock
    private ISecurityService securityService;

    @Mock
    private Subject subject;

    private JobsRegistry<Void, List<RepairResult>> registry;
    private RepairJobsService repairJobsService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = RepairJobsService.registryBuilder().build();
        // a spy, so that no job opens a real transaction on its worker thread
        repairJobsService = spy(new RepairJobsService(polarionService, securityService, registry));
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

    @Test
    void testJobsProperties() {
        assertEquals(60, RepairJobsService.jobsProperties().getInProgressJobTimeout());
        assertEquals(30, RepairJobsService.jobsProperties().getFinishedJobTimeout());
    }

    @Test
    void testSuccessfulRepair() {
        List<RepairResult> expected = List.of(mock(RepairResult.class));
        doAnswer(invocation -> {
            invocation.<JobControl>getArgument(1).reportProgress("1 of 1");
            return expected;
        }).when(repairJobsService).runRepair(any(), any());

        String jobId = repairJobsService.startJob(new RepairParams());

        waitUntilOver(jobId);
        assertEquals(JobStatus.SUCCESSFULLY_FINISHED, repairJobsService.getJobState(jobId).status());
        assertEquals("1 of 1", repairJobsService.getJobState(jobId).progressMessage());
        assertEquals(Optional.of(expected), repairJobsService.getJobResult(jobId));
    }

    @Test
    void testRepairFailedAtCommit() {
        doAnswer(invocation -> {
            throw new IllegalStateException("Resource conflict detected during commit");
        }).when(repairJobsService).runRepair(any(), any());

        String jobId = repairJobsService.startJob(new RepairParams());

        waitUntilOver(jobId);
        assertEquals(JobStatus.FAILED, repairJobsService.getJobState(jobId).status());
        assertEquals("Resource conflict detected during commit", repairJobsService.getJobState(jobId).errorMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRunRepairReportsSavingAndClearsCachesAfterCommit() {
        RepairParams params = new RepairParams();
        List<RepairResult> results = List.of(mock(RepairResult.class));
        JobControl control = mock(JobControl.class);
        when(polarionService.repair(eq(params), any())).thenAnswer(invocation -> {
            invocation.<Consumer<String>>getArgument(1).accept("1 of 1");
            return results;
        });

        try (MockedStatic<TransactionalExecutor> executor = mockStatic(TransactionalExecutor.class)) {
            executor.when(() -> TransactionalExecutor.executeInWriteTransaction(any()))
                    .thenAnswer(invocation -> invocation.<RunnableInWriteTransaction<?>>getArgument(0).run(mock(WriteTransaction.class)));

            assertSame(results, repairJobsService.runRepair(params, control));
        }

        InOrder order = inOrder(control, polarionService);
        order.verify(control).reportProgress("1 of 1");
        order.verify(control).reportProgress(RepairJobsService.SAVING_PROGRESS);
        order.verify(polarionService).clearStaleCaches(results);
    }

    @Test
    void testRunRepairKeepsCachesWhenTheCommitFails() {
        try (MockedStatic<TransactionalExecutor> executor = mockStatic(TransactionalExecutor.class)) {
            executor.when(() -> TransactionalExecutor.executeInWriteTransaction(any()))
                    .thenThrow(new IllegalStateException("Resource conflict detected during commit"));

            RepairParams params = new RepairParams();
            JobControl control = mock(JobControl.class);
            assertThrows(IllegalStateException.class, () -> repairJobsService.runRepair(params, control));
        }

        verify(polarionService, never()).clearStaleCaches(any());
    }

    @Test
    void testServiceOfTheExtension() {
        assertNotNull(new RepairJobsService(polarionService, securityService));
        assertDoesNotThrow(RepairJobsService::startCleaner);
    }

    @Test
    void testRunRepairWithoutResult() {
        try (MockedStatic<TransactionalExecutor> executor = mockStatic(TransactionalExecutor.class)) {
            executor.when(() -> TransactionalExecutor.executeInWriteTransaction(any())).thenReturn(null);

            assertNull(repairJobsService.runRepair(new RepairParams(), mock(JobControl.class)));
        }
        verify(polarionService, never()).clearStaleCaches(any());
    }

    private void waitUntilOver(String jobId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (repairJobsService.getJobState(jobId).status() == JobStatus.IN_PROGRESS) {
            assertTrue(System.nanoTime() < deadline, "Repair job is not over in time");
            Thread.onSpinWait();
        }
    }
}
