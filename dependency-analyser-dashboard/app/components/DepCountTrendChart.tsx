'use client';
import { useEffect, useState } from 'react';
import {
  LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, Legend, ResponsiveContainer,
} from 'recharts';
import { supabase } from '../../lib/supabaseClient';

type RawRow = {
  scan_id: string;
  scanned_at: string;
  project_name: string;
  org_name: string | null;
  relation: string;
  dep_count: number;
};

type ChartPoint = {
  date: string;
  projectDirect: number | null;
  projectIndirect: number | null;
  orgDirect: number | null;
  orgIndirect: number | null;
};

function toDate(ts: string): string {
  return ts.substring(0, 10);
}

interface Props {
  selectedProject: string | null;
  selectedOrg: string | null;
}

export default function DepCountTrendChart({ selectedProject, selectedOrg }: Props) {
  const [rows, setRows] = useState<RawRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    supabase
      .from('v_dep_count_per_scan')
      .select('*')
      .order('scanned_at', { ascending: true })
      .then(({ data }) => {
        if (data)
          setRows(
            data.map((r: Record<string, unknown>) => ({
              scan_id: r.scan_id as string,
              scanned_at: r.scanned_at as string,
              project_name: r.project_name as string,
              org_name: r.org_name as string | null,
              relation: r.relation as string,
              dep_count: Number(r.dep_count),
            }))
          );
        setLoading(false);
      });
  }, []);

  if (loading) return <p className="text-gray-400 text-sm">Loading...</p>;
  if (!selectedProject && !selectedOrg) {
    return (
      <p className="text-gray-400 text-sm">
        Select a project or organisation above to view the dependency count trend.
      </p>
    );
  }

  // Project series: group by scan_id first, then key by date
  const projectScanMap = new Map<string, { scanned_at: string; direct: number; indirect: number }>();
  if (selectedProject) {
    for (const row of rows.filter(r => r.project_name === selectedProject)) {
      if (!projectScanMap.has(row.scan_id)) {
        projectScanMap.set(row.scan_id, { scanned_at: row.scanned_at, direct: 0, indirect: 0 });
      }
      const entry = projectScanMap.get(row.scan_id)!;
      if (row.relation === 'DIRECT') entry.direct = row.dep_count;
      else if (row.relation === 'INDIRECT') entry.indirect = row.dep_count;
    }
  }
  const projectByDate = new Map<string, { direct: number; indirect: number }>();
  for (const [, v] of projectScanMap) {
    projectByDate.set(toDate(v.scanned_at), { direct: v.direct, indirect: v.indirect });
  }

  // Org series: aggregate all projects in the org per day
  const orgByDate = new Map<string, { direct: number; indirect: number }>();
  if (selectedOrg) {
    for (const row of rows.filter(r => r.org_name === selectedOrg)) {
      const date = toDate(row.scanned_at);
      if (!orgByDate.has(date)) orgByDate.set(date, { direct: 0, indirect: 0 });
      const entry = orgByDate.get(date)!;
      if (row.relation === 'DIRECT') entry.direct += row.dep_count;
      else if (row.relation === 'INDIRECT') entry.indirect += row.dep_count;
    }
  }

  const allDates = [...new Set([...projectByDate.keys(), ...orgByDate.keys()])].sort();
  const chartData: ChartPoint[] = allDates.map(date => ({
    date,
    projectDirect: projectByDate.get(date)?.direct ?? null,
    projectIndirect: projectByDate.get(date)?.indirect ?? null,
    orgDirect: orgByDate.get(date)?.direct ?? null,
    orgIndirect: orgByDate.get(date)?.indirect ?? null,
  }));

  if (chartData.length === 0) {
    return (
      <p className="text-gray-400 text-sm">
        No scan data for the selected project / organisation yet.
      </p>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
        <XAxis dataKey="date" tick={{ fill: '#9ca3af', fontSize: 11 }} />
        <YAxis allowDecimals={false} tick={{ fill: '#9ca3af', fontSize: 12 }} />
        <Tooltip
          contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: 6 }}
          labelStyle={{ color: '#f3f4f6' }}
          itemStyle={{ color: '#d1d5db' }}
        />
        <Legend wrapperStyle={{ color: '#9ca3af', fontSize: 12 }} />
        {selectedProject && (
          <>
            <Line
              type="monotone"
              dataKey="projectDirect"
              name={`${selectedProject} — Direct`}
              stroke="#3b82f6"
              strokeWidth={2}
              dot={{ r: 4 }}
              connectNulls={false}
            />
            <Line
              type="monotone"
              dataKey="projectIndirect"
              name={`${selectedProject} — Indirect`}
              stroke="#93c5fd"
              strokeWidth={2}
              dot={{ r: 4 }}
              connectNulls={false}
            />
          </>
        )}
        {selectedOrg && (
          <>
            <Line
              type="monotone"
              dataKey="orgDirect"
              name={`${selectedOrg} — Direct (org)`}
              stroke="#f97316"
              strokeWidth={2}
              strokeDasharray="6 3"
              dot={{ r: 4 }}
              connectNulls={false}
            />
            <Line
              type="monotone"
              dataKey="orgIndirect"
              name={`${selectedOrg} — Indirect (org)`}
              stroke="#fdba74"
              strokeWidth={2}
              strokeDasharray="6 3"
              dot={{ r: 4 }}
              connectNulls={false}
            />
          </>
        )}
      </LineChart>
    </ResponsiveContainer>
  );
}
