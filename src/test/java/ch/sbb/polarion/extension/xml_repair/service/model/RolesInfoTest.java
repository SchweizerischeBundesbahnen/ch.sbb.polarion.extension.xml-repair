package ch.sbb.polarion.extension.xml_repair.service.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolesInfoTest {

    @Test
    void testAllArgsConstructor() {
        RolesInfo info = new RolesInfo(List.of("admin", "user"), List.of("project_lead"));

        assertEquals(List.of("admin", "user"), info.globalRoles());
        assertEquals(List.of("project_lead"), info.projectRoles());
    }

    @Test
    void testEmptyProjectRolesForNonProjectScope() {
        RolesInfo info = new RolesInfo(List.of("admin"), List.of());

        assertEquals(List.of("admin"), info.globalRoles());
        assertTrue(info.projectRoles().isEmpty());
    }

    @Test
    void testEqualsAndHashCode() {
        RolesInfo a = new RolesInfo(List.of("admin"), List.of("lead"));
        RolesInfo b = new RolesInfo(List.of("admin"), List.of("lead"));
        RolesInfo differentGlobal = new RolesInfo(List.of("user"), List.of("lead"));
        RolesInfo differentProject = new RolesInfo(List.of("admin"), List.of("member"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, differentGlobal);
        assertNotEquals(a, differentProject);
    }

    @Test
    void testToStringContainsBothRoleLists() {
        RolesInfo info = new RolesInfo(List.of("admin"), List.of("lead"));

        String text = info.toString();

        assertTrue(text.contains("admin"));
        assertTrue(text.contains("lead"));
    }
}
