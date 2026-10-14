-- Preserve existing places without attributing them to an active user.
-- The blocked account is a placeholder; ownership can be reassigned by a future admin workflow.

ALTER TABLE places ADD COLUMN owner_id BIGINT;

INSERT INTO users (email, password_hash, display_name, status, created_at, updated_at)
SELECT 'legacy-places@onmyway.invalid', '!', 'Legacy places (migration)', 'BLOCKED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM places WHERE owner_id IS NULL)
ON CONFLICT (email) DO NOTHING;

UPDATE places
SET owner_id = (
    SELECT id FROM users WHERE email = 'legacy-places@onmyway.invalid'
)
WHERE owner_id IS NULL;

ALTER TABLE places ALTER COLUMN owner_id SET NOT NULL;

ALTER TABLE places
    ADD CONSTRAINT fk_places_owner FOREIGN KEY (owner_id) REFERENCES users (id);

CREATE INDEX IF NOT EXISTS idx_places_owner_id ON places (owner_id);
