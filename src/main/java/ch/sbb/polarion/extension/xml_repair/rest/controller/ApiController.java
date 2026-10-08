package ch.sbb.polarion.extension.xml_repair.rest.controller;

import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import ch.sbb.polarion.extension.generic.util.RequestContextUtil;
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
    public Response listLinkRoles(String projectId) {
        return polarionService.callPrivileged(() -> super.listLinkRoles(projectId));
    }

    @Override
    public Response listEntities(String projectId, EntityType entityType, String entitySubtype) {
        return polarionService.callPrivileged(() -> super.listEntities(projectId, entityType, entitySubtype));
    }

    @Override
    public Response scan(ScanParams scanParams) {
        return polarionService.callPrivileged(() -> super.scan(scanParams));
    }

    @Override
    public Response startRepairJob(RepairParams repairParams) {
        // The job runs after this response and ends the session itself; a start which fails gives it back.
        RequestContextUtil.keepSessionAlive();
        try {
            return polarionService.callPrivileged(() -> super.startRepairJob(repairParams));
        } catch (RuntimeException e) {
            RequestContextUtil.releaseSession();
            throw e;
        }
    }

    @Override
    public Response getRepairJobStatus(String jobId) {
        return polarionService.callPrivileged(() -> super.getRepairJobStatus(jobId));
    }

    @Override
    public Response getRepairJobResult(String jobId) {
        return polarionService.callPrivileged(() -> super.getRepairJobResult(jobId));
    }

    @Override
    public Response startScanJob(ScanParams scanParams) {
        // The job runs after this response and ends the session itself; a start which fails gives it back.
        RequestContextUtil.keepSessionAlive();
        try {
            return polarionService.callPrivileged(() -> super.startScanJob(scanParams));
        } catch (RuntimeException e) {
            RequestContextUtil.releaseSession();
            throw e;
        }
    }

    @Override
    public Response getScanJobStatus(String jobId) {
        return polarionService.callPrivileged(() -> super.getScanJobStatus(jobId));
    }

    @Override
    public Response getScanJobResult(String jobId) {
        return polarionService.callPrivileged(() -> super.getScanJobResult(jobId));
    }

    @Override
    public Response stopScanJob(String jobId) {
        return polarionService.callPrivileged(() -> super.stopScanJob(jobId));
    }

    @Override
    public Response repair(RepairParams repairParams) {
        return polarionService.callPrivileged(() -> super.repair(repairParams));
    }

}
