ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

UPDATE users SET role = 'USER';

-- Email is the login name for both password and Google accounts. Use a
-- collision-free intermediate value before normalizing historical usernames.
UPDATE users SET username = id::text;
UPDATE users SET username = lower(email);

ALTER TABLE users
    ADD CONSTRAINT users_role_check CHECK (role IN ('USER'));

CREATE TABLE user_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    phone_number VARCHAR(40),
    image_url TEXT
);

INSERT INTO user_profiles (user_id, name, phone_number, image_url)
SELECT u.id,
       COALESCE(NULLIF(mp.name, ''), NULLIF(tp.name, ''), u.email),
       COALESCE(mp.phone_number, tp.phone_number),
       COALESCE(mp.image_url, tp.image_url)
FROM users u
LEFT JOIN manager_profiles mp ON mp.user_id = u.id
LEFT JOIN tenant_profiles tp ON tp.user_id = u.id;
