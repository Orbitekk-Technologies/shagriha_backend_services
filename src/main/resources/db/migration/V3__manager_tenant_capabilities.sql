INSERT INTO tenant_profiles (user_id, name)
SELECT mp.user_id, mp.name
FROM manager_profiles mp
WHERE NOT EXISTS (
    SELECT 1
    FROM tenant_profiles tp
    WHERE tp.user_id = mp.user_id
);
