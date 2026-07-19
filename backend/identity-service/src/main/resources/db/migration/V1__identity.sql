-- Identity store (009): clients (tenants) + users. Holds no client business data.
-- Usernames are unique WITHIN a client; company_id + username resolve exactly one user (FR-019).

CREATE TABLE client (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       TEXT NOT NULL UNIQUE,        -- the identifier entered at login (e.g. "client-a")
    name             TEXT NOT NULL,
    cloud_preference TEXT NOT NULL,               -- AWS | GCP | AZURE (determines prod target, FR-012)
    backend_endpoint TEXT NOT NULL,               -- how the edge reaches this client's backend
    active           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id     UUID NOT NULL REFERENCES client(id),
    username      TEXT NOT NULL,
    password_hash TEXT NOT NULL,                  -- BCrypt; never returned/logged
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until  TIMESTAMPTZ,
    UNIQUE (client_id, username)
);
