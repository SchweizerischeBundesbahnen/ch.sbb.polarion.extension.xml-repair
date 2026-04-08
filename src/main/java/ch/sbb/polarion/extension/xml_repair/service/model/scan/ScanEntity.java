package ch.sbb.polarion.extension.xml_repair.service.model.scan;

import ch.sbb.polarion.extension.xml_repair.service.model.EntityType;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.tracker.model.IModule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Getter
public final class ScanEntity {

    @JsonIgnore
    private final @NotNull IUniqueObject entity;

    @Schema(description = "Entity type", example = "DOCUMENT")
    private final @NotNull EntityType entityType;

    @Schema(description = "The unique identifier for the project", example = "elibrary")
    private final @NotNull String projectId;

    @Schema(description = "Space (in case of document)", example = "Specification")
    private final @Nullable String space;

    @Schema(description = "The unique identifier of the entity", example = "elibrary")
    private final @NotNull String entityId;

    @Schema(description = "List of issues found in the entity")
    private final @NotNull List<Issue> issues = new LinkedList<>();

    @Schema(description = "Map of field keys and their labels + rendered values")
    private final @NotNull Map<String, Map<String, String>> fields = new LinkedHashMap<>();

    @Schema(description = "List of sub-items, e.g. documents in a collection")
    private final List<ScanEntity> subitems = new ArrayList<>();

    @Schema(description = "List of warnings that are not issues but might be useful to know for the user")
    private final Set<String> warnings = new LinkedHashSet<>();


    private ScanEntity(@NotNull IUniqueObject entity, @NotNull EntityType entityType, @NotNull String projectId, @Nullable String space, @NotNull String entityId) {
        this.entity = entity;
        this.entityType = entityType;
        this.projectId = projectId;
        this.space = space;
        this.entityId = entityId;
    }

    public static ScanEntity from(@NotNull IUniqueObject entity) {
        return new ScanEntity(entity, EntityType.fromPrototype(entity.getPrototype()), entity.getProjectId(),
                entity instanceof IModule module ? module.getModuleFolder() : null, entity.getId());
    }

}
