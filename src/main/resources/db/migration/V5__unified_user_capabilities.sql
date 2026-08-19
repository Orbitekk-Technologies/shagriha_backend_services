UPDATE users SET role = 'MANAGER' WHERE role <> 'MANAGER';

INSERT INTO manager_profiles (user_id, name)
SELECT tp.user_id, tp.name
FROM tenant_profiles tp
WHERE NOT EXISTS (
    SELECT 1 FROM manager_profiles mp WHERE mp.user_id = tp.user_id
);
