'use client';
import { useEffect, useState } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer,
} from 'recharts';
import { supabase } from '../../lib/supabaseClient';

type ChartRow = {
  group_id: string;
  artifact_id: string;
  version_count: number;
  label: string;
  fullCoords: string;
};

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

export default function VersionConflictsChart() {
  const [data, setData] = useState<ChartRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    supabase
      .from('v_version_conflicts')
      .select('*')
      .order('version_count', { ascending: false })
      .limit(20)
      .then(({ data: rows }) => {
        if (rows) {
          setData(
            rows.map((r: { group_id: string; artifact_id: string; version_count: unknown }) => ({
              group_id: r.group_id,
              artifact_id: r.artifact_id,
              version_count: Number(r.version_count),
              label: r.artifact_id.length > 14 ? r.artifact_id.substring(0, 14) + '…' : r.artifact_id,
              fullCoords: `${r.group_id}:${r.artifact_id}`,
            }))
          );
        }
        setLoading(false);
      });
  }, []);

  if (loading) return <p className="text-gray-400 text-sm">Loading...</p>;
  if (data.length === 0) {
    return <p className="text-gray-400 text-sm">No version conflicts detected across scans.</p>;
  }

  return (
    <ResponsiveContainer width="100%" height={300}>
      <BarChart data={data} margin={{ top: 5, right: 20, left: 0, bottom: 60 }}>
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
        <Bar dataKey="version_count" name="Versions" fill="#3b82f6" />
      </BarChart>
    </ResponsiveContainer>
  );
}
