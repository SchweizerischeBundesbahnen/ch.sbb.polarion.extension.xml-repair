package ch.sbb.polarion.extension.xml_repair.service.model.scan;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reference to a single entity selected for scanning")
public class EntityRef {

    @Schema(description = "Space of the entity (documents only)", example = "Specification", nullable = true)
    private String space;

    @Schema(description = "The entity id: module name for documents, collection id for collections", example = "specification")
    private String id;
}
