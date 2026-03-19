DROP INDEX IF EXISTS idx_search_vector;

ALTER TABLE skill_search_document
    DROP COLUMN search_vector;

ALTER TABLE skill_search_document
    ALTER COLUMN keywords TYPE TEXT;
