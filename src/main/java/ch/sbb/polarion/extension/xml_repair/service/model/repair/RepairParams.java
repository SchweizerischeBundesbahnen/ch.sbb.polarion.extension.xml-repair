package ch.sbb.polarion.extension.xml_repair.service.model.repair;

import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepairParams {

    @Schema(description = "List of issues meta infos to fix", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> issueMetaInfos;

    @Schema(description = "User custom repair configurations", example = "{\"ModuleContentLinksRepairer\" : {\"convertToPlainText\": true}}", defaultValue = "{}")
    private UserConfigs configs = new UserConfigs();

}
