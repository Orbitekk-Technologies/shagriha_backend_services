ALTER TABLE locations RENAME COLUMN address TO address_line1;
ALTER TABLE locations RENAME COLUMN state TO state_name;
ALTER TABLE locations RENAME COLUMN country TO country_name;
ALTER TABLE locations ADD COLUMN address_line2 VARCHAR(120);
ALTER TABLE locations ADD COLUMN state_code VARCHAR(20);
ALTER TABLE locations ADD COLUMN country_code CHAR(2) NOT NULL DEFAULT 'US';
ALTER TABLE locations ADD COLUMN formatted_address VARCHAR(500);
ALTER TABLE locations ADD COLUMN mapbox_feature_id VARCHAR(255);

CREATE TABLE property_nearby_cache (
    property_id BIGINT PRIMARY KEY REFERENCES properties(id) ON DELETE CASCADE,
    response_json JSONB NOT NULL,
    cached_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
