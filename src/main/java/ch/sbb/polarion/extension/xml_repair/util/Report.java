package ch.sbb.polarion.extension.xml_repair.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Report {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final StringBuilder reportContent = new StringBuilder();

    public void log(String level, String entry) {
        reportContent.append("[%s] %s: ".formatted(LocalDateTime.now(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER), level)).append(entry).append("\n");
    }

    public void info(String entry) {
        log("INFO", entry);
    }

    public void warn(String entry) {
        log("WARN", entry);
    }

    @Override
    public String toString() {
        return reportContent.toString();
    }
}
