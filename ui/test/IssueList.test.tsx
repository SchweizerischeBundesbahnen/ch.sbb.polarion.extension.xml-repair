import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import IssueList from '../src/components/IssueList';
import type { Issue, Repairer } from '../src/types';

// IssueList is a pure presentational list; it is exercised end-to-end through the Repair page, but a few
// branches (the hidden-repairer filter and the unknown-repairer name fallback) are simplest to pin down
// by rendering it directly with crafted props.

const repairerMeta = (id: string, name: string): Repairer => ({ id, name, description: '', configs: [] });

const makeIssue = (repairer: string, overrides: Partial<Issue> = {}): Issue => ({
  metaInfo: `${repairer}-meta`,
  repairer,
  description: `${repairer} description`,
  warnings: [],
  ...overrides,
});

afterEach(cleanup);

describe('IssueList', () => {
  it('hides issues whose repairer is in hiddenRepairers', async () => {
    render(
      <IssueList
        issues={[makeIssue('shown'), makeIssue('hidden')]}
        selected={new Set()}
        repairers={[repairerMeta('shown', 'Shown Repairer'), repairerMeta('hidden', 'Hidden Repairer')]}
        hiddenRepairers={new Set(['hidden'])}
        onToggle={() => {}}
        disabled={false}
      />,
    );

    await vi.waitFor(() => expect(document.querySelectorAll('.issue-item')).toHaveLength(1));
    expect(document.body.textContent).toContain('Shown Repairer');
    expect(document.body.textContent).not.toContain('Hidden Repairer');
  });

  it('falls back to the raw repairer id when it is not among the known repairers', async () => {
    render(
      <IssueList
        issues={[makeIssue('unknown-repairer')]}
        selected={new Set()}
        repairers={[]}
        onToggle={() => {}}
        disabled={false}
      />,
    );

    await vi.waitFor(() => expect(document.body.textContent).toContain('unknown-repairer'));
  });

  it('shows the generic failure line when a repair failed without warnings', async () => {
    render(
      <IssueList
        issues={[makeIssue('r', { repairResult: { success: false, warnings: [] } })]}
        selected={new Set()}
        repairers={[repairerMeta('r', 'R')]}
        onToggle={() => {}}
        disabled={false}
      />,
    );

    await vi.waitFor(() => expect(document.querySelector('.issue-item')).not.toBeNull());
    expect(document.querySelector('.issue-item')?.className).toContain('issue-failed');
    expect(document.body.textContent).toContain('Repair failed');
  });
});
