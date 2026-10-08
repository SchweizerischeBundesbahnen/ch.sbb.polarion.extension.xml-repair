package ch.sbb.polarion.extension.xml_repair.service;

import ch.sbb.polarion.extension.generic.jobs.AsyncJobsService;
import ch.sbb.polarion.extension.generic.jobs.JobControl;
import ch.sbb.polarion.extension.generic.jobs.JobsProperties;
import ch.sbb.polarion.extension.generic.jobs.JobsRegistry;
import ch.sbb.polarion.extension.generic.jobs.TimeoutPolicy;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairParams;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.platform.security.ISecurityService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.List;

/**
 * Runs repairs in the background. The job mechanics are generic's {@link AsyncJobsService}.
 * <p>
 * All issues of a job are repaired in one write transaction, so the job makes one SVN revision. A conflict at its
 * commit rolls the whole transaction back, and the job fails with Polarion's message.
 * <p>
 * A repair writes, so it is never declared over from the outside ({@link TimeoutPolicy#COOPERATIVE}). It does not
 * check for a stop either: it always runs to its commit.
 */
public class RepairJobsService extends AsyncJobsService<Void, List<RepairResult>> {

    public static final String JOBS_PROPERTIES_FILE = "/repair-jobs.properties";
    public static final String SAVING_PROGRESS = "Saving...";

    // Static, so that the jobs survive the controller instance which started them
    private static final JobsRegistry<Void, List<RepairResult>> REGISTRY = registryBuilder().build();

    private final XmlRepairPolarionService polarionService;

    public RepairJobsService(@NotNull XmlRepairPolarionService polarionService, @NotNull ISecurityService securityService) {
        this(polarionService, securityService, REGISTRY);
    }

    @VisibleForTesting
    RepairJobsService(@NotNull XmlRepairPolarionService polarionService, @NotNull ISecurityService securityService,
                      @NotNull JobsRegistry<Void, List<RepairResult>> registry) {
        super(registry, securityService);
        this.polarionService = polarionService;
    }

    /**
     * @return the job timeouts of this extension
     */
    public static @NotNull JobsProperties jobsProperties() {
        return new JobsProperties(RepairJobsService.class, JOBS_PROPERTIES_FILE);
    }

    /**
     * Starts dropping finished repairs once they are older than the finished job timeout.
     */
    public static void startCleaner() {
        REGISTRY.startCleaner(jobsProperties().getFinishedJobTimeout());
    }

    /**
     * Stops the cleaner. A running repair still runs to its commit.
     */
    public static void shutdown() {
        REGISTRY.shutdown();
    }

    /**
     * Starts a repair with the in-progress timeout of this extension.
     */
    public @NotNull String startJob(@NotNull RepairParams repairParams) {
        return startJob(repairParams, jobsProperties().getInProgressJobTimeout());
    }

    public @NotNull String startJob(@NotNull RepairParams repairParams, int timeoutInMinutes) {
        return startJob(null, timeoutInMinutes, control -> runRepair(repairParams, control));
    }

    /**
     * Runs on the worker thread, so the transaction is opened there and not on the thread of the start request.
     */
    @VisibleForTesting
    @Nullable List<RepairResult> runRepair(@NotNull RepairParams repairParams, @NotNull JobControl control) {
        List<RepairResult> results = TransactionalExecutor.executeInWriteTransaction(transaction -> {
            List<RepairResult> repaired = polarionService.repair(repairParams, control::reportProgress);
            // the transaction commits once this returns, which can take a while for many documents
            control.reportProgress(SAVING_PROGRESS);
            return repaired;
        });
        if (results != null) {
            // Deliberately outside the transaction: see XmlRepairPolarionService.clearStaleCaches.
            polarionService.clearStaleCaches(results);
        }
        return results;
    }

    @VisibleForTesting
    static @NotNull JobsRegistry.Builder<Void, List<RepairResult>> registryBuilder() {
        return JobsRegistry.<Void, List<RepairResult>>builder("Repair")
                .timeoutPolicy(TimeoutPolicy.COOPERATIVE);
    }
}
