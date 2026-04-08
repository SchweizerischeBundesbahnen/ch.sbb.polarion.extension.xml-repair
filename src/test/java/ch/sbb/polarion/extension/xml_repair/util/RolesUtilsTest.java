package ch.sbb.polarion.extension.xml_repair.util;

import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.platform.core.IPlatform;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.security.ISecurityService;
import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@CurrentContextConfig("xml-repair")
class RolesUtilsTest {
    private ISecurityService securityServiceMock;
    private IProjectService projectServiceMock;

    @BeforeEach
    void setUp() {
        securityServiceMock = mock(ISecurityService.class);
        projectServiceMock = mock(IProjectService.class);

        IPlatform iPlatformMock = mock(IPlatform.class);
        when(PlatformContext.getPlatform()).thenReturn(iPlatformMock);
        lenient().when(iPlatformMock.lookupService(ISecurityService.class)).thenReturn(securityServiceMock);
        lenient().when(iPlatformMock.lookupService(IProjectService.class)).thenReturn(projectServiceMock);
    }

    @Test
    void testGetGlobalRoles() {
        List<String> expectedRoles = List.of("role1", "role2");
        lenient().when(securityServiceMock.getGlobalRoles()).thenReturn(expectedRoles);

        Collection<String> actualRoles = RolesUtils.getGlobalRoles();
        assertNotNull(actualRoles);
    }

    @Test
    void testGetProjectRolesWhenScopeIsValid() {
        String scope = "someScope";
        String projectId = "projectId";
        IContextId contextId = mock(IContextId.class);

        try (MockedStatic<ScopeUtils> mockScopeUtils = mockStatic(ScopeUtils.class)) {
            mockScopeUtils.when(() -> ScopeUtils.getProjectFromScope(scope)).thenReturn(projectId);
            IProject projectMock = mock(IProject.class);
            when(projectServiceMock.getProject(anyString())).thenReturn(projectMock);

            when(projectMock.getContextId()).thenReturn(contextId);

            Set<String> expectedRoles = Set.of("role1", "role2");
            when(securityServiceMock.getContextRoles(contextId)).thenReturn(expectedRoles);

            Collection<String> actualRoles = RolesUtils.getProjectRoles(scope);

            assertNotNull(actualRoles);
            assertEquals(expectedRoles, actualRoles);
        }
    }

    @Test
    void testGetProjectRolesWhenScopeIsNull() {
        try (MockedStatic<ScopeUtils> mockScopeUtils = mockStatic(ScopeUtils.class)) {
            mockScopeUtils.when(() -> ScopeUtils.getProjectFromScope(anyString())).thenReturn(null);

            Collection<String> actualRoles = RolesUtils.getProjectRoles("invalidScope");

            assertNotNull(actualRoles);
            assertTrue(actualRoles.isEmpty());
        }
    }
}
