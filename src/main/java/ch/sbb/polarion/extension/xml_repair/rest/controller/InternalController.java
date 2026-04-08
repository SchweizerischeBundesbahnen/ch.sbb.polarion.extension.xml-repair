package ch.sbb.polarion.extension.xml_repair.rest.controller;

import ch.sbb.polarion.extension.xml_repair.service.XmlRepairPolarionService;
import ch.sbb.polarion.extension.xml_repair.service.model.*;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairParams;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairerMeta;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
@Hidden
@Path("/internal")
@Tag(name = "")
public class InternalController {

    protected final XmlRepairPolarionService polarionService = new XmlRepairPolarionService();

    @GET
    @Path("/repairers")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get list of available repairers for the specified entity type",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "Successfully retrieved the list of available repairers",
                            content = @Content(schema = @Schema(implementation = RepairerMeta.class))
                    )
            })
    public Response listRepairers(@Parameter(description = "Entity type", required = true) @QueryParam("entityType") EntityType entityType) {
        return Response.ok().entity(polarionService.getRepairerMetas(entityType)).build();
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
                transaction -> polarionService.scan(scanParams))).build();
    }

}
