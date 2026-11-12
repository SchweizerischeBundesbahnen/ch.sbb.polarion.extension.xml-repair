package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.util.LuceneUtils;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigType;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.ITestRecord;
import com.polarion.alm.tracker.model.ITestRun;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.StringUtils;
import com.polarion.platform.persistence.model.IPObjectList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds work item references of a test run which cannot be resolved: the test case and the defects of each test
 * record, and the summary defect of the test run.
 */
public class TestRunBrokenLinksRepairer extends BaseRepairer {

    public static final String NAME = "Broken Test Run Links";
    @VisibleForTesting
    static final String DELETE_UNRESOLVABLE = "deleteUnresolvable";
    private static final String LINK_KIND = "linkKind";
    private static final String PROBLEM = "problem";
    private static final String LINK_PROJECT_ID = "linkProjectId";
    private static final String LINK_ID = "linkId";
    private static final String TEST_CASE_PROJECT_ID = "testCaseProjectId";
    private static final String TEST_CASE_ID = "testCaseId";
    private static final String TEST_CASE_REVISION = "testCaseRevision";
    private static final String CONTENT_CHANGED = "Issue not found, possibly it was already repaired or the content was changed since the scan.";
    private static final String NOT_READABLE_WARNING = "Cannot repair automatically: the work item exists, but the current user cannot read it. Check the permissions or fix it manually.";
    private static final String CACHE_UNCHANGED_KEY_PREFIX = "TEST_RUN_UNCHANGED_REFERENCES";

    @Override
    public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        if (!(entity instanceof ITestRun testRun) || entity.isUnresolvable()) {
            return issues;
        }
        for (ITestRecord testRecord : testRun.getAllRecords()) {
            scanRecord(testRun, testRecord, context, issues);
        }

        IWorkItem summaryDefect = testRun.getSummaryDefect();
        if (summaryDefect != null) {
            Problem summaryDefectProblem = findProblem(summaryDefect, null, context.polarionService());
            if (summaryDefectProblem != null) {
                issues.add(createIssue(IssueMetaInfo.create(testRun), LinkKind.SUMMARY_DEFECT, summaryDefectProblem, summaryDefect,
                        "Summary defect '%s/%s' %s".formatted(StringUtils.getEmptyIfNull(summaryDefect.getProjectId()), summaryDefect.getId(), summaryDefectProblem.message)));
            }
        }
        return issues;
    }

    /**
     * Defect issues come before the test case issue: their repair finds the record by its test case, which the test case
     * repair changes.
     */
    private void scanRecord(@NotNull ITestRun testRun, @NotNull ITestRecord testRecord, @NotNull ScanContext context, @NotNull List<Issue> issues) {
        IWorkItem testCase = testRecord.getTestCase();
        if (testCase == null) {
            return;
        }
        String testCaseProjectId = StringUtils.getEmptyIfNull(testCase.getProjectId());
        String testCaseRevision = getTestCaseRevision(testRecord);

        for (IWorkItem defect : testRecord.getDefects()) {
            Problem defectProblem = findProblem(defect, null, context.polarionService());
            if (defectProblem != null) {
                issues.add(createIssue(recordMetaInfo(testRun, testCase, testCaseRevision), LinkKind.DEFECT, defectProblem, defect,
                        "Defect '%s/%s' of the test record for '%s/%s' %s".formatted(StringUtils.getEmptyIfNull(defect.getProjectId()), defect.getId(),
                                testCaseProjectId, testCase.getId(), defectProblem.message)));
            }
        }

        Problem testCaseProblem = findProblem(testCase, testCaseRevision, context.polarionService());
        if (testCaseProblem != null) {
            String reference = testCaseProjectId + "/" + testCase.getId() + (StringUtils.isEmpty(testCaseRevision) ? "" : ":" + testCaseRevision);
            issues.add(createIssue(recordMetaInfo(testRun, testCase, testCaseRevision), LinkKind.TEST_CASE, testCaseProblem, testCase,
                    "Test case '%s' of a test record %s".formatted(reference, testCaseProblem.message)));
        }
    }

    private @NotNull IssueMetaInfo recordMetaInfo(@NotNull ITestRun testRun, @NotNull IWorkItem testCase, @NotNull String testCaseRevision) {
        return IssueMetaInfo.create(testRun)
                .set(TEST_CASE_PROJECT_ID, StringUtils.getEmptyIfNull(testCase.getProjectId()))
                .set(TEST_CASE_ID, testCase.getId())
                .set(TEST_CASE_REVISION, testCaseRevision);
    }

    private @NotNull Issue createIssue(@NotNull IssueMetaInfo metaInfo, @NotNull LinkKind linkKind, @NotNull Problem problem,
                                       @NotNull IWorkItem linkedItem, @NotNull String message) {
        metaInfo.set(LINK_KIND, linkKind.name())
                .set(PROBLEM, problem.name())
                .set(LINK_PROJECT_ID, StringUtils.getEmptyIfNull(linkedItem.getProjectId()))
                .set(LINK_ID, linkedItem.getId());
        return new Issue(metaInfo, this, message);
    }

    /**
     * An item the user cannot read is reported apart from a missing one: it exists, so it must never be deleted
     * as broken, but an export run by this user silently leaves it out all the same.
     */
    @VisibleForTesting
    @Nullable Problem findProblem(@NotNull IWorkItem item, @Nullable String revision, @NotNull XmlRepairPolarionService polarionService) {
        String projectId = item.getProjectId();
        if (item.isUnresolvable() || StringUtils.isEmpty(projectId)) {
            return Problem.UNRESOLVABLE;
        }
        if (!item.can().read()) {
            return Problem.NOT_READABLE;
        }
        // isUnresolvable() checks the HEAD only, Polarion silently falls back to it for a revision that does not exist
        if (!StringUtils.isEmpty(revision) && !polarionService.isWorkItemExists(projectId, item.getId(), revision)) {
            return Problem.UNRESOLVABLE;
        }
        return null;
    }

    @Override
    protected @NotNull RepairResult repair(IWorkflowObject entity, RepairContext context) {
        IssueMetaInfo metaInfo = context.issueMetaInfo();
        RepairResult result = new RepairResult(metaInfo, false);
        if (Problem.valueOf(metaInfo.getString(PROBLEM)) == Problem.NOT_READABLE) {
            result.getWarnings().add(NOT_READABLE_WARNING);
            return result;
        }

        ITestRun testRun = (ITestRun) entity;
        // A reference left unchanged still matches its record, so the next issue with the same reference must pass it by.
        AtomicInteger unchanged = context.getAndCache(unchangedCountKey(testRun, metaInfo), AtomicInteger::new);
        String warning = switch (LinkKind.valueOf(metaInfo.getString(LINK_KIND))) {
            case TEST_CASE -> repairTestCase(testRun, context, unchanged.get());
            case DEFECT -> repairDefect(testRun, context, unchanged.get());
            case SUMMARY_DEFECT -> repairSummaryDefect(testRun, context);
        };
        if (StringUtils.isEmpty(warning)) {
            result.setSuccess(true);
        } else {
            unchanged.incrementAndGet();
            result.getWarnings().add(warning);
        }
        return result;
    }

    private @NotNull String unchangedCountKey(@NotNull ITestRun testRun, @NotNull IssueMetaInfo metaInfo) {
        return String.join("|", CACHE_UNCHANGED_KEY_PREFIX, testRun.getProjectId(), testRun.getId(), metaInfo.getString(LINK_KIND),
                metaInfo.getString(TEST_CASE_PROJECT_ID), metaInfo.getString(TEST_CASE_ID), metaInfo.getString(TEST_CASE_REVISION),
                metaInfo.getString(LINK_PROJECT_ID), metaInfo.getString(LINK_ID));
    }

    private @Nullable String repairTestCase(@NotNull ITestRun testRun, @NotNull RepairContext context, int recordsToPass) {
        List<ITestRecord> records = testRun.getAllRecords();
        int recordIndex = findRecordIndex(records, context.issueMetaInfo(), recordsToPass);
        ITestRecord testRecord = records.get(recordIndex);
        IWorkItem testCase = Objects.requireNonNull(testRecord.getTestCase());
        String revision = getTestCaseRevision(testRecord);
        String liveWarning = checkStillBroken(testCase, revision, context);
        if (liveWarning != null) {
            return liveWarning;
        }

        List<String> warnings = new ArrayList<>();
        IWorkItem replacement = findReplacement(testRun, testCase.getProjectId(), testCase.getId(), revision, context, warnings);
        if (replacement != null) {
            if (isSigned(testRecord)) {
                return "The test record is signed, changing its test case needs an e-signature. Fix it in Polarion or use 'Delete broken items' feature to remove it.";
            }
            setTestCase(testRecord, replacement);
            return null;
        }
        if (!warnings.isEmpty()) {
            return warnings.getFirst();
        }
        if (isDeleteUnresolvable(context)) {
            records.remove(recordIndex);
            return null;
        }
        return "Cannot find replacement for the test case. Use 'Delete broken items' feature to remove test records like this.";
    }

    private @Nullable String repairDefect(@NotNull ITestRun testRun, @NotNull RepairContext context, int defectsToPass) {
        IssueMetaInfo metaInfo = context.issueMetaInfo();
        List<IWorkItem> defects = null;
        int defectIndex = -1;
        int passed = 0;
        // Several records can share one test case (iterations), each of them can hold the defect.
        for (ITestRecord testRecord : testRun.getAllRecords()) {
            int index = matchesTestCase(testRecord, metaInfo) ? findLinkIndex(testRecord.getDefects(), metaInfo) : -1;
            if (index >= 0 && passed++ == defectsToPass) {
                defects = testRecord.getDefects();
                defectIndex = index;
                break;
            }
        }
        if (defects == null) {
            throw new IllegalStateException(CONTENT_CHANGED);
        }

        IWorkItem defect = defects.get(defectIndex);
        String liveWarning = checkStillBroken(defect, null, context);
        if (liveWarning != null) {
            return liveWarning;
        }
        List<String> warnings = new ArrayList<>();
        IWorkItem replacement = findReplacement(testRun, defect.getProjectId(), defect.getId(), null, context, warnings);
        if (replacement != null) {
            defects.set(defectIndex, replacement);
            return null;
        }
        if (!warnings.isEmpty()) {
            return warnings.getFirst();
        }
        if (isDeleteUnresolvable(context)) {
            defects.remove(defectIndex);
            return null;
        }
        return "Cannot find replacement for the defect. Use 'Delete broken items' feature to remove defects like this.";
    }

    private @Nullable String repairSummaryDefect(@NotNull ITestRun testRun, @NotNull RepairContext context) {
        IWorkItem summaryDefect = testRun.getSummaryDefect();
        if (summaryDefect == null || findLinkIndex(List.of(summaryDefect), context.issueMetaInfo()) < 0) {
            throw new IllegalStateException(CONTENT_CHANGED);
        }
        String liveWarning = checkStillBroken(summaryDefect, null, context);
        if (liveWarning != null) {
            return liveWarning;
        }

        List<String> warnings = new ArrayList<>();
        IWorkItem replacement = findReplacement(testRun, summaryDefect.getProjectId(), summaryDefect.getId(), null, context, warnings);
        if (replacement != null) {
            testRun.setValue(ITestRun.KEY_SUMMARY_DEFECT, replacement);
            return null;
        }
        if (!warnings.isEmpty()) {
            return warnings.getFirst();
        }
        if (isDeleteUnresolvable(context)) {
            testRun.setValue(ITestRun.KEY_SUMMARY_DEFECT, null);
            return null;
        }
        return "Cannot find replacement for the summary defect. Use 'Delete broken items' feature to remove summary defects like this.";
    }

    /**
     * The work item can be restored, or its permissions changed, between the scan and the repair. Then it is not broken any
     * more, or must not be deleted.
     */
    private @Nullable String checkStillBroken(@NotNull IWorkItem item, @Nullable String revision, @NotNull RepairContext context) {
        Problem problem = findProblem(item, revision, context.polarionService());
        if (problem == null) {
            return "Work item '%s/%s' exists now, nothing to repair. Scan again.".formatted(item.getProjectId(), item.getId());
        }
        return problem == Problem.NOT_READABLE ? NOT_READABLE_WARNING : null;
    }

    /**
     * Same search order as {@link BrokenLinkedWorkItemsRepairer}: the current project, then without the revision,
     * then all projects by id.
     */
    @VisibleForTesting
    @Nullable IWorkItem findReplacement(@NotNull ITestRun testRun, @Nullable String projectId, @NotNull String workItemId,
                                        @Nullable String revision, @NotNull RepairContext context, @NotNull List<String> warnings) {
        XmlRepairPolarionService polarionService = context.polarionService();
        String currentProjectId = Objects.requireNonNull(testRun.getProjectId());
        String effectiveRevision = StringUtils.getNullIfEmpty(revision);
        if (!Objects.equals(projectId, currentProjectId) && polarionService.isWorkItemExists(currentProjectId, workItemId, effectiveRevision)) {
            return polarionService.getWorkItem(currentProjectId, workItemId, effectiveRevision);
        }
        if (effectiveRevision != null && polarionService.isWorkItemExists(currentProjectId, workItemId, null)) {
            return polarionService.getWorkItem(currentProjectId, workItemId, null);
        }

        List<IWorkItem> itemsFound = searchResolvable(testRun, workItemId);
        if (itemsFound.size() > 1) {
            warnings.add("Work item '%s' found at least in 2 projects: '%s' and '%s'".formatted(workItemId, itemsFound.get(0).getProjectId(), itemsFound.get(1).getProjectId()));
            return null;
        }
        if (itemsFound.size() == 1) {
            IWorkItem found = itemsFound.getFirst();
            String foundProjectId = Objects.requireNonNull(found.getProjectId());
            if (effectiveRevision != null && polarionService.isWorkItemExists(foundProjectId, found.getId(), effectiveRevision)) {
                return polarionService.getWorkItem(foundProjectId, found.getId(), effectiveRevision);
            }
            return found;
        }
        return null;
    }

    /**
     * Up to two hits which resolve. The index can still return items deleted since, so a hit counts only once it resolves,
     * and the search has no limit of its own.
     */
    private @NotNull List<IWorkItem> searchResolvable(@NotNull ITestRun testRun, @NotNull String workItemId) {
        @SuppressWarnings("unchecked")
        IPObjectList<IWorkItem> hits = testRun.getDataSvc().searchInstances(IWorkItem.PROTO, LuceneUtils.term("id", workItemId), null);
        List<IWorkItem> resolvable = new ArrayList<>(2);
        for (IWorkItem hit : hits) {
            if (!hit.isUnresolvable()) {
                resolvable.add(hit);
                if (resolvable.size() == 2) {
                    break;
                }
            }
        }
        return resolvable;
    }

    /**
     * A versioned item keeps its revision in the record. A HEAD item clears the old revision, which would otherwise
     * still point the record at the broken one.
     */
    @VisibleForTesting
    void setTestCase(@NotNull ITestRecord testRecord, @NotNull IWorkItem testCase) {
        if (testCase.getRevision() == null) {
            testRecord.setTestCase(testCase);
            testRecord.setValue(ITestRecord.KEY_TEST_CASE_REVISION, null);
        } else {
            testRecord.setTestCaseWithRevision(testCase);
        }
    }

    /**
     * The matching record after the ones to pass, not one at a stored index: an earlier repair in the same run can delete
     * records and shift the indexes. Records sharing a broken test case are broken alike, so each issue takes the next one.
     */
    private int findRecordIndex(@NotNull List<ITestRecord> records, @NotNull IssueMetaInfo metaInfo, int recordsToPass) {
        int passed = 0;
        for (int i = 0; i < records.size(); i++) {
            if (matchesTestCase(records.get(i), metaInfo) && passed++ == recordsToPass) {
                return i;
            }
        }
        throw new IllegalStateException(CONTENT_CHANGED);
    }

    private boolean matchesTestCase(@NotNull ITestRecord testRecord, @NotNull IssueMetaInfo metaInfo) {
        IWorkItem testCase = testRecord.getTestCase();
        return testCase != null
                && Objects.equals(metaInfo.getString(TEST_CASE_PROJECT_ID), StringUtils.getEmptyIfNull(testCase.getProjectId()))
                && Objects.equals(metaInfo.getString(TEST_CASE_ID), testCase.getId())
                && Objects.equals(metaInfo.getString(TEST_CASE_REVISION), getTestCaseRevision(testRecord));
    }

    private int findLinkIndex(@NotNull List<IWorkItem> items, @NotNull IssueMetaInfo metaInfo) {
        for (int i = 0; i < items.size(); i++) {
            IWorkItem item = items.get(i);
            if (Objects.equals(metaInfo.getString(LINK_PROJECT_ID), StringUtils.getEmptyIfNull(item.getProjectId()))
                    && Objects.equals(metaInfo.getString(LINK_ID), item.getId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Polarion refuses to change a signed record outside an e-signature context, removing the record is still allowed.
     */
    private boolean isSigned(@NotNull ITestRecord testRecord) {
        return Boolean.TRUE.equals(testRecord.getValue(ITestRecord.KEY_SIGNED));
    }

    private @NotNull String getTestCaseRevision(@NotNull ITestRecord testRecord) {
        return StringUtils.getEmptyIfNull((String) testRecord.getValue(ITestRecord.KEY_TEST_CASE_REVISION));
    }

    private boolean isDeleteUnresolvable(@NotNull RepairContext context) {
        return context.configs().getBoolean(getClass(), DELETE_UNRESOLVABLE);
    }

    @Override
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Finds test case and defect references of test runs which do not exist or cannot be read. Repairer attempts to find and rewrite work items by ID.";
    }

    @Override
    public List<RepairerConfigMeta> getConfigs() {
        return List.of(
                new RepairerConfigMeta(DELETE_UNRESOLVABLE, "Delete broken items",
                        "Delete test records and defects whose work item cannot be found in any available project. Items the current user cannot read are never deleted",
                        RepairerConfigType.BOOLEAN, false)
        );
    }

    @VisibleForTesting
    enum LinkKind {
        TEST_CASE,
        DEFECT,
        SUMMARY_DEFECT
    }

    @VisibleForTesting
    enum Problem {
        UNRESOLVABLE("does not exist"),
        NOT_READABLE("cannot be read by the current user");

        private final String message;

        Problem(String message) {
            this.message = message;
        }
    }

}
