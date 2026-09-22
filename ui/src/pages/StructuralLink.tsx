import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { PageLayout, SearchableSelect } from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import type { ResultsTerms } from '../components/ResultsTable';
import ResultsTable from '../components/ResultsTable';
import ScanParamsPanel from '../components/ScanParamsPanel';
import { getCookie, setCookie } from '../services/cookies';
import { applyWriteResults, collectSelectedIssues } from '../services/scanEntities';
import useRemote from '../services/useRemote';
import useScanParams from '../services/useScanParams';
import useScanSelection from '../services/useScanSelection';
import type {
  IconSelectOption,
  Issue,
  LinkRole,
  RepairParams,
  RepairResult,
  Repairer,
  RepairerConfigValues,
  ScanResult,
} from '../types';

// Its own cookie namespace, so the documents this page remembers do not change what the other pages scan.
const COOKIE_PREFIX = 'xmlRepair_structuralLink_';
const ROLE_COOKIE = `${COOKIE_PREFIX}targetRole`;
const EXISTING_LINKS_COOKIE = `${COOKIE_PREFIX}existingLinks`;
const EXISTING_LINKS_ROLE_COOKIE = `${COOKIE_PREFIX}existingLinksRole`;

// Lives in XmlRepairPolarionService.STRUCTURE_LINK_REPAIRERS, apart from the General checks registry because
// changing the role rewrites how a document is structured. A repairer's id is its Java class simple name.
const STRUCTURE_LINK_REPAIRER_ID = 'ModuleStructureLinkRoleRepairer';
const TARGET_ROLE_CONFIG = 'targetRole';
const EXISTING_LINKS_CONFIG = 'existingLinks';
const EXISTING_LINKS_ROLE_CONFIG = 'existingLinksRole';

/** What Polarion uses unless a document was created with another role. Same default as the Java side. */
const DEFAULT_TARGET_ROLE = 'parent';

/**
 * What the repair does with the links a document already holds under the selected role. Mirrors the
 * EXISTING_LINKS_* constants of ModuleStructureLinkRoleRepairer, which reads them from the repairer configs.
 */
type ExistingLinks = 'INTERRUPT' | 'CHANGE' | 'IGNORE' | 'DELETE';

const IGNORE_HELP =
  'These links stay in place now, but Polarion removes them the next time each work item is saved. After the ' +
  'change it cannot tell them apart from the links the document derives from its own structure.';

const EXISTING_LINKS_OPTIONS: { id: ExistingLinks; label: string; help?: string }[] = [
  { id: 'INTERRUPT', label: 'Interrupt modification' },
  { id: 'IGNORE', label: 'Ignore', help: IGNORE_HELP },
  { id: 'CHANGE', label: 'Change link to' },
  { id: 'DELETE', label: 'Delete link' },
];

const EXISTING_LINKS_HELP =
  'Some work items in the document may already be linked with the role you picked. Once the document uses ' +
  'that role for its own structure, those links are lost. Choose what happens to them instead.';

const RESULTS_TERMS: ResultsTerms = {
  issueSingular: 'finding',
  issuePlural: 'findings',
  // A document either uses the selected role or does not, so the column shows which role it uses today
  // rather than a count that is always one. The breakdown still counts, and names its column separately.
  issueColumn: 'Link Role',
  countColumn: 'Findings',
  emptyMessage: 'No document uses a different structure link role.',
  groupColumn: 'Check',
  selectAction: 'change',
};

/**
 * What the "Link Role" column shows for one document: the role it uses today. A finding carries it as its
 * label; a document without one is on the selected role already, which is what `fallback` names. Every result
 * on display was scanned against the role currently selected, because changing it discards the results.
 */
function usedRoles(issues: Issue[], fallback: string): string {
  const roles = [...new Set(issues.map((issue) => issue.label).filter(Boolean))];
  return roles.length > 0 ? roles.join(', ') : fallback;
}

/**
 * Structural link: finds the documents whose structure link role differs from the selected one, and switches
 * the picked ones over to it.
 *
 * Documents only, HEAD only. A document is the only entity carrying a structure link role, and the backend
 * refuses to write anything resolved at a revision - which is also what the issue means by "only on head".
 */
export default function StructuralLink() {
  const { sendRequest } = useRemote();

  // A document already on the selected role is not a finding, so it is hidden unless asked otherwise.
  const params = useScanParams(COOKIE_PREFIX, sendRequest, {
    defaultHideValid: true,
    supportsRevision: false,
    entityTypes: ['DOCUMENT'],
  });
  const { projectId } = params;

  const [roles, setRoles] = useState<LinkRole[]>([]);
  const [rolesLoading, setRolesLoading] = useState(false);
  const [targetRole, setTargetRole] = useState(() => getCookie(ROLE_COOKIE) || DEFAULT_TARGET_ROLE);
  const [existingLinks, setExistingLinks] = useState<ExistingLinks>(
    () => (getCookie(EXISTING_LINKS_COOKIE) as ExistingLinks) || 'INTERRUPT',
  );
  const [existingLinksRole, setExistingLinksRole] = useState(() => getCookie(EXISTING_LINKS_ROLE_COOKIE) || '');

  const [scanning, setScanning] = useState(false);
  const [changing, setChanging] = useState(false);
  const [changingEntity, setChangingEntity] = useState<string | null>(null);
  const [result, setResult] = useState<ScanResult | null>(null);
  const [resultHideValid, setResultHideValid] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // Identifies the scan whose response may still be installed, so a response arriving after a parameter change
  // is dropped instead of reviving what it was scanned for. Same guard as on the Purge page.
  const scanRunRef = useRef(0);
  const scanInFlightRef = useRef(false);

  // One repairer and no finer grouping key on its issues, so this holds at most the repairer id.
  const [hiddenGroups, setHiddenGroups] = useState<Set<string>>(new Set());
  const issueGroups = useMemo<Repairer[]>(
    () => [{ id: STRUCTURE_LINK_REPAIRER_ID, name: 'Structure link role', description: '', configs: [] }],
    [],
  );

  const selection = useScanSelection({ result, hiddenGroups, busy: changing });

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  useEffect(() => {
    setCookie(ROLE_COOKIE, targetRole);
  }, [targetRole]);

  useEffect(() => {
    setCookie(EXISTING_LINKS_COOKIE, existingLinks);
  }, [existingLinks]);

  useEffect(() => {
    setCookie(EXISTING_LINKS_ROLE_COOKIE, existingLinksRole);
  }, [existingLinksRole]);

  useEffect(() => {
    if (!projectId) {
      setRoles([]);
      return;
    }
    let cancelled = false;
    const loadRoles = async () => {
      setRolesLoading(true);
      try {
        const response = await sendRequest({
          method: 'GET',
          url: `/link-roles?projectId=${encodeURIComponent(projectId)}`,
        });
        if (cancelled) return;
        if (response.ok) {
          setRoles(await response.json());
        } else {
          toast.error('Failed to load the link roles');
        }
      } catch {
        if (!cancelled) toast.error('Failed to load the link roles');
      } finally {
        if (!cancelled) setRolesLoading(false);
      }
    };
    void loadRoles();
    return () => {
      cancelled = true;
    };
  }, [projectId, sendRequest]);

  const roleOptions = useMemo<IconSelectOption[]>(() => {
    const options = roles.map((role) => ({ id: role.id, name: `${role.name} (${role.id})`, iconURL: role.iconURL }));
    // A role remembered from another project may be missing from this one. Keeping it as an option is what
    // lets the select show what will actually be submitted; the backend reports it as non-existent on repair.
    return options.some((option) => option.id === targetRole)
      ? options
      : [...options, { id: targetRole, name: targetRole }];
  }, [roles, targetRole]);

  /** The selected role is excluded: it is the very role that is about to become structural. */
  const replacementOptions = useMemo<IconSelectOption[]>(
    () =>
      roles
        .filter((role) => role.id !== targetRole)
        .map((role) => ({ id: role.id, name: `${role.name} (${role.id})`, iconURL: role.iconURL })),
    [roles, targetRole],
  );

  // Also what a fresh page starts on, and where a selection invalidated by a new target role lands.
  useEffect(() => {
    if (replacementOptions.length > 0 && !replacementOptions.some((option) => option.id === existingLinksRole)) {
      setExistingLinksRole(replacementOptions[0].id);
    }
  }, [replacementOptions, existingLinksRole]);

  const configs: RepairerConfigValues = useMemo(
    () => ({
      [STRUCTURE_LINK_REPAIRER_ID]: {
        [TARGET_ROLE_CONFIG]: targetRole,
        [EXISTING_LINKS_CONFIG]: existingLinks,
        [EXISTING_LINKS_ROLE_CONFIG]: existingLinksRole,
      },
    }),
    [targetRole, existingLinks, existingLinksRole],
  );

  const clearScanState = useCallback(() => {
    setResult(null);
    setError(null);
    setHiddenGroups(new Set());
  }, []);

  /** Drops the selections of whatever it hides, so an invisible issue cannot stay ticked. */
  const toggleGroup = (id: string) => {
    const next = new Set(hiddenGroups);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setHiddenGroups(next);
    selection.pruneHiddenGroups(next);
  };

  /** The same, and additionally orphans a scan still in flight, because what it would land in no longer applies. */
  const discardResult = useCallback(() => {
    scanRunRef.current += 1;
    clearScanState();
  }, [clearScanState]);

  /**
   * The entity selection as the request will carry it. In query mode the remembered keys are not part of the
   * scan at all, so pruning them must leave a completed scan alone.
   */
  const submittedEntities = params.selectionActive ? params.selectedEntities : null;

  useEffect(() => {
    discardResult();
  }, [submittedEntities, params.filterMode, params.entitySubtype, targetRole, discardResult]);

  const handleEntityChange = (value: string) => {
    params.handleEntityChange(value);
    discardResult();
  };

  const handleScan = async () => {
    if (!projectId) {
      setError('Project ID is missing from the URL. Open this page from Polarion navigation.');
      return;
    }
    if (scanInFlightRef.current) {
      return;
    }
    scanInFlightRef.current = true;
    const runId = ++scanRunRef.current;
    /** True once this run's response is no longer the one the page is waiting for. */
    const superseded = () => scanRunRef.current !== runId;

    clearScanState();
    setScanning(true);
    setElapsed(0);
    selection.reset();

    const startTime = Date.now();
    timerRef.current = setInterval(() => setElapsed(Date.now() - startTime), 100);

    try {
      const response = await sendRequest({
        method: 'POST',
        url: '/scan',
        body: JSON.stringify(params.buildScanParams([STRUCTURE_LINK_REPAIRER_ID], configs)),
        contentType: 'application/json',
      });

      if (response.ok) {
        const scanResult: ScanResult = await response.json();
        if (superseded()) return;
        setResultHideValid(params.hideValid);
        setResult(scanResult);
      } else {
        const errData = await response.json().catch(() => null);
        if (superseded()) return;
        const msg = errData?.message || `Request failed with status ${response.status}`;
        setError(msg);
        toast.error(msg);
      }
    } catch (e) {
      if (superseded()) return;
      const msg = (e as Error).message;
      setError(msg);
      toast.error(msg);
    } finally {
      scanInFlightRef.current = false;
      clearInterval(timerRef.current);
      setScanning(false);
    }
  };

  const handleChange = async () => {
    if (!result) return;

    const { issueMetaInfos, affectedKeys } = collectSelectedIssues(result, selection.selectedIssues);
    if (issueMetaInfos.length === 0) return;

    setError(null);
    setChanging(true);
    for (const key of affectedKeys) {
      setChangingEntity(key);
    }

    try {
      // The repairer write path, so this obeys the Repair Authorization setting.
      const response = await sendRequest({
        method: 'POST',
        url: '/repair',
        body: JSON.stringify({ issueMetaInfos, configs } satisfies RepairParams),
        contentType: 'application/json',
      });

      if (response.ok) {
        const changeResults: RepairResult[] = await response.json();
        setResult((prev) => (prev ? applyWriteResults(prev, affectedKeys, changeResults) : prev));

        const successCount = changeResults.filter((r) => r.success).length;
        const failCount = changeResults.length - successCount;
        if (successCount === 0) {
          toast.error('Structure link role was not changed');
        } else if (failCount === 0) {
          toast.success(`${successCount} document(s) switched to '${targetRole}'`);
        } else {
          toast.warning(`${successCount} document(s) switched, ${failCount} failed`);
        }
      } else {
        const errData = await response.json().catch(() => null);
        const msg = errData?.message || `Change failed with status ${response.status}`;
        setError(msg);
        toast.error(msg);
      }
    } catch (e) {
      const msg = (e as Error).message;
      setError(msg);
      toast.error(msg);
    }

    setChangingEntity(null);
    setChanging(false);
    selection.clearSelection();
  };

  const scanDisabled = scanning || changing || params.selectionPending;

  return (
    <PageLayout>
      <div className="xml-repair-app">
        <div className="layout-columns">
          <div className="panel-left">
            <div className="form-section">
              <div className="form-row">
                <label>
                  Structure link role
                  <span
                    className="help-icon"
                    title="The role a document must use to structure its content. Documents using another role are reported, and can be switched over to this one."
                  >
                    ?
                  </span>
                </label>
                <SearchableSelect
                  value={targetRole}
                  onChange={setTargetRole}
                  options={roleOptions}
                  allowEmpty={false}
                  loading={rolesLoading}
                />
              </div>
            </div>

            <ScanParamsPanel
              {...params.panelProps}
              onEntityChange={handleEntityChange}
              hideValidLabel="Show documents with a different role only"
              onEnterKey={() => {
                if (!scanDisabled) void handleScan();
              }}
            />

            <div className="actions">
              <button
                className="btn btn-scan"
                onClick={handleScan}
                disabled={scanDisabled}
                title={params.selectionPending ? 'Please wait until the entity list is loaded' : ''}
              >
                {scanning ? 'Scanning...' : 'Scan'}
              </button>
              {result && (
                <button
                  className="btn btn-repair"
                  onClick={handleChange}
                  disabled={!selection.hasAnySelection || changing || scanning}
                  title={
                    !selection.hasAnySelection && !changing ? 'Please select at least one document to be changed' : ''
                  }
                >
                  {changing
                    ? 'Changing...'
                    : selection.hasAnySelection
                      ? `Change role (documents: ${selection.selectedIssueCount})`
                      : 'Change role'}
                </button>
              )}
            </div>

            <div className="form-section existing-links-section">
              <div className="existing-links-title">
                Existing <span className="existing-links-role">{targetRole}</span> links
                <span className="help-icon" title={EXISTING_LINKS_HELP}>
                  ?
                </span>
              </div>
              {EXISTING_LINKS_OPTIONS.map((option) => (
                <div className="existing-links-row" key={option.id}>
                  <label htmlFor={`existing-links-${option.id}`}>
                    <input
                      id={`existing-links-${option.id}`}
                      type="radio"
                      name="existing-links"
                      value={option.id}
                      checked={existingLinks === option.id}
                      onChange={() => setExistingLinks(option.id)}
                    />
                    <span>{option.label}</span>
                  </label>
                  {/* Outside the label, so clicking the icon does not pick the answer it explains. */}
                  {option.help && (
                    <span className="help-icon" title={option.help}>
                      ?
                    </span>
                  )}
                  {option.id === 'CHANGE' && (
                    <SearchableSelect
                      value={existingLinksRole}
                      onChange={setExistingLinksRole}
                      options={replacementOptions}
                      allowEmpty={false}
                      loading={rolesLoading}
                      disabled={existingLinks !== 'CHANGE'}
                    />
                  )}
                </div>
              ))}
            </div>
          </div>

          <div className="panel-right">
            {!scanning && !result && !error && (
              <div className="panel-right-placeholder">Choose parameters and initiate scanning with 'Scan' button</div>
            )}

            {scanning && (
              <div className="scanning-indicator">
                <span className="spinner" />
                <span>Scanning... {(elapsed / 1000).toFixed(1)}s</span>
              </div>
            )}

            {error && <div className="error-message">{error}</div>}

            {result && (
              <ResultsTable
                result={result}
                hideValidAtScanTime={resultHideValid}
                hiddenRepairers={hiddenGroups}
                onToggleRepairer={toggleGroup}
                repairers={issueGroups}
                terms={RESULTS_TERMS}
                issueCell={(issues) => usedRoles(issues, targetRole)}
                selectedIssues={selection.selectedIssues}
                expandedRows={selection.expandedRows}
                repairingEntity={changingEntity}
                batchRepairing={changing}
                onToggleEntitySelection={selection.toggleEntitySelection}
                onToggleCollectionSelection={selection.toggleCollectionSelection}
                onToggleIssueSelection={selection.toggleIssueSelection}
                onToggleExpanded={selection.toggleExpanded}
                onToggleSelectAll={selection.toggleSelectAll}
                onExpandAll={selection.expandAll}
                onCollapseAll={selection.collapseAll}
                allItemsSelected={selection.allItemsSelected}
                someItemsSelected={selection.someItemsSelected}
              />
            )}
          </div>
        </div>
      </div>
    </PageLayout>
  );
}
