import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';
import type { NumericInputHint } from '../components/NumericInput';
import type {
  BaselineInfo,
  EntityInfo,
  EntitySubtype,
  EntityType,
  FilterMode,
  IconSelectOption,
  ScanParams,
} from '../types';
import { getCookie as getRawCookie, setCookie as setRawCookie } from './cookies';
import { entityKey, entityKeyToRef } from './scanEntities';
import type { SendRequest } from './useRemote';

export const ENTITY_TYPE_OPTIONS: IconSelectOption[] = [
  { id: 'WORKITEM', name: 'Work Items', iconURL: '/polarion/ria/images/topicIconsSmallDark/workItems.svg' },
  { id: 'DOCUMENT', name: 'Documents', iconURL: '/polarion/ria/images/topicIconsSmallDark/document-line.svg' },
  { id: 'COLLECTION', name: 'Collections', iconURL: '/polarion/ria/images/topicIconsSmallDark/collectionsTopic.svg' },
];

// Entity types whose entities can be picked from a dropdown. Work items are excluded on purpose: a
// project holds far too many of them, so they stay query-only.
export const SELECTABLE_ENTITY_TYPES: EntityType[] = ['DOCUMENT', 'COLLECTION'];

const TYPE_ENDPOINTS: Record<string, string> = {
  WORKITEM: '/work-item-types',
  DOCUMENT: '/document-types',
};

/**
 * Everything the left-hand ScanParamsPanel drives: what to scan, how to filter it, and the advanced limits.
 * Shared by the Scan & Repair and the Purge pages, which submit the same `ScanParams` to the same endpoint and
 * differ only in which repairers they ask for.
 *
 * Each page passes its own `cookiePrefix`, so remembering "documents of type X" on one page does not silently
 * change what the other one would scan.
 *
 * What happens when the entity type changes is deliberately NOT handled here: the pages invalidate their own
 * results there, and Scan & Repair also reloads its repairer list. They do it through `entityType`,
 * `subtypeSetExplicitly` and `setEntitySubtype`.
 */
interface ScanParamsOptions {
  /** What "Show items with issues only" starts as before the user ever touched it on this page. */
  defaultHideValid?: boolean;
  /**
   * Whether the page can scan a revision or baseline. False drops the row from the parameters panel, skips
   * loading the project's baselines, and pins the submitted revision to HEAD - for a page whose only purpose
   * is writing, since the backend refuses to write anything resolved at a revision.
   */
  supportsRevision?: boolean;
}

export default function useScanParams(
  cookiePrefix: string,
  sendRequest: SendRequest,
  { defaultHideValid = false, supportsRevision = true }: ScanParamsOptions = {},
) {
  const getCookie = useCallback((key: string): string | null => getRawCookie(cookiePrefix + key), [cookiePrefix]);
  const setCookie = useCallback(
    (key: string, value: string): void => setRawCookie(cookiePrefix + key, value),
    [cookiePrefix],
  );

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
  // True once a load succeeded for the current entity type and subtype, so `entities` can be trusted as
  // the complete list - which is what lets an empty list prune a stale selection instead of ignoring it.
  const [entitiesLoaded, setEntitiesLoaded] = useState(false);
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
  const [hideValid, setHideValid] = useState(() => {
    // An absent cookie means the user never chose on this page, so the page's own default applies.
    const saved = getCookie('hideValid');
    return saved === null ? defaultHideValid : saved === 'true';
  });

  const [allSubtypes, setAllSubtypes] = useState<Record<string, EntitySubtype[]>>({});
  const [entitySubtype, setEntitySubtype] = useState(() => getCookie('entitySubtype') || '');

  const subtypeSetExplicitly = useRef(false);

  // Persist user preferences to cookies
  useEffect(() => {
    setCookie('entityType', entityType);
  }, [entityType, setCookie]);
  useEffect(() => {
    setCookie('entitySubtype', entitySubtype);
  }, [entitySubtype, setCookie]);
  useEffect(() => {
    setCookie('filterMode', filterMode);
  }, [filterMode, setCookie]);
  useEffect(() => {
    setCookie('userQuery', userQuery);
  }, [userQuery, setCookie]);
  useEffect(() => {
    setCookie('selectedEntities', selectedEntities.join(','));
  }, [selectedEntities, setCookie]);
  useEffect(() => {
    setCookie('revision', revision ? String(revision) : '');
  }, [revision, setCookie]);
  useEffect(() => {
    setCookie('sort', sort);
  }, [sort, setCookie]);
  useEffect(() => {
    setCookie('limit', String(limit));
  }, [limit, setCookie]);
  useEffect(() => {
    setCookie('timeout', String(timeout));
  }, [timeout, setCookie]);
  useEffect(() => {
    setCookie('hideValid', String(hideValid));
  }, [hideValid, setCookie]);

  useEffect(() => {
    if (!projectId) {
      setAllSubtypes({});
      return;
    }

    const loadAllSubtypes = async () => {
      const result: Record<string, EntitySubtype[]> = {};
      await Promise.all(
        Object.entries(TYPE_ENDPOINTS).map(async ([type, endpoint]) => {
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
      setEntitiesLoaded(false);
      return;
    }
    let cancelled = false;
    const loadEntities = async () => {
      setEntitiesLoading(true);
      setEntitiesLoaded(false);
      try {
        const params = new URLSearchParams({ projectId, entityType });
        if (entitySubtype) {
          params.set('entitySubtype', entitySubtype);
        }
        const response = await sendRequest({ method: 'GET', url: `/entities?${params.toString()}` });
        if (cancelled) return;
        if (response.ok) {
          setEntities(await response.json());
          // Marks the list authoritative for the current type and subtype, including when it came back
          // empty. A failed load leaves it false, because then we do not know what the project holds.
          setEntitiesLoaded(true);
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

  // Drop selected entities the loaded list does not offer - a cookie restored from another project, or
  // documents of a subtype that is no longer selected. Runs only once the list is authoritative, so a
  // pending load, a failed one, or a switch to work items never wipes the selection. An authoritative
  // empty list does prune: a project or subtype with no entities at all must not keep submitting keys
  // the picker cannot even show.
  useEffect(() => {
    if (!entitiesLoaded || selectedEntities.length === 0) {
      return;
    }
    const known = new Set(entities.map(entityKey));
    const pruned = selectedEntities.filter((key) => known.has(key));
    if (pruned.length !== selectedEntities.length) {
      setSelectedEntities(pruned);
    }
  }, [entities, entitiesLoaded, selectedEntities]);

  // The baselines only feed the revision row's hints, so a page without that row does not ask for them.
  useEffect(() => {
    if (!projectId || !supportsRevision) {
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
  }, [projectId, sendRequest, supportsRevision]);

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

  // A selection cannot be scanned while its list is still loading: until the list arrives the remembered
  // keys have not been checked against the current type and subtype, so a scan started now could submit
  // entities of the previous one. Blocking the button for that moment is enough - the prune runs as soon
  // as the list is there. A failed load deliberately does NOT block: nothing then says the remembered
  // selection is wrong, and the backend resolves each entity directly, so the user can still work.
  const selectionPending = selectionActive && entitiesLoading;

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

  /**
   * Switching the entity type starts the entity selection from scratch, because a key of one type means
   * nothing for another. A subtype switch keeps it - the prune effect drops whatever the new list omits.
   */
  const handleEntityChange = (value: string) => {
    const parts = value.split('::');
    if (parts[1]) subtypeSetExplicitly.current = true;
    const nextEntityType = parts[0] as EntityType;
    if (nextEntityType !== entityType) {
      setSelectedEntities([]);
    }
    setEntityType(nextEntityType);
    setEntitySubtype(parts[1] || '');
  };

  /**
   * The scan request body for the given repairers. Both pages POST the same shape to /scan and differ only in
   * which repairers they ask for, so the mapping from the form to the request lives here once.
   */
  const buildScanParams = (repairers: string[], configs: Record<string, Record<string, boolean>> = {}): ScanParams => ({
    projectId,
    entityType,
    entitySubtype: entitySubtype || null,
    // The two filters are mutually exclusive in the UI, so exactly one of them reaches the backend.
    userQuery: selectionActive ? null : userQuery || null,
    entities: selectionActive && selectedEntities.length > 0 ? selectedEntities.map(entityKeyToRef) : null,
    revision: !supportsRevision || entityType === 'COLLECTION' || !revision ? null : String(revision),
    sort: sort || null,
    limit,
    repairers,
    timeout: timeout * 1000,
    hideValid,
    configs,
  });

  /**
   * Everything ScanParamsPanel needs except the two callbacks a page owns: `onEntityChange`, because each page
   * invalidates its own result there, and `onEnterKey`, which starts that page's scan.
   */
  const panelProps = {
    entityType,
    entityValue,
    combinedEntityOptions,
    filterMode,
    onFilterModeChange: setFilterMode,
    entityOptions,
    entitiesLoading,
    selectedEntities,
    onSelectedEntitiesChange: setSelectedEntities,
    userQuery,
    onUserQueryChange: setUserQuery,
    showRevision: supportsRevision,
    revision,
    onRevisionChange: setRevision,
    revisionHints,
    revisionLoading: baselinesLoading,
    sort,
    onSortChange: setSort,
    limit,
    onLimitChange: setLimit,
    timeout,
    onTimeoutChange: setTimeout,
    hideValid,
    onHideValidChange: setHideValid,
  };

  return {
    panelProps,
    buildScanParams,
    projectId,
    entityType,
    entitySubtype,
    setEntitySubtype,
    subtypeSetExplicitly,
    entityValue,
    combinedEntityOptions,
    handleEntityChange,
    filterMode,
    setFilterMode,
    entityOptions,
    entitiesLoading,
    selectedEntities,
    setSelectedEntities,
    userQuery,
    setUserQuery,
    revision,
    setRevision,
    revisionHints,
    baselinesLoading,
    sort,
    setSort,
    limit,
    setLimit,
    timeout,
    setTimeout,
    hideValid,
    setHideValid,
    selectionActive,
    selectionPending,
  };
}
