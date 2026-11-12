package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import ch.sbb.polarion.extension.generic.util.LuceneUtils;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.UserConfigs;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import com.polarion.alm.tracker.model.ITestRecord;
import com.polarion.alm.tracker.model.ITestRun;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkItemPermissions;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.platform.persistence.IDataService;
import com.polarion.platform.persistence.model.IPObjectList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ch.sbb.polarion.extension.xml_repair.testsupport.RepairerTestFixtures.createScanContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({PlatformContextMockExtension.class, MockitoExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class TestRunBrokenLinksRepairerTest {

    private static final String PROJECT = "proj";
    private static final UserConfigs DELETE_CONFIGS = new UserConfigs(Map.of("TestRunBrokenLinksRepairer", Map.of("deleteUnresolvable", true)));

    private final TestRunBrokenLinksRepairer repairer = new TestRunBrokenLinksRepairer();
    private XmlRepairPolarionService polarionService;
    private IDataService dataService;
    private ITestRun testRun;
    private List<ITestRecord> records;
    private Cache cache;

    @BeforeEach
    void setUp() {
        polarionService = mock(XmlRepairPolarionService.class);
        dataService = mock(IDataService.class);
        testRun = mock(ITestRun.class);
        records = new ArrayList<>();
        cache = new Cache();
        when(testRun.getProjectId()).thenReturn(PROJECT);
        when(testRun.getId()).thenReturn("TR-1");
        when(testRun.getAllRecords()).thenReturn(records);
        when(testRun.getDataSvc()).thenReturn(dataService);
        mockSearch("ANY");
    }

    // --- scan() ---

    @Test
    void testScanEntityNotTestRunReturnsEmpty() {
        assertTrue(repairer.scan(mock(IWorkflowObject.class), createScanContext(polarionService)).isEmpty());
    }

    @Test
    void testScanUnresolvableTestRunReturnsEmpty() {
        when(testRun.isUnresolvable()).thenReturn(true);
        addRecord(item(PROJECT, "TC-1", true, true), null);

        assertTrue(scan().isEmpty());
    }

    @Test
    void testScanValidTestRunReturnsEmpty() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", false, true), null);
        testRecord.getDefects().add(item(PROJECT, "BUG-1", false, true));
        IWorkItem summaryDefect = item(PROJECT, "BUG-2", false, true);
        when(testRun.getSummaryDefect()).thenReturn(summaryDefect);

        assertTrue(scan().isEmpty());
    }

    @Test
    void testScanSkipsRecordWithoutTestCase() {
        ITestRecord testRecord = mock(ITestRecord.class);
        when(testRecord.getDefects()).thenReturn(new ArrayList<>());
        records.add(testRecord);

        assertTrue(scan().isEmpty());
    }

    @Test
    void testScanReportsUnresolvableTestCase() {
        addRecord(item(PROJECT, "TC-1", true, true), null);

        List<Issue> issues = scan();

        assertEquals(1, issues.size());
        assertEquals("Test case 'proj/TC-1' of a test record does not exist", issues.getFirst().getDescription());
        assertEquals(TestRunBrokenLinksRepairer.class.getSimpleName(), issues.getFirst().getRepairer());
        assertEquals(Boolean.TRUE, issues.getFirst().getRawMetaInfo().getBoolean(IssueMetaInfo.TEST_RUN));
    }

    @Test
    void testScanReportsTestCaseWithoutProject() {
        addRecord(item(null, "TC-1", false, true), null);

        assertEquals("Test case '/TC-1' of a test record does not exist", scan().getFirst().getDescription());
    }

    @Test
    void testScanReportsNotReadableTestCase() {
        addRecord(item(PROJECT, "TC-1", false, false), null);

        assertEquals("Test case 'proj/TC-1' of a test record cannot be read by the current user", scan().getFirst().getDescription());
    }

    @Test
    void testScanReportsMissingTestCaseRevision() {
        addRecord(item(PROJECT, "TC-1", false, true), "42");
        when(polarionService.isWorkItemExists(PROJECT, "TC-1", "42")).thenReturn(false);

        assertEquals("Test case 'proj/TC-1:42' of a test record does not exist", scan().getFirst().getDescription());
    }

    @Test
    void testScanAcceptsExistingTestCaseRevision() {
        addRecord(item(PROJECT, "TC-1", false, true), "42");
        when(polarionService.isWorkItemExists(PROJECT, "TC-1", "42")).thenReturn(true);

        assertTrue(scan().isEmpty());
    }

    @Test
    void testScanReportsBrokenDefects() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", false, true), null);
        testRecord.getDefects().add(item("other", "BUG-1", true, true));
        testRecord.getDefects().add(item(PROJECT, "BUG-2", false, false));

        List<Issue> issues = scan();

        assertEquals(List.of(
                "Defect 'other/BUG-1' of the test record for 'proj/TC-1' does not exist",
                "Defect 'proj/BUG-2' of the test record for 'proj/TC-1' cannot be read by the current user"
        ), issues.stream().map(Issue::getDescription).toList());
        assertNotEquals(issues.get(0).getMetaInfo(), issues.get(1).getMetaInfo());
    }

    @Test
    void testScanReportsBrokenSummaryDefect() {
        IWorkItem summaryDefect = item(PROJECT, "BUG-9", true, true);
        when(testRun.getSummaryDefect()).thenReturn(summaryDefect);

        assertEquals("Summary defect 'proj/BUG-9' does not exist", scan().getFirst().getDescription());
    }

    // --- repair() of a test case ---

    @Test
    void testRepairNotReadableReturnsWarning() {
        addRecord(item(PROJECT, "TC-1", false, false), null);

        RepairResult result = repair(scan().getFirst(), new UserConfigs(Map.of("TestRunBrokenLinksRepairer", Map.of("deleteUnresolvable", true))));

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().iterator().next().startsWith("Cannot repair automatically"));
        assertEquals(1, records.size());
    }

    @Test
    void testRepairTestCaseFromCurrentProject() {
        ITestRecord testRecord = addRecord(item("other", "TC-1", true, true), null);
        IWorkItem replacement = item(PROJECT, "TC-1", false, true);
        when(polarionService.isWorkItemExists(PROJECT, "TC-1", null)).thenReturn(true);
        when(polarionService.getWorkItem(PROJECT, "TC-1", null)).thenReturn(replacement);

        RepairResult result = repair(scan().getFirst(), new UserConfigs());

        assertTrue(result.isSuccess());
        verify(testRecord).setTestCase(replacement);
        verify(testRecord).setValue(ITestRecord.KEY_TEST_CASE_REVISION, null);
    }

    @Test
    void testRepairTestCaseKeepsRevisionInCurrentProject() {
        ITestRecord testRecord = addRecord(item("other", "TC-1", true, true), "42");
        IWorkItem replacement = item(PROJECT, "TC-1", false, true);
        when(replacement.getRevision()).thenReturn("42");
        when(polarionService.isWorkItemExists(PROJECT, "TC-1", "42")).thenReturn(true);
        when(polarionService.getWorkItem(PROJECT, "TC-1", "42")).thenReturn(replacement);

        assertTrue(repair(scan().getFirst(), new UserConfigs()).isSuccess());
        verify(testRecord).setTestCaseWithRevision(replacement);
    }

    @Test
    void testRepairTestCaseDropsMissingRevision() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", false, true), "42");
        IWorkItem replacement = item(PROJECT, "TC-1", false, true);
        when(polarionService.isWorkItemExists(PROJECT, "TC-1", "42")).thenReturn(false);
        when(polarionService.isWorkItemExists(PROJECT, "TC-1", null)).thenReturn(true);
        when(polarionService.getWorkItem(PROJECT, "TC-1", null)).thenReturn(replacement);

        assertTrue(repair(scan().getFirst(), new UserConfigs()).isSuccess());
        verify(testRecord).setTestCase(replacement);
        verify(testRecord).setValue(ITestRecord.KEY_TEST_CASE_REVISION, null);
    }

    @Test
    void testRepairTestCaseFoundGlobally() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", true, true), null);
        IWorkItem found = item("global", "TC-1", false, true);
        mockSearch("TC-1", found);

        assertTrue(repair(scan().getFirst(), new UserConfigs()).isSuccess());
        verify(testRecord).setTestCase(found);
    }

    @Test
    void testRepairTestCaseFoundGloballyKeepsRevision() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", true, true), "42");
        IWorkItem found = item("global", "TC-1", false, true);
        IWorkItem versioned = item("global", "TC-1", false, true);
        when(versioned.getRevision()).thenReturn("42");
        mockSearch("TC-1", found);
        when(polarionService.isWorkItemExists("global", "TC-1", "42")).thenReturn(true);
        when(polarionService.getWorkItem("global", "TC-1", "42")).thenReturn(versioned);

        assertTrue(repair(scan().getFirst(), new UserConfigs()).isSuccess());
        verify(testRecord).setTestCaseWithRevision(versioned);
    }

    @Test
    void testRepairTestCaseFoundInTwoProjectsReturnsWarning() {
        addRecord(item(PROJECT, "TC-1", true, true), null);
        mockSearch("TC-1", item("a", "TC-1", false, true), item("b", "TC-1", false, true));

        RepairResult result = repair(scan().getFirst(), new UserConfigs(Map.of("TestRunBrokenLinksRepairer", Map.of("deleteUnresolvable", true))));

        assertFalse(result.isSuccess());
        assertEquals("Work item 'TC-1' found at least in 2 projects: 'a' and 'b'", result.getWarnings().iterator().next());
        assertEquals(1, records.size());
    }

    @Test
    void testRepairTestCaseIgnoresUnresolvableHits() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", true, true), null);
        mockSearch("TC-1", item("stale", "TC-1", true, true), item("stale2", "TC-1", true, true));

        RepairResult result = repair(scan().getFirst(), new UserConfigs());

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().iterator().next().startsWith("Cannot find replacement for the test case"));
        verify(testRecord, never()).setTestCase(any());
    }

    @Test
    void testRepairSignedTestCaseKeepsRecord() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", true, true), null);
        when(testRecord.getValue(ITestRecord.KEY_SIGNED)).thenReturn(true);
        mockSearch("TC-1", item("global", "TC-1", false, true));

        RepairResult result = repair(scan().getFirst(), new UserConfigs(Map.of("TestRunBrokenLinksRepairer", Map.of("deleteUnresolvable", true))));

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().iterator().next().startsWith("The test record is signed"));
        verify(testRecord, never()).setTestCase(any());
        verify(testRecord, never()).setTestCaseWithRevision(any());
        assertEquals(1, records.size());
    }

    @Test
    void testRepairSignedTestCaseNotFoundDeletesRecord() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", true, true), null);
        when(testRecord.getValue(ITestRecord.KEY_SIGNED)).thenReturn(true);

        assertTrue(repair(scan().getFirst(), new UserConfigs(Map.of("TestRunBrokenLinksRepairer", Map.of("deleteUnresolvable", true)))).isSuccess());
        assertTrue(records.isEmpty());
    }

    @Test
    void testRepairTestCaseCountsOnlyResolvableHits() {
        addRecord(item(PROJECT, "TC-1", true, true), null);
        mockSearch("TC-1", item("stale", "TC-1", true, true), item("a", "TC-1", false, true), item("b", "TC-1", false, true));

        RepairResult result = repair(scan().getFirst(), DELETE_CONFIGS);

        assertFalse(result.isSuccess());
        assertEquals("Work item 'TC-1' found at least in 2 projects: 'a' and 'b'", result.getWarnings().iterator().next());
        assertEquals(1, records.size());
    }

    @Test
    void testRepairTestCaseRestoredSinceScanIsKept() {
        IWorkItem testCase = item(PROJECT, "TC-1", true, true);
        addRecord(testCase, null);
        Issue issue = scan().getFirst();
        when(testCase.isUnresolvable()).thenReturn(false);

        RepairResult result = repair(issue, DELETE_CONFIGS);

        assertFalse(result.isSuccess());
        assertEquals("Work item 'proj/TC-1' exists now, nothing to repair. Scan again.", result.getWarnings().iterator().next());
        assertEquals(1, records.size());
    }

    @Test
    void testRepairDefectNotReadableSinceScanIsKept() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", false, true), null);
        IWorkItem defect = item(PROJECT, "BUG-1", true, true);
        testRecord.getDefects().add(defect);
        Issue issue = scan().getFirst();
        when(defect.isUnresolvable()).thenReturn(false);
        when(defect.can().read()).thenReturn(false);

        RepairResult result = repair(issue, DELETE_CONFIGS);

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().iterator().next().startsWith("Cannot repair automatically: the work item exists"));
        assertEquals(1, testRecord.getDefects().size());
    }

    @Test
    void testScanReportsDefectsBeforeTestCase() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", true, true), null);
        testRecord.getDefects().add(item(PROJECT, "BUG-1", true, true));

        assertEquals(List.of(
                "Defect 'proj/BUG-1' of the test record for 'proj/TC-1' does not exist",
                "Test case 'proj/TC-1' of a test record does not exist"
        ), scan().stream().map(Issue::getDescription).toList());
    }

    @Test
    void testRepairDefectAndRelinkedTestCaseOfOneRecord() {
        ITestRecord testRecord = addRecord(item("other", "TC-1", true, true), null);
        testRecord.getDefects().add(item(PROJECT, "BUG-1", true, true));
        IWorkItem replacement = item(PROJECT, "TC-1", false, true);
        when(polarionService.isWorkItemExists(PROJECT, "TC-1", null)).thenReturn(true);
        when(polarionService.getWorkItem(PROJECT, "TC-1", null)).thenReturn(replacement);
        List<Issue> issues = scan();

        assertTrue(repair(issues.get(0), DELETE_CONFIGS).isSuccess());
        assertTrue(repair(issues.get(1), DELETE_CONFIGS).isSuccess());

        assertTrue(testRecord.getDefects().isEmpty());
        verify(testRecord).setTestCase(replacement);
    }

    @Test
    void testRepairSignedRecordIsPassedByForTheNextIteration() {
        IWorkItem testCase = item(PROJECT, "TC-1", true, true);
        ITestRecord signed = addRecord(testCase, null);
        when(signed.getValue(ITestRecord.KEY_SIGNED)).thenReturn(true);
        ITestRecord unsigned = addRecord(testCase, null);
        IWorkItem found = item("global", "TC-1", false, true);
        mockSearch("TC-1", found);
        List<Issue> issues = scan();

        assertFalse(repair(issues.get(0), new UserConfigs()).isSuccess());
        assertTrue(repair(issues.get(1), new UserConfigs()).isSuccess());

        verify(signed, never()).setTestCase(any());
        verify(unsigned).setTestCase(found);
    }

    @Test
    void testRepairDefectKeptInFirstIterationIsPassedBy() {
        IWorkItem testCase = item(PROJECT, "TC-1", false, true);
        IWorkItem defect = item(PROJECT, "BUG-1", true, true);
        ITestRecord first = addRecord(testCase, null);
        first.getDefects().add(defect);
        ITestRecord second = addRecord(testCase, null);
        second.getDefects().add(defect);
        List<Issue> issues = scan();

        assertFalse(repair(issues.get(0), new UserConfigs()).isSuccess());
        IWorkItem found = item("global", "BUG-1", false, true);
        mockSearch("BUG-1", found);
        assertTrue(repair(issues.get(1), new UserConfigs()).isSuccess());

        assertSame(defect, first.getDefects().getFirst());
        assertSame(found, second.getDefects().getFirst());
    }

    @Test
    void testRepairTestCaseNotFoundReturnsWarning() {
        addRecord(item(PROJECT, "TC-1", true, true), null);

        RepairResult result = repair(scan().getFirst(), new UserConfigs());

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().iterator().next().startsWith("Cannot find replacement for the test case"));
        assertEquals(1, records.size());
    }

    @Test
    void testRepairTestCaseNotFoundDeletesRecords() {
        IWorkItem brokenTestCase = item(PROJECT, "TC-1", true, true);
        addRecord(brokenTestCase, null);
        ITestRecord valid = addRecord(item(PROJECT, "TC-2", false, true), null);
        addRecord(brokenTestCase, null);
        List<Issue> issues = scan();
        UserConfigs configs = new UserConfigs(Map.of("TestRunBrokenLinksRepairer", Map.of("deleteUnresolvable", true)));

        // the second repair must still find its record after the first one shifted the list
        assertTrue(repair(issues.get(0), configs).isSuccess());
        assertTrue(repair(issues.get(1), configs).isSuccess());

        assertEquals(List.of(valid), records);
    }

    @Test
    void testRepairTestCaseChangedSinceScanThrows() {
        addRecord(item(PROJECT, "TC-1", true, true), null);
        Issue issue = scan().getFirst();
        records.clear();

        UserConfigs configs = new UserConfigs();
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> repair(issue, configs));
        assertTrue(exception.getMessage().startsWith("Issue not found"));
    }

    // --- repair() of a defect ---

    @Test
    void testRepairDefectFoundGlobally() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", false, true), null);
        testRecord.getDefects().add(item(PROJECT, "BUG-1", false, true));
        testRecord.getDefects().add(item(PROJECT, "BUG-2", true, true));
        IWorkItem found = item("global", "BUG-2", false, true);
        mockSearch("BUG-2", found);

        assertTrue(repair(scan().getFirst(), new UserConfigs()).isSuccess());
        assertSame(found, testRecord.getDefects().get(1));
    }

    @Test
    void testRepairDefectInLaterIteration() {
        IWorkItem testCase = item(PROJECT, "TC-1", false, true);
        addRecord(testCase, null);
        ITestRecord secondIteration = addRecord(testCase, null);
        secondIteration.getDefects().add(item(PROJECT, "BUG-1", true, true));

        assertTrue(repair(scan().getFirst(), new UserConfigs(Map.of("TestRunBrokenLinksRepairer", Map.of("deleteUnresolvable", true)))).isSuccess());
        assertTrue(secondIteration.getDefects().isEmpty());
    }

    @Test
    void testRepairDefectNotFoundReturnsWarning() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", false, true), null);
        testRecord.getDefects().add(item(PROJECT, "BUG-1", true, true));

        RepairResult result = repair(scan().getFirst(), new UserConfigs());

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().iterator().next().startsWith("Cannot find replacement for the defect"));
        assertEquals(1, testRecord.getDefects().size());
    }

    @Test
    void testRepairDefectFoundInTwoProjectsReturnsWarning() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", false, true), null);
        testRecord.getDefects().add(item(PROJECT, "BUG-1", true, true));
        mockSearch("BUG-1", item("a", "BUG-1", false, true), item("b", "BUG-1", false, true));

        RepairResult result = repair(scan().getFirst(), new UserConfigs());

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().iterator().next().startsWith("Work item 'BUG-1' found at least in 2 projects"));
    }

    @Test
    void testRepairDefectChangedSinceScanThrows() {
        ITestRecord testRecord = addRecord(item(PROJECT, "TC-1", false, true), null);
        testRecord.getDefects().add(item(PROJECT, "BUG-1", true, true));
        Issue issue = scan().getFirst();
        testRecord.getDefects().clear();

        UserConfigs configs = new UserConfigs();
        assertThrows(IllegalStateException.class, () -> repair(issue, configs));
    }

    // --- repair() of the summary defect ---

    @Test
    void testRepairSummaryDefectFromCurrentProject() {
        IWorkItem summaryDefect = item("other", "BUG-9", true, true);
        when(testRun.getSummaryDefect()).thenReturn(summaryDefect);
        IWorkItem replacement = item(PROJECT, "BUG-9", false, true);
        when(polarionService.isWorkItemExists(PROJECT, "BUG-9", null)).thenReturn(true);
        when(polarionService.getWorkItem(PROJECT, "BUG-9", null)).thenReturn(replacement);

        assertTrue(repair(scan().getFirst(), new UserConfigs()).isSuccess());
        verify(testRun).setValue(ITestRun.KEY_SUMMARY_DEFECT, replacement);
    }

    @Test
    void testRepairSummaryDefectNotFoundDeletes() {
        IWorkItem summaryDefect = item(PROJECT, "BUG-9", true, true);
        when(testRun.getSummaryDefect()).thenReturn(summaryDefect);

        assertTrue(repair(scan().getFirst(), new UserConfigs(Map.of("TestRunBrokenLinksRepairer", Map.of("deleteUnresolvable", true)))).isSuccess());
        verify(testRun).setValue(ITestRun.KEY_SUMMARY_DEFECT, null);
    }

    @Test
    void testRepairSummaryDefectNotFoundReturnsWarning() {
        IWorkItem summaryDefect = item(PROJECT, "BUG-9", true, true);
        when(testRun.getSummaryDefect()).thenReturn(summaryDefect);

        RepairResult result = repair(scan().getFirst(), new UserConfigs());

        assertFalse(result.isSuccess());
        assertTrue(result.getWarnings().iterator().next().startsWith("Cannot find replacement for the summary defect"));
        verify(testRun, never()).setValue(eq(ITestRun.KEY_SUMMARY_DEFECT), any());
    }

    @Test
    void testRepairSummaryDefectFoundInTwoProjectsReturnsWarning() {
        IWorkItem summaryDefect = item(PROJECT, "BUG-9", true, true);
        when(testRun.getSummaryDefect()).thenReturn(summaryDefect);
        mockSearch("BUG-9", item("a", "BUG-9", false, true), item("b", "BUG-9", false, true));

        assertFalse(repair(scan().getFirst(), new UserConfigs()).isSuccess());
    }

    @Test
    void testRepairSummaryDefectChangedSinceScanThrows() {
        IWorkItem summaryDefect = item(PROJECT, "BUG-9", true, true);
        when(testRun.getSummaryDefect()).thenReturn(summaryDefect);
        Issue issue = scan().getFirst();
        when(testRun.getSummaryDefect()).thenReturn(null);

        UserConfigs configs = new UserConfigs();
        assertThrows(IllegalStateException.class, () -> repair(issue, configs));
    }

    // --- metadata ---

    @Test
    void testMetadata() {
        assertEquals(TestRunBrokenLinksRepairer.NAME, repairer.getDisplayName());
        assertFalse(repairer.getDescription().isEmpty());
        List<RepairerConfigMeta> configs = repairer.getConfigs();
        assertEquals(1, configs.size());
        assertEquals(TestRunBrokenLinksRepairer.DELETE_UNRESOLVABLE, configs.getFirst().getKey());
    }

    // --- helpers ---

    private List<Issue> scan() {
        return repairer.scan(testRun, createScanContext(polarionService));
    }

    private RepairResult repair(Issue issue, UserConfigs configs) {
        IssueMetaInfo metaInfo = IssueMetaInfo.fromString(issue.getMetaInfo());
        return repairer.repair(testRun, new RepairContext(metaInfo, polarionService, configs, cache));
    }

    private ITestRecord addRecord(IWorkItem testCase, String testCaseRevision) {
        ITestRecord testRecord = mock(ITestRecord.class);
        when(testRecord.getTestCase()).thenReturn(testCase);
        when(testRecord.getValue(ITestRecord.KEY_TEST_CASE_REVISION)).thenReturn(testCaseRevision);
        when(testRecord.getDefects()).thenReturn(new ArrayList<>());
        records.add(testRecord);
        return testRecord;
    }

    private static IWorkItem item(String projectId, String id, boolean unresolvable, boolean readable) {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn(projectId);
        when(workItem.getId()).thenReturn(id);
        when(workItem.isUnresolvable()).thenReturn(unresolvable);
        IWorkItemPermissions permissions = mock(IWorkItemPermissions.class);
        when(permissions.read()).thenReturn(readable);
        when(workItem.can()).thenReturn(permissions);
        return workItem;
    }

    @SuppressWarnings("unchecked")
    private void mockSearch(String workItemId, IWorkItem... found) {
        IPObjectList<IWorkItem> result = mock(IPObjectList.class);
        when(result.iterator()).thenAnswer(invocation -> List.of(found).iterator());
        if ("ANY".equals(workItemId)) {
            when(dataService.searchInstances(anyString(), anyString(), isNull())).thenReturn(result);
        } else {
            when(dataService.searchInstances(eq(IWorkItem.PROTO), eq(LuceneUtils.term("id", workItemId)), isNull())).thenReturn(result);
        }
    }

}
