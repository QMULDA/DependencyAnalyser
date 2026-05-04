'use client';
import { useEffect, useState } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, Cell, ResponsiveContainer,
} from 'recharts';
import { supabase } from '../../lib/supabaseClient';

const TIER_ORDER = ['NONE', 'LOW', 'MEDIUM', 'HIGH'];
const TIER_COLORS: Record<string, string> = {
  NONE: '#9ca3af',
  LOW: '#22c55e',
  MEDIUM: '#f59e0b',
  HIGH: '#ef4444',
};

type Row = { risk_tier: string; count: number };

export default function RiskTierBarChart() {
  const [data, setData] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    supabase
      .from('v_risk_tier_distribution')
      .select('*')
      .then(({ data: rows }) => {
        if (rows) {
          const sorted = TIER_ORDER.map(tier => {
            const found = rows.find((r: { risk_tier: string; count: unknown }) => r.risk_tier === tier);
            return { risk_tier: tier, count: found ? Number(found.count) : 0 };
          });
          setData(sorted);
        }
        setLoading(false);
      });
  }, []);

  if (loading) return <p className="text-gray-400 text-sm">Loading...</p>;
  if (data.every(d => d.count === 0)) {
    return <p className="text-gray-400 text-sm">No data yet — export a scan to cloud first.</p>;
  }

  return (
    <ResponsiveContainer width="100%" height={300}>
      <BarChart data={data} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
        <XAxis dataKey="risk_tier" tick={{ fill: '#9ca3af', fontSize: 12 }} />
        <YAxis allowDecimals={false} tick={{ fill: '#9ca3af', fontSize: 12 }} />
        <Tooltip
          contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: 6 }}
          labelStyle={{ color: '#f3f4f6' }}
          itemStyle={{ color: '#d1d5db' }}
        />
        <Bar dataKey="count" name="Dependencies">
          {data.map(entry => (
            <Cell key={entry.risk_tier} fill={TIER_COLORS[entry.risk_tier] ?? '#6b7280'} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
