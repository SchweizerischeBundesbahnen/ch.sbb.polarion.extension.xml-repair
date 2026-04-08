package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.xml_repair.service.EntityRenderer;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.transaction.internal.InternalReadOnlyTransaction;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.internal.ModulePagePart;
import com.polarion.alm.tracker.model.IModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(PlatformContextMockExtension.class)
class ModuleWrongTitleHeadingPositionRepairerTest {

    @Test
    void testDisplayNameAndDescription() {
        ModuleWrongTitleHeadingPositionRepairer repairer = new ModuleWrongTitleHeadingPositionRepairer();
        assertEquals("Document content: Wrong title-heading position", repairer.getDisplayName());
        assertNotNull(repairer.getDescription());
        assertTrue(repairer.getDescription().contains("title-heading"));
        assertEquals("ModuleWrongTitleHeadingPositionRepairer", repairer.getRepairerId());
    }

    // --- isHeadingAtProperPosition tests ---

    @Test
    void testHeadingAtFirstPosition() {
        ModuleWrongTitleHeadingPositionRepairer repairer = new ModuleWrongTitleHeadingPositionRepairer();
        assertTrue(repairer.isHeadingAtProperPosition(List.of(mockPart(true, false, false, false))));
    }

    @Test
    void testEmptyList() {
        ModuleWrongTitleHeadingPositionRepairer repairer = new ModuleWrongTitleHeadingPositionRepairer();
        assertFalse(repairer.isHeadingAtProperPosition(List.of()));
    }

    @Test
    void testHeadingAfterEmptyParagraphs() {
        ModuleWrongTitleHeadingPositionRepairer repairer = new ModuleWrongTitleHeadingPositionRepairer();
        assertTrue(repairer.isHeadingAtProperPosition(List.of(
                mockPart(false, false, false, true),
                mockPart(true, false, false, false))));
    }

    @Test
    void testHeadingAfterMacroAndPageBreak() {
        ModuleWrongTitleHeadingPositionRepairer repairer = new ModuleWrongTitleHeadingPositionRepairer();
        assertTrue(repairer.isHeadingAtProperPosition(List.of(
                mockPart(false, true, false, false),
                mockPart(false, false, true, false),
                mockPart(true, false, false, false))));
    }

    @Test
    void testHeadingNotAtProperPositionAfterWorkItem() {
        ModuleWrongTitleHeadingPositionRepairer repairer = new ModuleWrongTitleHeadingPositionRepairer();
        assertFalse(repairer.isHeadingAtProperPosition(List.of(
                mockPart(false, false, false, false),
                mockPart(true, false, false, false))));
    }

    @Test
    void testPageBreakWithoutMacro() {
        ModuleWrongTitleHeadingPositionRepairer repairer = new ModuleWrongTitleHeadingPositionRepairer();
        assertFalse(repairer.isHeadingAtProperPosition(List.of(
                mockPart(false, false, true, false),
                mockPart(true, false, false, false))));
    }

    @Test
    void testSecondPageBreakBreaksLoop() {
        ModuleWrongTitleHeadingPositionRepairer repairer = new ModuleWrongTitleHeadingPositionRepairer();
        assertFalse(repairer.isHeadingAtProperPosition(List.of(
                mockPart(false, true, false, false),
                mockPart(false, false, true, false),
                mockPart(false, false, true, false),
                mockPart(true, false, false, false))));
    }

    @Test
    void testEmptyParagraphsThenMacroThenPageBreakThenHeading() {
        ModuleWrongTitleHeadingPositionRepairer repairer = new ModuleWrongTitleHeadingPositionRepairer();
        assertTrue(repairer.isHeadingAtProperPosition(List.of(
                mockPart(false, false, false, true),
                mockPart(false, true, false, false),
                mockPart(false, false, true, false),
                mockPart(true, false, false, false))));
    }

    @Test
    void testOnlyMacroNoHeading() {
        ModuleWrongTitleHeadingPositionRepairer repairer = new ModuleWrongTitleHeadingPositionRepairer();
        assertFalse(repairer.isHeadingAtProperPosition(List.of(mockPart(false, true, false, false))));
    }

    // --- scan tests ---

    @Test
    void testScanNoTitleHeading() {
        ModuleWrongTitleHeadingPositionRepairer repairer = spy(new ModuleWrongTitleHeadingPositionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        doReturn(false).when(repairer).hasTitleHeading(module);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertTrue(issues.isEmpty());
        verify(repairer, never()).getContentParts(any());
    }

    @Test
    void testScanHeadingAtProperPosition() {
        ModuleWrongTitleHeadingPositionRepairer repairer = spy(new ModuleWrongTitleHeadingPositionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        doReturn(true).when(repairer).hasTitleHeading(module);
        doReturn(List.of(mockPart(true, false, false, false))).when(repairer).getContentParts(module);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertTrue(issues.isEmpty());
    }

    @Test
    void testScanHeadingWrongPosition() {
        ModuleWrongTitleHeadingPositionRepairer repairer = spy(new ModuleWrongTitleHeadingPositionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleName()).thenReturn("TestDocument");
        doReturn(true).when(repairer).hasTitleHeading(module);
        doReturn(List.of(
                mockPart(false, false, false, false),
                mockPart(true, false, false, false)
        )).when(repairer).getContentParts(module);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        List<Issue> issues = repairer.scan(module, createScanContext(polarionService));

        assertEquals(1, issues.size());
        Issue issue = issues.getFirst();
        assertTrue(issue.getDescription().contains("wrong title-heading position"));
    }

    @Test
    void testRepairHeadingWrongPosition() {
        ModuleWrongTitleHeadingPositionRepairer repairer = spy(new ModuleWrongTitleHeadingPositionRepairer());

        IModule module = mock(IModule.class, RETURNS_DEEP_STUBS);
        when(module.getProjectId()).thenReturn("elibrary");
        when(module.getModuleName()).thenReturn("TestDocument");
        doReturn(true).when(repairer).hasTitleHeading(module);
        doReturn(List.of(
                mockPart(false, false, false, false),
                mockPart(true, false, false, false)
        )).when(repairer).getContentParts(module);
        doNothing().when(repairer).moveHeadingToProperPosition(module);

        XmlRepairPolarionService polarionService = mock(XmlRepairPolarionService.class);
        IssueMetaInfo metaInfo = mock(IssueMetaInfo.class);
        when(metaInfo.serialize()).thenReturn("serialized");
        RepairContext repairContext = new RepairContext(metaInfo, polarionService, new UserConfigs());

        RepairResult result = repairer.repair(module, repairContext);

        assertTrue(result.isSuccess());
        verify(repairer).moveHeadingToProperPosition(module);
    }

    private ScanContext createScanContext(XmlRepairPolarionService polarionService) {
        lenient().when(polarionService.getTrackerService()).thenReturn(mock(ITrackerService.class));
        try (MockedStatic<TransactionalExecutorImpl> txMock = mockStatic(TransactionalExecutorImpl.class);
             MockedConstruction<EntityRenderer> ignored = mockConstruction(EntityRenderer.class)) {
            txMock.when(TransactionalExecutorImpl::currentTransaction).thenReturn(mock(InternalReadOnlyTransaction.class));
            return new ScanContext(polarionService, List.of(), new UserConfigs(), new Report());
        }
    }

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
