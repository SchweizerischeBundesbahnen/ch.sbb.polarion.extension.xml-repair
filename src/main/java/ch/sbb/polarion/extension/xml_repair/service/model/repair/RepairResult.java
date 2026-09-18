package ch.sbb.polarion.extension.xml_repair.service.model.repair;

import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.polarion.subterra.base.SubterraURI;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
public class RepairResult {

    private final String issueMetaInfo;
    @JsonIgnore
    private final IssueMetaInfo rawIssueMetaInfo;
    // Ordered: a repairer adding several warnings means them to be read in that order.
    private final Set<String> warnings = new LinkedHashSet<>();
    /**
     * The objects whose cached copy no longer matches storage once this repair is committed. Only a repair
     * whose write changes how OTHER objects are read fills this; for the rest it stays empty, because Polarion
     * invalidates what it writes. Acted on by {@code XmlRepairPolarionService.clearStaleCaches}.
     */
    @JsonIgnore
    private final Set<SubterraURI> staleCacheUris = new HashSet<>();
    @Setter
    private boolean success;

    public RepairResult(IssueMetaInfo issueMetaInfo, boolean success, String... warnings) {
        this.rawIssueMetaInfo = issueMetaInfo;
        this.issueMetaInfo = issueMetaInfo.serialize();
        this.success = success;
        this.warnings.addAll(Arrays.asList(warnings));
    }

}
