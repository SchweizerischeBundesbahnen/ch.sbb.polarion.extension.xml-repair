package ch.sbb.polarion.extension.xml_repair.repairers;

import ch.sbb.polarion.extension.xml_repair.repairers.config.RepairerConfigMeta;
import ch.sbb.polarion.extension.xml_repair.service.model.Issue;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairContext;
import ch.sbb.polarion.extension.xml_repair.service.model.repair.RepairResult;
import ch.sbb.polarion.extension.xml_repair.service.model.scan.ScanContext;
import com.polarion.alm.projects.model.IUniqueObject;

import java.util.List;

public interface IRepairer {

    List<Issue> scan(IUniqueObject entity, ScanContext context);

    RepairResult repair(IUniqueObject entity, RepairContext context);

    String getDisplayName();

    String getDescription();

    default List<RepairerConfigMeta> getConfigs() {
        return List.of();
    }

    default String getRepairerId() {
        return this.getClass().getSimpleName();
    }

}
