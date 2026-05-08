'use client';
import { useMemo } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, Cell, ResponsiveContainer,
} from 'recharts';
import { AdvisoryRawRow } from '../../lib/types';

type ChartRow = AdvisoryRawRow & { label: string; fullCoords: string };

function AdvisoryTooltip({
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
      <p className="text-gray-300">{d.cve_count} CVE{d.cve_count !== 1 ? 's' : ''}</p>
    </div>
  );
}

interface Props {
  data: AdvisoryRawRow[];
  activeLibraryKey: string | null;
  onLibraryClick: (key: string | null) => void;
}

export default function AdvisoryLeaderboard({ data, activeLibraryKey, onLibraryClick }: Props) {
  const processedData = useMemo<ChartRow[]>(() =>
    data
      .map(r => ({
        ...r,
        label: r.artifact_id.length > 22 ? r.artifact_id.substring(0, 22) + '…' : r.artifact_id,
        fullCoords: `${r.group_id}:${r.artifact_id}`,
      }))
      .sort((a, b) => b.cve_count - a.cve_count),
    [data]
  );

  if (processedData.length === 0) {
    return <p className="text-gray-400 text-sm">No CVEs found in scanned dependencies.</p>;
  }

  const chartHeight = Math.max(250, processedData.length * 32);

  return (
    <ResponsiveContainer width="100%" height={chartHeight}>
      <BarChart
        layout="vertical"
        data={processedData}
        margin={{ top: 5, right: 30, left: 10, bottom: 5 }}
      >
        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
        <XAxis type="number" allowDecimals={false} tick={{ fill: '#9ca3af', fontSize: 12 }} />
        <YAxis
          type="category"
          dataKey="label"
          width={150}
          tick={{ fill: '#9ca3af', fontSize: 11 }}
        />
        <Tooltip content={<AdvisoryTooltip />} />
        <Bar
          dataKey="cve_count"
          name="CVEs"
          cursor="pointer"
          onClick={(data) => {
            const entry = data as unknown as ChartRow;
            onLibraryClick(activeLibraryKey === entry.fullCoords ? null : entry.fullCoords);
          }}
        >
          {processedData.map(entry => (
            <Cell
              key={entry.fullCoords}
              fill={activeLibraryKey && activeLibraryKey !== entry.fullCoords ? '#991b1b' : '#ef4444'}
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
