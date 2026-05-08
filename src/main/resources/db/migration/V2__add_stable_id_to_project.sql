ALTER TABLE project ADD COLUMN stable_id VARCHAR(64);
ALTER TABLE project ADD CONSTRAINT uq_project_stable_id UNIQUE (stable_id);
