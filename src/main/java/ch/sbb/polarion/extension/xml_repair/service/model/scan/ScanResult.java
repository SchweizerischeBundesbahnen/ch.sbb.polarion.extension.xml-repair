package ch.sbb.polarion.extension.xml_repair.service.model.scan;

import lombok.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
@Setter
@NoArgsConstructor
public class ScanResult {

    @NotNull
    @Setter(AccessLevel.NONE)
    private final List<ScanEntity> items = new ArrayList<>();

    @NotNull
    private String report = "";

}
