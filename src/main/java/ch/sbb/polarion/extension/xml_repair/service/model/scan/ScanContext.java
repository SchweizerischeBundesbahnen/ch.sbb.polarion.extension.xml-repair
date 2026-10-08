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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ScanContext implements IContext {
    private final @NotNull XmlRepairPolarionService polarionService;
    private final @NotNull List<String> repairers;
    private final @NotNull UserConfigs configs;
    private final @NotNull Report report;
    private final @NotNull Cache cache;
    private List<IModule> collectionDocuments;
    private final EntityRenderer entityRenderer;
    private @NotNull ScanControl control = ScanControl.NONE;

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

    public ScanContext control(@NotNull ScanControl control) {
        this.control = control;
        return this;
    }

    /**
     * @return why the caller of the scan asked it to stop, or {@code null} to go on
     */
    public @Nullable String stopReason() {
        return control.stopReason();
    }

}
