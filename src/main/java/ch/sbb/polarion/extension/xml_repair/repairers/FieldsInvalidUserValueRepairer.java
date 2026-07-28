package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User fields are technically enumeration fields, so originally they were handled by
 * {@link FieldsInvalidEnumerationValueRepairer} as well. For convenience, they were split into this separate repairer:
 * via {@link #shouldFixSpecificEnum(String)} the base class skips the {@code @user} enumeration and this one handles
 * only it.
 */
public class FieldsInvalidUserValueRepairer extends FieldsInvalidEnumerationValueRepairer {

    public static final String NAME = "User fields: Invalid value";

    @Override
    boolean shouldFixSpecificEnum(@NotNull String enumId) {
        return USER_ENUM_ID.equals(enumId);
    }

    @Override
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Finds user fields with invalid values. Repair removes the invalid value if possible.";
    }

    @Override
    public List<RepairerConfigMeta> getConfigs() {
        return List.of(
                new RepairerConfigMeta(REMOVE_INVALID_VALUES, "Remove invalid values",
                        "Clear/remove value if the user isn't found", RepairerConfigType.BOOLEAN, false)
        );
    }
}
