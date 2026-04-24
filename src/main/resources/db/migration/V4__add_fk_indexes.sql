CREATE INDEX idx_scan_project_id       ON scan(project_id);
CREATE INDEX idx_dependency_scan_id    ON dependency(scan_id);
CREATE INDEX idx_dependency_version_id ON dependency(version_id);
CREATE INDEX idx_version_library_id    ON version(library_id);
