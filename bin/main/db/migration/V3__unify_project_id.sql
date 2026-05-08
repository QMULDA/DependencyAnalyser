-- Clear data that cannot be backfilled
DELETE FROM dependency;
DELETE FROM scan;
DELETE FROM project;

-- Drop FK from scan to project
ALTER TABLE scan DROP CONSTRAINT fk_scan_project;

-- Replace scan.project_id (INT) with VARCHAR(64)
ALTER TABLE scan DROP COLUMN project_id;
ALTER TABLE scan ADD COLUMN project_id VARCHAR(64) NOT NULL;

-- Remove redundant unique constraint on stable_id before restructuring project PK
ALTER TABLE project DROP CONSTRAINT uq_project_stable_id;

-- Drop INT primary key and column from project
ALTER TABLE project DROP PRIMARY KEY;
ALTER TABLE project DROP COLUMN project_id;

-- Promote stable_id to be the new project_id PK
ALTER TABLE project RENAME COLUMN stable_id TO project_id;
ALTER TABLE project ALTER COLUMN project_id VARCHAR(64) NOT NULL;
ALTER TABLE project ADD PRIMARY KEY (project_id);

-- Re-add FK
ALTER TABLE scan ADD CONSTRAINT fk_scan_project
    FOREIGN KEY (project_id) REFERENCES project (project_id);
