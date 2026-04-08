package ch.sbb.polarion.extension.xml_repair.util;

import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.security.ISecurityService;
import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Set;

@UtilityClass
public class RolesUtils {

    public static final String MSG_NOT_AUTHORIZED_BY_ADMIN = "Repair operation is restricted for current user by the Administrator.";
    public static final String MSG_NO_PERMISSIONS = "Current user is not allowed to modify the entity.";

    private static final ISecurityService securityService = PlatformContext.getPlatform().lookupService(ISecurityService.class);
    private static final IProjectService projectService = PlatformContext.getPlatform().lookupService(IProjectService.class);

    public static Collection<String> getGlobalRoles() {
        return securityService.getGlobalRoles();
    }

    public static Collection<String> getProjectRoles(String scope) {
        String projectId = ScopeUtils.getProjectFromScope(scope);
        if (projectId == null) {
            return Set.of();
        }
        IProject project = projectService.getProject(projectId);
        return securityService.getContextRoles(project.getContextId());
    }
}
