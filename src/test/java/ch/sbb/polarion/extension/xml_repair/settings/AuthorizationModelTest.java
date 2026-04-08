package ch.sbb.polarion.extension.xml_repair.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationModelTest {

    private AuthorizationModel authorizationModel;

    @BeforeEach
    void setUp() {
        authorizationModel = new AuthorizationModel();
    }

    @Test
    void testConstants() {
        assertEquals("globalRoles", AuthorizationModel.GLOBAL_ROLES);
        assertEquals("projectRoles", AuthorizationModel.PROJECT_ROLES);
    }

    @Test
    void testSetGlobalRolesWithMultipleRoles() {
        authorizationModel.setGlobalRoles("admin", "user", "manager");

        List<String> globalRoles = authorizationModel.getGlobalRoles();
        assertEquals(3, globalRoles.size());
        assertTrue(globalRoles.contains("admin"));
        assertTrue(globalRoles.contains("user"));
        assertTrue(globalRoles.contains("manager"));
    }

    @Test
    void testSetGlobalRolesWithSingleRole() {
        authorizationModel.setGlobalRoles("admin");

        List<String> globalRoles = authorizationModel.getGlobalRoles();
        assertEquals(1, globalRoles.size());
        assertEquals("admin", globalRoles.get(0));
    }

    @Test
    void testSetGlobalRolesWithEmptyArray() {
        authorizationModel.setGlobalRoles();

        List<String> globalRoles = authorizationModel.getGlobalRoles();
        assertNotNull(globalRoles);
        assertTrue(globalRoles.isEmpty());
    }

    @Test
    void testSetProjectRolesWithMultipleRoles() {
        authorizationModel.setProjectRoles("project-admin", "project-user", "project-viewer");

        List<String> projectRoles = authorizationModel.getProjectRoles();
        assertEquals(3, projectRoles.size());
        assertTrue(projectRoles.contains("project-admin"));
        assertTrue(projectRoles.contains("project-user"));
        assertTrue(projectRoles.contains("project-viewer"));
    }

    @Test
    void testSetProjectRolesWithSingleRole() {
        authorizationModel.setProjectRoles("project-admin");

        List<String> projectRoles = authorizationModel.getProjectRoles();
        assertEquals(1, projectRoles.size());
        assertEquals("project-admin", projectRoles.get(0));
    }

    @Test
    void testSetProjectRolesWithEmptyArray() {
        authorizationModel.setProjectRoles();

        List<String> projectRoles = authorizationModel.getProjectRoles();
        assertNotNull(projectRoles);
        assertTrue(projectRoles.isEmpty());
    }

    @Test
    void testGetAllRolesWithBothGlobalAndProjectRoles() {
        authorizationModel.setGlobalRoles("admin", "user");
        authorizationModel.setProjectRoles("project-admin", "project-user");

        List<String> allRoles = authorizationModel.getAllRoles();
        assertEquals(4, allRoles.size());
        assertTrue(allRoles.contains("admin"));
        assertTrue(allRoles.contains("user"));
        assertTrue(allRoles.contains("project-admin"));
        assertTrue(allRoles.contains("project-user"));
    }

    @Test
    void testGetAllRolesWithOnlyGlobalRoles() {
        authorizationModel.setGlobalRoles("admin", "user");
        authorizationModel.setProjectRoles(); // Initialize with empty array

        List<String> allRoles = authorizationModel.getAllRoles();
        assertEquals(2, allRoles.size());
        assertTrue(allRoles.contains("admin"));
        assertTrue(allRoles.contains("user"));
    }

    @Test
    void testGetAllRolesWithOnlyProjectRoles() {
        authorizationModel.setGlobalRoles(); // Initialize with empty array
        authorizationModel.setProjectRoles("project-admin", "project-user");

        List<String> allRoles = authorizationModel.getAllRoles();
        assertEquals(2, allRoles.size());
        assertTrue(allRoles.contains("project-admin"));
        assertTrue(allRoles.contains("project-user"));
    }

    @Test
    void testGetAllRolesWithNoRoles() {
        authorizationModel.setGlobalRoles();
        authorizationModel.setProjectRoles();

        List<String> allRoles = authorizationModel.getAllRoles();
        assertNotNull(allRoles);
        assertTrue(allRoles.isEmpty());
    }

    @Test
    void testSerializeRolesWithValidRoles() {
        List<String> roles = List.of("admin", "user", "manager");
        String serialized = authorizationModel.serializeRoles(roles);
        assertEquals("admin,user,manager", serialized);
    }

    @Test
    void testSerializeRolesWithSingleRole() {
        List<String> roles = List.of("admin");
        String serialized = authorizationModel.serializeRoles(roles);
        assertEquals("admin", serialized);
    }

    @Test
    void testSerializeRolesWithEmptyList() {
        List<String> roles = new ArrayList<>();
        String serialized = authorizationModel.serializeRoles(roles);
        assertEquals("", serialized);
    }

    @Test
    void testSerializeRolesWithNullList() {
        String serialized = authorizationModel.serializeRoles(null);
        assertEquals("", serialized);
    }

    @Test
    void testDeserializeRolesWithValidRoles() {
        authorizationModel.setGlobalRoles("admin", "user", "manager");
        String serialized = authorizationModel.serialize();

        AuthorizationModel deserializedModel = new AuthorizationModel();
        deserializedModel.deserialize(serialized);

        assertEquals(3, deserializedModel.getGlobalRoles().size());
        assertTrue(deserializedModel.getGlobalRoles().contains("admin"));
        assertTrue(deserializedModel.getGlobalRoles().contains("user"));
        assertTrue(deserializedModel.getGlobalRoles().contains("manager"));
    }

    @Test
    void testDeserializeRolesWithSingleRole() {
        authorizationModel.setGlobalRoles("admin");
        String serialized = authorizationModel.serialize();

        AuthorizationModel deserializedModel = new AuthorizationModel();
        deserializedModel.deserialize(serialized);

        assertEquals(1, deserializedModel.getGlobalRoles().size());
        assertEquals("admin", deserializedModel.getGlobalRoles().get(0));
    }

    @Test
    void testDeserializeRolesWithEmptyRoles() {
        authorizationModel.setGlobalRoles();
        authorizationModel.setProjectRoles("project-admin");
        String serialized = authorizationModel.serialize();

        AuthorizationModel deserializedModel = new AuthorizationModel();
        deserializedModel.deserialize(serialized);

        assertTrue(deserializedModel.getGlobalRoles().isEmpty());
        assertEquals(1, deserializedModel.getProjectRoles().size());
        assertEquals("project-admin", deserializedModel.getProjectRoles().get(0));
    }

    @Test
    void testDeserializeRolesWithMissingKey() {
        authorizationModel.setProjectRoles("project-admin");
        String serialized = authorizationModel.serialize();

        AuthorizationModel deserializedModel = new AuthorizationModel();
        deserializedModel.deserialize(serialized);

        assertNotNull(deserializedModel.getGlobalRoles());
        assertTrue(deserializedModel.getGlobalRoles().isEmpty());
        assertEquals(1, deserializedModel.getProjectRoles().size());
        assertEquals("project-admin", deserializedModel.getProjectRoles().get(0));
    }

    @Test
    void testSpaceTrimmingInDeserialization() {
        AuthorizationModel model = new AuthorizationModel();
        model.setGlobalRoles("admin", "user");

        String serialized = model.serialize();
        String modifiedSerialized = serialized.replace("admin,user", " admin , user ");

        AuthorizationModel deserializedModel = new AuthorizationModel();
        deserializedModel.deserialize(modifiedSerialized);

        assertEquals(2, deserializedModel.getGlobalRoles().size());
        assertTrue(deserializedModel.getGlobalRoles().contains("admin"));
        assertTrue(deserializedModel.getGlobalRoles().contains("user"));
    }

    @Test
    void testEmptyStringFilteringInDeserialization() {
        AuthorizationModel model = new AuthorizationModel();
        model.setGlobalRoles("admin", "user");

        String serialized = model.serialize();
        String modifiedSerialized = serialized.replace("admin,user", "admin,,user,,");

        AuthorizationModel deserializedModel = new AuthorizationModel();
        deserializedModel.deserialize(modifiedSerialized);

        assertEquals(2, deserializedModel.getGlobalRoles().size());
        assertTrue(deserializedModel.getGlobalRoles().contains("admin"));
        assertTrue(deserializedModel.getGlobalRoles().contains("user"));
    }

    @Test
    void testSerializeAndDeserializeModelData() {
        authorizationModel.setGlobalRoles("admin", "user");
        authorizationModel.setProjectRoles("project-admin", "project-user");

        String serialized = authorizationModel.serialize();
        assertNotNull(serialized);
        assertTrue(serialized.contains("admin,user"));
        assertTrue(serialized.contains("project-admin,project-user"));

        AuthorizationModel deserializedModel = new AuthorizationModel();
        deserializedModel.deserialize(serialized);

        assertEquals(2, deserializedModel.getGlobalRoles().size());
        assertTrue(deserializedModel.getGlobalRoles().contains("admin"));
        assertTrue(deserializedModel.getGlobalRoles().contains("user"));

        assertEquals(2, deserializedModel.getProjectRoles().size());
        assertTrue(deserializedModel.getProjectRoles().contains("project-admin"));
        assertTrue(deserializedModel.getProjectRoles().contains("project-user"));
    }

    @Test
    void testSerializeAndDeserializeWithEmptyRoles() {
        authorizationModel.setGlobalRoles();
        authorizationModel.setProjectRoles();

        String serialized = authorizationModel.serialize();
        assertNotNull(serialized);

        AuthorizationModel deserializedModel = new AuthorizationModel();
        deserializedModel.deserialize(serialized);

        assertNotNull(deserializedModel.getGlobalRoles());
        assertTrue(deserializedModel.getGlobalRoles().isEmpty());
        assertNotNull(deserializedModel.getProjectRoles());
        assertTrue(deserializedModel.getProjectRoles().isEmpty());
    }

    @Test
    void testEqualsAndHashCode() {
        AuthorizationModel model1 = new AuthorizationModel();
        model1.setGlobalRoles("admin", "user");
        model1.setProjectRoles("project-admin");

        AuthorizationModel model2 = new AuthorizationModel();
        model2.setGlobalRoles("admin", "user");
        model2.setProjectRoles("project-admin");

        AuthorizationModel model3 = new AuthorizationModel();
        model3.setGlobalRoles("admin");
        model3.setProjectRoles("project-admin");

        assertEquals(model1, model2);
        assertEquals(model1.hashCode(), model2.hashCode());

        assertNotEquals(model1, model3);
        assertNotEquals(model1.hashCode(), model3.hashCode());
    }

    @Test
    void testEqualsWithDifferentGlobalRoles() {
        AuthorizationModel model1 = new AuthorizationModel();
        model1.setGlobalRoles("admin", "user");
        model1.setProjectRoles("project-admin");

        AuthorizationModel model2 = new AuthorizationModel();
        model2.setGlobalRoles("admin", "manager");
        model2.setProjectRoles("project-admin");

        assertNotEquals(model1, model2);
    }

    @Test
    void testEqualsWithDifferentProjectRoles() {
        AuthorizationModel model1 = new AuthorizationModel();
        model1.setGlobalRoles("admin");
        model1.setProjectRoles("project-admin");

        AuthorizationModel model2 = new AuthorizationModel();
        model2.setGlobalRoles("admin");
        model2.setProjectRoles("project-user");

        assertNotEquals(model1, model2);
    }

    @Test
    void testNullSafety() {
        AuthorizationModel model = new AuthorizationModel();
        model.setGlobalRoles();
        model.setProjectRoles();

        assertNotNull(model.getAllRoles());
        assertTrue(model.getAllRoles().isEmpty());

        assertEquals("", model.serializeRoles(null));

        assertNotNull(model.getGlobalRoles());
        assertNotNull(model.getProjectRoles());
    }
}
