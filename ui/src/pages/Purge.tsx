import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { PageLayout } from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import OutdatedAttributesPanel from '../components/OutdatedAttributesPanel';
import type { ResultsTerms } from '../components/ResultsTable';
import ResultsTable from '../components/ResultsTable';
import ScanParamsPanel from '../components/ScanParamsPanel';
import { applyWriteResults, collectIssueGroupCounts, collectSelectedIssues } from '../services/scanEntities';
import useRemote from '../services/useRemote';
import useScanParams from '../services/useScanParams';
import useScanSelection from '../services/useScanSelection';
import type { RepairParams, RepairResult, Repairer, ScanResult } from '../types';

// Its own cookie namespace, so remembering "documents of type X" here does not change what General checks
// would scan.
const COOKIE_PREFIX = 'xmlRepair_purge_';

// The only repairer this page scans with; it lives in XmlRepairPolarionService.PURGE_REPAIRERS and reports one
// issue per attribute that is filled but no longer defined. Its Java class simple name is its id.
const PURGE_REPAIRER_ID = 'OutdatedCustomFieldsRepairer';

const RESULTS_TERMS: ResultsTerms = {
  issueSingular: 'outdated attribute',
  issuePlural: 'outdated attributes',
  issueColumn: 'Attributes',
  emptyMessage: 'No outdated attributes found.',
  groupColumn: 'Attribute',
};

/**
 * Purge outdated data: finds attributes which hold a value on the scanned entities but are not defined in their
 * custom fields configuration, and clears the ones the user picks.
 *
 * It is the General checks page with two differences. The left-hand block lists the attributes the scan found
 * rather than repairers to run, because which attributes exist is only known afterwards. And ticking those
 * attributes is what the results list shows: the ones left unticked are passed to the table as hidden groups,
 * the same filter the repairer breakdown uses on the other page.
 */
export default function Purge() {
  const { sendRequest } = useRemote();

  // An entity without an outdated attribute is noise on this page, so it hides them unless asked otherwise.
  // No revision either: purging writes, and the backend refuses to write anything resolved at a revision.
  const params = useScanParams(COOKIE_PREFIX, sendRequest, { defaultHideValid: true, supportsRevision: false });
  const { entityType, setEntitySubtype, subtypeSetExplicitly } = params;

  const [scanning, setScanning] = useState(false);
  const [purging, setPurging] = useState(false);
  const [purgingEntity, setPurgingEntity] = useState<string | null>(null);
  const [result, setResult] = useState<ScanResult | null>(null);
  const [resultHideValid, setResultHideValid] = useState(false);
  const [attributes, setAttributes] = useState<string[]>([]);
  const [selectedAttributes, setSelectedAttributes] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const attributesRef = useRef<HTMLDetailsElement>(null);
  const isFirstRender = useRef(true);

  const attributeCounts = useMemo(
    () => (result ? collectIssueGroupCounts(result) : new Map<string, number>()),
    [result],
  );

  // Unticked attributes are hidden groups, which is what filters the results table down to the entities that
  // hold at least one ticked attribute.
  const hiddenGroups = useMemo(
    () => new Set(attributes.filter((attribute) => !selectedAttributes.has(attribute))),
    [attributes, selectedAttributes],
  );

  // The table labels each issue from this list, so an attribute id shows as itself.
  const attributeGroups = useMemo<Repairer[]>(
    () => attributes.map((attribute) => ({ id: attribute, name: attribute, description: '', configs: [] })),
    [attributes],
  );

  const selection = useScanSelection({ result, hiddenGroups, busy: purging });

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  /** Nothing found for the previous parameters still holds, and neither does the attribute list built from it. */
  const discardResult = useCallback(() => {
    setResult(null);
    setError(null);
    setAttributes([]);
    setSelectedAttributes(new Set());
  }, []);

  // A different entity type has different definitions. The subtype is cleared unless the user picked one
  // explicitly, which is what handleEntityChange records.
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
    } else if (subtypeSetExplicitly.current) {
      subtypeSetExplicitly.current = false;
    } else {
      setEntitySubtype('');
    }
    discardResult();
  }, [entityType, setEntitySubtype, subtypeSetExplicitly, discardResult]);

  // Any change of what would be scanned invalidates the displayed result.
  useEffect(() => {
    discardResult();
  }, [params.selectedEntities, params.filterMode, discardResult]);

  /**
   * Applies a new attribute selection and drops the row selections it just hid, so an unticked attribute
   * cannot leave an invisible issue ticked for the next purge.
   */
  const applyAttributeSelection = (next: Set<string>) => {
    setSelectedAttributes(next);
    selection.pruneHiddenGroups(new Set(attributes.filter((attribute) => !next.has(attribute))));
  };

  const toggleAttribute = (id: string) => {
    const next = new Set(selectedAttributes);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    applyAttributeSelection(next);
  };

  const toggleAllAttributes = () => {
    applyAttributeSelection(selectedAttributes.size === attributes.length ? new Set() : new Set(attributes));
  };

  const handleEntityChange = (value: string) => {
    params.handleEntityChange(value);
    discardResult();
  };

  const handleScan = async () => {
    if (!params.projectId) {
      setError('Project ID is missing from the URL. Open this page from Polarion navigation.');
      return;
    }

    setError(null);
    setResult(null);
    setScanning(true);
    setElapsed(0);
    selection.reset();

    const startTime = Date.now();
    timerRef.current = setInterval(() => setElapsed(Date.now() - startTime), 100);

    try {
      const response = await sendRequest({
        method: 'POST',
        url: '/scan',
        body: JSON.stringify(params.buildScanParams([PURGE_REPAIRER_ID])),
        contentType: 'application/json',
      });

      if (response.ok) {
        const scanResult: ScanResult = await response.json();
        // Everything found starts ticked, so the results are visible straight away; unticking narrows them.
        const found = [...collectIssueGroupCounts(scanResult).keys()].sort((a, b) => a.localeCompare(b));
        setAttributes(found);
        setSelectedAttributes(new Set(found));
        setResultHideValid(params.hideValid);
        setResult(scanResult);
        if (attributesRef.current) {
          attributesRef.current.open = found.length > 0;
        }
      } else {
        const errData = await response.json().catch(() => null);
        const msg = errData?.message || `Request failed with status ${response.status}`;
        setError(msg);
        toast.error(msg);
      }
    } catch (e) {
      const msg = (e as Error).message;
      setError(msg);
      toast.error(msg);
    } finally {
      clearInterval(timerRef.current);
      setScanning(false);
    }
  };

  const handlePurge = async () => {
    if (!result) return;

    const { issueMetaInfos, affectedKeys } = collectSelectedIssues(result, selection.selectedIssues);
    if (issueMetaInfos.length === 0) return;

    setError(null);
    setPurging(true);
    for (const key of affectedKeys) {
      setPurgingEntity(key);
    }

    try {
      // The same write path the repairers use: the backend decodes each meta info back into an entity plus a
      // repairer, checks the Repair Authorization setting, clears the attribute and saves.
      const response = await sendRequest({
        method: 'POST',
        url: '/repair',
        body: JSON.stringify({ issueMetaInfos, configs: {} } satisfies RepairParams),
        contentType: 'application/json',
      });

      if (response.ok) {
        const purgeResults: RepairResult[] = await response.json();
        setResult((prev) => (prev ? applyWriteResults(prev, affectedKeys, purgeResults) : prev));

        const successCount = purgeResults.filter((r) => r.success).length;
        const failCount = purgeResults.length - successCount;
        if (successCount === 0) {
          toast.error('Purge failed');
        } else if (failCount === 0) {
          toast.success(`${successCount} attribute(s) purged successfully`);
        } else {
          toast.warning(`${successCount} attribute(s) purged, ${failCount} failed`);
        }
      } else {
        const errData = await response.json().catch(() => null);
        const msg = errData?.message || `Purge failed with status ${response.status}`;
        setError(msg);
        toast.error(msg);
      }
    } catch (e) {
      const msg = (e as Error).message;
      setError(msg);
      toast.error(msg);
    }

    setPurgingEntity(null);
    setPurging(false);
    selection.clearSelection();
  };

  return (
    <PageLayout>
      <div className="xml-repair-app">
        <div className="layout-columns">
          <div className="panel-left">
            <ScanParamsPanel
              {...params.panelProps}
              onEntityChange={handleEntityChange}
              hideValidLabel="Show items with outdated attributes only"
              onEnterKey={() => void handleScan()}
            />

            <div className="actions">
              <button
                className="btn btn-scan"
                onClick={handleScan}
                disabled={scanning || purging || params.selectionPending}
                title={params.selectionPending ? 'Please wait until the entity list is loaded' : ''}
              >
                {scanning ? 'Scanning...' : 'Scan'}
              </button>
              {result && (
                <button
                  className="btn btn-repair"
                  onClick={handlePurge}
                  disabled={!selection.hasAnySelection || purging || scanning}
                  title={!selection.hasAnySelection && !purging ? 'Please select at least one item to be purged' : ''}
                >
                  {purging
                    ? 'Purging...'
                    : selection.hasAnySelection
                      ? `Purge (attributes: ${selection.selectedIssueCount})`
                      : 'Purge'}
                </button>
              )}
            </div>

            <OutdatedAttributesPanel
              attributes={attributes}
              attributeCounts={attributeCounts}
              selectedAttributes={selectedAttributes}
              onToggleAttribute={toggleAttribute}
              onToggleAll={toggleAllAttributes}
              detailsRef={attributesRef}
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
                hiddenRepairers={hiddenGroups}
                onToggleRepairer={toggleAttribute}
                repairers={attributeGroups}
                terms={RESULTS_TERMS}
                selectedIssues={selection.selectedIssues}
                expandedRows={selection.expandedRows}
                repairingEntity={purgingEntity}
                batchRepairing={purging}
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
