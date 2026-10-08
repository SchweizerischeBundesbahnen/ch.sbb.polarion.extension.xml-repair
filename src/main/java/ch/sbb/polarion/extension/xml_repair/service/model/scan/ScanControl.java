package ch.sbb.polarion.extension.xml_repair.service.model.scan;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a running scan asks its caller between entities: whether to stop, and where to report its progress.
 */
public interface ScanControl {

    ScanControl NONE = new ScanControl() {
        @Override
        public @Nullable String stopReason() {
            return null;
        }

        @Override
        public void reportProgress(@NotNull String message) {
            // nobody polls a scan without a job
        }
    };

    /**
     * @return why the scan must stop now, or {@code null} to go on. A stopped scan still returns what it found so far.
     */
    @Nullable String stopReason();

    void reportProgress(@NotNull String message);
}
