package ch.sbb.polarion.extension.xml_repair.repairers.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class RepairerConfigMeta {

    private String key;
    private String description;
    private String hint;
    private RepairerConfigType type;
    private Object defaultValue;

}
