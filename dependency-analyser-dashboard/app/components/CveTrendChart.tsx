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
  cve_count: number;
};

type ChartPoint = {
  date: string;
  project: number | null;
  org: number | null;
};

function toDate(ts: string): string {
  return ts.substring(0, 10);
}

interface Props {
  selectedProject: string | null;
  selectedOrg: string | null;
}

export default function CveTrendChart({ selectedProject, selectedOrg }: Props) {
  const [rows, setRows] = useState<RawRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    supabase
      .from('v_cve_count_per_scan')
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
              cve_count: Number(r.cve_count),
            }))
          );
        setLoading(false);
      });
  }, []);

  if (loading) return <p className="text-gray-400 text-sm">Loading...</p>;
  if (!selectedProject && !selectedOrg) {
    return (
      <p className="text-gray-400 text-sm">
        Select a project or organisation above to view the CVE trend.
      </p>
    );
  }

  // Project line: one point per scan of the selected project
  const projectByDate = new Map<string, number>();
  if (selectedProject) {
    for (const row of rows.filter(r => r.project_name === selectedProject)) {
      projectByDate.set(toDate(row.scanned_at), row.cve_count);
    }
  }

  // Org line: sum cve_count per day across all projects in the selected org
  const orgByDate = new Map<string, number>();
  if (selectedOrg) {
    for (const row of rows.filter(r => r.org_name === selectedOrg)) {
      const date = toDate(row.scanned_at);
      orgByDate.set(date, (orgByDate.get(date) ?? 0) + row.cve_count);
    }
  }

  const allDates = [...new Set([...projectByDate.keys(), ...orgByDate.keys()])].sort();
  const chartData: ChartPoint[] = allDates.map(date => ({
    date,
    project: projectByDate.get(date) ?? null,
    org: orgByDate.get(date) ?? null,
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
          <Line
            type="monotone"
            dataKey="project"
            name={selectedProject}
            stroke="#3b82f6"
            strokeWidth={2}
            dot={{ r: 4 }}
            connectNulls={false}
          />
        )}
        {selectedOrg && (
          <Line
            type="monotone"
            dataKey="org"
            name={`${selectedOrg} (org total)`}
            stroke="#f97316"
            strokeWidth={2}
            strokeDasharray="6 3"
            dot={{ r: 4 }}
            connectNulls={false}
          />
        )}
      </LineChart>
    </ResponsiveContainer>
  );
}
