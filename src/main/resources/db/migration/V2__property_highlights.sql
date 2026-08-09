CREATE TABLE property_highlights (
    property_id BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    highlight VARCHAR(60) NOT NULL,
    PRIMARY KEY(property_id, highlight)
);
