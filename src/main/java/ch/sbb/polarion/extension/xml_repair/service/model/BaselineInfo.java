package ch.sbb.polarion.extension.xml_repair.service.model;

import com.polarion.core.util.logging.Logger;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;

@Schema(description = "Baseline information")
public record BaselineInfo(@Schema(description = "The SVN revision", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull String revision, @Schema(description = "The baseline name", nullable = true) @Nullable String name) implements Comparable<BaselineInfo> {

    private static final Logger logger = Logger.getLogger(BaselineInfo.class);

    public BaselineInfo {
        Objects.requireNonNull(revision, "revision must not be null");
    }

    public int compareTo(@NotNull BaselineInfo that) {
        // Reverse sorting is used to show the most recent revision first.
        // Tie-break on name keeps compareTo consistent with the record-generated equals.
        int byRevision = Long.compare(parseRevision(that.revision), parseRevision(this.revision));
        if (byRevision != 0) {
            return byRevision;
        }
        return Objects.compare(this.name, that.name, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static long parseRevision(@NotNull String revision) {
        try {
            return Long.parseLong(revision);
        } catch (NumberFormatException e) {
            logger.warn("Unexpected revision found: " + revision);
            return 0L;
        }
    }
}
