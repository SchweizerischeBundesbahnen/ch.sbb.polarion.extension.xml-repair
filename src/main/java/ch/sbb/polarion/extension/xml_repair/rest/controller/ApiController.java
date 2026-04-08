package ch.sbb.polarion.extension.xml_repair.rest.controller;

import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import ch.sbb.polarion.extension.xml_repair.service.model.EntityType;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairParams;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanParams;

import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Singleton
@Secured
@Path("/api")
public class ApiController extends InternalController {

    @Override
    public Response listRepairers(EntityType entityType) {
        return polarionService.callPrivileged(() -> super.listRepairers(entityType));
    }

    @Override
    public Response scan(ScanParams scanParams) {
        return polarionService.callPrivileged(() -> super.scan(scanParams));
    }

    @Override
    public Response repair(RepairParams repairParams) {
        return polarionService.callPrivileged(() -> super.repair(repairParams));
    }

}
