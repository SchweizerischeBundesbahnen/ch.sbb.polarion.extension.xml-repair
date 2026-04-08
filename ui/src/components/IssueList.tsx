import type { Issue, Repairer } from '../types';

interface IssueListProps {
  issues: Issue[];
  selected: Set<number>;
  repairers: Repairer[];
  onToggle: (index: number) => void;
  disabled: boolean;
  className?: string;
}

export default function IssueList({ issues, selected, repairers, onToggle, disabled, className }: IssueListProps) {
  return (
    <ul className={`issue-list${className ? ` ${className}` : ''}`}>
      {issues.map((issue, i) => (
        <li
          key={i}
          className={`issue-item${issue.repairResult?.success ? ' issue-success' : ''}${issue.repairResult && !issue.repairResult.success ? ' issue-failed' : ''}`}
        >
          {issue.repairResult?.success ? (
            <span className="issue-status-icon success">&#10003;</span>
          ) : (
            <input type="checkbox" checked={selected.has(i)} onChange={() => onToggle(i)} disabled={disabled} />
          )}
          <span>
            <strong>{repairers.find((r) => r.id === issue.repairer)?.name || issue.repairer}</strong>:{' '}
            {issue.description}
          </span>
          {issue.repairResult && !issue.repairResult.success && !(issue.repairResult.warnings?.length > 0) && (
            <ul className="issue-warnings">
              <li>Repair failed. Please try one more time or contact to administrator</li>
            </ul>
          )}
          {(issue.repairResult?.warnings?.length ?? 0) > 0 && (
            <ul className="issue-warnings">
              {issue.repairResult!.warnings.map((w, wi) => (
                <li key={wi}>{w}</li>
              ))}
            </ul>
          )}
        </li>
      ))}
    </ul>
  );
}
