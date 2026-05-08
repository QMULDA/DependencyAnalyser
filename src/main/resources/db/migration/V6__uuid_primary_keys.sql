-- Drop in reverse FK order
DROP TABLE IF EXISTS version_advisory;
DROP TABLE IF EXISTS dependency;
DROP TABLE IF EXISTS version;
DROP TABLE IF EXISTS release_cycle;
DROP TABLE IF EXISTS scan;
DROP TABLE IF EXISTS library;

-- Recreate with UUID PKs
CREATE TABLE library (
    library_id  UUID         NOT NULL,
    group_id    VARCHAR(255) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,
    CONSTRAINT pk_library PRIMARY KEY (library_id),
    CONSTRAINT uq_library UNIQUE (group_id, artifact_id)
);

CREATE TABLE scan (
    scan_id    UUID        NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    scanned_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_scan         PRIMARY KEY (scan_id),
    CONSTRAINT fk_scan_project FOREIGN KEY (project_id) REFERENCES project (project_id)
);

CREATE TABLE release_cycle (
    release_cycle_id UUID         NOT NULL,
    library_id       UUID         NOT NULL,
    cycle_name       VARCHAR(50)  NOT NULL,
    eol_from         DATE,
    is_eol           BOOLEAN      NOT NULL,
    latest_version   VARCHAR(100),
    CONSTRAINT pk_release_cycle         PRIMARY KEY (release_cycle_id),
    CONSTRAINT fk_release_cycle_library FOREIGN KEY (library_id) REFERENCES library (library_id),
    CONSTRAINT uq_release_cycle         UNIQUE (library_id, cycle_name)
);

CREATE TABLE version (
    version_id       UUID         NOT NULL,
    library_id       UUID         NOT NULL,
    version_string   VARCHAR(100) NOT NULL,
    risk_tier        VARCHAR(10),
    release_cycle_id UUID,
    CONSTRAINT pk_version         PRIMARY KEY (version_id),
    CONSTRAINT risk_tier_valid    CHECK (risk_tier IN ('NONE', 'LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT fk_version_library FOREIGN KEY (library_id)       REFERENCES library       (library_id),
    CONSTRAINT fk_version_cycle   FOREIGN KEY (release_cycle_id) REFERENCES release_cycle (release_cycle_id),
    CONSTRAINT uq_version         UNIQUE (library_id, version_string)
);

CREATE TABLE dependency (
    dependency_id UUID        NOT NULL,
    scan_id       UUID        NOT NULL,
    version_id    UUID        NOT NULL,
    scope         VARCHAR(50),
    relation      VARCHAR(10) NOT NULL,
    CONSTRAINT pk_dependency         PRIMARY KEY (dependency_id),
    CONSTRAINT relation_valid        CHECK (relation IN ('SELF', 'DIRECT', 'INDIRECT')),
    CONSTRAINT fk_dependency_scan    FOREIGN KEY (scan_id)    REFERENCES scan    (scan_id),
    CONSTRAINT fk_dependency_version FOREIGN KEY (version_id) REFERENCES version (version_id)
);

CREATE TABLE version_advisory (
    version_id  UUID        NOT NULL,
    advisory_id VARCHAR(50) NOT NULL,
    CONSTRAINT pk_version_advisory PRIMARY KEY (version_id, advisory_id),
    CONSTRAINT fk_va_version       FOREIGN KEY (version_id)  REFERENCES version  (version_id),
    CONSTRAINT fk_va_advisory      FOREIGN KEY (advisory_id) REFERENCES advisory (advisory_id)
);

-- Recreate FK indexes
CREATE INDEX idx_scan_project_id       ON scan(project_id);
CREATE INDEX idx_dependency_scan_id    ON dependency(scan_id);
CREATE INDEX idx_dependency_version_id ON dependency(version_id);
CREATE INDEX idx_version_library_id    ON version(library_id);
CREATE INDEX idx_version_release_cycle ON version(release_cycle_id);
CREATE INDEX idx_va_advisory_id        ON version_advisory(advisory_id);
