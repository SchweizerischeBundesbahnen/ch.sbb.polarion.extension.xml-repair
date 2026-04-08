package ch.sbb.polarion.extension.xml_repair.service.model;

import ch.sbb.polarion.extension.xml_repair.repairers.BaseRepairer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Getter
public class Issue {

    private final IssueMetaInfo metaInfo;
    private final String repairer;
    private final String description;
    private final List<String> warnings = new ArrayList<>();

    public <T extends BaseRepairer> Issue(@NotNull IssueMetaInfo metaInfo, @NotNull T repairer, @NotNull String description) {
        this.metaInfo = metaInfo;
        this.repairer = repairer.getRepairerId();
        this.description = description;
        this.metaInfo.set(IssueMetaInfo.REPAIRER, repairer.getRepairerId());
    }

    public String getMetaInfo() {
        return metaInfo.serialize();
    }

    @JsonIgnore
    public IssueMetaInfo getRawMetaInfo() {
        return metaInfo;
    }
}
