'use client';
import { EolRow } from '../../lib/types';

interface Props {
  data: EolRow[];
}

function StatusBadge({ row }: { row: EolRow }) {
  if (row.is_eol) {
    return (
      <span className="px-2 py-0.5 rounded text-xs font-semibold bg-red-900 text-red-200">
        EOL
      </span>
    );
  }
  if (row.eol_from) {
    const diffDays = (new Date(row.eol_from).getTime() - Date.now()) / 86_400_000;
    if (diffDays >= 0 && diffDays <= 180) {
      return (
        <span className="px-2 py-0.5 rounded text-xs font-semibold bg-amber-900 text-amber-200">
          EOL {row.eol_from}
        </span>
      );
    }
  }
  return (
    <span className="px-2 py-0.5 rounded text-xs font-semibold bg-green-900 text-green-200">
      Active
    </span>
  );
}

export default function EolTable({ data }: Props) {
  if (data.length === 0) {
    return (
      <p className="text-gray-400 text-sm">
        No EOL-tracked libraries found. EOL data is sourced from endoflife.date — only libraries
        tracked there will appear.
      </p>
    );
  }

  return (
    <div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left">
          <thead>
            <tr className="text-gray-400 border-b border-gray-700">
              <th className="pb-2 pr-4 font-medium">Library</th>
              <th className="pb-2 pr-4 font-medium">Version</th>
              <th className="pb-2 pr-4 font-medium">Cycle</th>
              <th className="pb-2 pr-4 font-medium">EOL Date</th>
              <th className="pb-2 pr-4 font-medium">Status</th>
              <th className="pb-2 font-medium">Project</th>
            </tr>
          </thead>
          <tbody>
            {data.map((row, i) => (
              <tr key={i} className="border-b border-gray-800 hover:bg-gray-800/50">
                <td className="py-2 pr-4 font-mono text-xs text-gray-200">
                  {row.group_id}:{row.artifact_id}
                </td>
                <td className="py-2 pr-4 font-mono text-xs text-gray-300">{row.version_string}</td>
                <td className="py-2 pr-4 text-xs text-gray-300">{row.cycle_name ?? '—'}</td>
                <td className="py-2 pr-4 text-xs text-gray-300">{row.eol_from ?? '—'}</td>
                <td className="py-2 pr-4">
                  <StatusBadge row={row} />
                </td>
                <td className="py-2 text-xs text-gray-400">{row.project_name}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-gray-500 text-xs mt-2">Showing up to 25 nearest-EOL libraries</p>
    </div>
  );
}
