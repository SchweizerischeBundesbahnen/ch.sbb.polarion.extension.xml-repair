package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import ch.sbb.polarion.extension.xml_repair.service.model.IContext;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.IssueMetaInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.StringUtils;
import com.polarion.core.util.types.Text;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Checks the links to documents which rich text carries as 'polarion-rte-link' spans of the type 'document'.
 * Such a link addresses its target by space and name, so moving the document to another space breaks it.
 * The counterpart for links to work items is {@link BaseLinksRepairer}.
 */
public class RichTextDocumentLinksRepairer extends BaseRepairer {

    public static final String NAME = "Rich text: Broken Links to Documents";

    static final String DEFAULT_SPACE = "_default";
    private static final String LINK = "link";
    private static final String GROUP_NAME = "documentName";
    private static final String GROUP_SPACE = "spaceName";
    private static final String GROUP_PROJECT = "projectId";
    private static final String GROUP_REVISION = "revision";
    private static final String DATA_SPACE_NAME_TEMPLATE = "data-space-name=\"%s\"";
    private static final String DATA_ITEM_NAME_ANCHOR = "data-item-name=";
    private static final String CACHE_EXISTS_KEY_TEMPLATE = "DOCUMENT_EXISTS_%s_%s_%s_%s";
    private static final String CACHE_SPACES_KEY_TEMPLATE = "DOCUMENT_SPACES_%s_%s";

    // Only the opening tag is matched: every attribute lives there, and it is the only part a repair rewrites.
    private static final String LINK_REGEX = "<span\\s+" +
            "(?=[^>]*class=\"polarion-rte-link\")" +
            "(?=[^>]*data-type=\"document\")" +
            "(?=[^>]*data-item-name=\"(?<documentName>[^\"]+)\")" +
            "(?=[^>]*data-space-name=\"(?<spaceName>[^\"]*)\")?" +
            "(?=[^>]*data-scope=\"(?<projectId>[^\"]+)\")?" +
            "(?=[^>]*data-revision=\"(?<revision>[^\"]+)\")?" +
            "[^>]*>";

    @Override
    public List<Issue> scan(IWorkflowObject entity, ScanContext context) {
        List<Issue> issues = new ArrayList<>();
        for (String fieldId : getTextFieldIds(entity, context)) {
            if (entity.getValue(fieldId) instanceof Text textValue && textValue.getContent() != null) {
                RegexMatcher.get(LINK_REGEX).useJavaUtil().processEntry(textValue.getContent(), regexEngine -> {
                    DocumentLink link = DocumentLink.from(regexEngine.group(), regexEngine.group(GROUP_NAME), regexEngine.group(GROUP_SPACE),
                            regexEngine.group(GROUP_PROJECT), regexEngine.group(GROUP_REVISION), entity);
                    if (!documentExists(context, link, link.space())) {
                        issues.add(new Issue(IssueMetaInfo.create(entity).set(FIELD_ID, fieldId).set(LINK, link.markup()), this,
                                "%s %s".formatted(describeBrokenLink(link), describeRepairability(link, findOtherSpaces(context, link)))));
                    }
                });
            }
        }
        return issues;
    }

    @Override
    protected @NotNull RepairResult repair(IWorkflowObject entity, RepairContext context) {
        IssueMetaInfo issueMetaInfo = context.issueMetaInfo();
        String fieldId = issueMetaInfo.getString(FIELD_ID);
        if (!(entity.getValue(fieldId) instanceof Text text)) {
            return new RepairResult(issueMetaInfo, false, "Field '%s' is not of type Text.".formatted(fieldId));
        }
        String linkToFix = issueMetaInfo.getString(LINK);
        String html = StringUtils.getEmptyIfNull(text.getContent());
        if (!html.contains(linkToFix)) {
            return new RepairResult(issueMetaInfo, false, "The link to fix was not found in the content, possibly it was already fixed or the content was changed since the scan.");
        }

        RepairResult result = new RepairResult(issueMetaInfo, false);
        // The scan reports one issue per occurrence, so the same link used several times yields several issues
        // carrying identical markup. Repair only the first occurrence still broken: fixing all of them at once
        // would leave the remaining issues reporting a failure although the content is correct.
        AtomicBoolean occurrenceHandled = new AtomicBoolean(false);
        String fixedHtml = RegexMatcher.get(LINK_REGEX).useJavaUtil().replace(html, regexEngine -> {
            String markup = regexEngine.group();
            if (!Objects.equals(markup, linkToFix) || occurrenceHandled.getAndSet(true)) {
                return markup;
            }
            String rawSpace = regexEngine.group(GROUP_SPACE);
            DocumentLink link = DocumentLink.from(markup, regexEngine.group(GROUP_NAME), rawSpace,
                    regexEngine.group(GROUP_PROJECT), regexEngine.group(GROUP_REVISION), entity);
            if (documentExists(context, link, link.space())) {
                result.getWarnings().add("The link is not broken any more: document '%s' exists in the space '%s'.".formatted(link.name(), link.space()));
                return markup;
            }
            List<String> otherSpaces = findOtherSpaces(context, link);
            if (otherSpaces.size() != 1) {
                result.getWarnings().add(describeRepairability(link, otherSpaces));
                return markup;
            }
            result.setSuccess(true);
            String newSpaceAttribute = DATA_SPACE_NAME_TEMPLATE.formatted(escapeAttribute(otherSpaces.getFirst()));
            return rawSpace == null
                    // there was no space provided - insert it before the attribute every such link has
                    ? markup.replace(DATA_ITEM_NAME_ANCHOR, "%s %s".formatted(newSpaceAttribute, DATA_ITEM_NAME_ANCHOR))
                    : markup.replace(DATA_SPACE_NAME_TEMPLATE.formatted(rawSpace), newSpaceAttribute);
        });
        if (result.isSuccess()) {
            entity.setValue(fieldId, text.isPlain() ? Text.plain(fixedHtml) : Text.html(fixedHtml));
        }
        return result;
    }

    @Override
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Finds links to documents in rich text (fields and the document body) and checks if the referenced document exists in the space the link names. " +
                "Such a link breaks when the document is moved to another space. " +
                "If the document is found in exactly one other space of the same project, the link is pointed to that space; " +
                "otherwise the link is reported as not repairable.";
    }

    private Set<String> getTextFieldIds(IWorkflowObject entity, ScanContext context) {
        Set<String> fieldIds = getAllFieldsUsingCache(context, entity.getPrototype().getName(), entity.getContextId(),
                Objects.requireNonNull(entity.getType()).getId(), false, FieldType.TEXT.getType(), FieldType.RICH.getType())
                .stream().map(FieldMetadata::getId).collect(Collectors.toCollection(TreeSet::new));
        if (entity instanceof IModule) {
            // The body of a document is where most of these links live, so it is scanned whether or not
            // the field listing above returns it.
            fieldIds.add(IModule.KEY_HOMEPAGECONTENT);
        }
        return fieldIds;
    }

    private boolean documentExists(IContext context, DocumentLink link, String space) {
        String key = CACHE_EXISTS_KEY_TEMPLATE.formatted(link.projectId(), space, link.name(), link.revision());
        return Boolean.TRUE.equals(context.getAndCache(key, () -> context.polarionService().isDocumentExists(link.projectId(), space, link.name(), link.revision())));
    }

    /**
     * The spaces of the link's project, other than the one the link names, where a document of that name can
     * be opened. For a link pinned to a revision, only those where it existed at that revision.
     */
    private List<String> findOtherSpaces(IContext context, DocumentLink link) {
        List<String> spaces = context.getAndCache(CACHE_SPACES_KEY_TEMPLATE.formatted(link.projectId(), link.name()),
                () -> context.polarionService().findDocumentSpaces(link.projectId(), link.name()));
        return (spaces == null ? List.<String>of() : spaces).stream()
                .filter(space -> !space.equals(link.space()))
                .filter(space -> link.revision() == null || documentExists(context, link, space))
                .toList();
    }

    private String describeBrokenLink(DocumentLink link) {
        return "Broken link found: document '%s' does not exist in the space '%s' of the project '%s'.".formatted(
                link.name() + (link.revision() == null ? "" : (":" + link.revision())), link.space(), link.projectId());
    }

    private String describeRepairability(DocumentLink link, List<String> otherSpaces) {
        return switch (otherSpaces.size()) {
            case 0 -> "The link cannot be fixed: document '%s' was not found in any other space of the project.".formatted(link.name());
            case 1 -> "The document was found in the space '%s', the link can be fixed.".formatted(otherSpaces.getFirst());
            default -> "The link cannot be fixed: document '%s' exists in several spaces (%s).".formatted(link.name(),
                    otherSpaces.stream().map("'%s'"::formatted).collect(Collectors.joining(", ")));
        };
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * A link as it has to be looked up: the attribute values decoded, the space and the project defaulted the
     * way Polarion does when the link leaves them out.
     */
    private record DocumentLink(@NotNull String markup, @NotNull String name, @NotNull String space, @NotNull String projectId, @Nullable String revision) {

        @SuppressWarnings("deprecation") // commons-text, which took this class over, is not among Polarion's bundles
        static DocumentLink from(String markup, String rawName, @Nullable String rawSpace, @Nullable String rawProjectId, @Nullable String revision, IWorkflowObject entity) {
            String space = rawSpace == null ? null : StringEscapeUtils.unescapeHtml4(rawSpace);
            return new DocumentLink(markup,
                    StringEscapeUtils.unescapeHtml4(rawName),
                    StringUtils.isEmptyTrimmed(space) ? DEFAULT_SPACE : space,
                    StringUtils.isEmpty(rawProjectId) ? entity.getProjectId() : rawProjectId,
                    revision);
        }
    }

}
