import type { Issue, Repairer } from '../types';

interface IssueListProps {
  issues: Issue[];
  selected: Set<number>;
  repairers: Repairer[];
  hiddenRepairers?: Set<string>;
  onToggle: (index: number) => void;
  disabled: boolean;
  disabledTitle?: string;
  className?: string;
}

export default function IssueList({
  issues,
  selected,
  repairers,
  hiddenRepairers,
  onToggle,
  disabled,
  disabledTitle,
  className,
}: IssueListProps) {
  return (
    <ul className={`issue-list${className ? ` ${className}` : ''}`}>
      {issues.map((issue, i) => {
        if (hiddenRepairers?.has(issue.repairer)) return null;
        return (
          <li
            key={i}
            className={`issue-item${issue.repairResult?.success ? ' issue-success' : ''}${issue.repairResult && !issue.repairResult.success ? ' issue-failed' : ''}`}
          >
            {issue.repairResult?.success ? (
              <span className="issue-status-icon success">&#10003;</span>
            ) : (
              <input
                type="checkbox"
                checked={selected.has(i)}
                onChange={() => onToggle(i)}
                disabled={disabled}
                title={disabled ? disabledTitle : undefined}
              />
            )}
            <span>
              <strong>{repairers.find((r) => r.id === issue.repairer)?.name || issue.repairer}</strong>:{' '}
              {issue.description}
            </span>
            {issue.repairResult && !issue.repairResult.success && !(issue.repairResult.warnings?.length > 0) && (
              <ul className="issue-warnings">
                <li>Repair failed. Please try one more time or contact the administrator</li>
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
        );
      })}
    </ul>
  );
}
