package ch.sbb.polarion.extension.xml_repair.rest.controller;

import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import ch.sbb.polarion.extension.xml_repair.service.model.EntityType;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairParams;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams;

import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Singleton
@Secured
@Path("/api")
public class ApiController extends InternalController {

    @Override
    public Response listRepairers(EntityType entityType) {
        return polarionService.callPrivileged(() -> super.listRepairers(entityType));
    }

    @Override
    public Response listBaselines(String projectId) {
        return polarionService.callPrivileged(() -> super.listBaselines(projectId));
    }

    @Override
    public Response listWorkItemTypes(String projectId) {
        return polarionService.callPrivileged(() -> super.listWorkItemTypes(projectId));
    }

    @Override
    public Response listDocumentTypes(String projectId) {
        return polarionService.callPrivileged(() -> super.listDocumentTypes(projectId));
    }

    @Override
    public Response scan(ScanParams scanParams) {
        return polarionService.callPrivileged(() -> super.scan(scanParams));
    }

    @Override
    public Response repair(RepairParams repairParams) {
        return polarionService.callPrivileged(() -> super.repair(repairParams));
    }

    @Override
    public Response listRoles(String scope) {
        return polarionService.callPrivileged(() -> super.listRoles(scope));
    }

}
