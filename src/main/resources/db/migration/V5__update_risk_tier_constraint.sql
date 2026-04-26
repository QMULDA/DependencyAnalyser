-- Replace the original risk_tier check (LOW/MEDIUM/HIGH) with one that also permits NONE.
-- NONE means the version is the newest of its library in the current scan.
ALTER TABLE version ALTER COLUMN risk_tier DROP CONSTRAINT;
ALTER TABLE version ALTER COLUMN risk_tier SET CHECK (risk_tier IN ('NONE', 'LOW', 'MEDIUM', 'HIGH'));
