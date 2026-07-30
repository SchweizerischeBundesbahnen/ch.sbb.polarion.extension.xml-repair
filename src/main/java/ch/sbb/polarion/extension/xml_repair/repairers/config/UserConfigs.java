package ch.sbb.polarion.extension.xml_repair.repairers.config;

import ch.sbb.polarion.extension.xml_repair.repairers.IRepairer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Repair parameters that the user chooses per repairer, sent as part of {@code ScanParams} and {@code RepairParams}.
 * Keys are repairer simple names. Values are maps of parameter id to parameter value.
 *
 * <p>A plain {@code Map<String, Object>} would carry the same data, so this type exists for three reasons:
 *
 * <ol>
 *   <li><b>It hides the storage shape.</b> Repairers ask a question and get an answer. They never walk a nested
 *       map, cast an {@code Object}, or handle a missing level. The two-level layout stays an implementation
 *       detail, so it can change without touching any call site.</li>
 *   <li><b>It gives the typed accessors a home.</b> {@link #getBoolean} is the first one. More will follow as
 *       repairers gain parameters that are not booleans. On a raw map each of those would become duplicated
 *       read-and-cast code at every call site.</li>
 *   <li><b>It centralizes defensive reads.</b> The map is deserialized from a request body that nothing
 *       validates, so any key may hold any JSON value. The accessors resolve that in one place. See
 *       {@link #getBoolean} for the rule that applies.</li>
 * </ol>
 *
 * <p>The wire format is a flat JSON object, for example
 * {@code {"ModuleContentLinksRepairer": {"convertToPlainText": true}}}. It does not change if the internal
 * storage changes.
 */
public record UserConfigs(Map<String, Object> configs) {

    public UserConfigs() {
        this(new HashMap<>());
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public UserConfigs(Map<String, Object> configs) {
        this.configs = new HashMap<>(configs);
    }

    /**
     * @return true only if the repairer is configured with a map holding {@link Boolean#TRUE} for the given parameter.
     * Any other content is treated as "not configured", since the map is deserialized from an unvalidated request body.
     */
    public <T extends IRepairer> boolean getBoolean(Class<T> repairerClass, String paramId) {
        return configs.get(repairerClass.getSimpleName()) instanceof Map<?, ?> repairerConfig && Boolean.TRUE.equals(repairerConfig.get(paramId));
    }

    /**
     * @return an unmodifiable view of the underlying map, also used as the JSON representation
     */
    @Override
    @JsonValue
    public Map<String, Object> configs() {
        return Collections.unmodifiableMap(configs);
    }

    public Object put(String key, Object value) {
        return configs.put(key, value);
    }

    public boolean containsKey(String key) {
        return configs.containsKey(key);
    }

    public int size() {
        return configs.size();
    }

    public void clear() {
        configs.clear();
    }

    @Override
    public @NotNull String toString() {
        return configs.toString();
    }
}
