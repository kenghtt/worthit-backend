-- Ensure `experience.active` exists and is safe for existing populated databases.
DO $$
BEGIN
    IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'experience'
              AND column_name = 'active'
    ) THEN
        ALTER TABLE experience
            ADD COLUMN active boolean;
    END IF;
END $$;

UPDATE experience
SET active = false
WHERE active IS NULL;

ALTER TABLE experience
ALTER COLUMN active SET NOT NULL;

ALTER TABLE experience
ALTER COLUMN active SET DEFAULT false;
