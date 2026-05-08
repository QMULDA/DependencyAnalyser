CREATE TABLE advisory (
    advisory_id VARCHAR(50) NOT NULL,
    CONSTRAINT pk_advisory PRIMARY KEY (advisory_id)
);

CREATE TABLE release_cycle (
    release_cycle_id INT AUTO_INCREMENT PRIMARY KEY,
    library_id       INT          NOT NULL,
    cycle_name       VARCHAR(50)  NOT NULL,
    eol_from         DATE,
    is_eol           BOOLEAN      NOT NULL,
    latest_version   VARCHAR(100),
    CONSTRAINT fk_release_cycle_library FOREIGN KEY (library_id) REFERENCES library (library_id),
    CONSTRAINT uq_release_cycle UNIQUE (library_id, cycle_name)
);

CREATE TABLE version_advisory (
    version_id  INT         NOT NULL,
    advisory_id VARCHAR(50) NOT NULL,
    CONSTRAINT pk_version_advisory PRIMARY KEY (version_id, advisory_id),
    CONSTRAINT fk_va_version        FOREIGN KEY (version_id)  REFERENCES version  (version_id),
    CONSTRAINT fk_va_advisory       FOREIGN KEY (advisory_id) REFERENCES advisory (advisory_id)
);

ALTER TABLE version ADD COLUMN release_cycle_id INT;
ALTER TABLE version ADD CONSTRAINT fk_version_release_cycle
    FOREIGN KEY (release_cycle_id) REFERENCES release_cycle (release_cycle_id);

CREATE INDEX idx_version_release_cycle_id ON version(release_cycle_id);
CREATE INDEX idx_va_advisory_id           ON version_advisory(advisory_id);
