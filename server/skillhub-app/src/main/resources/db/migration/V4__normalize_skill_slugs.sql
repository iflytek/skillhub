CREATE OR REPLACE FUNCTION skillhub_slugify(raw_text TEXT)
RETURNS VARCHAR(100)
LANGUAGE plpgsql
AS $$
DECLARE
    slug TEXT;
BEGIN
    IF raw_text IS NULL OR btrim(raw_text) = '' THEN
        RAISE EXCEPTION 'skill slug source cannot be blank';
    END IF;

    slug := lower(btrim(raw_text));
    slug := regexp_replace(slug, '[^a-z0-9]+', '-', 'g');
    slug := regexp_replace(slug, '^-+', '');
    slug := regexp_replace(slug, '-+$', '');
    slug := regexp_replace(slug, '-{2,}', '-', 'g');

    IF slug = '' THEN
        RAISE EXCEPTION 'skill slug normalization produced empty slug for input %', raw_text;
    END IF;

    IF length(slug) < 2 OR length(slug) > 64 THEN
        RAISE EXCEPTION 'normalized skill slug % has invalid length', slug;
    END IF;

    IF slug IN ('admin', 'api', 'dashboard', 'search', 'auth', 'me', 'global', 'system', 'static', 'assets', 'health') THEN
        RAISE EXCEPTION 'normalized skill slug % is reserved', slug;
    END IF;

    RETURN slug::VARCHAR(100);
END;
$$;

DO $$
BEGIN
    IF EXISTS (
        WITH normalized AS (
            SELECT id, namespace_id, slug, skillhub_slugify(slug) AS normalized_slug
            FROM skill
        )
        SELECT 1
        FROM normalized
        GROUP BY namespace_id, normalized_slug
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'skill slug normalization would create duplicate slugs; resolve manually before applying migration';
    END IF;

    UPDATE skill
    SET slug = skillhub_slugify(slug)
    WHERE slug <> skillhub_slugify(slug);
END;
$$;

DROP FUNCTION skillhub_slugify(TEXT);
