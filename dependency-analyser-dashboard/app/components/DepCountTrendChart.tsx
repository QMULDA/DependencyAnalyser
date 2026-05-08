'use client';
import {
  LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, Legend, Brush, ResponsiveContainer,
} from 'recharts';
import { DepCountRow } from '../../lib/types';

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
  data: DepCountRow[];
  selectedProject: string | null;
  selectedOrg: string | null;
}

export default function DepCountTrendChart({ data, selectedProject, selectedOrg }: Props) {
  if (!selectedProject && !selectedOrg) {
    return (
      <p className="text-gray-400 text-sm">
        Select a project or organisation above to view the dependency count trend.
      </p>
    );
  }

  const projectScanMap = new Map<string, { scanned_at: string; direct: number; indirect: number }>();
  if (selectedProject) {
    for (const row of data.filter(r => r.project_name === selectedProject)) {
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

  const orgByDate = new Map<string, { direct: number; indirect: number }>();
  if (selectedOrg) {
    for (const row of data.filter(r => r.org_name === selectedOrg)) {
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
    <ResponsiveContainer width="100%" height={340}>
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
        <Brush dataKey="date" height={30} stroke="#4b5563" fill="#111827" travellerWidth={10} />
      </LineChart>
    </ResponsiveContainer>
  );
}
