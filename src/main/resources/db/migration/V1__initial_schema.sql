CREATE TABLE project (
    project_id   INT           AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(255)  NOT NULL,
    path         VARCHAR(1024) NOT NULL,
    last_scanned TIMESTAMP
);

CREATE TABLE scan (
    scan_id    INT       AUTO_INCREMENT PRIMARY KEY,
    project_id INT       NOT NULL,
    scanned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scan_project FOREIGN KEY (project_id) REFERENCES project (project_id)
);

CREATE TABLE library (
    library_id  INT          AUTO_INCREMENT PRIMARY KEY,
    group_id    VARCHAR(255) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,
    CONSTRAINT uq_library UNIQUE (group_id, artifact_id)
);

CREATE TABLE version (
    version_id     INT          AUTO_INCREMENT PRIMARY KEY,
    library_id     INT          NOT NULL,
    version_string VARCHAR(100) NOT NULL,
    risk_tier      VARCHAR(10),
    CONSTRAINT risk_tier_valid CHECK (risk_tier IN ('NONE', 'LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT fk_version_library FOREIGN KEY (library_id) REFERENCES library (library_id),
    CONSTRAINT uq_version UNIQUE (library_id, version_string)
);

CREATE TABLE dependency (
    dependency_id INT        AUTO_INCREMENT PRIMARY KEY,
    scan_id       INT        NOT NULL,
    version_id    INT        NOT NULL,
    scope         VARCHAR(50),
    relation      VARCHAR(10) NOT NULL,
    CONSTRAINT relation_valid CHECK (relation IN ('SELF', 'DIRECT', 'INDIRECT')),
    CONSTRAINT fk_dependency_scan    FOREIGN KEY (scan_id)    REFERENCES scan    (scan_id),
    CONSTRAINT fk_dependency_version FOREIGN KEY (version_id) REFERENCES version (version_id)
);
