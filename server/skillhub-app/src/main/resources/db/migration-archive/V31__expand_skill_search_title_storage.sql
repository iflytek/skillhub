ALTER TABLE skill_search_document
    ALTER COLUMN title TYPE VARCHAR(512);

ALTER TABLE skill_search_document
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(keywords, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(search_text, '')), 'C')
) STORED;

CREATE INDEX idx_search_vector ON skill_search_document USING GIN (search_vector);
