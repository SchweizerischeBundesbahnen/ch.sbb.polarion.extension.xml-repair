package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.util.HtmlUtils;
import com.polarion.alm.tracker.IModuleManager;
import com.polarion.alm.tracker.IModulePageLayouter;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.core.util.StringUtils;
import com.polarion.core.util.types.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ModuleTablesAndFiguresCaptionRepairer extends BaseRepairer {

    public static final String NAME = "Document content: ToT/ToF captions";

    private static final String PREFIX = "prefix";
    private static final String DATA_SEQUENCE = "dataSequence";

    // Used for optimization purposes only. We skip work items repair if this parameter contains 'false'
    private static final String WORK_ITEM = "isWorkItem";

    private static final String CAPTION_REGEX = "(?:\\R|>)(?<prefix>[^<]+)<span\\s+data-sequence=\"(?<dataSequence>[^\"]+)\"\\s+class=\"polarion-rte-caption\">";

    @Override
    public List<Issue> scan(IModule module, ScanContext context) {
        String content = StringUtils.getEmptyIfNull(module.getHomePageContent().getContent());

        List<Issue> issues = new ArrayList<>();
        findIssues(content, module, issues, false);

        streamModuleWorkItems(module).forEach(workItem -> {
            Set<String> usedFieldIds = getRenderedFieldIds(module, context.polarionService(), Objects.requireNonNull(workItem.getType()).getId());
            for (String fieldId : usedFieldIds) {
                if (workItem.getValue(fieldId) instanceof Text textValue) {
                    findIssues(StringUtils.getEmptyIfNull(textValue.getContent()), module, issues, true);
                }
            }
        });

        return issues;
    }

    private void findIssues(String content, IModule module, List<Issue> issues, boolean workItem) {
        RegexMatcher.get(CAPTION_REGEX).useJavaUtil().processEntry(content, regexEngine -> {
            String prefixTrimmed = HtmlUtils.cleanupHtmlSpaces(regexEngine.group(PREFIX)).trim();
            String dataSequenceTrimmed = regexEngine.group(DATA_SEQUENCE).trim();

            if (!Objects.equals(prefixTrimmed, dataSequenceTrimmed)) {
                String description = "Misaligned caption id '%s' with the label '%s'".formatted(org.springframework.web.util.HtmlUtils.htmlUnescape(dataSequenceTrimmed), org.springframework.web.util.HtmlUtils.htmlUnescape(prefixTrimmed));
                Issue existingIssue = issues.stream().filter(i -> i.getDescription().equals(description)).findFirst().orElse(null);
                if (existingIssue != null) {
                    existingIssue.getRawMetaInfo().set(WORK_ITEM, workItem || Boolean.TRUE.equals(existingIssue.getRawMetaInfo().getBoolean(WORK_ITEM)));
                } else {
                    issues.add(new Issue(IssueMetaInfo.create(module).set(ISSUE_DESCRIPTION, description).set(PREFIX, prefixTrimmed).set(DATA_SEQUENCE, dataSequenceTrimmed).set(WORK_ITEM, workItem), this, description));
                }
            }
        });
    }

    @Override
    @SuppressWarnings("java:S3776") // Ignore cognitive complexity warning, refactoring would make the code less readable
    protected @NotNull RepairResult repair(IModule module, RepairContext context) {

        IssueMetaInfo issueMetaInfo = context.issueMetaInfo();
        RepairResult result = new RepairResult(issueMetaInfo, false);

        String content = StringUtils.getEmptyIfNull(module.getHomePageContent().getContent());
        String fixedContent = fixCaptionIds(content, issueMetaInfo, result);
        if (!Objects.equals(fixedContent, content)) {
            module.setHomePageContent(module.getHomePageContent().isPlain() ? Text.plain(fixedContent) : Text.html(fixedContent));
        }

        if (Boolean.TRUE.equals(issueMetaInfo.getBoolean(WORK_ITEM))) {
            streamModuleWorkItems(module).forEach(workItem -> {
                Set<String> usedFieldIds = getRenderedFieldIds(module, context.polarionService(), Objects.requireNonNull(workItem.getType()).getId());
                for (String fieldId : usedFieldIds) {
                    if (workItem.getValue(fieldId) instanceof Text textValue) {
                        String fieldContent = textValue.getContent();
                        String fixedFieldContent = fixCaptionIds(fieldContent, issueMetaInfo, result);
                        if (!Objects.equals(fixedFieldContent, fieldContent)) {
                            workItem.setValue(fieldId, textValue.isPlain() ? Text.plain(fixedFieldContent) : Text.html(fixedFieldContent));
                            workItem.save();
                        }
                    }
                }
            });
        }

        return result;
    }

    @VisibleForTesting
    String fixCaptionIds(String content, IssueMetaInfo issueMetaInfo, RepairResult result) {
        String expectedPrefixTrimmed = issueMetaInfo.getString(PREFIX);
        String expectedDataSequenceTrimmed = issueMetaInfo.getString(DATA_SEQUENCE);
        return RegexMatcher.get(CAPTION_REGEX).useJavaUtil().replace(content, regexEngine -> {
            String caption = regexEngine.group();
            String prefixTrimmed = HtmlUtils.cleanupHtmlSpaces(regexEngine.group(PREFIX)).trim();
            String dataSequence = regexEngine.group(DATA_SEQUENCE);
            String dataSequenceTrimmed = dataSequence.trim();

            if (Objects.equals(prefixTrimmed, expectedPrefixTrimmed) && Objects.equals(dataSequenceTrimmed, expectedDataSequenceTrimmed)) {
                result.setSuccess(true);
                return caption.replace("data-sequence=\"%s\"".formatted(dataSequence), "data-sequence=\"%s\"".formatted(prefixTrimmed));
            } else {
                return caption;
            }
        });
    }

    @VisibleForTesting
    Set<String> getRenderedFieldIds(IModule module, @NotNull XmlRepairPolarionService polarionService, String workItemTypeId) {
        IModuleManager moduleManager = polarionService.getTrackerService().getModuleManager();
        return module.getRenderingLayouts().stream().filter(layout -> Objects.equals(layout.getType(), workItemTypeId)).flatMap(layout -> {
            IModulePageLayouter layouter = moduleManager.getModulePageLayouter(layout.getLayouter());
            return layouter.getRenderedFieldIds(layout).stream();
        }).collect(Collectors.toSet());
    }

    public String getDisplayName() {
        return NAME;
    }

    public String getDescription() {
        return "Finds captions of tables and figures in the document body which are misaligned with internal identifiers. " +
                "Usually this happens when captions are modified manually. As a result Table of tables/figures contain " +
                "unexpected caption. Repairing such items mean replacing internal identifier with the modified label.";
    }

}
