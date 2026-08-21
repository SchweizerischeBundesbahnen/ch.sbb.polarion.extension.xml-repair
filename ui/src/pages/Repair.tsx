import { useCallback, useEffect, useRef, useState } from 'react';
import { PageLayout } from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import RepairersPanel from '../components/RepairersPanel';
import ResultsTable from '../components/ResultsTable';
import ScanParamsPanel from '../components/ScanParamsPanel';
import { getCookie as getRawCookie, setCookie as setRawCookie } from '../services/cookies';
import { applyWriteResults, collectSelectedIssues } from '../services/scanEntities';
import useRemote from '../services/useRemote';
import useScanParams from '../services/useScanParams';
import useScanSelection from '../services/useScanSelection';
import type { RepairParams, RepairResult, Repairer, ScanResult } from '../types';

const COOKIE_PREFIX = 'xmlRepair_';

// Repairer IDs (Java class simple names) that should be off by default for first-time users.
// Existing users with a saved cookie are unaffected - their cookie won't list these IDs anyway.
const OPT_OUT_BY_DEFAULT_REPAIRERS = new Set<string>(['ModuleStandardStructureLinkRoleRepairer']);

// Thin wrappers over the shared cookie helpers that pin every key to this app's prefix, so the raw
// read/write logic lives in one place (services/cookies) and the prefix relationship stays explicit.
function getCookie(key: string): string | null {
  return getRawCookie(COOKIE_PREFIX + key);
}

function setCookie(key: string, value: string): void {
  setRawCookie(COOKIE_PREFIX + key, value);
}

export default function Repair() {
  const { sendRequest } = useRemote();

  // What to scan, how to filter it and the advanced limits, shared with the Purge page under its own cookies.
  const params = useScanParams(COOKIE_PREFIX, sendRequest);
  const { projectId, entityType, setEntitySubtype, subtypeSetExplicitly } = params;

  const [repairers, setRepairers] = useState<Repairer[]>([]);
  const [selectedRepairers, setSelectedRepairers] = useState<string[]>([]);
  const [repairerConfigs, setRepairerConfigs] = useState<Record<string, Record<string, boolean>>>({});

  const [scanning, setScanning] = useState(false);
  const [batchRepairing, setBatchRepairing] = useState(false);
  const [repairingEntity, setRepairingEntity] = useState<string | null>(null);
  const [result, setResult] = useState<ScanResult | null>(null);
  const [resultHideValid, setResultHideValid] = useState(false);
  const [hiddenRepairers, setHiddenRepairers] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const repairersRef = useRef<HTMLDetailsElement>(null);
  const isFirstRender = useRef(true);
  // Identifies the scan whose response may still be installed. Bumped by a new scan and by anything that
  // discards the result, so a response arriving late is dropped rather than reviving results for parameters
  // the user has moved on from - which would leave stale issues selectable for the next repair.
  const scanRunRef = useRef(0);
  // Whether a scan is in flight right now. A ref rather than the `scanning` state, because that only reaches
  // the handlers on the next render: pressing Enter in a parameter field immediately after clicking Scan would
  // still see `scanning === false` and start a second, overlapping request.
  const scanInFlightRef = useRef(false);

  // Row and issue selection, shared with the Purge page: same keys, same toggles, same write bookkeeping.
  const selection = useScanSelection({ result, hiddenGroups: hiddenRepairers, busy: batchRepairing });

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  // Persist the repairer selection and its per-repairer settings; useScanParams owns the scan parameters.
  useEffect(() => {
    if (repairers.length > 0) setCookie(`repairers_${entityType}`, selectedRepairers.join(','));
  }, [selectedRepairers, entityType, repairers]);
  useEffect(() => {
    for (const [repairerId, configs] of Object.entries(repairerConfigs)) {
      for (const [key, val] of Object.entries(configs)) {
        setCookie(`rc_${repairerId}_${key}`, String(val));
      }
    }
  }, [repairerConfigs]);

  const loadRepairers = useCallback(async () => {
    const response = await sendRequest({
      method: 'GET',
      url: `/repairers?entityType=${entityType}`,
    });
    if (response.ok) {
      const data: Repairer[] = await response.json();
      setRepairers(data);
      const availableIds = data.map((r) => r.id);
      const defaultSelectedIds = availableIds.filter((id) => !OPT_OUT_BY_DEFAULT_REPAIRERS.has(id));
      if (isFirstRender.current) {
        const savedRaw = getCookie(`repairers_${entityType}`);
        const savedIds = savedRaw ? savedRaw.split(',').filter((id) => availableIds.includes(id)) : [];
        setSelectedRepairers(savedIds.length > 0 ? savedIds : defaultSelectedIds);
      } else {
        setSelectedRepairers(defaultSelectedIds);
      }
      const defaults: Record<string, Record<string, boolean>> = {};
      data.forEach((r) => {
        if (r.configs.length > 0) {
          defaults[r.id] = {};
          r.configs.forEach((c) => {
            defaults[r.id][c.key] = c.defaultValue;
          });
        }
      });
      for (const [repairerId, configs] of Object.entries(defaults)) {
        for (const key of Object.keys(configs)) {
          const saved = getCookie(`rc_${repairerId}_${key}`);
          if (saved !== null) {
            defaults[repairerId][key] = saved === 'true';
          }
        }
      }
      setRepairerConfigs(defaults);
    } else {
      toast.error('Failed to load repairers');
    }
  }, [entityType, sendRequest]);

  /**
   * Nothing found for the previous parameters still holds, so a scan still in flight for them is orphaned.
   *
   * Only for the invalidation points - a parameter change. Starting a scan clears the same state inline
   * instead: bumping the counter here would supersede the very run being started, so its own response would be
   * dropped and no scan could ever show results.
   */
  const discardResult = useCallback(() => {
    scanRunRef.current += 1;
    setResult(null);
    setError(null);
  }, []);

  useEffect(() => {
    void loadRepairers();
    if (isFirstRender.current) {
      isFirstRender.current = false;
    } else if (subtypeSetExplicitly.current) {
      subtypeSetExplicitly.current = false;
    } else {
      setEntitySubtype('');
    }
    discardResult();
  }, [loadRepairers, setEntitySubtype, subtypeSetExplicitly, discardResult]);

  // Any change of what would be scanned invalidates the displayed result.
  useEffect(() => {
    discardResult();
  }, [selectedRepairers, params.selectedEntities, params.filterMode, discardResult]);

  const handleEntityChange = (value: string) => {
    params.handleEntityChange(value);
    discardResult();
  };

  /** Hiding a repairer must also drop the selections of the issues it just hid. */
  const toggleRepairerHidden = (id: string) => {
    const next = new Set(hiddenRepairers);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setHiddenRepairers(next);
    selection.pruneHiddenGroups(next);
  };

  const updateRepairerConfig = (repairerId: string, settingId: string, value: boolean) => {
    setRepairerConfigs((prev) => ({
      ...prev,
      [repairerId]: { ...prev[repairerId], [settingId]: value },
    }));
  };

  const toggleRepairer = (id: string) => {
    setSelectedRepairers((prev) => (prev.includes(id) ? prev.filter((r) => r !== id) : [...prev, id]));
  };

  const toggleAllRepairers = () => {
    if (selectedRepairers.length === repairers.length) {
      setSelectedRepairers([]);
    } else {
      setSelectedRepairers(repairers.map((r) => r.id));
    }
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

    setError(null);
    setResult(null);
    setScanning(true);
    setElapsed(0);
    selection.reset();

    if (repairersRef.current) {
      repairersRef.current.open = false;
    }

    const startTime = Date.now();
    timerRef.current = setInterval(() => setElapsed(Date.now() - startTime), 100);

    const activeConfigs: Record<string, Record<string, boolean>> = {};
    selectedRepairers.forEach((id) => {
      if (repairerConfigs[id]) {
        activeConfigs[id] = repairerConfigs[id];
      }
    });

    const body = JSON.stringify(params.buildScanParams(selectedRepairers, activeConfigs));

    try {
      const response = await sendRequest({
        method: 'POST',
        url: '/scan',
        body,
        contentType: 'application/json',
      });

      if (response.ok) {
        const scanResult: ScanResult = await response.json();
        if (superseded()) {
          return;
        }
        setResultHideValid(params.hideValid);
        setHiddenRepairers(new Set());
        setResult(scanResult);
      } else {
        const errData = await response.json().catch(() => null);
        if (superseded()) {
          return;
        }
        const msg = errData?.message || `Request failed with status ${response.status}`;
        setError(msg);
        toast.error(msg);
      }
    } catch (e) {
      if (superseded()) {
        return;
      }
      const msg = (e as Error).message;
      setError(msg);
      toast.error(msg);
    } finally {
      // Safe to reset unconditionally: the re-entry guard keeps a single scan in flight, so this run still owns
      // the timer and the flag even when its response was dropped as superseded.
      scanInFlightRef.current = false;
      clearInterval(timerRef.current);
      setScanning(false);
    }
  };

  const handleBatchRepair = async () => {
    if (!result) return;

    const { issueMetaInfos, affectedKeys } = collectSelectedIssues(result, selection.selectedIssues);
    if (issueMetaInfos.length === 0) return;

    setError(null);
    setBatchRepairing(true);

    const activeConfigs: Record<string, Record<string, boolean>> = {};
    selectedRepairers.forEach((id) => {
      if (repairerConfigs[id]) {
        activeConfigs[id] = repairerConfigs[id];
      }
    });

    for (const key of affectedKeys) {
      setRepairingEntity(key);
    }

    try {
      const response = await sendRequest({
        method: 'POST',
        url: '/repair',
        body: JSON.stringify({
          issueMetaInfos,
          configs: activeConfigs,
        } satisfies RepairParams),
        contentType: 'application/json',
      });

      if (response.ok) {
        const repairResults: RepairResult[] = await response.json();
        setResult((prev) => (prev ? applyWriteResults(prev, affectedKeys, repairResults) : prev));

        const successCount = repairResults.filter((r) => r.success).length;
        const failCount = repairResults.length - successCount;
        const hasWarnings = repairResults.some((r) => r.success && r.warnings?.length > 0);

        if (successCount === 0) {
          toast.error('Repair failed');
        } else if (failCount === 0 && !hasWarnings) {
          toast.success(`${successCount} issue(s) repaired successfully`);
        } else if (failCount === 0 && hasWarnings) {
          toast.warning(`${successCount} issue(s) repaired with warnings`);
        } else {
          toast.warning(`${successCount} issue(s) repaired, ${failCount} failed`);
        }
      } else {
        const errData = await response.json().catch(() => null);
        const msg = errData?.message || `Repair failed with status ${response.status}`;
        setError(msg);
        toast.error(msg);
      }
    } catch (e) {
      const msg = (e as Error).message;
      setError(msg);
      toast.error(msg);
    }

    setRepairingEntity(null);
    setBatchRepairing(false);
    selection.clearSelection();
  };

  const scanDisabled = scanning || batchRepairing || selectedRepairers.length === 0 || params.selectionPending;

  return (
    // No title: Scan & Repair is the primary product surface, not an admin page, so it carries the
    // dev-only Overview back link (via PageLayout) but not the admin-style heading + underline.
    <PageLayout>
      <div className="xml-repair-app">
        <div className="layout-columns">
          <div className="panel-left">
            <ScanParamsPanel
              {...params.panelProps}
              onEntityChange={handleEntityChange}
              onEnterKey={() => {
                if (!scanDisabled) void handleScan();
              }}
            />

            <div className="actions">
              <button
                className="btn btn-scan"
                onClick={handleScan}
                disabled={scanDisabled}
                title={
                  selectedRepairers.length === 0
                    ? 'Please select at least one repairer'
                    : params.selectionPending
                      ? 'Please wait until the entity list is loaded'
                      : ''
                }
              >
                {scanning ? 'Scanning...' : 'Scan'}
              </button>
              {result && (
                <button
                  className="btn btn-repair"
                  onClick={handleBatchRepair}
                  disabled={!selection.hasAnySelection || batchRepairing || scanning}
                  title={
                    !selection.hasAnySelection && !batchRepairing
                      ? 'Please select at least one item to be repaired'
                      : ''
                  }
                >
                  {batchRepairing
                    ? 'Repairing...'
                    : selection.hasAnySelection
                      ? `Repair (issues: ${selection.selectedIssueCount})`
                      : 'Repair'}
                </button>
              )}
            </div>

            <RepairersPanel
              repairers={repairers}
              selectedRepairers={selectedRepairers}
              repairerConfigs={repairerConfigs}
              onToggleRepairer={toggleRepairer}
              onToggleAll={toggleAllRepairers}
              onUpdateConfig={updateRepairerConfig}
              detailsRef={repairersRef}
            />
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
                hiddenRepairers={hiddenRepairers}
                onToggleRepairer={toggleRepairerHidden}
                repairers={repairers}
                selectedIssues={selection.selectedIssues}
                expandedRows={selection.expandedRows}
                repairingEntity={repairingEntity}
                batchRepairing={batchRepairing}
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
