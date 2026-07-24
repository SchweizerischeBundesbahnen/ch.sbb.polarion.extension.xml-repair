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

export default function RepairerBreakdownTable({
  rows,
  hiddenRepairers,
  onToggleRepairer,
}: RepairerBreakdownTableProps) {
  return (
    <table className="sbb-table sbb-table--grid breakdown-table">
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
                <button type="button" className="breakdown-filter" onClick={() => onToggleRepairer(r.id)}>
                  ({hidden ? 'show' : 'hide'})
                </button>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
