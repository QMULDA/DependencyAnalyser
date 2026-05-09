export type RiskTier = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH';

export const TIER_ORDER: RiskTier[] = ['NONE', 'LOW', 'MEDIUM', 'HIGH'];

export const TIER_COLORS: Record<string, string> = {
  NONE:   '#6b7280',
  LOW:    '#22c55e',
  MEDIUM: '#f59e0b',
  HIGH:   '#ef4444',
};

export interface Filters {
  riskTier:   RiskTier | null;
  libraryKey: string | null;
}

export interface DetailRow {
  version_id:   string;
  group_id:     string;
  artifact_id:  string;
  version_string: string;
  risk_tier:    string | null;
  relation:     string;
  cve_count:    number;
  scanned_at:   string;
  project_name: string;
  org_name:     string | null;
}

export interface RiskTierRow {
  risk_tier: string;
  count:     number;
}

export interface ConflictRawRow {
  group_id:      string;
  artifact_id:   string;
  version_count: number;
}

export interface AdvisoryRawRow {
  group_id:    string;
  artifact_id: string;
  cve_count:   number;
}

export interface EolRow {
  group_id:      string;
  artifact_id:   string;
  version_string: string;
  cycle_name:    string | null;
  eol_from:      string | null;
  is_eol:        boolean;
  project_name:  string;
}

export interface CveScanRow {
  scan_id:      string;
  scanned_at:   string;
  project_name: string;
  org_name:     string | null;
  cve_count:    number;
}

export interface DepCountRow {
  scan_id:      string;
  scanned_at:   string;
  project_name: string;
  org_name:     string | null;
  relation:     string;
  dep_count:    number;
}
