package ch.sbb.polarion.extension.xml_repair.rest;

import ch.sbb.polarion.extension.generic.rest.GenericRestApplication;
import ch.sbb.polarion.extension.generic.rest.controller.roles.RolesApiController;
import ch.sbb.polarion.extension.generic.rest.controller.roles.RolesInternalController;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.xml_repair.rest.controller.ApiController;
import ch.sbb.polarion.extension.xml_repair.rest.controller.InternalController;
import ch.sbb.polarion.extension.xml_repair.settings.AuthorizationSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class XmlRepairRestApplication extends GenericRestApplication {

    public XmlRepairRestApplication() {
        NamedSettingsRegistry.INSTANCE.register(List.of(new AuthorizationSettings()));
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

}
