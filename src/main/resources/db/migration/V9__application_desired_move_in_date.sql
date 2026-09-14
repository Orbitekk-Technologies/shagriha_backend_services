ALTER TABLE applications
    ADD COLUMN desired_move_in_date DATE;

UPDATE applications
SET desired_move_in_date = applied_at::date
WHERE desired_move_in_date IS NULL;

ALTER TABLE applications
    ALTER COLUMN desired_move_in_date SET NOT NULL;
