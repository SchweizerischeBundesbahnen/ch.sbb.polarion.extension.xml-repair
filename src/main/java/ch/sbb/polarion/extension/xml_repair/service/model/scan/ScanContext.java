package ch.sbb.polarion.extension.xml_repair.service.model.scan;

import ch.sbb.polarion.extension.xml_repair.service.EntityRenderer;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.model.IContext;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollectionElement;
import org.apache.commons.lang3.time.StopWatch;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanContext implements IContext {
    private final @NotNull XmlRepairPolarionService polarionService;
    private final @NotNull List<String> repairers;
    private final @NotNull UserConfigs configs;
    private final @NotNull Report report;
    private final @NotNull Cache cache;
    private final Set<String> globalWarnings = new LinkedHashSet<>();
    private List<IModule> collectionDocuments;
    private final EntityRenderer entityRenderer;

    private static final String TIMEOUT_REACHED_WARNING = "Operation timeout reached, some issues may not be processed fully.";
    private final StopWatch stopWatch = StopWatch.createStarted();
    private final AtomicBoolean timeoutReached = new AtomicBoolean(false);
    private long timeout = 0;

    public ScanContext(@NotNull XmlRepairPolarionService polarionService, @NotNull List<String> repairers, @NotNull UserConfigs configs, @NotNull Report report, @NotNull Cache cache) {
        this.polarionService = polarionService;
        this.repairers = repairers;
        this.configs = configs;
        this.report = report;
        this.cache = cache;
        this.entityRenderer = new EntityRenderer(Objects.requireNonNull(TransactionalExecutorImpl.currentTransaction()), polarionService().getTrackerService());
    }

    public @NotNull XmlRepairPolarionService polarionService() {
        return polarionService;
    }

    public @NotNull Cache cache() {
        return cache;
    }

    public @NotNull List<String> repairers() {
        return repairers;
    }

    public @NotNull UserConfigs configs() {
        return configs;
    }

    public @NotNull Report report() {
        return report;
    }

    public @NotNull Set<String> globalWarnings() {
        return globalWarnings;
    }

    public EntityRenderer entityRenderer() {
        return entityRenderer;
    }

    public List<IModule> collectionDocuments(IBaselineCollection collection) {
        if (collectionDocuments == null) {
            collectionDocuments = collection.getElements().stream()
                    .map(IBaselineCollectionElement::getObjectWithRevision)
                    .filter(IModule.class::isInstance)
                    .map(IModule.class::cast)
                    .toList();
        }
        return collectionDocuments;
    }

    public ScanContext timeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public boolean timeoutReached() {
        if (timeoutReached.get()) {
            return true;
        } else if (timeout > 0 && stopWatch.getTime() >= timeout) {
            globalWarnings.add(TIMEOUT_REACHED_WARNING);
            report.warn(TIMEOUT_REACHED_WARNING);
            timeoutReached.set(true);
            return true;
        } else {
            return false;
        }
    }

}
