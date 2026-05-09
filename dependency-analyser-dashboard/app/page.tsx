'use client';
import { useEffect, useState, useMemo } from 'react';
import { supabase } from '../lib/supabaseClient';
import {
  Filters, RiskTier,
  DetailRow, RiskTierRow, ConflictRawRow, AdvisoryRawRow, EolRow, CveScanRow, DepCountRow,
} from '../lib/types';
import RiskTierBarChart from './components/RiskTierBarChart';
import VersionConflictsChart from './components/VersionConflictsChart';
import CveTrendChart from './components/CveTrendChart';
import DepCountTrendChart from './components/DepCountTrendChart';
import EolTable from './components/EolTable';
import AdvisoryLeaderboard from './components/AdvisoryLeaderboard';
import DependencyTable from './components/DependencyTable';

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

const TIER_CHIP_COLORS: Record<string, string> = {
  NONE: 'bg-gray-700 text-gray-200',
  LOW: 'bg-green-900/60 text-green-300',
  MEDIUM: 'bg-amber-900/60 text-amber-300',
  HIGH: 'bg-red-900/60 text-red-300',
};

export default function Home() {
  const [projects, setProjects] = useState<string[]>([]);
  const [orgs, setOrgs] = useState<string[]>([]);
  const [selectedProject, setSelectedProject] = useState<string | null>(null);
  const [selectedOrg, setSelectedOrg] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Raw data from Supabase
  const [detailRows,   setDetailRows]   = useState<DetailRow[]>([]);
  const [eolData,      setEolData]      = useState<EolRow[]>([]);
  const [cveTrendData, setCveTrendData] = useState<CveScanRow[]>([]);
  const [depTrendData, setDepTrendData] = useState<DepCountRow[]>([]);

  // Cross-filter state
  const [filters, setFilters] = useState<Filters>({ riskTier: null, libraryKey: null });

  useEffect(() => {
    Promise.all([
      supabase.from('v_dependency_detail').select('*'),
      supabase.from('v_eol_approaching').select('*').limit(25),
      supabase.from('v_cve_count_per_scan').select('*').order('scanned_at', { ascending: true }),
      supabase.from('v_dep_count_per_scan').select('*').order('scanned_at', { ascending: true }),
      supabase.from('project').select('name'),
      supabase.from('organisation').select('name'),
    ]).then(([detail, eol, cve, dep, projs, orgs]) => {
      if (detail.data) setDetailRows(detail.data as DetailRow[]);
      if (eol.data)    setEolData(eol.data as EolRow[]);
      if (cve.data)    setCveTrendData(cve.data.map((r: Record<string, unknown>) => ({ scan_id: r.scan_id as string, scanned_at: r.scanned_at as string, project_name: r.project_name as string, org_name: r.org_name as string | null, cve_count: Number(r.cve_count) })));
      if (dep.data)    setDepTrendData(dep.data.map((r: Record<string, unknown>) => ({ scan_id: r.scan_id as string, scanned_at: r.scanned_at as string, project_name: r.project_name as string, org_name: r.org_name as string | null, relation: r.relation as string, dep_count: Number(r.dep_count) })));

      const projNames: string[] = projs.data?.map((p: { name: string }) => p.name) ?? [];
      const orgNames: string[]  = orgs.data?.map((o: { name: string }) => o.name) ?? [];
      setProjects(projNames);
      setOrgs(orgNames);
      if (projNames.length > 0) setSelectedProject(projNames[0]);
      if (orgNames.length > 0)  setSelectedOrg(orgNames[0]);
      setLoading(false);
    });
  }, []);

  // --- Derived / filtered datasets ---

  // Base: detail rows scoped to the selected project/org (no cross-filter applied yet)
  const projectFilteredRows = useMemo(() =>
          detailRows.filter(r => {
            if (selectedProject && r.project_name !== selectedProject) return false;
            if (selectedOrg && r.org_name !== selectedOrg) return false;
            return true;
          }),
      [detailRows, selectedProject, selectedOrg]
  );

  // Libraries matching the active riskTier filter within the selected project
  const librariesForTier = useMemo<Set<string> | null>(() => {
    if (!filters.riskTier) return null;
    return new Set(
        projectFilteredRows
            .filter(r => r.risk_tier === filters.riskTier)
            .map(r => `${r.group_id}:${r.artifact_id}`)
    );
  }, [projectFilteredRows, filters.riskTier]);

  // Risk tier distribution derived from the selected project's versions (dedup by version_id)
  const derivedRiskTierData = useMemo<RiskTierRow[]>(() => {
    const seen = new Set<string>();
    const counts: Record<string, number> = {};
    for (const r of projectFilteredRows) {
      if (!r.risk_tier || seen.has(r.version_id)) continue;
      seen.add(r.version_id);
      counts[r.risk_tier] = (counts[r.risk_tier] ?? 0) + 1;
    }
    return Object.entries(counts).map(([risk_tier, count]) => ({ risk_tier, count }));
  }, [projectFilteredRows]);

  // Version conflicts derived from the selected project (dedup by version_id per library)
  const derivedConflictsData = useMemo<ConflictRawRow[]>(() => {
    const libVersions = new Map<string, Set<string>>();
    const seen = new Set<string>();
    for (const r of projectFilteredRows) {
      if (seen.has(r.version_id)) continue;
      seen.add(r.version_id);
      const key = `${r.group_id}:${r.artifact_id}`;
      if (!libVersions.has(key)) libVersions.set(key, new Set());
      libVersions.get(key)!.add(r.version_string);
    }
    return Array.from(libVersions.entries())
        .filter(([, versions]) => versions.size > 1)
        .map(([key, versions]) => {
          const [group_id, artifact_id] = key.split(':');
          return { group_id, artifact_id, version_count: versions.size };
        })
        .sort((a, b) => b.version_count - a.version_count)
        .slice(0, 20);
  }, [projectFilteredRows]);

  // Advisory leaderboard derived from the selected project (dedup by version_id)
  const derivedAdvisoryData = useMemo<AdvisoryRawRow[]>(() => {
    const seen = new Set<string>();
    const libCounts = new Map<string, number>();
    for (const r of projectFilteredRows) {
      if (seen.has(r.version_id)) continue;
      seen.add(r.version_id);
      const key = `${r.group_id}:${r.artifact_id}`;
      libCounts.set(key, (libCounts.get(key) ?? 0) + r.cve_count);
    }
    return Array.from(libCounts.entries())
        .filter(([, count]) => count > 0)
        .map(([key, cve_count]) => {
          const [group_id, artifact_id] = key.split(':');
          return { group_id, artifact_id, cve_count };
        })
        .sort((a, b) => b.cve_count - a.cve_count)
        .slice(0, 20);
  }, [projectFilteredRows]);

  const filteredConflicts = useMemo(() =>
          derivedConflictsData.filter(r => {
            const key = `${r.group_id}:${r.artifact_id}`;
            if (librariesForTier && !librariesForTier.has(key)) return false;
            if (filters.libraryKey && key !== filters.libraryKey) return false;
            return true;
          }),
      [derivedConflictsData, librariesForTier, filters.libraryKey]
  );

  const filteredAdvisory = useMemo(() =>
          derivedAdvisoryData.filter(r => {
            const key = `${r.group_id}:${r.artifact_id}`;
            if (librariesForTier && !librariesForTier.has(key)) return false;
            if (filters.libraryKey && key !== filters.libraryKey) return false;
            return true;
          }),
      [derivedAdvisoryData, librariesForTier, filters.libraryKey]
  );

  const filteredEol = useMemo(() =>
          eolData.filter(r => {
            if (selectedProject && r.project_name !== selectedProject) return false;
            if (filters.libraryKey && `${r.group_id}:${r.artifact_id}` !== filters.libraryKey) return false;
            return true;
          }),
      [eolData, selectedProject, filters.libraryKey]
  );

  // Detail table: apply all active filters + project/org selection
  const filteredDetail = useMemo(() =>
          detailRows.filter(r => {
            if (filters.riskTier && r.risk_tier !== filters.riskTier) return false;
            if (filters.libraryKey && `${r.group_id}:${r.artifact_id}` !== filters.libraryKey) return false;
            if (selectedProject && r.project_name !== selectedProject) return false;
            if (selectedOrg && r.org_name !== selectedOrg) return false;
            return true;
          }),
      [detailRows, filters, selectedProject, selectedOrg]
  );

  // --- Filter helpers ---
  const setRiskTier   = (t: RiskTier | null) => setFilters(f => ({ ...f, riskTier: t }));
  const setLibraryKey = (k: string | null)    => setFilters(f => ({ ...f, libraryKey: k }));
  const clearFilters  = () => setFilters({ riskTier: null, libraryKey: null });

  const hasActiveFilters = filters.riskTier !== null || filters.libraryKey !== null;

  return (
      <main className="min-h-screen bg-gray-950 text-gray-100">
        <header className="bg-gray-900 border-b border-gray-700 px-6 py-4">
          <h1 className="text-xl font-semibold tracking-tight">Dependency Analyser</h1>
          <p className="text-gray-400 text-sm mt-0.5">
            Dependency health across your Maven projects
          </p>
        </header>

        <div className="px-6 py-6 max-w-screen-xl mx-auto space-y-6">

          {/* Trend filter bar */}
          <div className="flex flex-wrap gap-6 items-center bg-gray-900 rounded-xl px-5 py-4 border border-gray-800">
            <span className="text-sm text-gray-400 font-medium">Trend filters</span>
            <div className="flex items-center gap-2">
              <label className="text-sm text-gray-400" htmlFor="proj-select">Project</label>
              <select
                  id="proj-select"
                  className="bg-gray-800 border border-gray-600 rounded px-3 py-1 text-sm text-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  value={selectedProject ?? ''}
                  onChange={e => setSelectedProject(e.target.value || null)}
              >
                {projects.length === 0 && <option value="">No projects exported yet</option>}
                {projects.map(p => <option key={p} value={p}>{p}</option>)}
              </select>
            </div>
            <div className="flex items-center gap-2">
              <label className="text-sm text-gray-400" htmlFor="org-select">Organisation</label>
              <select
                  id="org-select"
                  className="bg-gray-800 border border-gray-600 rounded px-3 py-1 text-sm text-gray-100 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  value={selectedOrg ?? ''}
                  onChange={e => setSelectedOrg(e.target.value || null)}
              >
                {orgs.length === 0 && <option value="">No organisations yet</option>}
                {orgs.map(o => <option key={o} value={o}>{o}</option>)}
              </select>
            </div>
            <p className="text-gray-500 text-xs">Applies to the trend charts below.</p>
          </div>

          {/* Active cross-filter chips */}
          {hasActiveFilters && (
              <div className="flex flex-wrap gap-2 items-center bg-gray-900/50 rounded-lg px-4 py-2.5 border border-gray-700">
                <span className="text-xs text-gray-500 mr-1">Active filters:</span>
                {filters.riskTier && (
                    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${TIER_CHIP_COLORS[filters.riskTier]}`}>
                Risk: {filters.riskTier}
                      <button
                          onClick={() => setRiskTier(null)}
                          className="hover:opacity-70 leading-none"
                          aria-label="Clear risk tier filter"
                      >×</button>
              </span>
                )}
                {filters.libraryKey && (
                    <span className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium bg-blue-900/60 text-blue-300">
                Library: <span className="font-mono">{filters.libraryKey}</span>
                <button
                    onClick={() => setLibraryKey(null)}
                    className="hover:opacity-70 leading-none"
                    aria-label="Clear library filter"
                >×</button>
              </span>
                )}
                <button
                    onClick={clearFilters}
                    className="text-xs text-gray-500 underline hover:text-gray-300 ml-1"
                >
                  Clear all
                </button>
              </div>
          )}

          {loading ? (
              <p className="text-gray-400 text-sm py-8 text-center">Loading dashboard data…</p>
          ) : (
              <>
                {/* Section 1: Version Health */}
                <Section title="Version Health">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <Card title="Risk Tier Distribution">
                      <RiskTierBarChart
                          data={derivedRiskTierData}
                          activeRiskTier={filters.riskTier}
                          onRiskTierClick={setRiskTier}
                      />
                    </Card>
                    <Card title="Libraries with Multiple Versions">
                      <VersionConflictsChart
                          data={filteredConflicts}
                          activeLibraryKey={filters.libraryKey}
                          onLibraryClick={setLibraryKey}
                      />
                    </Card>
                  </div>
                </Section>

                {/* Section 2: Trends Over Time */}
                <Section title="Trends Over Time">
                  <div className="space-y-6">
                    <Card title="CVE Count Over Time">
                      <CveTrendChart
                          data={cveTrendData}
                          selectedProject={selectedProject}
                          selectedOrg={selectedOrg}
                      />
                    </Card>
                    <Card title="Dependency Count Over Time (Direct vs Indirect)">
                      <DepCountTrendChart
                          data={depTrendData}
                          selectedProject={selectedProject}
                          selectedOrg={selectedOrg}
                      />
                    </Card>
                  </div>
                </Section>

                {/* Section 3: EOL & Advisories */}
                <Section title="EOL & Advisories">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <Card title="Libraries Approaching End of Life">
                      <EolTable data={filteredEol} />
                    </Card>
                    <Card title="Top Libraries by CVE Count">
                      <AdvisoryLeaderboard
                          data={filteredAdvisory}
                          activeLibraryKey={filters.libraryKey}
                          onLibraryClick={setLibraryKey}
                      />
                    </Card>
                  </div>
                </Section>

                {/* Section 4: Dependency Detail */}
                <Section title="Dependency Detail">
                  <Card title="All Dependencies">
                    <DependencyTable data={filteredDetail} />
                  </Card>
                </Section>
              </>
          )}
        </div>
      </main>
  );
}
