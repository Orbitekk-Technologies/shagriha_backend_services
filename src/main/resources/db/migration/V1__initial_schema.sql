CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    role VARCHAR(20) NOT NULL CHECK (role IN ('TENANT', 'MANAGER')),
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('LOCAL', 'GOOGLE')),
    provider_subject VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_subject)
);

CREATE TABLE tenant_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    phone_number VARCHAR(40),
    image_url TEXT
);

CREATE TABLE manager_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    phone_number VARCHAR(40),
    image_url TEXT
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(120) NOT NULL,
    state VARCHAR(120) NOT NULL,
    country VARCHAR(120) NOT NULL,
    postal_code VARCHAR(30) NOT NULL,
    coordinates geography(Point, 4326) NOT NULL
);
CREATE INDEX locations_coordinates_gix ON locations USING GIST (coordinates);

CREATE TABLE properties (
    id BIGSERIAL PRIMARY KEY,
    manager_user_id UUID NOT NULL REFERENCES users(id),
    location_id BIGINT NOT NULL REFERENCES locations(id),
    name VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    stay_type VARCHAR(30) NOT NULL,
    bath_type VARCHAR(30) NOT NULL,
    gender_preference VARCHAR(30) NOT NULL,
    price_per_month NUMERIC(12,2) NOT NULL CHECK (price_per_month >= 0),
    security_deposit NUMERIC(12,2) NOT NULL CHECK (security_deposit >= 0),
    application_fee NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (application_fee >= 0),
    pets_allowed BOOLEAN NOT NULL,
    parking_included BOOLEAN NOT NULL,
    beds INTEGER NOT NULL CHECK (beds >= 0),
    baths INTEGER NOT NULL CHECK (baths >= 0),
    square_feet INTEGER NOT NULL CHECK (square_feet > 0),
    property_type VARCHAR(40) NOT NULL,
    available_from DATE,
    posted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'
);
CREATE INDEX properties_manager_idx ON properties(manager_user_id);
CREATE INDEX properties_price_idx ON properties(price_per_month);

CREATE TABLE property_photos (
    id BIGSERIAL PRIMARY KEY,
    property_id BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    display_order SMALLINT NOT NULL,
    UNIQUE(property_id, display_order)
);
CREATE TABLE property_amenities (
    property_id BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    amenity VARCHAR(60) NOT NULL,
    PRIMARY KEY(property_id, amenity)
);
CREATE TABLE tenant_favorites (
    tenant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    property_id BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(tenant_user_id, property_id)
);

CREATE TABLE applications (
    id BIGSERIAL PRIMARY KEY,
    property_id BIGINT NOT NULL REFERENCES properties(id),
    tenant_user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(160) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(40) NOT NULL,
    message TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','DENIED')),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(property_id, tenant_user_id)
);

CREATE TABLE leases (
    id BIGSERIAL PRIMARY KEY,
    property_id BIGINT NOT NULL REFERENCES properties(id),
    tenant_user_id UUID NOT NULL REFERENCES users(id),
    application_id BIGINT UNIQUE REFERENCES applications(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    rent NUMERIC(12,2) NOT NULL,
    deposit NUMERIC(12,2) NOT NULL,
    CHECK (end_date > start_date)
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    lease_id BIGINT NOT NULL REFERENCES leases(id) ON DELETE CASCADE,
    amount_due NUMERIC(12,2) NOT NULL,
    amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
    due_date DATE NOT NULL,
    payment_date TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','PAID','PARTIALLY_PAID','OVERDUE'))
);

CREATE TABLE payment_methods (
    id BIGSERIAL PRIMARY KEY,
    tenant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    provider_payment_method_id VARCHAR(255) NOT NULL UNIQUE,
    brand VARCHAR(40) NOT NULL,
    last4 CHAR(4) NOT NULL,
    expiry_month SMALLINT NOT NULL,
    expiry_year SMALLINT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    billing_address VARCHAR(255), city VARCHAR(120), state VARCHAR(120),
    country VARCHAR(120), postal_code VARCHAR(30)
);

