package ch.sbb.polarion.extension.xml_repair.service.model;

import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

public interface IContext {

    @NotNull XmlRepairPolarionService polarionService();

    @NotNull UserConfigs configs();

    @NotNull Cache cache();

    default <T> T getAndCache(@NotNull String key, @NotNull Callable<T> getValueCallable) {
        return cache().getOrCompute(key, getValueCallable);
    }

}
