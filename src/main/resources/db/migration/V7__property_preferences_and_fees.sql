ALTER TABLE properties
    ADD COLUMN pet_count INTEGER CHECK (pet_count >= 0),
    ADD COLUMN pet_fee NUMERIC(12,2) CHECK (pet_fee >= 0),
    ADD COLUMN parking_fee NUMERIC(12,2) CHECK (parking_fee >= 0),
    ADD COLUMN smoking_included BOOLEAN NOT NULL DEFAULT FALSE;
