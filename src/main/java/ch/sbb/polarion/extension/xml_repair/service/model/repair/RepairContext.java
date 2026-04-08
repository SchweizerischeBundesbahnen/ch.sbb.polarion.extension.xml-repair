package ch.sbb.polarion.extension.xml_repair.service.model.repair;

import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import org.jetbrains.annotations.NotNull;

public record RepairContext(@NotNull IssueMetaInfo issueMetaInfo,
                            @NotNull XmlRepairPolarionService polarionService,
                            @NotNull UserConfigs configs) {
}
