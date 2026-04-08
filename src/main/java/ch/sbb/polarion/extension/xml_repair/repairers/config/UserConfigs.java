package ch.sbb.polarion.extension.xml_repair.repairers.config;

import ch.sbb.polarion.extension.xml_repair.repairers.IRepairer;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class UserConfigs extends HashMap<String, Object> {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unchecked")
    public <T extends IRepairer> boolean getBoolean(Class<T> repairerClass, String paramId) {
        return Boolean.TRUE.equals(Optional.ofNullable((Map<String, Object>) get(repairerClass.getSimpleName())).map(map -> map.get(paramId)).orElse(false));
    }

}
