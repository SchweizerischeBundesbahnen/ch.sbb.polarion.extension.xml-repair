package ch.sbb.polarion.extension.xml_repair.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Schema(description = "Selectable entity of a project: a document or a collection")
public record EntityInfo(
        @Schema(description = "Space of the entity (documents only)", example = "Specification", nullable = true) @Nullable String space,
        @Schema(description = "The entity id: module name for documents, collection id for collections", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull String id,
        @Schema(description = "The entity display name: title for documents, name for collections", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull String name,
        @Schema(description = "The entity type id, used to resolve the display icon (documents only)", example = "specification", nullable = true) @Nullable String type) {
}
