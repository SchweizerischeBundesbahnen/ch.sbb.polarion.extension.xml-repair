package ch.sbb.polarion.extension.xml_repair.service.model.repair;

import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RepairerMeta {

    private String id;
    private String name;
    private String description;
    private List<RepairerConfigMeta> configs;

}
