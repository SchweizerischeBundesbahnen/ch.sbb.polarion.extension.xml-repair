import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { PageLayout } from '@grigoriev/react-sbb-polarion';
import { toast } from 'sonner';
import type { NumericInputHint } from '../components/NumericInput';
import RepairersPanel from '../components/RepairersPanel';
import ResultsTable from '../components/ResultsTable';
import ScanParamsPanel from '../components/ScanParamsPanel';
import { getCookie as getRawCookie, setCookie as setRawCookie } from '../services/cookies';
import useRemote from '../services/useRemote';
import type {
  BaselineInfo,
  EntityInfo,
  EntityRef,
  EntitySubtype,
  EntityType,
  FilterMode,
  IconSelectOption,
  Issue,
  RepairIssueResult,
  RepairParams,
  RepairResult,
  Repairer,
  ScanEntity,
  ScanParams,
  ScanResult,
} from '../types';

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

const ENTITY_TYPE_OPTIONS: IconSelectOption[] = [
  { id: 'WORKITEM', name: 'Work Items', iconURL: '/polarion/ria/images/topicIconsSmallDark/workItems.svg' },
  { id: 'DOCUMENT', name: 'Documents', iconURL: '/polarion/ria/images/topicIconsSmallDark/document-line.svg' },
  { id: 'COLLECTION', name: 'Collections', iconURL: '/polarion/ria/images/topicIconsSmallDark/collectionsTopic.svg' },
];

// Entity types whose entities can be picked from a dropdown. Work items are excluded on purpose: a
// project holds far too many of them, so they stay query-only.
const SELECTABLE_ENTITY_TYPES: EntityType[] = ['DOCUMENT', 'COLLECTION'];

// One selected entity as a single dropdown option value. A document is identified by space + module
// name, a collection by its id alone; module names cannot contain '/', so the last separator splits the
// key back apart even for a nested space like "Specification/Sub".
const entityKey = (entity: EntityInfo): string => (entity.space ? `${entity.space}/${entity.id}` : entity.id);

const entityKeyToRef = (key: string): EntityRef => {
  const separator = key.lastIndexOf('/');
  return separator < 0 ? { space: null, id: key } : { space: key.slice(0, separator), id: key.slice(separator + 1) };
};

const hasSubitems = (item: ScanEntity): boolean => item.subitems && item.subitems.length > 0;
const itemKey = (item: ScanEntity): string => `${item.projectId}-${item.space || ''}-${item.entityId}`;
const subitemKey = (parentKey: string, sub: ScanEntity): string =>
  `${parentKey}/${sub.projectId}-${sub.space || ''}-${sub.entityId}`;

export default function Repair() {
  const { sendRequest } = useRemote();

  const [entityType, setEntityType] = useState<EntityType>(() => {
    const saved = getCookie('entityType');
    return ENTITY_TYPE_OPTIONS.some((o) => o.id === saved) ? (saved as EntityType) : 'WORKITEM';
  });
  const [projectId] = useState(() => String(new URLSearchParams(window.location.search).get('projectId') || ''));
  const [filterMode, setFilterMode] = useState<FilterMode>(() =>
    getCookie('filterMode') === 'QUERY' ? 'QUERY' : 'SELECTION',
  );
  const [userQuery, setUserQuery] = useState(() => String(getCookie('userQuery') || ''));
  const [entities, setEntities] = useState<EntityInfo[]>([]);
  const [entitiesLoading, setEntitiesLoading] = useState(false);
  const [selectedEntities, setSelectedEntities] = useState<string[]>(() =>
    (getCookie('selectedEntities') || '').split(',').filter(Boolean),
  );
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
  const [resultHideValid, setResultHideValid] = useState(false);
  const [hiddenRepairers, setHiddenRepairers] = useState<Set<string>>(new Set());
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
    setCookie('filterMode', filterMode);
  }, [filterMode]);
  useEffect(() => {
    setCookie('userQuery', userQuery);
  }, [userQuery]);
  useEffect(() => {
    setCookie('selectedEntities', selectedEntities.join(','));
  }, [selectedEntities]);
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

    const typeEndpoints = {
      WORKITEM: '/work-item-types',
      DOCUMENT: '/document-types',
    };

    const loadAllSubtypes = async () => {
      const result: Record<string, EntitySubtype[]> = {};
      await Promise.all(
        Object.entries(typeEndpoints).map(async ([type, endpoint]) => {
          try {
            const response = await sendRequest({
              method: 'GET',
              url: `${endpoint}?projectId=${encodeURIComponent(projectId)}`,
            });
            if (response.ok) {
              result[type] = await response.json();
            }
          } catch {
            toast.error(`Failed to load ${type.toLowerCase()} subtypes`);
          }
        }),
      );
      setAllSubtypes(result);
    };
    void loadAllSubtypes();
  }, [projectId, sendRequest]);

  // Validate saved entitySubtype once subtypes are loaded
  useEffect(() => {
    if (entitySubtype && Object.keys(allSubtypes).length > 0) {
      const subs = allSubtypes[entityType] || [];
      if (!subs.some((s) => s.id === entitySubtype)) {
        setEntitySubtype('');
      }
    }
  }, [allSubtypes, entityType, entitySubtype]);

  // The entities the user can pick instead of typing a query. Reloaded per entity type and subtype, so
  // the list always matches what the Entity Type row selects.
  useEffect(() => {
    if (!projectId || !SELECTABLE_ENTITY_TYPES.includes(entityType)) {
      setEntities([]);
      return;
    }
    let cancelled = false;
    const loadEntities = async () => {
      setEntitiesLoading(true);
      try {
        const params = new URLSearchParams({ projectId, entityType });
        if (entitySubtype) {
          params.set('entitySubtype', entitySubtype);
        }
        const response = await sendRequest({ method: 'GET', url: `/entities?${params.toString()}` });
        if (cancelled) return;
        if (response.ok) {
          setEntities(await response.json());
        } else {
          setEntities([]);
          toast.error('Failed to load the entity list');
        }
      } catch {
        if (!cancelled) {
          setEntities([]);
          toast.error('Failed to load the entity list');
        }
      } finally {
        if (!cancelled) {
          setEntitiesLoading(false);
        }
      }
    };
    void loadEntities();
    return () => {
      // A later entity type/subtype switch must win over an in-flight response of the previous one.
      cancelled = true;
    };
  }, [projectId, entityType, entitySubtype, sendRequest]);

  // Drop selected entities the loaded list no longer offers - a cookie restored from another project, or
  // documents of a subtype that is no longer selected. Skipped while the list is unavailable, so a
  // pending load or a switch to work items doesn't wipe the selection.
  useEffect(() => {
    if (entitiesLoading || entities.length === 0 || selectedEntities.length === 0) {
      return;
    }
    const known = new Set(entities.map(entityKey));
    const pruned = selectedEntities.filter((key) => known.has(key));
    if (pruned.length !== selectedEntities.length) {
      setSelectedEntities(pruned);
    }
  }, [entities, entitiesLoading, selectedEntities]);

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

  // Any change of what would be scanned invalidates the displayed result.
  useEffect(() => {
    setResult(null);
    setError(null);
  }, [selectedRepairers, selectedEntities, filterMode]);

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

  // Selection is offered for documents and collections only; work items ignore the mode and stay on the
  // query field.
  const selectionActive = SELECTABLE_ENTITY_TYPES.includes(entityType) && filterMode === 'SELECTION';

  const entityOptions = useMemo((): IconSelectOption[] => {
    // Each entity carries its type id; the icon comes from the subtype list already loaded for the
    // Entity Type row, with the entity type's own icon as the fallback.
    const iconByType = new Map((allSubtypes[entityType] || []).map((subtype) => [subtype.id, subtype.iconURL]));
    const fallbackIcon = ENTITY_TYPE_OPTIONS.find((option) => option.id === entityType)?.iconURL;
    return entities.map((entity) => ({
      id: entityKey(entity),
      // Document names repeat across spaces, so the space stays part of the label.
      name: entity.space ? `${entity.name} (${entity.space})` : entity.name,
      iconURL: (entity.type ? iconByType.get(entity.type) : undefined) || fallbackIcon,
    }));
  }, [entities, allSubtypes, entityType]);

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
    const nextEntityType = parts[0] as EntityType;
    if (nextEntityType !== entityType) {
      // Entity keys of one type mean nothing for another one, so a type switch starts from scratch. A
      // subtype switch keeps the selection - the prune effect drops whatever the new list omits.
      setSelectedEntities([]);
    }
    setEntityType(nextEntityType);
    setEntitySubtype(parts[1] || '');
    setResult(null);
    setError(null);
  };

  const toggleEntitySelection = (entityKey: string, indices: number[]) => {
    setSelectedIssues((prev) => {
      const next = new Map(prev);
      const current = next.get(entityKey) || new Set();
      const allSelected = indices.length > 0 && indices.every((i) => current.has(i));
      if (allSelected) {
        next.delete(entityKey);
      } else {
        next.set(entityKey, new Set(indices));
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

  const toggleRepairerHidden = (id: string) => {
    const next = new Set(hiddenRepairers);
    if (next.has(id)) next.delete(id);
    else next.add(id);

    setHiddenRepairers(next);
    if (!result) return;

    const hiddenIndicesByKey = new Map<string, Set<number>>();
    const collect = (entity: ScanEntity, key: string) => {
      const hiddenIdx = new Set<number>();
      entity.issues.forEach((iss, i) => {
        if (next.has(iss.repairer)) hiddenIdx.add(i);
      });
      if (hiddenIdx.size > 0) hiddenIndicesByKey.set(key, hiddenIdx);
    };
    for (const item of result.items) {
      if (hasSubitems(item)) {
        const parentKey = itemKey(item);
        for (const sub of item.subitems) {
          if (sub.issues.length > 0) collect(sub, subitemKey(parentKey, sub));
        }
      } else if (item.issues.length > 0) {
        collect(item, itemKey(item));
      }
    }

    setSelectedIssues((sel) => {
      const updated = new Map(sel);
      let changed = false;
      for (const [key, selected] of sel) {
        const hiddenIdx = hiddenIndicesByKey.get(key);
        if (!hiddenIdx) continue;
        const filtered = new Set<number>();
        selected.forEach((i) => {
          if (!hiddenIdx.has(i)) filtered.add(i);
        });
        if (filtered.size !== selected.size) {
          changed = true;
          if (filtered.size === 0) updated.delete(key);
          else updated.set(key, filtered);
        }
      }
      return changed ? updated : sel;
    });
  };

  const visibleIssueIndices = (entity: ScanEntity, hidden: Set<string>): number[] => {
    const out: number[] = [];
    entity.issues.forEach((iss, i) => {
      if (!hidden.has(iss.repairer)) out.push(i);
    });
    return out;
  };

  const collectSelectableKeys = (items: ScanEntity[]) => {
    const keys: { key: string; indices: number[] }[] = [];
    for (const item of items) {
      if (hasSubitems(item)) {
        const parentKey = itemKey(item);
        for (const sub of item.subitems) {
          if (sub.issues.length > 0 && !sub.repaired && !sub.revision) {
            const indices = visibleIssueIndices(sub, hiddenRepairers);
            if (indices.length > 0) keys.push({ key: subitemKey(parentKey, sub), indices });
          }
        }
      } else if (item.issues.length > 0 && !item.repaired && !item.revision) {
        const indices = visibleIssueIndices(item, hiddenRepairers);
        if (indices.length > 0) keys.push({ key: itemKey(item), indices });
      }
    }
    return keys;
  };

  const selectableKeys = result ? collectSelectableKeys(result.items) : [];
  const allItemsSelected =
    selectableKeys.length > 0 &&
    selectableKeys.every(({ key, indices }) => {
      const sel = selectedIssues.get(key);
      return !!sel && indices.every((i) => sel.has(i));
    });
  const someItemsSelected = hasAnySelection && !allItemsSelected;

  const toggleSelectAll = () => {
    if (allItemsSelected) {
      setSelectedIssues(new Map());
    } else {
      const next = new Map<string, Set<number>>();
      selectableKeys.forEach(({ key, indices }) => {
        next.set(key, new Set(indices));
      });
      setSelectedIssues(next);
    }
  };

  const toggleCollectionSelection = (item: ScanEntity) => {
    const parentKey = itemKey(item);
    const subs = item.subitems
      .filter((sub) => sub.issues.length > 0 && !sub.repaired && !sub.revision)
      .map((sub) => ({ sub, indices: visibleIssueIndices(sub, hiddenRepairers) }))
      .filter(({ indices }) => indices.length > 0);
    const allSubsSelected =
      subs.length > 0 &&
      subs.every(({ sub, indices }) => {
        const sel = selectedIssues.get(subitemKey(parentKey, sub));
        return !!sel && indices.every((i) => sel.has(i));
      });
    setSelectedIssues((prev) => {
      const next = new Map(prev);
      if (allSubsSelected) {
        subs.forEach(({ sub }) => next.delete(subitemKey(parentKey, sub)));
      } else {
        subs.forEach(({ sub, indices }) => {
          next.set(subitemKey(parentKey, sub), new Set(indices));
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
      // The two filters are mutually exclusive in the UI, so exactly one of them reaches the backend.
      userQuery: selectionActive ? null : userQuery || null,
      entities: selectionActive && selectedEntities.length > 0 ? selectedEntities.map(entityKeyToRef) : null,
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
        setResultHideValid(hideValid);
        setHiddenRepairers(new Set());
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
    // No title: Scan & Repair is the primary product surface, not an admin page, so it carries the
    // dev-only Overview back link (via PageLayout) but not the admin-style heading + underline.
    <PageLayout>
      <div className="xml-repair-app">
        <div className="layout-columns">
          <div className="panel-left">
            <ScanParamsPanel
              entityType={entityType}
              entityValue={entityValue}
              combinedEntityOptions={combinedEntityOptions}
              onEntityChange={handleEntityChange}
              filterMode={filterMode}
              onFilterModeChange={setFilterMode}
              entityOptions={entityOptions}
              entitiesLoading={entitiesLoading}
              selectedEntities={selectedEntities}
              onSelectedEntitiesChange={setSelectedEntities}
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
                hideValidAtScanTime={resultHideValid}
                hiddenRepairers={hiddenRepairers}
                onToggleRepairer={toggleRepairerHidden}
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
    </PageLayout>
  );
}
