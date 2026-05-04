'use client';
import { useEffect, useState } from 'react';
import { supabase } from '../lib/supabaseClient';
import RiskTierBarChart from './components/RiskTierBarChart';
import VersionConflictsChart from './components/VersionConflictsChart';
import CveTrendChart from './components/CveTrendChart';
import DepCountTrendChart from './components/DepCountTrendChart';
import EolTable from './components/EolTable';
import AdvisoryLeaderboard from './components/AdvisoryLeaderboard';

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="text-lg font-medium text-gray-200 mb-4 border-b border-gray-800 pb-2">
        {title}
      </h2>
      {children}
    </section>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-gray-900 rounded-xl p-5 border border-gray-800">
      <h3 className="text-sm font-medium text-gray-300 mb-4">{title}</h3>
      {children}
    </div>
  );
}

export default function Home() {
  const [projects, setProjects] = useState<string[]>([]);
  const [orgs, setOrgs] = useState<string[]>([]);
  const [selectedProject, setSelectedProject] = useState<string | null>(null);
  const [selectedOrg, setSelectedOrg] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      supabase.from('project').select('name'),
      supabase.from('organisation').select('name'),
    ]).then(([{ data: projData }, { data: orgData }]) => {
      const projNames: string[] = projData?.map((p: { name: string }) => p.name) ?? [];
      const orgNames: string[] = orgData?.map((o: { name: string }) => o.name) ?? [];
      setProjects(projNames);
      setOrgs(orgNames);
      if (projNames.length > 0) setSelectedProject(projNames[0]);
      if (orgNames.length > 0) setSelectedOrg(orgNames[0]);
    });
  }, []);

  return (
    <main className="min-h-screen bg-gray-950 text-gray-100">
      <header className="bg-gray-900 border-b border-gray-700 px-6 py-4">
        <h1 className="text-xl font-semibold tracking-tight">Dependency Analyser</h1>
        <p className="text-gray-400 text-sm mt-0.5">
          Dependency health across your Maven projects
        </p>
      </header>

      <div className="px-6 py-6 max-w-screen-xl mx-auto space-y-10">
        {/* Filter bar — controls the two trend charts */}
        <div className="flex flex-wrap gap-6 items-center bg-gray-900 rounded-xl px-5 py-4 border border-gray-800">
          <span className="text-sm text-gray-400 font-medium">Trend filters</span>
          <div className="flex items-center gap-2">
            <label className="text-sm text-gray-400" htmlFor="proj-select">
              Project
            </label>
            <select
              id="proj-select"
              className="bg-gray-800 border border-gray-600 rounded px-3 py-1 text-sm text-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-500"
              value={selectedProject ?? ''}
              onChange={e => setSelectedProject(e.target.value || null)}
            >
              {projects.length === 0 && <option value="">No projects exported yet</option>}
              {projects.map(p => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          </div>
          <div className="flex items-center gap-2">
            <label className="text-sm text-gray-400" htmlFor="org-select">
              Organisation
            </label>
            <select
              id="org-select"
              className="bg-gray-800 border border-gray-600 rounded px-3 py-1 text-sm text-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-500"
              value={selectedOrg ?? ''}
              onChange={e => setSelectedOrg(e.target.value || null)}
            >
              {orgs.length === 0 && <option value="">No organisations yet</option>}
              {orgs.map(o => (
                <option key={o} value={o}>
                  {o}
                </option>
              ))}
            </select>
          </div>
          <p className="text-gray-500 text-xs">
            Applies to the trend charts below. Aggregate charts show all data.
          </p>
        </div>

        {/* Section 1: Version Health */}
        <Section title="Version Health">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card title="Risk Tier Distribution">
              <RiskTierBarChart />
            </Card>
            <Card title="Libraries with Multiple Versions">
              <VersionConflictsChart />
            </Card>
          </div>
        </Section>

        {/* Section 2: Trends Over Time */}
        <Section title="Trends Over Time">
          <div className="space-y-6">
            <Card title="CVE Count Over Time">
              <CveTrendChart selectedProject={selectedProject} selectedOrg={selectedOrg} />
            </Card>
            <Card title="Dependency Count Over Time (Direct vs Indirect)">
              <DepCountTrendChart selectedProject={selectedProject} selectedOrg={selectedOrg} />
            </Card>
          </div>
        </Section>

        {/* Section 3: EOL & Advisories */}
        <Section title="EOL & Advisories">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card title="Libraries Approaching End of Life">
              <EolTable />
            </Card>
            <Card title="Top Libraries by CVE Count">
              <AdvisoryLeaderboard />
            </Card>
          </div>
        </Section>
      </div>
    </main>
  );
}
