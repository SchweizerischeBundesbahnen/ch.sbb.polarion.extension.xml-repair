package ch.sbb.polarion.extension.xml_repair.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Schema(description = "Work item or document type information")
public record TypeInfo(
        @Schema(description = "The type id", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull String id,
        @Schema(description = "The type display name", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull String name,
        @Schema(description = "The type icon URL", nullable = true) @Nullable String iconURL) {
}
