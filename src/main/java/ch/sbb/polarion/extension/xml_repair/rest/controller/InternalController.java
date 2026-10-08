package ch.sbb.polarion.extension.xml_repair.rest.controller;

import ch.sbb.polarion.extension.generic.jobs.AsyncJobsService;
import ch.sbb.polarion.extension.generic.jobs.JobState;
import ch.sbb.polarion.extension.generic.rest.JobResponses;
import ch.sbb.polarion.extension.generic.rest.model.jobs.JobDetails;
import ch.sbb.polarion.extension.generic.rest.model.jobs.JobStatus;
import ch.sbb.polarion.extension.xml_repair.service.RepairJobsService;
import ch.sbb.polarion.extension.xml_repair.service.ScanJobsService;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.BaselineInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.EntityInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.EntityType;
import ch.sbb.polarion.extension.xml_repair.service.model.TypeInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairParams;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairerMeta;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanResult;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.security.ISecurityService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;


@Singleton
@Hidden
@Path("/internal")
@Tag(name = "XML Repair")
public class InternalController {

    private static final long JOB_POLL_INTERVAL_MS = 200;

    protected final XmlRepairPolarionService polarionService;
    private final ScanJobsService scanJobsService;
    private final RepairJobsService repairJobsService;

    @Context
    private UriInfo uriInfo;

    public InternalController() {
        this.polarionService = new XmlRepairPolarionService();
        ISecurityService securityService = PlatformContext.getPlatform().lookupService(ISecurityService.class);
        this.scanJobsService = new ScanJobsService(polarionService, securityService);
        this.repairJobsService = new RepairJobsService(polarionService, securityService);
    }

    @VisibleForTesting
    InternalController(@NotNull XmlRepairPolarionService polarionService, @NotNull ScanJobsService scanJobsService,
                       @NotNull RepairJobsService repairJobsService, @NotNull UriInfo uriInfo) {
        this.polarionService = polarionService;
        this.scanJobsService = scanJobsService;
        this.repairJobsService = repairJobsService;
        this.uriInfo = uriInfo;
    }

    @GET
    @Path("/repairers")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get list of available repairers for the specified entity type",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved the list of available repairers",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RepairerMeta.class)))
                    )
            })
    public Response listRepairers(@Parameter(description = "Entity type", required = true) @QueryParam("entityType") EntityType entityType) {
        return Response.ok().entity(polarionService.getRepairerMetas(entityType)).build();
    }

    @GET
    @Path("/baselines")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get list of baselines for the specified project",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved the list of baselines",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = BaselineInfo.class)))
                    )
            })
    public Response listBaselines(@Parameter(description = "Project ID", required = true) @QueryParam("projectId") String projectId) {
        return Response.ok().entity(TransactionalExecutor.executeInReadOnlyTransaction(
                transaction -> polarionService.getBaselines(projectId))).build();
    }

    @GET
    @Path("/work-item-types")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get list of work item types for the specified project",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved the list of work item types",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TypeInfo.class)))
                    )
            })
    public Response listWorkItemTypes(@Parameter(description = "Project ID", required = true) @QueryParam("projectId") String projectId) {
        return Response.ok().entity(TransactionalExecutor.executeInReadOnlyTransaction(
                transaction -> polarionService.getWorkItemTypes(projectId))).build();
    }

    @GET
    @Path("/document-types")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get list of document types for the specified project",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved the list of document types",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TypeInfo.class)))
                    )
            })
    public Response listDocumentTypes(@Parameter(description = "Project ID", required = true) @QueryParam("projectId") String projectId) {
        return Response.ok().entity(TransactionalExecutor.executeInReadOnlyTransaction(
                transaction -> polarionService.getDocumentTypes(projectId))).build();
    }

    @GET
    @Path("/link-roles")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get list of work item link roles for the specified project",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved the list of link roles",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TypeInfo.class)))
                    )
            })
    public Response listLinkRoles(@Parameter(description = "Project ID", required = true) @QueryParam("projectId") String projectId) {
        return Response.ok().entity(TransactionalExecutor.executeInReadOnlyTransaction(
                transaction -> polarionService.getLinkRoles(projectId))).build();
    }

    @GET
    @Path("/entities")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get list of entities of the specified type which can be selected for scanning. Not supported for work items",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved the list of entities",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = EntityInfo.class)))
                    ),
                    @ApiResponse(responseCode = "400",
                            description = "The entity type does not support selection"
                    )
            })
    public Response listEntities(@Parameter(description = "Project ID", required = true) @QueryParam("projectId") String projectId,
                                 @Parameter(description = "Entity type", required = true) @QueryParam("entityType") EntityType entityType,
                                 @Parameter(description = "Entity subtype") @QueryParam("entitySubtype") String entitySubtype) {
        if (entityType == null || entityType == EntityType.WORKITEM) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Supported entity types: %s, %s".formatted(EntityType.DOCUMENT, EntityType.COLLECTION))
                    .build();
        }
        return Response.ok().entity(TransactionalExecutor.executeInReadOnlyTransaction(
                transaction -> polarionService.getEntities(projectId, entityType, entitySubtype))).build();
    }

    @POST
    @Path("/repair")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Repair XML issues in the specified entity and wait for the result. "
            + "A long repair can outlast a gateway timeout, the repair jobs endpoints avoid that",
            requestBody = @RequestBody(description = "Repair parameters",
                    required = true,
                    content = @Content(schema = @Schema(implementation = RepairParams.class),
                            mediaType = MediaType.APPLICATION_JSON
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Processing completed successfully",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RepairResult.class)))
                    )
            })
    public Response repair(RepairParams repairParams) {
        String jobId = repairJobsService.startJob(repairParams);
        // Runs as the job the UI starts, so both ways to repair share one implementation.
        try {
            awaitJob(repairJobsService, jobId);
        } catch (InterruptedException e) {
            // a repair has no stop: it still runs to its commit
            Thread.currentThread().interrupt();
            throw new InternalServerErrorException("Waiting for the repair was interrupted, the repair itself goes on", e);
        }
        return Response.ok().entity(repairJobsService.getJobResult(jobId).orElseThrow()).build();
    }

    @POST
    @Path("/repair/jobs")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Starts an asynchronous repair of XML issues. All issues are repaired in one transaction",
            requestBody = @RequestBody(description = "Repair parameters",
                    required = true,
                    content = @Content(schema = @Schema(implementation = RepairParams.class),
                            mediaType = MediaType.APPLICATION_JSON
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "202",
                            description = "Repair job is started, job URI is returned in Location header"
                    )
            })
    public Response startRepairJob(RepairParams repairParams) {
        String jobId = repairJobsService.startJob(repairParams);
        URI jobUri = UriBuilder.fromUri(uriInfo.getRequestUri().getPath()).path(jobId).build();
        return Response.accepted().location(jobUri).build();
    }

    @GET
    @Path("/repair/jobs/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Returns repair job status",
            responses = {
                    // OpenAPI response MediaTypes for 303 and 202 response codes are generic to satisfy automatic redirect in SwaggerUI
                    @ApiResponse(responseCode = "303",
                            description = "Repair job is finished, Location header contains result URL",
                            content = {@Content(mediaType = "application/*", schema = @Schema(implementation = JobDetails.class))}
                    ),
                    @ApiResponse(responseCode = "202",
                            description = "Repair job is still in progress",
                            content = {@Content(mediaType = "application/*", schema = @Schema(implementation = JobDetails.class))}
                    ),
                    @ApiResponse(responseCode = "409",
                            description = "Repair job failed, nothing was saved"
                    )
            })
    public Response getRepairJobStatus(@PathParam("id") String jobId) {
        return JobResponses.jobStatus(JobDetails.from(repairJobsService.getJobState(jobId)), uriInfo);
    }

    @GET
    @Path("/repair/jobs/{id}/result")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Returns repair job result",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Repair results, one per issue",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RepairResult.class)))
                    ),
                    @ApiResponse(responseCode = "204",
                            description = "Repair job is still in progress"
                    ),
                    @ApiResponse(responseCode = "409",
                            description = "Repair job failed, nothing was saved"
                    )
            })
    public Response getRepairJobResult(@PathParam("id") String jobId) {
        Optional<List<RepairResult>> results = repairJobsService.getJobResult(jobId);
        if (results.isEmpty()) {
            return Response.noContent().build();
        }
        return Response.ok().entity(results.get()).build();
    }

    @POST
    @Path("/scan")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Scan a list of entities for XML issues based on the provided parameters and wait for the result. "
            + "A long scan can outlast a gateway timeout, the scan jobs endpoints avoid that",
            requestBody = @RequestBody(description = "Scan parameters",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ScanParams.class),
                            mediaType = MediaType.APPLICATION_JSON
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Scan completed successfully",
                            content = @Content(schema = @Schema(implementation = ScanResult.class))
                    )
            })
    public Response scan(ScanParams scanParams) {
        polarionService.validateScanParams(scanParams);
        String jobId = scanJobsService.startJob(scanParams);
        // Runs as the job the UI starts, so both ways to scan share one implementation.
        try {
            awaitJob(scanJobsService, jobId);
        } catch (InterruptedException e) {
            scanJobsService.cancelJob(jobId);
            Thread.currentThread().interrupt();
            throw new InternalServerErrorException("Scan was interrupted", e);
        }
        return Response.ok().entity(scanJobsService.getJobResult(jobId).orElseThrow()).build();
    }

    /**
     * Waits until the job is over.
     *
     * @throws InternalServerErrorException with the message of the job, if it failed
     */
    private static void awaitJob(@NotNull AsyncJobsService<?, ?> jobsService, @NotNull String jobId) throws InterruptedException {
        JobState jobState = jobsService.getJobState(jobId);
        while (!jobState.isDone()) {
            TimeUnit.MILLISECONDS.sleep(JOB_POLL_INTERVAL_MS);
            jobState = jobsService.getJobState(jobId);
        }
        if (jobState.status() != JobStatus.SUCCESSFULLY_FINISHED) {
            throw new InternalServerErrorException(jobState.errorMessage());
        }
    }

    @POST
    @Path("/scan/jobs")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Starts an asynchronous scan of a list of entities for XML issues based on the provided parameters",
            requestBody = @RequestBody(description = "Scan parameters",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ScanParams.class),
                            mediaType = MediaType.APPLICATION_JSON
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "202",
                            description = "Scan job is started, job URI is returned in Location header"
                    )
            })
    public Response startScanJob(ScanParams scanParams) {
        polarionService.validateScanParams(scanParams);
        String jobId = scanJobsService.startJob(scanParams);
        URI jobUri = UriBuilder.fromUri(uriInfo.getRequestUri().getPath()).path(jobId).build();
        return Response.accepted().location(jobUri).build();
    }

    @GET
    @Path("/scan/jobs/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Returns scan job status",
            responses = {
                    // OpenAPI response MediaTypes for 303 and 202 response codes are generic to satisfy automatic redirect in SwaggerUI
                    @ApiResponse(responseCode = "303",
                            description = "Scan job is finished, Location header contains result URL",
                            content = {@Content(mediaType = "application/*", schema = @Schema(implementation = JobDetails.class))}
                    ),
                    @ApiResponse(responseCode = "202",
                            description = "Scan job is still in progress",
                            content = {@Content(mediaType = "application/*", schema = @Schema(implementation = JobDetails.class))}
                    ),
                    @ApiResponse(responseCode = "409",
                            description = "Scan job failed or was cancelled before it started"
                    )
            })
    public Response getScanJobStatus(@PathParam("id") String jobId) {
        return JobResponses.jobStatus(JobDetails.from(scanJobsService.getJobState(jobId)), uriInfo);
    }

    @GET
    @Path("/scan/jobs/{id}/result")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Returns scan job result",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Scan result",
                            content = @Content(schema = @Schema(implementation = ScanResult.class))
                    ),
                    @ApiResponse(responseCode = "204",
                            description = "Scan job is still in progress"
                    ),
                    @ApiResponse(responseCode = "409",
                            description = "Scan job failed or was cancelled before it started"
                    )
            })
    public Response getScanJobResult(@PathParam("id") String jobId) {
        Optional<ScanResult> result = scanJobsService.getJobResult(jobId);
        if (result.isEmpty()) {
            return Response.noContent().build();
        }
        return Response.ok().entity(result.get()).build();
    }

    @POST
    @Path("/scan/jobs/{id}/stop")
    @Operation(summary = "Stops a running scan job. The job still finishes successfully, with the items scanned so far",
            responses = {
                    @ApiResponse(responseCode = "204",
                            description = "Stop request accepted"
                    )
            })
    public Response stopScanJob(@PathParam("id") String jobId) {
        scanJobsService.cancelJob(jobId);
        return Response.noContent().build();
    }

}
