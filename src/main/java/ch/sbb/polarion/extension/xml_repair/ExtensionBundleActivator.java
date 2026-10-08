package ch.sbb.polarion.extension.xml_repair;

import ch.sbb.polarion.extension.generic.GenericBundleActivator;
import ch.sbb.polarion.extension.xml_repair.service.RepairJobsService;
import ch.sbb.polarion.extension.xml_repair.service.ScanJobsService;
import com.polarion.alm.ui.server.forms.extensions.IFormExtension;
import org.osgi.framework.BundleContext;

import java.util.Map;

/**
 * Stops the jobs with the bundle: a running scan is asked to stop at its next entity, a running repair still runs to
 * its commit, and a job which waits for a thread is cancelled.
 */
public class ExtensionBundleActivator extends GenericBundleActivator {

    @Override
    protected Map<String, IFormExtension> getExtensions() {
        return Map.of();
    }

    @Override
    public void stop(BundleContext context) {
        ScanJobsService.shutdown();
        RepairJobsService.shutdown();
        super.stop(context);
    }

}
