package ch.sbb.polarion.extension.xml_repair.settings;

import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.AuthorizationModel;
import ch.sbb.polarion.extension.generic.settings.GenericNamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.polarion.subterra.base.location.ILocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, CurrentContextExtension.class})
@CurrentContextConfig("xml-repair")
class AuthorizationSettingsTest {

    @Test
    void testDefaultSettings() {
        try (MockedStatic<ScopeUtils> mockScopeUtils = mockStatic(ScopeUtils.class)) {
            SettingsService mockedSettingsService = mock(SettingsService.class);
            mockScopeUtils.when(() -> ScopeUtils.getFileContent(any())).thenCallRealMethod();

            final AuthorizationModel defaultValues = new AuthorizationSettings(mockedSettingsService).defaultValues();
            assertNotNull(defaultValues);
            assertTrue(defaultValues.getAllRoles().contains("admin"));
        }
    }

    @Test
    void testLoadCustomWhenSettingExists() {
        try (MockedStatic<ScopeUtils> mockScopeUtils = mockStatic(ScopeUtils.class)) {
            SettingsService mockedSettingsService = mock(SettingsService.class);
            mockScopeUtils.when(() -> ScopeUtils.getFileContent(any())).thenCallRealMethod();

            GenericNamedSettings<AuthorizationModel> exporterSettings = new AuthorizationSettings(mockedSettingsService);

            String projectName = "test_project";

            ILocation mockDefaultLocation = mock(ILocation.class);
            when(mockDefaultLocation.append(anyString())).thenReturn(mockDefaultLocation);
            mockScopeUtils.when(() -> ScopeUtils.getContextLocation("")).thenReturn(mockDefaultLocation);

            ILocation mockProjectLocation = mock(ILocation.class);
            when(mockProjectLocation.append(anyString())).thenReturn(mockProjectLocation);
            mockScopeUtils.when(() -> ScopeUtils.getContextLocationByProject(projectName)).thenReturn(mockProjectLocation);
            mockScopeUtils.when(() -> ScopeUtils.getScopeFromProject(projectName)).thenCallRealMethod();
            mockScopeUtils.when(() -> ScopeUtils.getContextLocation("project/test_project/")).thenReturn(mockProjectLocation);

            AuthorizationModel customProjectModel = new AuthorizationModel();
            customProjectModel.setProjectRoles("projectRole");
            customProjectModel.setGlobalRoles("globalRole");
            customProjectModel.setBundleTimestamp("custom");
            when(mockedSettingsService.read(eq(mockProjectLocation), any())).thenReturn(customProjectModel.serialize());

            when(mockedSettingsService.getLastRevision(mockProjectLocation)).thenReturn("345");
            when(mockedSettingsService.getPersistedSettingFileNames(mockProjectLocation)).thenReturn(List.of("Any setting name"));

            AuthorizationModel loadedModel = exporterSettings.load(projectName, SettingId.fromName("Any setting name"));
            assertEquals("custom", loadedModel.getBundleTimestamp());

            assertNotNull(loadedModel);

            assertTrue(loadedModel.getProjectRoles().contains("projectRole"));
            assertTrue(loadedModel.getGlobalRoles().contains("globalRole"));
        }
    }

    @Test
    void testNamedSettings() {
        try (MockedStatic<ScopeUtils> mockScopeUtils = mockStatic(ScopeUtils.class)) {
            SettingsService mockedSettingsService = mock(SettingsService.class);
            mockScopeUtils.when(() -> ScopeUtils.getFileContent(any())).thenCallRealMethod();

            GenericNamedSettings<AuthorizationModel> settings = new AuthorizationSettings(mockedSettingsService);

            String projectName = "test_project";
            String settingOne = "setting_one";
            String settingTwo = "setting_two";

            mockScopeUtils.when(() -> ScopeUtils.getScopeFromProject(projectName)).thenReturn("project/test_project/");

            ILocation mockDefaultLocation = mock(ILocation.class);
            when(mockDefaultLocation.append(anyString())).thenReturn(mockDefaultLocation);
            mockScopeUtils.when(() -> ScopeUtils.getContextLocation("")).thenReturn(mockDefaultLocation);
            when(mockedSettingsService.getLastRevision(mockDefaultLocation)).thenReturn("some_revision");

            ILocation mockProjectLocation = mock(ILocation.class);
            when(mockedSettingsService.getPersistedSettingFileNames(mockProjectLocation)).thenReturn(List.of(settingOne, settingTwo));
            mockScopeUtils.when(() -> ScopeUtils.getContextLocation("project/test_project/")).thenReturn(mockProjectLocation);
            when(mockProjectLocation.append(contains("xml"))).thenReturn(mockProjectLocation);
            ILocation settingOneLocation = mock(ILocation.class);
            when(mockProjectLocation.append(contains(settingOne))).thenReturn(settingOneLocation);
            ILocation settingTwoLocation = mock(ILocation.class);
            when(mockProjectLocation.append(contains(settingTwo))).thenReturn(settingTwoLocation);
            mockScopeUtils.when(() -> ScopeUtils.getContextLocationByProject(projectName)).thenReturn(mockProjectLocation);
            when(mockedSettingsService.getLastRevision(mockProjectLocation)).thenReturn("some_revision");

            AuthorizationModel settingOneModel = new AuthorizationModel();
            settingOneModel.setBundleTimestamp("setting_one");
            settingOneModel.setProjectRoles("projectRole");
            settingOneModel.setGlobalRoles("globalRole");
            when(mockedSettingsService.read(eq(settingOneLocation), any())).thenReturn(settingOneModel.serialize());

            AuthorizationModel settingTwoModel = new AuthorizationModel();
            settingTwoModel.setBundleTimestamp("setting_two");
            settingTwoModel.setProjectRoles("projectRole");
            settingTwoModel.setGlobalRoles("globalRole");
            when(mockedSettingsService.read(eq(settingTwoLocation), any())).thenReturn(settingTwoModel.serialize());


            AuthorizationModel loadedOneModel = settings.load(projectName, SettingId.fromName(settingOne));
            AuthorizationModel loadedTwoModel = settings.load(projectName, SettingId.fromName(settingTwo));
            // **Assertions for setting_one**
            assertNotNull(loadedOneModel);
            assertEquals("setting_one", loadedOneModel.getBundleTimestamp());
            assertTrue(loadedOneModel.getProjectRoles().contains("projectRole"));
            assertTrue(loadedOneModel.getGlobalRoles().contains("globalRole"));

            // **Assertions for setting_two**
            assertNotNull(loadedTwoModel);
            assertEquals("setting_two", loadedTwoModel.getBundleTimestamp());
            assertTrue(loadedTwoModel.getProjectRoles().contains("projectRole"));
            assertTrue(loadedTwoModel.getGlobalRoles().contains("globalRole"));
        }
    }

}
