ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_baths_check;
ALTER TABLE properties ALTER COLUMN baths TYPE NUMERIC(4, 1) USING baths::NUMERIC;
ALTER TABLE properties ADD CONSTRAINT properties_baths_check
    CHECK (baths > 0 AND baths * 2 = TRUNC(baths * 2));
