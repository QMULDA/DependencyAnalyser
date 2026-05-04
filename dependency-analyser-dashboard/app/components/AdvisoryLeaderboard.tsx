'use client';
import { useEffect, useState } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer,
} from 'recharts';
import { supabase } from '../../lib/supabaseClient';

type ChartRow = {
  group_id: string;
  artifact_id: string;
  cve_count: number;
  label: string;
  fullCoords: string;
};

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

export default function AdvisoryLeaderboard() {
  const [data, setData] = useState<ChartRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    supabase
      .from('v_advisory_leaderboard')
      .select('*')
      .limit(20)
      .then(({ data: rows }) => {
        if (rows) {
          setData(
            rows
              .map((r: { group_id: string; artifact_id: string; cve_count: unknown }) => ({
                group_id: r.group_id,
                artifact_id: r.artifact_id,
                cve_count: Number(r.cve_count),
                label:
                  r.artifact_id.length > 22
                    ? r.artifact_id.substring(0, 22) + '…'
                    : r.artifact_id,
                fullCoords: `${r.group_id}:${r.artifact_id}`,
              }))
              .sort((a: ChartRow, b: ChartRow) => b.cve_count - a.cve_count)
          );
        }
        setLoading(false);
      });
  }, []);

  if (loading) return <p className="text-gray-400 text-sm">Loading...</p>;
  if (data.length === 0) {
    return <p className="text-gray-400 text-sm">No CVEs found in scanned dependencies.</p>;
  }

  const chartHeight = Math.max(250, data.length * 32);

  return (
    <ResponsiveContainer width="100%" height={chartHeight}>
      <BarChart
        layout="vertical"
        data={data}
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
        <Bar dataKey="cve_count" name="CVEs" fill="#ef4444" />
      </BarChart>
    </ResponsiveContainer>
  );
}
