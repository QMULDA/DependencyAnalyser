'use client';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, Cell, ResponsiveContainer,
} from 'recharts';
import { RiskTierRow, RiskTier, TIER_ORDER, TIER_COLORS } from '../../lib/types';

interface Props {
  data: RiskTierRow[];
  activeRiskTier: RiskTier | null;
  onRiskTierClick: (tier: RiskTier | null) => void;
}

export default function RiskTierBarChart({ data, activeRiskTier, onRiskTierClick }: Props) {
  if (data.every(d => d.count === 0)) {
    return <p className="text-gray-400 text-sm">No data yet — export a scan to cloud first.</p>;
  }

  const sorted = TIER_ORDER.map(tier => {
    const found = data.find(r => r.risk_tier === tier);
    return { risk_tier: tier, count: found ? found.count : 0 };
  });

  return (
    <ResponsiveContainer width="100%" height={300}>
      <BarChart data={sorted} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
        <XAxis dataKey="risk_tier" tick={{ fill: '#9ca3af', fontSize: 12 }} />
        <YAxis allowDecimals={false} tick={{ fill: '#9ca3af', fontSize: 12 }} />
        <Tooltip
          contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: 6 }}
          labelStyle={{ color: '#f3f4f6' }}
          itemStyle={{ color: '#d1d5db' }}
        />
        <Bar
          dataKey="count"
          name="Dependencies"
          cursor="pointer"
          onClick={(data) => {
            const entry = data as unknown as RiskTierRow;
            onRiskTierClick(activeRiskTier === entry.risk_tier ? null : (entry.risk_tier as RiskTier));
          }}
        >
          {sorted.map(entry => (
            <Cell
              key={entry.risk_tier}
              fill={TIER_COLORS[entry.risk_tier] ?? '#6b7280'}
              opacity={activeRiskTier && activeRiskTier !== entry.risk_tier ? 0.3 : 1}
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
