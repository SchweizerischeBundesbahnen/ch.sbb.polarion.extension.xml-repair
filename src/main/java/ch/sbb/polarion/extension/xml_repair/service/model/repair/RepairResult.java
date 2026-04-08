package ch.sbb.polarion.extension.xml_repair.service.model.repair;

import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Getter
public class RepairResult {

    private final String issueMetaInfo;
    @JsonIgnore
    private final IssueMetaInfo rawIssueMetaInfo;
    private final Set<String> warnings = new HashSet<>();
    @Setter
    private boolean success;

    public RepairResult(IssueMetaInfo issueMetaInfo, boolean success, String... warnings) {
        this.rawIssueMetaInfo = issueMetaInfo;
        this.issueMetaInfo = issueMetaInfo.serialize();
        this.success = success;
        this.warnings.addAll(Arrays.asList(warnings));
    }

}
