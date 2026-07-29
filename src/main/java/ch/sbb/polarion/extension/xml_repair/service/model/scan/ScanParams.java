package ch.sbb.polarion.extension.xml_repair.service.model.scan;

import ch.sbb.polarion.extension.xml_repair.service.model.EntityType;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanParams {

    public static final int DEFAULT_LIMIT = 100;
    public static final long DEFAULT_TIMEOUT = 60000L;

    @Schema(description = "The unique identifier for the project", example = "elibrary")
    private String projectId;

    @Schema(description = "Entity prototype to scan. Currently supported values are: WORKITEM, DOCUMENT and COLLECTION", example = "WORKITEM")
    private EntityType entityType;

    @Schema(description = "Entity subtype to scan, e.g. 'requirement' for work items or 'specification' for documents (if not provided, all subtypes will be scanned)", nullable = true)
    private String entitySubtype;

    @Schema(description = "Additional query to select entities for scanning, e.g. 'id:TEST-12345'", nullable = true)
    private String userQuery;

    @Schema(description = "Explicit list of entities to scan, an alternative to 'userQuery' (both are combined with AND if provided together). "
            + "Empty or not provided means all entities of the given type", nullable = true)
    private List<EntityRef> entities;

    @Schema(description = "SVN revision to scan against. If null, scans current HEAD; otherwise the entire query runs as of that revision.", example = "12345", nullable = true)
    private String revision;

    @Schema(description = "Sorting criteria in the format 'created' or '~id'", nullable = true, defaultValue = "created")
    private String sort;

    @Schema(description = "Maximum number of entities to scan", nullable = true, defaultValue = "" + DEFAULT_LIMIT)
    private int limit = DEFAULT_LIMIT;

    @Schema(description = "Operation timeout, milliseconds", defaultValue = "" + DEFAULT_TIMEOUT)
    private long timeout = DEFAULT_TIMEOUT;

    @Schema(description = "List of repairers to use (if no data provided the default repairers will be applied)", nullable = true)
    private List<String> repairers;

    @Schema(description = "Hide entities without issues from the resulting list", defaultValue = "false", nullable = true)
    private boolean hideValid;

    @Schema(description = "User custom repair configurations", example = "{\"ModuleContentLinksRepairer\" : {\"convertToPlainText\": true}}", defaultValue = "{}")
    private UserConfigs configs = new UserConfigs();
}
