package ch.sbb.polarion.extension.xml_repair.rest.controller;

import ch.sbb.polarion.extension.generic.jobs.JobState;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.service.RepairJobsService;
import ch.sbb.polarion.extension.xml_repair.service.ScanJobsService;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairParams;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanResult;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// the platform must be mocked before Mockito mocks the polarion service, whose superclass looks it up
@ExtendWith({PlatformContextMockExtension.class, MockitoExtension.class})
class InternalControllerTest {

    private static final String JOB_ID = "job-1";
    private static final JobState IN_PROGRESS = new JobState(false, false, false, null, null);
    private static final JobState FINISHED = new JobState(true, false, false, null, null);

    @Mock
    private XmlRepairPolarionService polarionService;

    @Mock
    private ScanJobsService scanJobsService;

    @Mock
    private RepairJobsService repairJobsService;

    @Mock
    private UriInfo uriInfo;

    private InternalController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalController(polarionService, scanJobsService, repairJobsService, uriInfo);
    }

    @Test
    void testScanWaitsForTheJobResult() {
        ScanParams scanParams = new ScanParams();
        ScanResult scanResult = new ScanResult();
        when(scanJobsService.startJob(scanParams)).thenReturn(JOB_ID);
        when(scanJobsService.getJobState(JOB_ID)).thenReturn(IN_PROGRESS, FINISHED);
        when(scanJobsService.getJobResult(JOB_ID)).thenReturn(Optional.of(scanResult));

        try (Response response = controller.scan(scanParams)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertSame(scanResult, response.getEntity());
        }
        verify(polarionService).validateScanParams(scanParams);
    }

    @Test
    void testScanReportsTheFailureOfTheJob() {
        ScanParams scanParams = new ScanParams();
        when(scanJobsService.startJob(scanParams)).thenReturn(JOB_ID);
        when(scanJobsService.getJobState(JOB_ID)).thenReturn(new JobState(true, true, false, null, "Broken index"));

        InternalServerErrorException thrown = assertThrows(InternalServerErrorException.class, () -> controller.scan(scanParams));
        assertEquals("Broken index", thrown.getMessage());
    }

    @Test
    void testScanStopsTheJobWhenInterrupted() {
        ScanParams scanParams = new ScanParams();
        when(scanJobsService.startJob(scanParams)).thenReturn(JOB_ID);
        when(scanJobsService.getJobState(JOB_ID)).thenReturn(IN_PROGRESS);

        Thread.currentThread().interrupt();
        try {
            assertThrows(InternalServerErrorException.class, () -> controller.scan(scanParams));
        } finally {
            assertTrue(Thread.interrupted());
        }
        verify(scanJobsService).cancelJob(JOB_ID);
    }

    @Test
    void testScanRefusesInvalidParamsBeforeStartingAJob() {
        ScanParams scanParams = new ScanParams();
        doThrow(new IllegalArgumentException("Too many entities")).when(polarionService).validateScanParams(scanParams);

        assertThrows(IllegalArgumentException.class, () -> controller.scan(scanParams));
        assertThrows(IllegalArgumentException.class, () -> controller.startScanJob(scanParams));
        verify(scanJobsService, never()).startJob(any());
    }

    @Test
    void testStartScanJob() {
        ScanParams scanParams = new ScanParams();
        when(scanJobsService.startJob(scanParams)).thenReturn(JOB_ID);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost/polarion/xml-repair/rest/internal/scan/jobs"));

        try (Response response = controller.startScanJob(scanParams)) {
            assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());
            assertEquals(URI.create("/polarion/xml-repair/rest/internal/scan/jobs/" + JOB_ID), response.getLocation());
        }
    }

    @Test
    void testScanJobStatusInProgress() {
        when(scanJobsService.getJobState(JOB_ID)).thenReturn(new JobState(false, false, false, "3 items scanned, 1 with issues", null));

        try (Response response = controller.getScanJobStatus(JOB_ID)) {
            assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void testScanJobStatusFinished() {
        when(scanJobsService.getJobState(JOB_ID)).thenReturn(FINISHED);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost/polarion/xml-repair/rest/internal/scan/jobs/" + JOB_ID));

        try (Response response = controller.getScanJobStatus(JOB_ID)) {
            assertEquals(Response.Status.SEE_OTHER.getStatusCode(), response.getStatus());
            assertEquals(URI.create("/polarion/xml-repair/rest/internal/scan/jobs/" + JOB_ID + "/result"), response.getLocation());
        }
    }

    @Test
    void testScanJobStatusFailed() {
        when(scanJobsService.getJobState(JOB_ID)).thenReturn(new JobState(true, true, false, null, "Broken index"));

        try (Response response = controller.getScanJobStatus(JOB_ID)) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void testScanJobResult() {
        ScanResult scanResult = new ScanResult();
        when(scanJobsService.getJobResult(JOB_ID)).thenReturn(Optional.of(scanResult));

        try (Response response = controller.getScanJobResult(JOB_ID)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertSame(scanResult, response.getEntity());
        }
    }

    @Test
    void testScanJobResultInProgress() {
        when(scanJobsService.getJobResult(JOB_ID)).thenReturn(Optional.empty());

        try (Response response = controller.getScanJobResult(JOB_ID)) {
            assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void testStopScanJob() {
        try (Response response = controller.stopScanJob(JOB_ID)) {
            assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }
        verify(scanJobsService).cancelJob(JOB_ID);
    }

    @Test
    void testRepairWaitsForTheJobResult() {
        RepairParams repairParams = new RepairParams();
        List<RepairResult> results = List.of(mock(RepairResult.class));
        when(repairJobsService.startJob(repairParams)).thenReturn(JOB_ID);
        when(repairJobsService.getJobState(JOB_ID)).thenReturn(IN_PROGRESS, FINISHED);
        when(repairJobsService.getJobResult(JOB_ID)).thenReturn(Optional.of(results));

        try (Response response = controller.repair(repairParams)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertSame(results, response.getEntity());
        }
    }

    @Test
    void testRepairReportsTheFailureOfTheJob() {
        RepairParams repairParams = new RepairParams();
        when(repairJobsService.startJob(repairParams)).thenReturn(JOB_ID);
        when(repairJobsService.getJobState(JOB_ID)).thenReturn(new JobState(true, true, false, null, "Resource conflict"));

        InternalServerErrorException thrown = assertThrows(InternalServerErrorException.class, () -> controller.repair(repairParams));
        assertEquals("Resource conflict", thrown.getMessage());
    }

    @Test
    void testRepairKeepsTheJobRunningWhenInterrupted() {
        RepairParams repairParams = new RepairParams();
        when(repairJobsService.startJob(repairParams)).thenReturn(JOB_ID);
        when(repairJobsService.getJobState(JOB_ID)).thenReturn(IN_PROGRESS);

        Thread.currentThread().interrupt();
        try {
            assertThrows(InternalServerErrorException.class, () -> controller.repair(repairParams));
        } finally {
            assertTrue(Thread.interrupted());
        }
        verify(repairJobsService, never()).cancelJob(any());
    }

    @Test
    void testStartRepairJob() {
        RepairParams repairParams = new RepairParams();
        when(repairJobsService.startJob(repairParams)).thenReturn(JOB_ID);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost/polarion/xml-repair/rest/internal/repair/jobs"));

        try (Response response = controller.startRepairJob(repairParams)) {
            assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());
            assertEquals(URI.create("/polarion/xml-repair/rest/internal/repair/jobs/" + JOB_ID), response.getLocation());
        }
    }

    @Test
    void testRepairJobStatusInProgress() {
        when(repairJobsService.getJobState(JOB_ID)).thenReturn(new JobState(false, false, false, "3 of 7", null));

        try (Response response = controller.getRepairJobStatus(JOB_ID)) {
            assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void testRepairJobResult() {
        List<RepairResult> results = List.of(mock(RepairResult.class));
        when(repairJobsService.getJobResult(JOB_ID)).thenReturn(Optional.of(results));

        try (Response response = controller.getRepairJobResult(JOB_ID)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertSame(results, response.getEntity());
        }
    }

    @Test
    void testRepairJobResultInProgress() {
        when(repairJobsService.getJobResult(JOB_ID)).thenReturn(Optional.empty());

        try (Response response = controller.getRepairJobResult(JOB_ID)) {
            assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }
    }
}
