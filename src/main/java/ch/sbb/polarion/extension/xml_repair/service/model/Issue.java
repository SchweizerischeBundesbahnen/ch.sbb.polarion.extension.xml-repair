package ch.sbb.polarion.extension.xml_repair.service.model;

import ch.sbb.polarion.extension.xml_repair.repairers.BaseRepairer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Getter
public class Issue {

    private final IssueMetaInfo metaInfo;
    private final String repairer;
    private final String description;
    /**
     * Finer-grained grouping key, used by the UI wherever issues are grouped, filtered and counted. Null for
     * every repairer whose issues group by the repairer alone, which keeps it out of their JSON untouched.
     * OutdatedCustomFieldsRepairer sets it to the attribute id, so the "Purge outdated data" page can group
     * per attribute without decoding the meta info.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String group;
    private final List<String> warnings = new ArrayList<>();

    public <T extends BaseRepairer> Issue(@NotNull IssueMetaInfo metaInfo, @NotNull T repairer, @NotNull String description) {
        this(metaInfo, repairer, description, null);
    }

    public <T extends BaseRepairer> Issue(@NotNull IssueMetaInfo metaInfo, @NotNull T repairer, @NotNull String description, @Nullable String group) {
        this.metaInfo = metaInfo;
        this.repairer = repairer.getRepairerId();
        this.description = description;
        this.group = group;
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
