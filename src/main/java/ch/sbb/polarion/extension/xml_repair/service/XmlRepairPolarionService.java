package ch.sbb.polarion.extension.xml_repair.service;

import ch.sbb.polarion.extension.generic.exception.ObjectNotFoundException;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.rest.exception.UnauthorizedException;
import ch.sbb.polarion.extension.generic.service.PolarionService;
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
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanEntity;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanResult;
import ch.sbb.polarion.extension.xml_repair.settings.AuthorizationModel;
import ch.sbb.polarion.extension.xml_repair.settings.AuthorizationSettings;
import ch.sbb.polarion.extension.xml_repair.util.Report;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.server.api.transaction.TransactionalExecutorImpl;
import com.polarion.alm.shared.api.impl.ScopeFactoryImpl;
import com.polarion.alm.shared.api.model.ModelObject;
import com.polarion.alm.shared.api.model.ModelObjectsSearch;
import com.polarion.alm.shared.api.model.PrototypeEnum;
import com.polarion.alm.shared.api.model.document.Document;
import com.polarion.alm.shared.api.transaction.ReadOnlyTransaction;
import com.polarion.alm.shared.api.utils.collections.IterableWithSize;
import com.polarion.alm.shared.api.utils.internal.InternalPolarionUtils;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.internal.model.UniqueObject;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollectionElement;
import com.polarion.core.util.StringUtils;
import com.polarion.core.util.logging.Logger;
import com.polarion.platform.IPlatformService;
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

import static ch.sbb.polarion.extension.xml_repair.util.RolesUtils.MSG_NOT_AUTHORIZED_BY_ADMIN;
import static ch.sbb.polarion.extension.xml_repair.util.RolesUtils.MSG_NO_PERMISSIONS;

@SuppressWarnings("java:S1200") // This service class necessarily couples many domain types
public class XmlRepairPolarionService extends PolarionService {

    public static final String SCAN_TIME_LIMIT_REACHED_WARNING = "Scan time limit was reached during processing, some items may remain unchecked. Please consider increasing the time limit or narrow the query.";

    public static final Map<EntityType, List<IRepairer>> REPAIRERS = Map.of(
            EntityType.COLLECTION, List.of(
                    new ModuleContentLinksRepairer(),
                    new ModuleDuplicateLayoutDeclarationRepairer(),
                    new ModuleMissingTitleHeadingRepairer(),
                    new ModuleTablesAndFiguresCaptionRepairer(),
                    new ModuleWrongLayoutAssignmentsRepairer(),
                    new ModuleWrongTitleHeadingPositionRepairer(),
                    new FieldsFormattingSymbolsRepairer(),
                    new FieldsInvalidEnumerationValueRepairer(),
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
                    new FieldsFormattingSymbolsRepairer(),
                    new FieldsInvalidEnumerationValueRepairer(),
                    new FieldsRichTextLinksRepairer(),
                    new FieldsWrongTypeRepairer()
            ),
            EntityType.WORKITEM, List.of(
                    new FieldsFormattingSymbolsRepairer(),
                    new FieldsInvalidEnumerationValueRepairer(),
                    new FieldsRichTextLinksRepairer(),
                    new FieldsWrongTypeRepairer()
            )
    );
    private static final int DEFAULT_LIMIT = 100;

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
        for (String issueMetaInfo : params.getIssueMetaInfos()) {
            IssueMetaInfo metaInfo = IssueMetaInfo.fromString(issueMetaInfo);
            try {
                String projectId = metaInfo.getString(IssueMetaInfo.PROJECT_ID);
                String modulePath = metaInfo.getString(IssueMetaInfo.MODULE_PATH);
                String id = metaInfo.getString(IssueMetaInfo.ID);
                IUniqueObject entity = modulePath != null ?
                        getModule(getProject(projectId), Location.getLocation(modulePath)) : getWorkItem(projectId, id, null);
                results.add(repairEntity(entity, new RepairContext(metaInfo, this, params.getConfigs())));
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

    @SuppressWarnings("java:S3776") // Ignore cognitive complexity warning, refactoring would make the code less readable
    public ScanResult scan(@NotNull ScanParams params) {
        StopWatch stopWatch = StopWatch.createStarted();
        Report report = new Report();
        ScanResult result = new ScanResult();

        boolean skipScanTimeLimitReached = false;
        long processedItemsCount = 0;
        int queryOffset = 0;
        int batchSize = params.isHideValid() ? Math.max(params.getLimit(), DEFAULT_LIMIT) : params.getLimit();

        do {
            report.info("Query started (offset=%d)...".formatted(queryOffset));
            List<? extends ModelObject> entities = queryEntities(params.getProjectId(), params.getEntityType().proto(), params.getEntitySubtype(),
                    params.getUserQuery(), params.getSort(), queryOffset, batchSize);
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
                        ScanContext context = new ScanContext(this, params.getRepairers(), params.getConfigs(), report);
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

                if (params.isHideValid() && scanEntity.getIssues().isEmpty() && scanError == null) {
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
                            .documents().getBy().projectSpaceAndName(module.getProjectId(), module.getModuleFolder(), module.getModuleName());

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
        AuthorizationModel projectCustomFieldsSettingsModel = (AuthorizationModel)
                NamedSettingsRegistry.INSTANCE.getByFeatureName(AuthorizationSettings.FEATURE_NAME).read(
                        ScopeUtils.getScopeFromProject(projectId), SettingId.fromName(NamedSettings.DEFAULT_NAME), null);
        List<String> allowedRoles = projectCustomFieldsSettingsModel.getAllRoles();

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

    @SuppressWarnings({"rawtypes", "unchecked", "java:S1452"}) // Wildcard comes from Polarion API return type
    public List<? extends ModelObject> queryEntities(@NotNull String projectId, @NotNull PrototypeEnum entityPrototype,
                                                     @Nullable String subtype, @Nullable String customQuery,
                                                     @Nullable String sort, @Nullable Integer offset, @Nullable Integer limit) {
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

        IterableWithSize<? extends ModelObject> results = search
                .query(scopedQuery)
                .sort(StringUtils.isEmpty(sort) ? "created" : sort)
                .limit(limit == null ? DEFAULT_LIMIT : limit)
                .offset(offset == null ? 0 : offset);

        return results.toArrayList();
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

}
