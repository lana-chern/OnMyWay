-- Give each refresh-token family a fixed 90-day lifetime from its first token.
-- Existing families are backfilled from their earliest creation time.
ALTER TABLE refresh_tokens
    ADD COLUMN family_expires_at TIMESTAMP WITH TIME ZONE;

WITH family_lifetimes AS (
    SELECT family_id, MIN(created_at) + INTERVAL '90 days' AS family_expires_at
    FROM refresh_tokens
    GROUP BY family_id
)
UPDATE refresh_tokens token
SET family_expires_at = family_lifetimes.family_expires_at
FROM family_lifetimes
WHERE token.family_id = family_lifetimes.family_id;

-- Existing sliding expirations may extend beyond the new absolute family deadline.
UPDATE refresh_tokens
SET expires_at = family_expires_at
WHERE expires_at > family_expires_at;

ALTER TABLE refresh_tokens
    ALTER COLUMN family_expires_at SET NOT NULL;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT ck_refresh_token_expiration_within_family
        CHECK (expires_at <= family_expires_at);

CREATE INDEX idx_refresh_tokens_family_expires_at
    ON refresh_tokens(family_expires_at);
