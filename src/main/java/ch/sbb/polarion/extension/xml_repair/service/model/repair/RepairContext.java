package ch.sbb.polarion.extension.xml_repair.service.model.repair;

import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.IContext;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ClassCanBeRecord")
public final class RepairContext implements IContext {

    private final @NotNull IssueMetaInfo issueMetaInfo;
    private final @NotNull XmlRepairPolarionService polarionService;
    private final @NotNull UserConfigs configs;
    private final @NotNull Cache cache;

    public RepairContext(@NotNull IssueMetaInfo issueMetaInfo,
                         @NotNull XmlRepairPolarionService polarionService,
                         @NotNull UserConfigs configs,
                         @NotNull Cache cache) {
        this.issueMetaInfo = issueMetaInfo;
        this.polarionService = polarionService;
        this.configs = configs;
        this.cache = cache;
    }

    public @NotNull IssueMetaInfo issueMetaInfo() {
        return issueMetaInfo;
    }

    public @NotNull XmlRepairPolarionService polarionService() {
        return polarionService;
    }

    public @NotNull Cache cache() {
        return cache;
    }

    public @NotNull UserConfigs configs() {
        return configs;
    }

}
