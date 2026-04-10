package ch.sbb.polarion.extension.xml_repair.repairers;

import com.polarion.alm.tracker.ModuleUtils;
import com.polarion.alm.tracker.internal.ModulePagePart;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.core.util.types.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, PlatformContextMockExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class BaseHeadingsRepairerTest {

    private static class TestableHeadingsRepairer extends BaseHeadingsRepairer {
        @Override
        public String getDisplayName() {
            return "TestableHeadings";
        }

        @Override
        public String getDescription() {
            return "TestableHeadings";
        }
    }

    // ---- Utility method tests ----

    @Test
    void testIsEmptyParagraphTrue() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<p>  </p>");
        assertTrue(repairer.isEmptyParagraph(part));

        when(part.getElementHtml()).thenReturn("<p>\n</p>");
        assertTrue(repairer.isEmptyParagraph(part));

        when(part.getElementHtml()).thenReturn("<p class=\"test\"> \t </p>");
        assertTrue(repairer.isEmptyParagraph(part));

        when(part.getElementHtml()).thenReturn("<p></p>");
        assertTrue(repairer.isEmptyParagraph(part));
    }

    @Test
    void testIsEmptyParagraphFalse() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<p>Some text</p>");
        assertFalse(repairer.isEmptyParagraph(part));

        when(part.getElementHtml()).thenReturn("<div></div>");
        assertFalse(repairer.isEmptyParagraph(part));

        when(part.getElementHtml()).thenReturn("<p>  text  </p>");
        assertFalse(repairer.isEmptyParagraph(part));
    }

    @Test
    void testIsMacroTrue() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<div class=\"polarion-dle-wiki-block\">macro content</div>");
        assertTrue(repairer.isMacro(part));

        when(part.getElementHtml()).thenReturn("<div id=\"m1\" class=\"polarion-dle-wiki-block other-class\">content</div>");
        assertTrue(repairer.isMacro(part));
    }

    @Test
    void testIsMacroFalse() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<div class=\"regular-div\">content</div>");
        assertFalse(repairer.isMacro(part));

        when(part.getElementHtml()).thenReturn("<p class=\"polarion-dle-wiki-block\">content</p>");
        assertFalse(repairer.isMacro(part));

        when(part.getElementHtml()).thenReturn("<span>text</span>");
        assertFalse(repairer.isMacro(part));
    }

    @Test
    void testIsPageBreakTrue() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<div class=\"page\" name=page_break style=\"page-break-before:always\"></div>");
        assertTrue(repairer.isPageBreak(part));

        when(part.getElementHtml()).thenReturn("<div name=page_break></div>");
        assertTrue(repairer.isPageBreak(part));
    }

    @Test
    void testIsPageBreakFalse() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart part = mock(ModulePagePart.class);

        when(part.getElementHtml()).thenReturn("<div class=\"regular-div\">content</div>");
        assertFalse(repairer.isPageBreak(part));

        when(part.getElementHtml()).thenReturn("<div name=other_break></div>");
        assertFalse(repairer.isPageBreak(part));
    }

    // ---- findDesiredHeadingPosition tests ----

    @Test
    void testFindDesiredHeadingPositionEmptyList() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        assertEquals(0, repairer.findDesiredHeadingPosition(List.of()));
    }

    @Test
    void testFindDesiredHeadingPositionNoMacro() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart heading = mockPart(false, false, false, false);
        assertEquals(0, repairer.findDesiredHeadingPosition(List.of(heading)));
    }

    @Test
    void testFindDesiredHeadingPositionMacroThenPageBreak() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart macro = mockPart(false, true, false, false);
        ModulePagePart pageBreak = mockPart(false, false, true, false);
        ModulePagePart heading = mockPart(true, false, false, false);

        assertEquals(2, repairer.findDesiredHeadingPosition(List.of(macro, pageBreak, heading)));
    }

    @Test
    void testFindDesiredHeadingPositionPageBreakWithoutMacro() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart pageBreak = mockPart(false, false, true, false);
        ModulePagePart heading = mockPart(true, false, false, false);

        assertEquals(0, repairer.findDesiredHeadingPosition(List.of(pageBreak, heading)));
    }

    @Test
    void testFindDesiredHeadingPositionEmptyParagraphsThenMacroThenPageBreak() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart emptyP = mockPart(false, false, false, true);
        ModulePagePart macro = mockPart(false, true, false, false);
        ModulePagePart pageBreak = mockPart(false, false, true, false);

        assertEquals(3, repairer.findDesiredHeadingPosition(List.of(emptyP, macro, pageBreak)));
    }

    @Test
    void testFindDesiredHeadingPositionNonEmptyParagraphBreaksLoop() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart nonEmpty = mockPart(false, false, false, false);
        ModulePagePart macro = mockPart(false, true, false, false);

        assertEquals(0, repairer.findDesiredHeadingPosition(List.of(nonEmpty, macro)));
    }

    // ---- reorderHeadingToPosition tests ----

    @Test
    void testReorderHeadingToPositionMovesToFront() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart work1 = mockPart(false, false, false, false);
        ModulePagePart work2 = mockPart(false, false, false, false);
        ModulePagePart heading = mockPart(true, false, false, false);

        List<ModulePagePart> parts = new ArrayList<>(List.of(work1, work2, heading));
        repairer.reorderHeadingToPosition(parts, 0);

        assertEquals(heading, parts.get(0));
        assertEquals(work1, parts.get(1));
        assertEquals(work2, parts.get(2));
    }

    @Test
    void testReorderHeadingToPositionAlreadyAtDesiredPosition() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart heading = mockPart(true, false, false, false);
        ModulePagePart work1 = mockPart(false, false, false, false);

        List<ModulePagePart> parts = new ArrayList<>(List.of(heading, work1));
        repairer.reorderHeadingToPosition(parts, 0);

        assertEquals(heading, parts.get(0));
        assertEquals(work1, parts.get(1));
    }

    @Test
    void testReorderHeadingToMiddlePosition() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart macro = mockPart(false, true, false, false);
        ModulePagePart pageBreak = mockPart(false, false, true, false);
        ModulePagePart work1 = mockPart(false, false, false, false);
        ModulePagePart heading = mockPart(true, false, false, false);

        List<ModulePagePart> parts = new ArrayList<>(List.of(macro, pageBreak, work1, heading));
        repairer.reorderHeadingToPosition(parts, 2);

        assertEquals(macro, parts.get(0));
        assertEquals(pageBreak, parts.get(1));
        assertEquals(heading, parts.get(2));
        assertEquals(work1, parts.get(3));
    }

    @Test
    void testReorderHeadingNoHeadingPresent() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        ModulePagePart work1 = mockPart(false, false, false, false);
        ModulePagePart work2 = mockPart(false, false, false, false);

        List<ModulePagePart> parts = new ArrayList<>(List.of(work1, work2));
        repairer.reorderHeadingToPosition(parts, 0);

        assertEquals(work1, parts.get(0));
        assertEquals(work2, parts.get(1));
    }

    // ---- moveHeadingToProperPosition test ----

    @Test
    void testMoveHeadingToProperPosition() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);

        Text htmlText = mock(Text.class);
        when(module.getHomePageContent()).thenReturn(htmlText);
        when(htmlText.convertToHTML()).thenReturn(htmlText);
        when(htmlText.getContent()).thenReturn("<div>content</div>");
        when(module.getProjectId()).thenReturn("proj");

        ModulePagePart part = mock(ModulePagePart.class);
        when(part.isHeading()).thenReturn(true);
        when(part.getElementHtml()).thenReturn("<h1>Title</h1>");
        doAnswer(inv -> {
            ((StringBuilder) inv.getArgument(0)).append("<h1>Title</h1>");
            return null;
        }).when(part).append(any(StringBuilder.class));

        try (MockedStatic<ModuleUtils> moduleUtilsMock = mockStatic(ModuleUtils.class)) {
            moduleUtilsMock.when(() -> ModuleUtils.getContentPartsNew("<div>content</div>", "proj"))
                    .thenReturn(new ArrayList<>(List.of(part)));

            repairer.moveHeadingToProperPosition(module);

            verify(module).setHomePageContent(Text.html("<h1>Title</h1>"));
        }
    }

    // ---- hasTitleHeading tests ----

    @Test
    void testHasTitleHeadingReturnsTrueWhenHeadingTitleExists() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        Text mockText = mock(Text.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent()).thenReturn(mockText);
        when(mockText.convertToHTML().getContent()).thenReturn("<h1>Title</h1>");
        when(module.getProjectId()).thenReturn("proj");

        ModulePagePart part = mock(ModulePagePart.class);
        when(part.isHeadingTitle()).thenReturn(true);

        try (MockedStatic<ModuleUtils> moduleUtilsMock = mockStatic(ModuleUtils.class)) {
            moduleUtilsMock.when(() -> ModuleUtils.getContentPartsNew("<h1>Title</h1>", "proj"))
                    .thenReturn(List.of(part));
            assertTrue(repairer.hasTitleHeading(module));
        }
    }

    @Test
    void testHasTitleHeadingReturnsFalseWhenNoHeadingTitle() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        Text mockText = mock(Text.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent()).thenReturn(mockText);
        when(mockText.convertToHTML().getContent()).thenReturn("<h2>Not a title</h2>");
        when(module.getProjectId()).thenReturn("proj");

        ModulePagePart part = mock(ModulePagePart.class);
        when(part.isHeadingTitle()).thenReturn(false);

        try (MockedStatic<ModuleUtils> moduleUtilsMock = mockStatic(ModuleUtils.class)) {
            moduleUtilsMock.when(() -> ModuleUtils.getContentPartsNew("<h2>Not a title</h2>", "proj"))
                    .thenReturn(List.of(part));
            assertFalse(repairer.hasTitleHeading(module));
        }
    }

    @Test
    void testHasTitleHeadingReturnsFalseWhenPartsEmpty() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        Text mockText = mock(Text.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent()).thenReturn(mockText);
        when(mockText.convertToHTML().getContent()).thenReturn("");
        when(module.getProjectId()).thenReturn("proj");

        try (MockedStatic<ModuleUtils> moduleUtilsMock = mockStatic(ModuleUtils.class)) {
            moduleUtilsMock.when(() -> ModuleUtils.getContentPartsNew("", "proj"))
                    .thenReturn(List.of());
            assertFalse(repairer.hasTitleHeading(module));
        }
    }

    @Test
    void testHasTitleHeadingWithNullContentFallsBackToEmpty() {
        TestableHeadingsRepairer repairer = new TestableHeadingsRepairer();
        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getHomePageContent()).thenReturn(null);
        when(module.getProjectId()).thenReturn("proj");

        try (MockedStatic<ModuleUtils> moduleUtilsMock = mockStatic(ModuleUtils.class)) {
            moduleUtilsMock.when(() -> ModuleUtils.getContentPartsNew(anyString(), eq("proj")))
                    .thenReturn(List.of());
            assertFalse(repairer.hasTitleHeading(module));
        }
    }

    // ---- Helper ----

    private ModulePagePart mockPart(boolean isHeading, boolean isMacro, boolean isPageBreak, boolean isEmptyParagraph) {
        ModulePagePart part = mock(ModulePagePart.class);
        when(part.isHeading()).thenReturn(isHeading);
        if (isMacro) {
            when(part.getElementHtml()).thenReturn("<div class=\"polarion-dle-wiki-block\">macro</div>");
        } else if (isPageBreak) {
            when(part.getElementHtml()).thenReturn("<div name=page_break></div>");
        } else if (isEmptyParagraph) {
            when(part.getElementHtml()).thenReturn("<p>  </p>");
        } else {
            when(part.getElementHtml()).thenReturn("<div class=\"workItem\">content</div>");
        }
        return part;
    }
}
