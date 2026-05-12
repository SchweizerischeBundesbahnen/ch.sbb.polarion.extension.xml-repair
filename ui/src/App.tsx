import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Toaster, toast } from 'sonner';
import BreadcrumbInjector from './components/BreadcrumbInjector';
import type { NumericInputHint } from './components/NumericInput';
import RepairersPanel from './components/RepairersPanel';
import ResultsTable from './components/ResultsTable';
import ScanParamsPanel from './components/ScanParamsPanel';
import useRemote from './services/useRemote';
import type {
  BaselineInfo,
  EntitySubtype,
  EntityType,
  IconSelectOption,
  Issue,
  RepairIssueResult,
  RepairParams,
  RepairResult,
  Repairer,
  ScanEntity,
  ScanParams,
  ScanResult,
} from './types';

const COOKIE_PREFIX = 'xmlRepair_';

function getCookie(key: string): string | null {
  const name = COOKIE_PREFIX + key + '=';
  const parts = document.cookie.split('; ');
  for (const part of parts) {
    if (part.startsWith(name)) return decodeURIComponent(part.substring(name.length));
  }
  return null;
}

function setCookie(key: string, value: string): void {
  document.cookie = `${COOKIE_PREFIX}${key}=${encodeURIComponent(value)}; path=/; max-age=${60 * 60 * 24 * 365}; SameSite=Lax`;
}

const ENTITY_TYPE_OPTIONS: IconSelectOption[] = [
  { id: 'WORKITEM', name: 'Work Items', iconURL: '/polarion/ria/images/topicIconsSmallDark/workItems.svg' },
  { id: 'DOCUMENT', name: 'Documents', iconURL: '/polarion/ria/images/topicIconsSmallDark/document-line.svg' },
  { id: 'COLLECTION', name: 'Collections', iconURL: '/polarion/ria/images/topicIconsSmallDark/collectionsTopic.svg' },
];

const hasSubitems = (item: ScanEntity): boolean => item.subitems && item.subitems.length > 0;
const itemKey = (item: ScanEntity): string => `${item.projectId}-${item.space || ''}-${item.entityId}`;
const subitemKey = (parentKey: string, sub: ScanEntity): string =>
  `${parentKey}/${sub.projectId}-${sub.space || ''}-${sub.entityId}`;

export default function App() {
  const { sendRequest } = useRemote();

  const [entityType, setEntityType] = useState<EntityType>(() => {
    const saved = getCookie('entityType');
    return ENTITY_TYPE_OPTIONS.some((o) => o.id === saved) ? (saved as EntityType) : 'WORKITEM';
  });
  const [projectId] = useState(() => String(new URLSearchParams(window.location.search).get('projectId') || ''));
  const [userQuery, setUserQuery] = useState(() => String(getCookie('userQuery') || ''));
  const [revision, setRevision] = useState<number>(() => {
    const saved = parseInt(getCookie('revision') || '', 10);
    return saved > 0 ? saved : 0;
  });
  const [baselines, setBaselines] = useState<BaselineInfo[]>([]);
  const [baselinesLoading, setBaselinesLoading] = useState(false);
  const [sort, setSort] = useState(() => String(getCookie('sort') || '~updated'));
  const [limit, setLimit] = useState(() => {
    const saved = parseInt(getCookie('limit') || '');
    return saved > 0 ? saved : 100;
  });
  const [timeout, setTimeout] = useState(() => {
    const saved = parseInt(getCookie('timeout') || '');
    return saved > 0 ? saved : 60;
  });
  const [hideValid, setHideValid] = useState(() => getCookie('hideValid') === 'true');

  const [allSubtypes, setAllSubtypes] = useState<Record<string, EntitySubtype[]>>({});
  const [entitySubtype, setEntitySubtype] = useState(() => getCookie('entitySubtype') || '');

  const [repairers, setRepairers] = useState<Repairer[]>([]);
  const [selectedRepairers, setSelectedRepairers] = useState<string[]>([]);
  const [repairerConfigs, setRepairerConfigs] = useState<Record<string, Record<string, boolean>>>({});

  const [scanning, setScanning] = useState(false);
  const [batchRepairing, setBatchRepairing] = useState(false);
  const [repairingEntity, setRepairingEntity] = useState<string | null>(null);
  const [result, setResult] = useState<ScanResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const repairersRef = useRef<HTMLDetailsElement>(null);
  const isFirstRender = useRef(true);
  const subtypeSetExplicitly = useRef(false);

  const [selectedIssues, setSelectedIssues] = useState<Map<string, Set<number>>>(new Map());
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  // Persist user preferences to cookies
  useEffect(() => {
    setCookie('entityType', entityType);
  }, [entityType]);
  useEffect(() => {
    setCookie('entitySubtype', entitySubtype);
  }, [entitySubtype]);
  useEffect(() => {
    setCookie('userQuery', userQuery);
  }, [userQuery]);
  useEffect(() => {
    setCookie('revision', revision ? String(revision) : '');
  }, [revision]);
  useEffect(() => {
    setCookie('sort', sort);
  }, [sort]);
  useEffect(() => {
    setCookie('limit', String(limit));
  }, [limit]);
  useEffect(() => {
    setCookie('timeout', String(timeout));
  }, [timeout]);
  useEffect(() => {
    setCookie('hideValid', String(hideValid));
  }, [hideValid]);
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
      if (isFirstRender.current) {
        const savedRaw = getCookie(`repairers_${entityType}`);
        const savedIds = savedRaw ? savedRaw.split(',').filter((id) => availableIds.includes(id)) : [];
        setSelectedRepairers(savedIds.length > 0 ? savedIds : availableIds);
      } else {
        setSelectedRepairers(availableIds);
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

  useEffect(() => {
    void loadRepairers();
    if (isFirstRender.current) {
      isFirstRender.current = false;
    } else if (subtypeSetExplicitly.current) {
      subtypeSetExplicitly.current = false;
    } else {
      setEntitySubtype('');
    }
    setResult(null);
    setError(null);
  }, [loadRepairers]);

  useEffect(() => {
    if (!projectId) {
      setAllSubtypes({});
      return;
    }

    const enumPaths = {
      WORKITEM: '~/work-item-type/~',
      DOCUMENT: 'documents/document-type/~',
    };

    const loadAllSubtypes = async () => {
      const headers: Record<string, string> = {};
      if (import.meta.env.VITE_BEARER_TOKEN) {
        headers['Authorization'] = `Bearer ${import.meta.env.VITE_BEARER_TOKEN}`;
      }
      const result: Record<string, EntitySubtype[]> = {};
      await Promise.all(
        Object.entries(enumPaths).map(async ([type, enumPath]) => {
          try {
            const params = new URLSearchParams({ 'fields[enumerations]': '@all' });
            const url = `/polarion/rest/v1/projects/${encodeURIComponent(projectId)}/enumerations/${enumPath}?${params}`;
            const response = await fetch(url, { headers });
            if (response.ok) {
              const json = await response.json();
              result[type] = json.data?.attributes?.options || [];
            }
          } catch {
            toast.error(`Failed to load ${type.toLowerCase()} subtypes`);
          }
        }),
      );
      setAllSubtypes(result);
    };
    void loadAllSubtypes();
  }, [projectId]);

  // Validate saved entitySubtype once subtypes are loaded
  useEffect(() => {
    if (entitySubtype && Object.keys(allSubtypes).length > 0) {
      const subs = allSubtypes[entityType] || [];
      if (!subs.some((s) => s.id === entitySubtype)) {
        setEntitySubtype('');
      }
    }
  }, [allSubtypes, entityType, entitySubtype]);

  useEffect(() => {
    if (!projectId) {
      setBaselines([]);
      return;
    }
    const loadBaselines = async () => {
      setBaselinesLoading(true);
      try {
        const response = await sendRequest({
          method: 'GET',
          url: `/baselines?projectId=${encodeURIComponent(projectId)}`,
        });
        if (response.ok) {
          const data: BaselineInfo[] = await response.json();
          setBaselines(data);
        } else {
          toast.error('Failed to load baselines');
        }
      } catch {
        toast.error('Failed to load baselines');
      } finally {
        setBaselinesLoading(false);
      }
    };
    void loadBaselines();
  }, [projectId, sendRequest]);

  useEffect(() => {
    setResult(null);
    setError(null);
  }, [selectedRepairers]);

  const combinedEntityOptions = useMemo((): IconSelectOption[] => {
    const result: IconSelectOption[] = [];
    for (const opt of ENTITY_TYPE_OPTIONS) {
      result.push(opt);
      const subs = allSubtypes[opt.id] || [];
      for (const sub of subs) {
        result.push({ id: `${opt.id}::${sub.id}`, name: sub.name, iconURL: sub.iconURL || opt.iconURL, indent: true });
      }
    }
    return result;
  }, [allSubtypes]);

  const entityValue = entitySubtype ? `${entityType}::${entitySubtype}` : entityType;

  const revisionHints = useMemo<NumericInputHint[]>(
    () =>
      baselines
        .map((b) => ({ value: Number(b.revision), label: b.name || '' }))
        .filter((h) => Number.isFinite(h.value) && h.value > 0),
    [baselines],
  );

  const handleEntityChange = (val: string) => {
    const parts = val.split('::');
    if (parts[1]) subtypeSetExplicitly.current = true;
    setEntityType(parts[0] as EntityType);
    setEntitySubtype(parts[1] || '');
    setResult(null);
    setError(null);
  };

  const toggleEntitySelection = (entityKey: string, totalIssues: number) => {
    setSelectedIssues((prev) => {
      const next = new Map(prev);
      const current = next.get(entityKey) || new Set();
      if (current.size === totalIssues) {
        next.delete(entityKey);
      } else {
        next.set(entityKey, new Set(Array.from({ length: totalIssues }, (_, i) => i)));
      }
      return next;
    });
  };

  const toggleIssueSelection = (entityKey: string, issueIndex: number) => {
    setSelectedIssues((prev) => {
      const next = new Map(prev);
      const current = new Set(next.get(entityKey) || []);
      if (current.has(issueIndex)) {
        current.delete(issueIndex);
      } else {
        current.add(issueIndex);
      }
      if (current.size === 0) {
        next.delete(entityKey);
      } else {
        next.set(entityKey, current);
      }
      return next;
    });
  };

  const toggleExpanded = (entityKey: string) => {
    if (batchRepairing) return;
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(entityKey)) next.delete(entityKey);
      else next.add(entityKey);
      return next;
    });
  };

  const hasAnySelection = selectedIssues.size > 0;

  const collectSelectableKeys = (items: ScanEntity[]) => {
    const keys: { key: string; count: number }[] = [];
    for (const item of items) {
      if (hasSubitems(item)) {
        const parentKey = itemKey(item);
        for (const sub of item.subitems) {
          if (sub.issues.length > 0 && !sub.repaired && !sub.revision) {
            keys.push({ key: subitemKey(parentKey, sub), count: sub.issues.length });
          }
        }
      } else if (item.issues.length > 0 && !item.repaired && !item.revision) {
        keys.push({ key: itemKey(item), count: item.issues.length });
      }
    }
    return keys;
  };

  const selectableKeys = result ? collectSelectableKeys(result.items) : [];
  const allItemsSelected =
    selectableKeys.length > 0 &&
    selectableKeys.every(({ key, count }) => {
      const sel = selectedIssues.get(key);
      return sel && sel.size === count;
    });
  const someItemsSelected = hasAnySelection && !allItemsSelected;

  const toggleSelectAll = () => {
    if (allItemsSelected) {
      setSelectedIssues(new Map());
    } else {
      const next = new Map<string, Set<number>>();
      selectableKeys.forEach(({ key, count }) => {
        next.set(key, new Set(Array.from({ length: count }, (_, i) => i)));
      });
      setSelectedIssues(next);
    }
  };

  const toggleCollectionSelection = (item: ScanEntity) => {
    const parentKey = itemKey(item);
    const subs = item.subitems.filter((sub) => sub.issues.length > 0 && !sub.repaired && !sub.revision);
    const allSubsSelected =
      subs.length > 0 &&
      subs.every((sub) => {
        const key = subitemKey(parentKey, sub);
        const sel = selectedIssues.get(key);
        return sel && sel.size === sub.issues.length;
      });
    setSelectedIssues((prev) => {
      const next = new Map(prev);
      if (allSubsSelected) {
        subs.forEach((sub) => next.delete(subitemKey(parentKey, sub)));
      } else {
        subs.forEach((sub) => {
          next.set(subitemKey(parentKey, sub), new Set(Array.from({ length: sub.issues.length }, (_, i) => i)));
        });
      }
      return next;
    });
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

    setError(null);
    setResult(null);
    setScanning(true);
    setElapsed(0);
    setSelectedIssues(new Map());
    setExpandedRows(new Set());

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

    const body = JSON.stringify({
      projectId,
      entityType,
      entitySubtype: entitySubtype || null,
      userQuery: userQuery || null,
      revision: entityType === 'COLLECTION' || !revision ? null : String(revision),
      sort: sort || null,
      limit,
      repairers: selectedRepairers,
      timeout: timeout * 1000,
      hideValid,
      configs: activeConfigs,
    } satisfies ScanParams);

    try {
      const response = await sendRequest({
        method: 'POST',
        url: '/scan',
        body,
        contentType: 'application/json',
      });

      if (response.ok) {
        const scanResult: ScanResult = await response.json();
        setResult(scanResult);
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

  const handleBatchRepair = async () => {
    if (!result) return;

    const issueMetaInfos: string[] = [];
    const affectedEntities: { key: string; item: ScanEntity; parentItem?: ScanEntity }[] = [];

    for (const item of result.items) {
      if (hasSubitems(item)) {
        const parentKey = itemKey(item);
        for (const sub of item.subitems) {
          const key = subitemKey(parentKey, sub);
          const selected = selectedIssues.get(key);
          if (selected && selected.size > 0) {
            selected.forEach((i) => {
              if (sub.issues[i]) issueMetaInfos.push(sub.issues[i].metaInfo);
            });
            affectedEntities.push({ key, item: sub, parentItem: item });
          }
        }
      } else {
        const key = itemKey(item);
        const selected = selectedIssues.get(key);
        if (selected && selected.size > 0) {
          selected.forEach((i) => {
            if (item.issues[i]) issueMetaInfos.push(item.issues[i].metaInfo);
          });
          affectedEntities.push({ key, item });
        }
      }
    }
    if (issueMetaInfos.length === 0) return;

    setError(null);
    setBatchRepairing(true);

    const activeConfigs: Record<string, Record<string, boolean>> = {};
    selectedRepairers.forEach((id) => {
      if (repairerConfigs[id]) {
        activeConfigs[id] = repairerConfigs[id];
      }
    });

    for (const { key } of affectedEntities) {
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
        const resultByMetaInfo = new Map<string, RepairIssueResult>();
        for (const r of repairResults) {
          resultByMetaInfo.set(r.issueMetaInfo, { success: r.success, warnings: [...(r.warnings || [])] });
        }

        const annotateIssues = (issues: Issue[]): Issue[] =>
          issues.map((issue) => {
            const repair = resultByMetaInfo.get(issue.metaInfo);
            return repair ? { ...issue, repairResult: repair } : issue;
          });
        const allRepaired = (issues: Issue[]): boolean => issues.every((issue) => issue.repairResult?.success);

        setResult((prev) => {
          if (!prev) return prev;
          const affectedKeys = new Set(affectedEntities.map((e) => e.key));
          return {
            ...prev,
            items: prev.items.map((it) => {
              const itKey = itemKey(it);
              if (hasSubitems(it)) {
                const parentKey = itKey;
                const hasAffectedSub = it.subitems.some((sub) => affectedKeys.has(subitemKey(parentKey, sub)));
                if (!hasAffectedSub) return it;
                return {
                  ...it,
                  subitems: it.subitems.map((sub) => {
                    const sKey = subitemKey(parentKey, sub);
                    if (!affectedKeys.has(sKey)) return sub;
                    const updated = annotateIssues(sub.issues);
                    return { ...sub, issues: updated, repaired: allRepaired(updated) };
                  }),
                };
              }
              if (!affectedKeys.has(itKey)) return it;
              const updated = annotateIssues(it.issues);
              return { ...it, issues: updated, repaired: allRepaired(updated) };
            }),
          };
        });

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
    setSelectedIssues(new Map());
  };

  return (
    <div className="xml-repair-app">
      <BreadcrumbInjector />
      <Toaster position="top-center" duration={5000} />
      <div className="layout-columns">
        <div className="panel-left">
          <ScanParamsPanel
            entityType={entityType}
            entityValue={entityValue}
            combinedEntityOptions={combinedEntityOptions}
            onEntityChange={handleEntityChange}
            userQuery={userQuery}
            onUserQueryChange={setUserQuery}
            revision={revision}
            onRevisionChange={setRevision}
            revisionHints={revisionHints}
            revisionLoading={baselinesLoading}
            sort={sort}
            onSortChange={setSort}
            limit={limit}
            onLimitChange={setLimit}
            timeout={timeout}
            onTimeoutChange={setTimeout}
            hideValid={hideValid}
            onHideValidChange={setHideValid}
            onEnterKey={() => void handleScan()}
          />

          <div className="actions">
            <button
              className="btn btn-scan"
              onClick={handleScan}
              disabled={scanning || batchRepairing || selectedRepairers.length === 0}
              title={selectedRepairers.length === 0 ? 'Please select at least one repairer' : ''}
            >
              {scanning ? 'Scanning...' : 'Scan'}
            </button>
            {result && (
              <button
                className="btn btn-repair"
                onClick={handleBatchRepair}
                disabled={!hasAnySelection || batchRepairing || scanning}
                title={!hasAnySelection && !batchRepairing ? 'Please select at least one item to be repaired' : ''}
              >
                {batchRepairing
                  ? 'Repairing...'
                  : hasAnySelection
                    ? `Repair (issues: ${[...selectedIssues.values()].reduce((sum, s) => sum + s.size, 0)})`
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
              repairers={repairers}
              selectedIssues={selectedIssues}
              expandedRows={expandedRows}
              repairingEntity={repairingEntity}
              batchRepairing={batchRepairing}
              onToggleEntitySelection={toggleEntitySelection}
              onToggleCollectionSelection={toggleCollectionSelection}
              onToggleIssueSelection={toggleIssueSelection}
              onToggleExpanded={toggleExpanded}
              onToggleSelectAll={toggleSelectAll}
              onExpandAll={(keys) => setExpandedRows(new Set(keys))}
              onCollapseAll={() => setExpandedRows(new Set())}
              allItemsSelected={allItemsSelected}
              someItemsSelected={someItemsSelected}
            />
          )}
        </div>
      </div>
    </div>
  );
}
