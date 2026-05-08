'use client';
import { useMemo } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, Cell, ResponsiveContainer,
} from 'recharts';
import { ConflictRawRow } from '../../lib/types';

type ChartRow = ConflictRawRow & { label: string; fullCoords: string };

function ConflictTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: Array<{ payload: ChartRow }>;
}) {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <div className="bg-gray-800 border border-gray-600 text-xs p-2 rounded">
      <p className="text-gray-100">{d.fullCoords}</p>
      <p className="text-gray-300">{d.version_count} distinct version{d.version_count !== 1 ? 's' : ''}</p>
    </div>
  );
}

interface Props {
  data: ConflictRawRow[];
  activeLibraryKey: string | null;
  onLibraryClick: (key: string | null) => void;
}

export default function VersionConflictsChart({ data, activeLibraryKey, onLibraryClick }: Props) {
  const processedData = useMemo<ChartRow[]>(() =>
    data.map(r => ({
      ...r,
      label: r.artifact_id.length > 14 ? r.artifact_id.substring(0, 14) + '…' : r.artifact_id,
      fullCoords: `${r.group_id}:${r.artifact_id}`,
    })),
    [data]
  );

  if (processedData.length === 0) {
    return <p className="text-gray-400 text-sm">No version conflicts detected across scans.</p>;
  }

  return (
    <ResponsiveContainer width="100%" height={300}>
      <BarChart data={processedData} margin={{ top: 5, right: 20, left: 0, bottom: 60 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
        <XAxis
          dataKey="label"
          angle={-40}
          textAnchor="end"
          interval={0}
          tick={{ fill: '#9ca3af', fontSize: 11 }}
        />
        <YAxis allowDecimals={false} tick={{ fill: '#9ca3af', fontSize: 12 }} />
        <Tooltip content={<ConflictTooltip />} />
        <Bar
          dataKey="version_count"
          name="Versions"
          cursor="pointer"
          onClick={(data) => {
            const entry = data as unknown as ChartRow;
            onLibraryClick(activeLibraryKey === entry.fullCoords ? null : entry.fullCoords);
          }}
        >
          {processedData.map(entry => (
            <Cell
              key={entry.fullCoords}
              fill={activeLibraryKey && activeLibraryKey !== entry.fullCoords ? '#1d4ed8' : '#3b82f6'}
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
