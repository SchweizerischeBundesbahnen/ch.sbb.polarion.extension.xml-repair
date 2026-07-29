package ch.sbb.polarion.extension.xml_repair.service;

import ch.sbb.polarion.extension.generic.exception.ObjectNotFoundException;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.rest.exception.UnauthorizedException;
import ch.sbb.polarion.extension.generic.service.PolarionService;
import ch.sbb.polarion.extension.generic.settings.AuthorizationModel;
import ch.sbb.polarion.extension.generic.settings.NamedSettings;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import ch.sbb.polarion.extension.xml_repair.repairers.*;
import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairParams;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairerMeta;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.EntityRef;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanEntity;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanResult;
import ch.sbb.polarion.extension.xml_repair.settings.AuthorizationSettings;
import ch.sbb.polarion.extension.xml_repair.util.Cache;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.impl.ScopeFactoryImpl;
import com.polarion.alm.shared.api.model.ModelObject;
import com.polarion.alm.shared.api.model.ModelObjectsSearch;
import com.polarion.alm.shared.api.model.PrototypeEnum;
import com.polarion.alm.shared.api.model.document.Document;
import com.polarion.alm.shared.api.model.eo.EnumOption;
import com.polarion.alm.shared.api.model.eo.Enumeration;
import com.polarion.alm.shared.api.transaction.ReadOnlyTransaction;
import com.polarion.alm.shared.api.utils.collections.IterableWithSize;
import com.polarion.alm.shared.api.utils.internal.InternalPolarionUtils;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.internal.model.UniqueObject;
import com.polarion.alm.tracker.model.IBaseline;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollectionElement;
import com.polarion.alm.tracker.model.ipi.IInternalBaselinesManager;
import com.polarion.core.util.StringUtils;
import com.polarion.core.util.logging.Logger;
import com.polarion.platform.IPlatformService;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.service.repository.IRepositoryService;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.data.model.IType;
import com.polarion.subterra.base.location.Location;
import org.apache.commons.lang3.time.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("java:S1200") // This service class necessarily couples many domain types
public class XmlRepairPolarionService extends PolarionService {

    // The two refusals checkAccess can raise: the administrator's role setting, then Polarion's own
    // permissions. They lived in RolesUtils, which the shared role code replaced.
    public static final String MSG_NOT_AUTHORIZED_BY_ADMIN = "Repair operation is restricted for current user by the Administrator.";
    public static final String MSG_NO_PERMISSIONS = "Current user is not allowed to modify the entity.";

    public static final String SCAN_TIME_LIMIT_REACHED_WARNING = "Scan time limit was reached during processing, some items may remain unchecked. Please consider increasing the time limit or narrow the query.";

    // Enumeration ids as resolved by Polarion's REST v1 enumerations endpoint (see EnumerationResourceReference#enumId)
    private static final String WORK_ITEM_TYPE_ENUM_ID = "work-item-type";
    private static final String DOCUMENT_TYPE_ENUM_ID = "documents/document-type";

    public static final Map<EntityType, List<IRepairer>> REPAIRERS = Map.of(
            EntityType.COLLECTION, List.of(
                    new ModuleContentLinksRepairer(),
                    new ModuleDuplicateLayoutDeclarationRepairer(),
                    new ModuleMissingTitleHeadingRepairer(),
                    new ModuleTablesAndFiguresCaptionRepairer(),
                    new ModuleWrongLayoutAssignmentsRepairer(),
                    new ModuleWrongTitleHeadingPositionRepairer(),
                    new ModuleNonExistentWorkItemsRepairer(),
                    new ModuleStandardStructureLinkRoleRepairer(),
                    new FieldsFormattingSymbolsRepairer(),
                    new FieldsInvalidEnumerationValueRepairer(),
                    new FieldsInvalidUserValueRepairer(),
                    new FieldsRichTextLinksRepairer(),
                    new FieldsWrongTypeRepairer()
            ),
            EntityType.DOCUMENT, List.of(
                    new ModuleContentLinksRepairer(),
                    new ModuleDuplicateLayoutDeclarationRepairer(),
                    new ModuleMissingTitleHeadingRepairer(),
                    new ModuleTablesAndFiguresCaptionRepairer(),
                    new ModuleWrongLayoutAssignmentsRepairer(),
                    new ModuleWrongTitleHeadingPositionRepairer(),
                    new ModuleNonExistentWorkItemsRepairer(),
                    new ModuleStandardStructureLinkRoleRepairer(),
                    new FieldsFormattingSymbolsRepairer(),
                    new FieldsInvalidEnumerationValueRepairer(),
                    new FieldsInvalidUserValueRepairer(),
                    new FieldsRichTextLinksRepairer(),
                    new FieldsWrongTypeRepairer()
            ),
            EntityType.WORKITEM, List.of(
                    new BrokenLinkedWorkItemsRepairer(),
                    new FieldsFormattingSymbolsRepairer(),
                    new FieldsInvalidEnumerationValueRepairer(),
                    new FieldsInvalidUserValueRepairer(),
                    new FieldsRichTextLinksRepairer(),
                    new FieldsWrongTypeRepairer()
            )
    );
    private static final int DEFAULT_LIMIT = 100;
    // Upper bound for the selectable entity list of a project. Big enough for any real project, small
    // enough to keep the response and the client-side dropdown filtering fast. Same number as the bound
    // on a submitted selection, so what the picker offers and what a scan accepts cannot diverge.
    @VisibleForTesting
    static final int ENTITY_LIST_LIMIT = ScanParams.MAX_ENTITIES;

    private final Logger logger = Logger.getLogger(XmlRepairPolarionService.class);

    public XmlRepairPolarionService() {
        super();
    }

    @VisibleForTesting
    XmlRepairPolarionService(@NotNull ITrackerService trackerService, @NotNull IProjectService projectService,
                             @NotNull ISecurityService securityService, @NotNull IPlatformService platformService,
                             @NotNull IRepositoryService repositoryService) {
        super(trackerService, projectService, securityService, platformService, repositoryService);
    }

    public List<RepairResult> repair(@NotNull RepairParams params) {
        List<RepairResult> results = new ArrayList<>();
        Cache cache = new Cache();
        for (String issueMetaInfo : params.getIssueMetaInfos()) {
            IssueMetaInfo metaInfo = IssueMetaInfo.fromString(issueMetaInfo);
            try {
                RepairResult result;
                if (metaInfo.getString(IssueMetaInfo.REVISION) != null) {
                    result = new RepairResult(metaInfo, false, "Cannot repair items from a baseline/revision; switch to HEAD to repair.");
                } else {
                    String projectId = metaInfo.getString(IssueMetaInfo.PROJECT_ID);
                    String modulePath = metaInfo.getString(IssueMetaInfo.MODULE_PATH);
                    String id = metaInfo.getString(IssueMetaInfo.ID);
                    IUniqueObject entity = modulePath != null ?
                            getModule(getProject(projectId), Location.getLocation(modulePath)) : getWorkItem(projectId, id, null);
                    result = repairEntity(entity, new RepairContext(metaInfo, this, params.getConfigs(), cache));
                }
                results.add(result);
            } catch (Exception e) {
                logger.error("Error during item repair: %s".formatted(e.getMessage()), e);
                results.add(new RepairResult(metaInfo, false, "Error during item repair: %s".formatted(e.getMessage())));
            }
        }
        return results;
    }

    public RepairResult repairEntity(@NotNull IUniqueObject entity, @NotNull RepairContext context) {
        checkAccess(entity);
        if (entity instanceof IWorkflowObject workflowObject && workflowObject.getType() == null) {
            String errorText = "Entity '%s' has no type.".formatted(((UniqueObject) entity).getReferencePath());
            return new RepairResult(IssueMetaInfo.create(workflowObject), false, errorText);
        }

        String repairerName = context.issueMetaInfo().getString(IssueMetaInfo.REPAIRER);
        IRepairer repairer = getRepairersForEntity(entity).stream()
                .filter(r -> r.getClass().getSimpleName().equals(repairerName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Repairer '%s' not found".formatted(repairerName)));
        return repairer.repair(entity, context);
    }

    @SuppressWarnings({"java:S3776", "java:S6541"}) // Ignore cognitive complexity/"brain"-method warning, refactoring would make the code less readable
    public ScanResult scan(@NotNull ScanParams params) {
        StopWatch stopWatch = StopWatch.createStarted();
        Report report = new Report();
        Cache cache =  new Cache();
        ScanResult result = new ScanResult();

        boolean skipScanTimeLimitReached = false;
        long processedItemsCount = 0;
        int queryOffset = 0;
        // An explicit selection is what the user asked to scan, so it must not be cut by the "show top
        // rows" limit: the batch is at least as big as the selection. Which is why the selection is
        // bounded first - it sizes both the query and the batch, and the UI can only ever pick from a
        // list of at most MAX_ENTITIES.
        int selectionSize = params.getEntities() == null ? 0 : params.getEntities().size();
        if (selectionSize > ScanParams.MAX_ENTITIES) {
            throw new IllegalArgumentException("Too many entities to scan: %d, at most %d are supported".formatted(selectionSize, ScanParams.MAX_ENTITIES));
        }
        String entitiesQuery = buildEntitiesQuery(params.getEntityType(), params.getEntities());
        if (selectionSize > 0 && entitiesQuery == null) {
            // Every reference was unusable (null, or without an id). Treating that as "no selection" would
            // silently widen the scan to the whole project, which is the opposite of what the caller asked.
            throw new IllegalArgumentException("Entity selection contains no usable entity reference");
        }
        int batchSize = Math.max(params.isHideValid() ? Math.max(params.getLimit(), DEFAULT_LIMIT) : params.getLimit(), selectionSize);
        String customQuery = combineQueries(params.getUserQuery(), entitiesQuery);

        do {
            report.info("Query started (offset=%d)...".formatted(queryOffset));
            List<? extends ModelObject> entities = queryEntities(params.getProjectId(), params.getEntityType().proto(), params.getEntitySubtype(),
                    customQuery, params.getRevision(), params.getSort(), queryOffset, batchSize);
            report.info("Query finished. %d items retrieved.".formatted(entities.size()));

            if (entities.isEmpty()) {
                break;
            }
            queryOffset += entities.size();

            for (ModelObject object : entities) {
                IUniqueObject entity = (IUniqueObject) object.getOldApi();
                ScanEntity scanEntity = ScanEntity.from(entity);

                String scanError = null;
                if (!skipScanTimeLimitReached) {
                    try {
                        long remainingTimeout = Math.max(params.getTimeout() - stopWatch.getTime(), 1); // prevent putting 0 - this will mean no timeout
                        ScanContext context = new ScanContext(this, params.getRepairers(), params.getConfigs(), report, cache);
                        scanEntity(scanEntity, context.timeout(remainingTimeout));
                        scanEntity.getFields().putAll(context.entityRenderer().renderEntity(object));
                        processedItemsCount++;
                    } catch (Exception e) {
                        scanError = "Error during item scan: %s".formatted(e.getMessage());
                        report.warn(scanError);
                        scanEntity.getWarnings().add(scanError);
                        logger.error(scanError, e);
                    }
                } else {
                    scanEntity.getWarnings().add("Time limit reached, the entity scan is skipped.");
                }

                boolean noIssues = scanEntity.getIssues().isEmpty()
                        && scanEntity.getSubitems().stream().allMatch(sub -> sub.getIssues().isEmpty());
                if (params.isHideValid() && noIssues && scanError == null) {
                    report.info("Item '%s' will be hidden".formatted(entity.getId()));
                } else {
                    result.getItems().add(scanEntity);
                    report.info("Item '%s' added to list".formatted(entity.getId()));
                }

                if (params.isHideValid() && result.getItems().size() >= params.getLimit()) {
                    report.warn("Top items limit reached, stopping processing further items.");
                    break;
                }

                if (!skipScanTimeLimitReached && stopWatch.getTime() > params.getTimeout()) {
                    report.info("Time limit of %d ms reached, further items scan will be skipped.".formatted(params.getTimeout()));
                    skipScanTimeLimitReached = true;
                }
            }
        } while (params.isHideValid() && result.getItems().size() < params.getLimit() && !skipScanTimeLimitReached);

        report.info("Scan process finished. %d items processed, %d items shown.".formatted(processedItemsCount, result.getItems().size()));
        report.info("Total execution time: %s".formatted(stopWatch.formatTime()));
        appendRepairerBreakdown(report, result);
        if (skipScanTimeLimitReached) {
            report.warn(SCAN_TIME_LIMIT_REACHED_WARNING);
        }
        result.setReport(report.toString());
        return result;
    }

    @VisibleForTesting
    void scanEntity(@NotNull ScanEntity entity, @NotNull ScanContext context) {
        if (entity.getEntity() instanceof IWorkflowObject workflowObject && workflowObject.getType() == null) {
            String errorText = "Entity '%s' has no type.".formatted(((UniqueObject) entity.getEntity()).getReferencePath());
            context.report().warn(errorText);
            entity.getWarnings().add(errorText);
            return;
        }

        StringBuilder reportEntryBuilder = new StringBuilder();

        List<IRepairer> selectedRepairers = getRepairersForEntity(entity.getEntity()).stream()
                .filter(r -> context.repairers().contains(r.getClass().getSimpleName())).toList();
        if (selectedRepairers.isEmpty()) {
            throw new IllegalArgumentException("No repairers selected for entity '%s' of type '%s'".formatted(((UniqueObject) entity.getEntity()).getReferencePath(), entity.getEntityType()));
        }

        if (entity.getEntityType().equals(EntityType.COLLECTION)) {
            for (IBaselineCollectionElement element : ((IBaselineCollection) entity.getEntity()).getElements()) {
                if (context.timeoutReached()) {
                    break;
                }
                if (element.getObjectWithRevision() instanceof IModule module) {
                    @SuppressWarnings("java:S1905") // Cast to ReadOnlyTransaction is required to access .documents()
                    Document document = Objects.requireNonNull((ReadOnlyTransaction) TransactionalExecutorImpl.currentTransaction())
                            .documents().getBy().revision(module.getRevision()).projectSpaceAndName(module.getProjectId(), module.getModuleFolder(), module.getModuleName());

                    ScanEntity docScanEntity = ScanEntity.from(module);
                    entity.getSubitems().add(docScanEntity);
                    scanEntity(docScanEntity, context);
                    docScanEntity.getFields().putAll(context.entityRenderer().renderEntity(document));
                }
            }
        } else {
            StopWatch stopWatch = StopWatch.createStarted();
            for (IRepairer selectedRepairer : selectedRepairers) {
                List<Issue> issues = selectedRepairer.scan(entity.getEntity(), context);
                reportEntryBuilder.append(selectedRepairer.getClass().getSimpleName()).append(":").append(stopWatch.formatTime()).append(" ");
                entity.getIssues().addAll(issues);
            }

            String itemPath = ((UniqueObject) entity.getEntity()).getReferencePath();
            context.report().info("Item '%s': %d issues found; %s".formatted(itemPath, entity.getIssues().size(), reportEntryBuilder.toString()));
        }
    }

    @SuppressWarnings("java:S1166") // We do not log or rethrow exception by design.
    public boolean isWorkItemExists(@NotNull String projectId, @NotNull String workItemId, @Nullable String revision) {
        try {
            getWorkItem(projectId, workItemId, revision);
            return true;
        } catch (ObjectNotFoundException e) {
            return false;
        }
    }

    public boolean userAuthorizedForRepair(@NotNull String projectId) {
        AuthorizationModel authorizationModel = (AuthorizationModel)
                NamedSettingsRegistry.INSTANCE.getByFeatureName(AuthorizationSettings.FEATURE_NAME).read(
                        ScopeUtils.getScopeFromProject(projectId), SettingId.fromName(NamedSettings.DEFAULT_NAME), null);
        List<String> allowedRoles = authorizationModel.getAllRoles();

        String currentUser = securityService.getCurrentUser();
        Collection<String> globalRoles = securityService.getRolesForUser(currentUser);
        if (globalRoles.stream().anyMatch(allowedRoles::contains)) {
            return true;
        } else {
            IContextId projectContext = getTrackerProject(projectId).getContextId();
            Collection<String> projectRoles = securityService.getRolesForUser(currentUser, projectContext);
            return projectRoles.stream().anyMatch(allowedRoles::contains);
        }
    }

    public List<RepairerMeta> getRepairerMetas(EntityType entityType) {
        return REPAIRERS.get(entityType).stream()
                .map(r -> new RepairerMeta(r.getClass().getSimpleName(), r.getDisplayName(), r.getDescription(), r.getConfigs())).toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked", "java:S1452", "java:S107"}) // Wildcard comes from Polarion API return type, 8 params is a lot but all of them are required
    public List<? extends ModelObject> queryEntities(@NotNull String projectId, @NotNull PrototypeEnum entityPrototype,
                                                     @Nullable String subtype, @Nullable String customQuery,
                                                     @Nullable String revision, @Nullable String sort,
                                                     @Nullable Integer offset, @Nullable Integer limit) {
        ReadOnlyTransaction transaction = TransactionalExecutorImpl.currentTransaction();
        if (transaction == null) {
            throw new IllegalStateException("This method must be called within a transaction");
        }
        ModelObjectsSearch search = transaction.byEnum(entityPrototype).search();
        String query = "";
        if (subtype != null) {
            query = "type:" + subtype;
        }
        if (customQuery != null && !customQuery.isEmpty()) {
            query = query.isEmpty() ? customQuery : query + " AND (" + customQuery + ")";
        }
        String scopedQuery = ((InternalPolarionUtils) transaction.utils())
                .addScopeToLuceneQuery(new ScopeFactoryImpl().fromPath(projectId), query);
        String sortNormalized = normalizeSort(sort);

        IterableWithSize<? extends ModelObject> results = search
                .query(scopedQuery)
                .baseline(revision)
                .sort(StringUtils.isEmpty(sortNormalized) ? "created" : sortNormalized)
                .limit(limit == null ? DEFAULT_LIMIT : limit)
                .offset(offset == null ? 0 : offset);

        return results.toArrayList();
    }

    /**
     * Lists the entities of a project the user can select for scanning. Not supported for work items:
     * a project holds far too many of them for a dropdown, they are selected by query instead.
     */
    public List<EntityInfo> getEntities(@NotNull String projectId, @NotNull EntityType entityType, @Nullable String entitySubtype) {
        if (entityType == EntityType.WORKITEM) {
            throw new IllegalArgumentException("Entity list is not supported for work items, use a query instead");
        }
        List<? extends ModelObject> entities = queryEntities(projectId, entityType.proto(), StringUtils.getNullIfEmpty(entitySubtype),
                null, null, null, 0, ENTITY_LIST_LIMIT);
        if (entities.size() >= ENTITY_LIST_LIMIT) {
            logger.warn("Entity list of project '%s' hit the limit of %d items, it may be incomplete.".formatted(projectId, ENTITY_LIST_LIMIT));
        }
        return entities.stream()
                .map(object -> toEntityInfo((IUniqueObject) object.getOldApi()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((EntityInfo info) -> info.space() == null ? "" : info.space(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(EntityInfo::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @VisibleForTesting
    @Nullable
    EntityInfo toEntityInfo(@NotNull IUniqueObject entity) {
        if (entity instanceof IModule module) {
            return new EntityInfo(module.getModuleFolder(), module.getModuleName(),
                    Objects.requireNonNullElse(module.getTitleOrName(), module.getModuleName()),
                    module.getType() == null ? null : module.getType().getId());
        }
        if (entity instanceof IBaselineCollection collection) {
            return new EntityInfo(null, entity.getId(), Objects.requireNonNullElse(collection.getName(), entity.getId()), null);
        }
        return null;
    }

    /**
     * Builds the query fragment selecting exactly the given entities. Documents are addressed the way
     * Polarion itself addresses them in Lucene (space.id + moduleName), everything else by id.
     */
    @VisibleForTesting
    @Nullable
    String buildEntitiesQuery(@NotNull EntityType entityType, @Nullable List<EntityRef> entities) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        Stream<String> fragments = entities.stream()
                .filter(ref -> ref != null && !StringUtils.isEmpty(ref.getId()))
                .map(ref -> entityType == EntityType.DOCUMENT ? documentFragment(ref) : "id:" + escapeLuceneValue(ref.getId()));
        String query = fragments.collect(Collectors.joining(" OR "));
        return StringUtils.getNullIfEmpty(query);
    }

    private @NotNull String documentFragment(@NotNull EntityRef ref) {
        String moduleName = "moduleName:" + escapeLuceneValue(ref.getId());
        return StringUtils.isEmpty(ref.getSpace())
                ? "(%s)".formatted(moduleName)
                : "(space.id:%s AND %s)".formatted(escapeLuceneValue(ref.getSpace()), moduleName);
    }

    @VisibleForTesting
    @Nullable
    String combineQueries(@Nullable String userQuery, @Nullable String entitiesQuery) {
        if (StringUtils.isEmpty(userQuery)) {
            return StringUtils.getNullIfEmpty(entitiesQuery);
        }
        if (StringUtils.isEmpty(entitiesQuery)) {
            return userQuery;
        }
        return "(%s) AND (%s)".formatted(userQuery, entitiesQuery);
    }

    /**
     * Escapes a value for a Lucene query the same way Polarion's own LuceneQueryPart.escape does.
     * Reimplemented here to keep this extension off Polarion's internal GWT-facing query classes.
     */
    @VisibleForTesting
    @NotNull
    String escapeLuceneValue(@NotNull String value) {
        String escaped = value;
        for (char character : "\\+-!(){}[]^\"~:".toCharArray()) {
            escaped = escaped.replace(String.valueOf(character), "\\" + character);
        }
        escaped = escaped.replace("&&", "\\&&").replace("||", "\\||");
        return escaped.contains(" ") ? "\"" + escaped + "\"" : escaped;
    }

    /**
     * Polarion has a bug: a leading space like " created" or two or more consecutive spaces between fields (e.g. "created  updated")
     * lead to StringIndexOutOfBoundsException. This method normalizes the sort parameter to prevent such exceptions.
     */
    @VisibleForTesting
    String normalizeSort(@Nullable String sort) {
        return sort == null ? "" : sort.trim().replaceAll("\\s+", " ");
    }

    @VisibleForTesting
    void checkAccess(IUniqueObject entity) {
        if (!userAuthorizedForRepair(Objects.requireNonNull(entity.getProjectId()))) {
            throw new UnauthorizedException(MSG_NOT_AUTHORIZED_BY_ADMIN);
        } else if (!entity.can().modify()) {
            throw new UnauthorizedException(MSG_NO_PERMISSIONS);
        }
    }

    @VisibleForTesting
    List<IRepairer> getRepairersForEntity(IUniqueObject entity) {
        return REPAIRERS.get(EntityType.fromPrototype(entity.getPrototype()));
    }

    @VisibleForTesting
    void appendRepairerBreakdown(@NotNull Report report, @NotNull ScanResult result) {
        Map<String, Integer> countByRepairer = new HashMap<>();
        for (ScanEntity item : result.getItems()) {
            countIssuesRecursive(item, countByRepairer);
        }
        if (countByRepairer.isEmpty()) {
            return;
        }
        Map<String, String> repairerNames = REPAIRERS.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(IRepairer::getRepairerId, IRepairer::getDisplayName, (a, b) -> a));
        StringBuilder sb = new StringBuilder("Issues by repairer:");
        countByRepairer.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> sb.append("\n  ")
                        .append(repairerNames.getOrDefault(e.getKey(), e.getKey()))
                        .append(": ")
                        .append(e.getValue()));
        report.info(sb.toString());
    }

    private void countIssuesRecursive(@NotNull ScanEntity entity, @NotNull Map<String, Integer> acc) {
        for (Issue issue : entity.getIssues()) {
            acc.merge(issue.getRepairer(), 1, Integer::sum);
        }
        for (ScanEntity sub : entity.getSubitems()) {
            countIssuesRecursive(sub, acc);
        }
    }

    public Set<FieldMetadata> getAllFields(String proto, IContextId contextId, String typeId, boolean compareTypeClass, IType... fieldTypes) {
        Set<FieldMetadata> generalFields = getGeneralFields(proto, contextId, typeId);
        Set<FieldMetadata> customFields = getCustomFields(proto, contextId, typeId);
        return Stream.of(generalFields, customFields).flatMap(Collection::stream)
                .filter(f -> {
                    Predicate<IType> typePredicate = t -> compareTypeClass ? t.getClass().equals(f.getType().getClass()) : t.equals(f.getType());
                    return fieldTypes.length == 0 || Stream.of(fieldTypes).anyMatch(typePredicate);
                })
                .collect(Collectors.toSet());
    }

    public List<BaselineInfo> getBaselines(String projectId) {
        IInternalBaselinesManager baselinesManager = (IInternalBaselinesManager) getTrackerService().getTrackerProject(projectId).getBaselinesManager();
        IPObjectList<IBaseline> projectBaselines = baselinesManager.getBaselines();
        return projectBaselines.stream()
                .map(b -> new BaselineInfo(b.getBaseRevision(), b.getName()))
                .sorted().toList();
    }

    public List<TypeInfo> getWorkItemTypes(@NotNull String projectId) {
        return getEnumerationTypes(projectId, WORK_ITEM_TYPE_ENUM_ID);
    }

    public List<TypeInfo> getDocumentTypes(@NotNull String projectId) {
        return getEnumerationTypes(projectId, DOCUMENT_TYPE_ENUM_ID);
    }

    private List<TypeInfo> getEnumerationTypes(@NotNull String projectId, @NotNull String enumId) {
        ReadOnlyTransaction transaction = TransactionalExecutorImpl.currentTransaction();
        if (transaction == null) {
            throw new IllegalStateException("This method must be called within a transaction");
        }
        Enumeration enumeration = transaction.enumerations().getEnumeration(enumId).forProject(projectId);
        List<TypeInfo> types = new ArrayList<>();
        for (EnumOption option : enumeration.options()) {
            types.add(new TypeInfo(option.id(), option.fields().name().get(), option.fields().iconURL().url()));
        }
        return types;
    }

}
