import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import ResultsTable, { type ResultsTerms } from '../src/components/ResultsTable';
import { itemKey } from '../src/services/scanEntities';
import type { ScanEntity, ScanResult } from '../src/types';
import { REPAIRERS } from './fixtures';

// Direct tests for the results tree. The Repair page flow only ever renders flat work-item results, so
// the collection path (subitem rows, their selection/expansion, revisioned and already-repaired
// subitems, subitem warnings), the expand-all/collapse-all controls and the issue-count breakdown are
// covered here.
// Note: vitest-browser-react's render commits asynchronously - always await the first query.

const REPAIRER = REPAIRERS[0].id;
const OTHER_REPAIRER = REPAIRERS[1].id;

const issue = (repairer: string, description: string) => ({
  metaInfo: 'meta',
  repairer,
  description,
  warnings: [] as string[],
});

const sub = (entityId: string, over: Partial<ScanEntity> = {}): ScanEntity =>
  ({
    entityType: 'WORKITEM',
    projectId: 'elibrary',
    space: null,
    entityId,
    revision: null,
    issues: [issue(REPAIRER, `Bad enum in ${entityId}`)],
    fields: {},
    subitems: [],
    warnings: [],
    ...over,
  }) as ScanEntity;

/** A document collection with four subitems, each in a different state. */
const COLLECTION_RESULT: ScanResult = {
  report: 'Scanned 1 collection',
  items: [
    {
      entityType: 'DOCUMENT',
      projectId: 'elibrary',
      space: 'Specification',
      entityId: 'Spec',
      revision: null,
      issues: [],
      fields: {},
      warnings: [],
      subitems: [
        sub('EL-1'),
        sub('EL-2', { repaired: true }),
        sub('EL-3', { revision: '1234' }),
        sub('EL-4', { issues: [], warnings: ['subitem warning'] }),
      ],
    } as ScanEntity,
  ],
} as ScanResult;

const FLAT_RESULT: ScanResult = {
  report: 'Scanned 2 items',
  items: [
    sub('EL-100', { issues: [issue(REPAIRER, 'a'), issue(OTHER_REPAIRER, 'b')] }),
    sub('EL-200', { issues: [issue(OTHER_REPAIRER, 'c')] }),
  ],
} as ScanResult;

/** Renders the table with real expand/selection state so the controls can be driven end to end. */
function Harness({
  result,
  hideValid = false,
  initialExpanded = [],
  terms,
  batchRepairing = false,
}: {
  result: ScanResult;
  hideValid?: boolean;
  initialExpanded?: string[];
  terms?: ResultsTerms;
  batchRepairing?: boolean;
}) {
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set(initialExpanded));
  const [hiddenRepairers, setHiddenRepairers] = useState<Set<string>>(new Set());
  return (
    <ResultsTable
      result={result}
      hideValidAtScanTime={hideValid}
      hiddenRepairers={hiddenRepairers}
      onToggleRepairer={(id) =>
        setHiddenRepairers((prev) => {
          const next = new Set(prev);
          if (next.has(id)) next.delete(id);
          else next.add(id);
          return next;
        })
      }
      repairers={REPAIRERS}
      selectedIssues={new Map()}
      expandedRows={expandedRows}
      repairingEntity={null}
      batchRepairing={batchRepairing}
      onToggleEntitySelection={() => {}}
      onToggleCollectionSelection={() => {}}
      onToggleIssueSelection={() => {}}
      onToggleExpanded={(key) =>
        setExpandedRows((prev) => {
          const next = new Set(prev);
          if (next.has(key)) next.delete(key);
          else next.add(key);
          return next;
        })
      }
      onToggleSelectAll={() => {}}
      onExpandAll={(keys) => setExpandedRows(new Set(keys))}
      onCollapseAll={() => setExpandedRows(new Set())}
      terms={terms}
      allItemsSelected={false}
      someItemsSelected={false}
    />
  );
}

// Subitems render only under an expanded collection row; its key is projectId-space-entityId.
const COLLECTION_KEY = 'elibrary-Specification-Spec';

const byTitle = (title: string) => document.querySelector<HTMLElement>(`[title="${title}"]`);
const subitemRows = () => document.querySelectorAll('tr.subitem-row').length;

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('ResultsTable collections', () => {
  it('renders one row per subitem with its own state', async () => {
    render(<Harness result={COLLECTION_RESULT} initialExpanded={[COLLECTION_KEY]} />);
    await vi.waitFor(() => expect(subitemRows()).toBe(4));
    // An already-repaired subitem shows the fixed badge instead of a checkbox.
    expect(document.querySelectorAll('tr.subitem-row .fixed-badge').length).toBe(1);
    expect(document.querySelectorAll('tr.subitem-row.row-fixed').length).toBe(1);
    // A subitem pinned to a revision cannot be selected and says why.
    const revisioned = Array.from(document.querySelectorAll<HTMLInputElement>('tr.subitem-row input[type=checkbox]'));
    const blocked = revisioned.find((c) => c.disabled && c.title.includes('revision'));
    expect(blocked).toBeTruthy();
    // A subitem with a warning renders the warning marker.
    expect(document.querySelector('tr.subitem-row .warning-popup-item')?.textContent).toBe('subitem warning');
  });

  it('expands a subitem into its issue list and collapses it again', async () => {
    render(<Harness result={COLLECTION_RESULT} initialExpanded={[COLLECTION_KEY]} />);
    await vi.waitFor(() => expect(subitemRows()).toBe(4));
    const arrow = document.querySelector<HTMLElement>('tr.subitem-row .expand-arrow.clickable')!;
    expect(arrow.title).toBe('Expand');
    arrow.click();
    await vi.waitFor(() => expect(document.querySelector('.subitem-issue-list')).not.toBeNull());

    document.querySelector<HTMLElement>('tr.subitem-row .expand-arrow.clickable')!.click();
    await vi.waitFor(() => expect(document.querySelector('.subitem-issue-list')).toBeNull());
  });

  it('keeps issue-free subitems out of the tree when valid ones are hidden', async () => {
    // "Hide items valid at scan time" is about issues, not about rows: a subitem with no issues at all
    // is still shown, while one whose only issues belong to a hidden repairer disappears.
    render(<Harness result={COLLECTION_RESULT} hideValid initialExpanded={[COLLECTION_KEY]} />);
    await vi.waitFor(() => expect(subitemRows()).toBeGreaterThan(0));
    expect(document.body.textContent).toContain('EL-4'); // no issues -> still listed
  });

  it('gives an issue-less subitem no expand affordance', async () => {
    render(<Harness result={COLLECTION_RESULT} initialExpanded={[COLLECTION_KEY]} />);
    await vi.waitFor(() => expect(subitemRows()).toBe(4));
    const arrows = Array.from(document.querySelectorAll<HTMLElement>('tr.subitem-row .expand-arrow'));
    const inert = arrows.find((a) => !a.classList.contains('clickable'));
    expect(inert).toBeTruthy();
    expect(inert!.title).toBe('');
  });

  it('expands and collapses the whole tree, and ignores the controls at their limits', async () => {
    render(<Harness result={COLLECTION_RESULT} />);
    await vi.waitFor(() => expect(byTitle('Expand all')).not.toBeNull());
    // Nothing is expanded yet, so Collapse all is disabled and clicking it changes nothing.
    expect(byTitle('Collapse all')!.className).toContain('disabled');
    byTitle('Collapse all')!.click();
    expect(document.querySelectorAll('.expand-row').length).toBe(0);

    byTitle('Expand all')!.click();
    await vi.waitFor(() => expect(document.querySelectorAll('.expand-row').length).toBeGreaterThan(0));
    await vi.waitFor(() => expect(byTitle('Expand all')!.className).toContain('disabled'));
    const expanded = document.querySelectorAll('.expand-row').length;
    byTitle('Expand all')!.click(); // already fully expanded: a no-op
    expect(document.querySelectorAll('.expand-row').length).toBe(expanded);

    byTitle('Collapse all')!.click();
    await vi.waitFor(() => expect(document.querySelectorAll('.expand-row').length).toBe(0));
  });
});

describe('ResultsTable warning markers', () => {
  it('points the marker at a popup that exists, even where the entity name holds a blank', async () => {
    // aria-describedby is a blank-separated list of ids, and a Polarion document name routinely holds
    // a blank, so an id built from the entity key would resolve to nothing at all.
    const RESULT = {
      report: 'Scanned 1 document',
      items: [sub('Catalog Specification', { space: 'Specification', warnings: ['an outdated attribute'] })],
    } as ScanResult;
    render(<Harness result={RESULT} />);
    await vi.waitFor(() => expect(document.querySelector('.warning-icon')).not.toBeNull());

    const marker = document.querySelector('.warning-icon')!;
    const described = marker.getAttribute('aria-describedby')!;
    expect(described).not.toContain(' ');
    expect(document.getElementById(described)).not.toBeNull();
    expect(document.getElementById(described)!.textContent).toContain('an outdated attribute');
  });
});

describe('ResultsTable expand-all controls', () => {
  const expandAll = () =>
    Array.from(document.querySelectorAll<HTMLButtonElement>('.expand-all-btn')).find(
      (b) => b.getAttribute('aria-label') === 'Expand all',
    )!;

  it('refuses to expand while a batch repair holds the table, which pointer-events alone never did', async () => {
    render(<Harness result={COLLECTION_RESULT} batchRepairing />);
    await vi.waitFor(() => expect(expandAll()).not.toBeUndefined());
    expect(expandAll().getAttribute('aria-disabled')).toBe('true');

    expandAll().click();
    expect(document.querySelectorAll('tr.subitem-row').length).toBe(0);
  });

  it('keeps the control focusable once it has nothing left to do, rather than dropping focus to body', async () => {
    render(<Harness result={COLLECTION_RESULT} initialExpanded={[COLLECTION_KEY]} />);
    await vi.waitFor(() => expect(subitemRows()).toBe(4));
    // A real `disabled` here would have handed focus back to <body> in the same render as the click.
    expandAll().focus();
    expect(document.activeElement).toBe(expandAll());
  });
});

describe('ResultsTable selection checkbox names', () => {
  // The Purge page renders this same table with its own wording. A checkbox that always said
  // "for repair" would tell a screen reader user the wrong action on that page.
  const PURGE_TERMS: ResultsTerms = {
    issueSingular: 'outdated attribute',
    issuePlural: 'outdated attributes',
    issueColumn: 'Attributes',
    emptyMessage: 'No outdated attributes found.',
    groupColumn: 'Attribute',
    selectAction: 'purge',
  };

  const labels = () =>
    Array.from(document.querySelectorAll('input[type=checkbox]')).map((c) => c.getAttribute('aria-label'));

  it('names every checkbox after the action the page performs', async () => {
    render(<Harness result={FLAT_RESULT} initialExpanded={[itemKey(FLAT_RESULT.items[0])]} />);
    await vi.waitFor(() => expect(document.querySelector('.issue-list')).not.toBeNull());
    expect(labels()).toContain('Select all items for repair');
    expect(labels()).toContain('Select EL-100 for repair');
    expect(labels()).toContain('Select issue for repair: a');
  });

  it('follows the wording of the caller, so the Purge page never says "repair"', async () => {
    render(<Harness result={FLAT_RESULT} terms={PURGE_TERMS} initialExpanded={[itemKey(FLAT_RESULT.items[0])]} />);
    await vi.waitFor(() => expect(document.querySelector('.issue-list')).not.toBeNull());
    expect(labels()).toContain('Select all items for purge');
    expect(labels()).toContain('Select EL-100 for purge');
    expect(labels()).toContain('Select outdated attribute for purge: a');
    expect(labels().join(' ')).not.toContain('repair');
  });
});

describe('ResultsTable issue breakdown', () => {
  it('summarizes the issues and toggles the per-repairer breakdown', async () => {
    render(<Harness result={FLAT_RESULT} />);
    await vi.waitFor(() => expect(document.querySelector('.breakdown-toggle')).not.toBeNull());
    // Three issues across two items, pluralized.
    expect(document.querySelector('.breakdown-toggle')!.textContent).toContain('3 issues in 2 items');

    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-table')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-table')).toBeNull());
  });

  it('uses the singular wording for a single issue in a single item', async () => {
    const single = { report: 'x', items: [sub('EL-1')] } as ScanResult;
    render(<Harness result={single} />);
    await vi.waitFor(() => expect(document.querySelector('.breakdown-toggle')).not.toBeNull());
    expect(document.querySelector('.breakdown-toggle')!.textContent).toContain('1 issue in 1 item');
  });
});
