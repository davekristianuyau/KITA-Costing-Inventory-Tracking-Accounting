#!/usr/bin/env bash
# 018 — dev cert bootstrap for internal mTLS + datastore TLS (FR-011/FR-012, SC-008).
# Generates a dev CA and one server+client cert per service (SAN = docker service name), plus server
# certs for Postgres and Redis, into ./generated (gitignored — nothing secret is ever committed).
# Run automatically by the compose/Floci stack before services start, so `docker compose up` comes up
# encrypted with NO manual cert steps. Idempotent: skips regeneration if the CA already exists.
#
# Usage: docker/certs/bootstrap-certs.sh [output-dir]  (default: docker/certs/generated)
set -euo pipefail

OUT="${1:-$(dirname "$0")/generated}"
DAYS_CA=3650
DAYS_LEAF=825
# Services that receive and/or make internal TLS calls (SAN = compose service name).
SERVICES=(operations-service hr-service crm-service procurement-service workflow-service gateway)
# Datastores that terminate TLS.
DATASTORES=(postgres redis)
PASS="${CERT_KEYSTORE_PASSWORD:-changeit}"

mkdir -p "$OUT"

if [[ -f "$OUT/ca.crt" ]]; then
  echo "[bootstrap-certs] CA already present in $OUT — skipping (delete it to regenerate)."
  exit 0
fi

echo "[bootstrap-certs] generating dev CA in $OUT"
openssl genrsa -out "$OUT/ca.key" 4096 >/dev/null 2>&1
openssl req -x509 -new -nodes -key "$OUT/ca.key" -sha256 -days "$DAYS_CA" \
  -subj "/O=KITA-dev/CN=KITA-dev-CA" -out "$OUT/ca.crt" >/dev/null 2>&1

issue_cert() {
  local name="$1"
  echo "[bootstrap-certs] issuing cert for $name"
  openssl genrsa -out "$OUT/$name.key" 2048 >/dev/null 2>&1
  openssl req -new -key "$OUT/$name.key" -subj "/O=KITA-dev/CN=$name" -out "$OUT/$name.csr" >/dev/null 2>&1
  openssl x509 -req -in "$OUT/$name.csr" -CA "$OUT/ca.crt" -CAkey "$OUT/ca.key" -CAcreateserial \
    -days "$DAYS_LEAF" -sha256 \
    -extfile <(printf "subjectAltName=DNS:%s,DNS:localhost\nextendedKeyUsage=serverAuth,clientAuth\n" "$name") \
    -out "$OUT/$name.crt" >/dev/null 2>&1
  # PKCS12 bundle (cert + key) for Spring Boot SSL bundles; truststore holds the CA.
  openssl pkcs12 -export -inkey "$OUT/$name.key" -in "$OUT/$name.crt" -certfile "$OUT/ca.crt" \
    -name "$name" -passout "pass:$PASS" -out "$OUT/$name.p12" >/dev/null 2>&1
  rm -f "$OUT/$name.csr"
}

for svc in "${SERVICES[@]}"; do issue_cert "$svc"; done
for ds in "${DATASTORES[@]}"; do issue_cert "$ds"; done

# Shared PKCS12 truststore holding just the CA (services trust peers signed by it). Built with openssl
# rather than keytool so this script needs no JRE in the init container.
openssl pkcs12 -export -nokeys -in "$OUT/ca.crt" -caname kita-dev-ca \
  -passout "pass:$PASS" -out "$OUT/truststore.p12"

# Postgres and Redis refuse to start if their private key is group/world-readable, and Postgres
# additionally requires the key to be owned by the account it runs as (uid 70 on the alpine images,
# 999 on the debian ones — override with POSTGRES_UID if you switch base image).
PG_UID="${POSTGRES_UID:-70}"
REDIS_UID="${REDIS_UID:-999}"
chmod 0600 "$OUT/postgres.key" "$OUT/redis.key"
chown "$PG_UID:$PG_UID" "$OUT/postgres.key" "$OUT/postgres.crt" 2>/dev/null || \
  echo "[bootstrap-certs] warn: could not chown postgres key (need root in the init container)"
chown "$REDIS_UID:$REDIS_UID" "$OUT/redis.key" "$OUT/redis.crt" 2>/dev/null || true
# The CA must stay readable by every service that validates a peer.
chmod 0644 "$OUT/ca.crt"

echo "[bootstrap-certs] done — bundles + truststore in $OUT (gitignored)."
