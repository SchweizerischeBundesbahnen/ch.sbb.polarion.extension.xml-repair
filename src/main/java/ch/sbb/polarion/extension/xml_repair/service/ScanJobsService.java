package ch.sbb.polarion.extension.xml_repair.service;

import ch.sbb.polarion.extension.generic.jobs.AsyncJobsService;
import ch.sbb.polarion.extension.generic.jobs.JobControl;
import ch.sbb.polarion.extension.generic.jobs.JobsProperties;
import ch.sbb.polarion.extension.generic.jobs.JobsRegistry;
import ch.sbb.polarion.extension.generic.jobs.TimeoutPolicy;
import ch.sbb.polarion.extension.generic.service.PolarionBaselineExecutor;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanControl;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanResult;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.core.util.StringUtils;
import com.polarion.platform.security.ISecurityService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs scans in the background. The job mechanics are generic's {@link AsyncJobsService}.
 * <p>
 * A scan is stopped {@link TimeoutPolicy#COOPERATIVE cooperatively}, not interrupted: an interrupt can reach Polarion
 * inside a repository or index read. A stopped scan returns what it found so far, so a stop by the user or by the job
 * timeout ends the job successfully, with the reason in the scan report.
 */
public class ScanJobsService extends AsyncJobsService<ScanJobsService.JobPayload, ScanResult> {

    public static final String JOBS_PROPERTIES_FILE = "/scan-jobs.properties";
    public static final String STOPPED_BY_USER_WARNING = "Scan was stopped by the user, further items are not scanned.";
    public static final String JOB_TIMEOUT_WARNING = "Scan job time limit of %d minutes was reached, further items are not scanned.";

    // Static, so that the jobs survive the controller instance which started them
    private static final JobsRegistry<JobPayload, ScanResult> REGISTRY = registryBuilder().build();

    private final XmlRepairPolarionService polarionService;

    public ScanJobsService(@NotNull XmlRepairPolarionService polarionService, @NotNull ISecurityService securityService) {
        this(polarionService, securityService, REGISTRY);
    }

    @VisibleForTesting
    ScanJobsService(@NotNull XmlRepairPolarionService polarionService, @NotNull ISecurityService securityService,
                    @NotNull JobsRegistry<JobPayload, ScanResult> registry) {
        super(registry, securityService);
        this.polarionService = polarionService;
    }

    /**
     * @return the job timeouts of this extension
     */
    public static @NotNull JobsProperties jobsProperties() {
        return new JobsProperties(ScanJobsService.class, JOBS_PROPERTIES_FILE);
    }

    /**
     * Starts dropping finished scans once they are older than the finished job timeout.
     */
    public static void startCleaner() {
        REGISTRY.startCleaner(jobsProperties().getFinishedJobTimeout());
    }

    /**
     * Stops the cleaner and the scan threads. Called when the bundle stops.
     */
    public static void shutdown() {
        REGISTRY.shutdown();
    }

    /**
     * Starts a scan with the in-progress timeout of this extension.
     */
    public @NotNull String startJob(@NotNull ScanParams scanParams) {
        return startJob(scanParams, jobsProperties().getInProgressJobTimeout());
    }

    public @NotNull String startJob(@NotNull ScanParams scanParams, int timeoutInMinutes) {
        JobPayload payload = new JobPayload(new AtomicBoolean());
        return startJob(payload, timeoutInMinutes, control -> runScan(scanParams, scanControl(control, payload, timeoutInMinutes)));
    }

    /**
     * Runs on the worker thread, so the transaction is opened there and not on the thread of the start request.
     */
    @VisibleForTesting
    @Nullable ScanResult runScan(@NotNull ScanParams scanParams, @NotNull ScanControl control) {
        return TransactionalExecutor.executeInReadOnlyTransaction(
                transaction -> PolarionBaselineExecutor.executeInBaseline(StringUtils.getNullIfEmpty(scanParams.getRevision()), transaction,
                        () -> polarionService.scan(scanParams, control)));
    }

    /**
     * Asks the scan to stop at the next entity. It still returns the items it has scanned so far.
     */
    @Override
    public void cancelJob(@NotNull String jobId) {
        // set before the stop request, so the scan which sees the request already sees who made it
        Objects.requireNonNull(getJobPayload(jobId), "Job payload is always set by startJob").stoppedByUser().set(true);
        super.cancelJob(jobId);
    }

    @VisibleForTesting
    static @NotNull ScanControl scanControl(@NotNull JobControl control, @NotNull JobPayload payload, int timeoutInMinutes) {
        return new ScanControl() {
            @Override
            public @Nullable String stopReason() {
                if (!control.isAbortRequested()) {
                    return null;
                }
                return payload.stoppedByUser().get() ? STOPPED_BY_USER_WARNING : JOB_TIMEOUT_WARNING.formatted(timeoutInMinutes);
            }

            @Override
            public void reportProgress(@NotNull String message) {
                control.reportProgress(message);
            }
        };
    }

    @VisibleForTesting
    static @NotNull JobsRegistry.Builder<JobPayload, ScanResult> registryBuilder() {
        return JobsRegistry.<JobPayload, ScanResult>builder("Scan")
                .timeoutPolicy(TimeoutPolicy.COOPERATIVE);
    }

    /**
     * What a scan keeps next to its result: whether its user stopped it, which the generic stop request does not tell.
     */
    public record JobPayload(@NotNull AtomicBoolean stoppedByUser) {
    }
}
