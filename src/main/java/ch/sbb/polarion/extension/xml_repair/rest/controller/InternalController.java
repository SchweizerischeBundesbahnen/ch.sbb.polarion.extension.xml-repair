package ch.sbb.polarion.extension.xml_repair.rest.controller;

import ch.sbb.polarion.extension.generic.service.PolarionBaselineExecutor;
import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.BaselineInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.EntityType;
import ch.sbb.polarion.extension.xml_repair.service.model.TypeInfo;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairParams;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairerMeta;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.core.util.StringUtils;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
@Hidden
@Path("/internal")
@Tag(name = "XML Repair")
public class InternalController {

    protected final XmlRepairPolarionService polarionService = new XmlRepairPolarionService();

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

    @POST
    @Path("/repair")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Repair XML issues in the specified entity",
            requestBody = @RequestBody(description = "Repair parameters",
                    required = true,
                    content = @Content(schema = @Schema(implementation = RepairParams.class),
                            mediaType = MediaType.APPLICATION_JSON
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Processing completed successfully"
                    )
            })
    public Response repair(RepairParams repairParams) {
        return Response.ok().entity(TransactionalExecutor.executeInWriteTransaction(
                transaction -> polarionService.repair(repairParams))).build();
    }

    @POST
    @Path("/scan")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Scan a list of entities for XML issues based on the provided parameters",
            requestBody = @RequestBody(description = "Scan parameters",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ScanParams.class),
                            mediaType = MediaType.APPLICATION_JSON
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Scan completed successfully"
                    )
            })
    public Response scan(ScanParams scanParams) {
        return Response.ok().entity(TransactionalExecutor.executeInReadOnlyTransaction(
                transaction -> PolarionBaselineExecutor.executeInBaseline(
                        StringUtils.getNullIfEmpty(scanParams.getRevision()), transaction, () -> polarionService.scan(scanParams)))).build();
    }

}
