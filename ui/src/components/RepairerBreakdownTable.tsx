interface RepairerBreakdownRow {
  id: string;
  name: string;
  count: number;
}

interface RepairerBreakdownTableProps {
  rows: RepairerBreakdownRow[];
  hiddenRepairers: Set<string>;
  onToggleRepairer: (id: string) => void;
}

export default function RepairerBreakdownTable({ rows, hiddenRepairers, onToggleRepairer }: RepairerBreakdownTableProps) {
  return (
    <table className="breakdown-table">
      <thead>
        <tr>
          <th>Repairer</th>
          <th>Issues</th>
          <th>Filter</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => {
          const hidden = hiddenRepairers.has(r.id);
          return (
            <tr key={r.id} className={hidden ? 'repairer-hidden' : undefined}>
              <td>{r.name}</td>
              <td>{r.count}</td>
              <td>
                <a
                  href="#"
                  className="breakdown-filter"
                  onClick={(e) => {
                    e.preventDefault();
                    onToggleRepairer(r.id);
                  }}
                >
                  ({hidden ? 'show' : 'hide'})
                </a>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
