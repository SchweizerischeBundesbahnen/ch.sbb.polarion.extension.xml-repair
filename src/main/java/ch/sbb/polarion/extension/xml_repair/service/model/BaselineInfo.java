package ch.sbb.polarion.extension.xml_repair.service.model;

import com.polarion.core.util.logging.Logger;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Baseline information")
public class BaselineInfo implements Comparable<BaselineInfo> {

    private static final Logger logger = Logger.getLogger(BaselineInfo.class);

    @Schema(description = "The SVN revision")
    private String revision;

    @Schema(description = "The baseline name")
    private String name;

    public int compareTo(@NotNull BaselineInfo that) {
        long thisRevision = 0L;
        long thatRevision = 0L;

        try {
            thisRevision = Long.parseLong(this.revision);
        } catch (NumberFormatException e) {
            logger.warn("Unexpected revision found: " + this.revision);
        }

        try {
            thatRevision = Long.parseLong(that.revision);
        } catch (NumberFormatException e) {
            logger.warn("Unexpected revision found: " + that.revision);
        }

        // Reverse sorting is used to show most last revision as first.
        // Tie-break on name to keep compareTo consistent with the @Data-generated equals.
        int byRevision = Long.compare(thatRevision, thisRevision);
        if (byRevision != 0) {
            return byRevision;
        }
        return Objects.compare(this.name, that.name, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
