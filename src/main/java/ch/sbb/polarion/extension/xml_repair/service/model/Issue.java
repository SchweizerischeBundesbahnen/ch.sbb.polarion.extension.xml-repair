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
    /**
     * A short value the results list shows in place of the issue count, for a page where counting says
     * nothing. ModuleStructureLinkRoleRepairer sets it to the role the document currently uses, because a
     * document either uses the selected role or does not - the count is always one.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String label;
    private final List<String> warnings = new ArrayList<>();

    public <T extends BaseRepairer> Issue(@NotNull IssueMetaInfo metaInfo, @NotNull T repairer, @NotNull String description) {
        this(metaInfo, repairer, description, null, null);
    }

    public <T extends BaseRepairer> Issue(@NotNull IssueMetaInfo metaInfo, @NotNull T repairer, @NotNull String description, @Nullable String group) {
        this(metaInfo, repairer, description, group, null);
    }

    public <T extends BaseRepairer> Issue(@NotNull IssueMetaInfo metaInfo, @NotNull T repairer, @NotNull String description, @Nullable String group, @Nullable String label) {
        this.metaInfo = metaInfo;
        this.repairer = repairer.getRepairerId();
        this.description = description;
        this.group = group;
        this.label = label;
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
