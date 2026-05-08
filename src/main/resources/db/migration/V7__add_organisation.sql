CREATE TABLE organisation (
    org_id VARCHAR(64)  NOT NULL,
    name   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_organisation      PRIMARY KEY (org_id),
    CONSTRAINT uq_organisation_name UNIQUE (name)
);

ALTER TABLE project ADD COLUMN org_id VARCHAR(64);
ALTER TABLE project ADD CONSTRAINT fk_project_org
    FOREIGN KEY (org_id) REFERENCES organisation (org_id);
