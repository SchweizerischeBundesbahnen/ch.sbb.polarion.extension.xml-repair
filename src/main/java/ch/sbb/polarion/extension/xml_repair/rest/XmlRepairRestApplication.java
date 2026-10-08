package ch.sbb.polarion.extension.xml_repair.rest;

import ch.sbb.polarion.extension.generic.rest.GenericRestApplication;
import ch.sbb.polarion.extension.generic.rest.controller.roles.RolesApiController;
import ch.sbb.polarion.extension.generic.rest.controller.roles.RolesInternalController;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.xml_repair.rest.controller.ApiController;
import ch.sbb.polarion.extension.xml_repair.rest.controller.InternalController;
import ch.sbb.polarion.extension.xml_repair.rest.exception.NoSuchElementExceptionMapper;
import ch.sbb.polarion.extension.xml_repair.service.RepairJobsService;
import ch.sbb.polarion.extension.xml_repair.service.ScanJobsService;
import ch.sbb.polarion.extension.xml_repair.settings.AuthorizationSettings;
import com.polarion.core.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class XmlRepairRestApplication extends GenericRestApplication {

    private final Logger logger = Logger.getLogger(XmlRepairRestApplication.class);

    public XmlRepairRestApplication() {
        NamedSettingsRegistry.INSTANCE.register(List.of(new AuthorizationSettings()));

        try {
            ScanJobsService.startCleaner();
            RepairJobsService.startCleaner();
        } catch (Exception e) {
            logger.error("Error during starting of jobs cleaners", e);
        }
    }

    @Override
    protected @NotNull Set<Class<?>> getExtensionControllerClasses() {
        return Set.of(
                ApiController.class,
                InternalController.class,
                // The role endpoints are opt-in in generic: only the extensions whose settings grant
                // permissions to roles serve them.
                RolesInternalController.class,
                RolesApiController.class
        );
    }

    @Override
    protected @NotNull Set<Object> getExtensionExceptionMapperSingletons() {
        return Set.of(new NoSuchElementExceptionMapper());
    }

}
