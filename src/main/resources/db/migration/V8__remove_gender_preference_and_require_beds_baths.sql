ALTER TABLE properties DROP COLUMN gender_preference;

UPDATE properties SET beds = 1 WHERE beds <= 0;
UPDATE properties SET baths = 1 WHERE baths <= 0;

ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_beds_check;
ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_baths_check;
ALTER TABLE properties ADD CONSTRAINT properties_beds_check CHECK (beds > 0);
ALTER TABLE properties ADD CONSTRAINT properties_baths_check CHECK (baths > 0);
ALTER TABLE properties ADD CONSTRAINT properties_description_length_check
    CHECK (char_length(description) <= 500) NOT VALID;
