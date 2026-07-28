import { useCallback, useEffect, useState } from 'react';
import { ConfigurationButtons, PageLayout, RevisionsTable, useConfirm } from '@grigoriev/react-sbb-polarion';
import type { Revision } from '@grigoriev/react-sbb-polarion';
import { toast } from 'sonner';
import useAuthorization from '../services/authorization';
import type { AuthorizationSettings, RolesInfo } from '../services/authorization';
import { getScope } from '../services/scope';

/**
 * Repair Authorization admin page (the React equivalent of the legacy authorization.jsp +
 * authorization.js). Lists the global and project roles available in the current scope as checkboxes;
 * the checked roles are the ones allowed to run repairs. Persisted as the generic single `Default`
 * setting, with the standard Save / Cancel / Default / Revisions toolbar.
 */
export default function Authorization() {
  const auth = useAuthorization();
  const scope = getScope();
  const { confirm, confirmDialog } = useConfirm();

  const [roles, setRoles] = useState<RolesInfo>({ globalRoles: [], projectRoles: [] });
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loaded, setLoaded] = useState(false);
  const [loadingError, setLoadingError] = useState(false);
  const [showRevisions, setShowRevisions] = useState(false);
  const [revisionsToken, setRevisionsToken] = useState(0);

  const applyContent = useCallback((content: AuthorizationSettings) => {
    setSelected(new Set([...(content.globalRoles ?? []), ...(content.projectRoles ?? [])]));
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoadingError(false);
    Promise.all([auth.loadRoles(scope), auth.loadContent(scope)])
      .then(([availableRoles, content]) => {
        if (cancelled) return;
        setRoles(availableRoles);
        applyContent(content);
        setLoaded(true);
      })
      .catch(() => {
        if (cancelled) return;
        setLoadingError(true);
        setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [auth, scope, applyContent]);

  const toggleRole = (role: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(role)) next.delete(role);
      else next.add(role);
      return next;
    });
  };

  const buildPayload = (): AuthorizationSettings => ({
    globalRoles: roles.globalRoles.filter((r) => selected.has(r)),
    projectRoles: roles.projectRoles.filter((r) => selected.has(r)),
  });

  const handleSave = async () => {
    toast.dismiss();
    try {
      await auth.saveContent(scope, buildPayload());
      setRevisionsToken((t) => t + 1);
      toast.success('Data successfully saved.');
    } catch (e) {
      toast.error((e as Error).message || 'Error occurred during saving the data.');
    }
  };

  const handleCancel = async () => {
    if (!(await confirm('Are you sure you want to cancel editing and revert all changes made?'))) return;
    try {
      applyContent(await auth.loadContent(scope));
      toast.dismiss();
    } catch {
      setLoadingError(true);
    }
  };

  const handleRevertToDefault = async () => {
    if (!(await confirm('Are you sure you want to return the default values?'))) return;
    toast.dismiss();
    try {
      applyContent(await auth.loadDefaultContent());
      toast.success('Reverted to the default values. Remember to save the configuration.');
    } catch {
      setLoadingError(true);
    }
  };

  const handleRevertToRevision = async (revision: Revision) => {
    try {
      applyContent(await auth.loadContent(scope, revision.name));
      toast.success(`Reverted to revision ${revision.name}. Don't forget to save.`);
    } catch {
      setLoadingError(true);
    }
  };

  if (!loaded) {
    return (
      <PageLayout title="Repair Authorization">
        <p>Loading...</p>
      </PageLayout>
    );
  }

  const renderRoles = (list: string[]) => (
    <ul className="roles-list">
      {list.map((role) => (
        <li key={role}>
          <label>
            <input type="checkbox" checked={selected.has(role)} onChange={() => toggleRole(role)} />
            <span>{role}</span>
          </label>
        </li>
      ))}
    </ul>
  );

  return (
    <PageLayout title="Repair Authorization">
      {loadingError && (
        <div className="alert alert-error">
          Error occurred loading the data. Be sure Polarion is started and accessible.
        </div>
      )}

      <div className="authorization-page">
        <div className="roles-group">
          <h2 className="align-left">Global Roles</h2>
          {roles.globalRoles.length > 0 ? renderRoles(roles.globalRoles) : <p>No global roles available.</p>}
        </div>

        {roles.projectRoles.length > 0 && (
          <div className="roles-group">
            <h2 className="align-left">Project Roles</h2>
            {renderRoles(roles.projectRoles)}
          </div>
        )}

        <ConfigurationButtons
          onSave={handleSave}
          onCancel={handleCancel}
          onRevertToDefault={handleRevertToDefault}
          onToggleRevisions={() => setShowRevisions((v) => !v)}
          revisionsShown={showRevisions}
        />

        {showRevisions && (
          <RevisionsTable
            name={auth.defaultName}
            scope={scope}
            reloadToken={revisionsToken}
            loadRevisions={auth.loadRevisions}
            onRevert={handleRevertToRevision}
          />
        )}
      </div>

      {confirmDialog}

      <div className="quick-help">
        <h2 className="align-left">Quick Help</h2>
        <div className="quick-help-text">
          <h3>Permissions</h3>
          <p>The finding issues functionality is unrestricted and available to all users.</p>
          <p>
            On the other hand, the repair functionality can be restricted or permitted for specific global or project
            roles.
          </p>
          <p>By default, only users with the global admin role have permission to repair.</p>
          <p>
            Additionally, project administrators can configure merging permissions based on the needs of their specific
            project, allowing for more granular control.
          </p>
        </div>
      </div>
    </PageLayout>
  );
}
