ALTER TABLE properties
    ADD COLUMN listed_by VARCHAR(20) NOT NULL DEFAULT 'OWNER',
    ADD CONSTRAINT properties_listed_by_check CHECK (listed_by IN ('AGENT', 'OWNER'));

CREATE INDEX properties_listed_by_idx ON properties(listed_by);
