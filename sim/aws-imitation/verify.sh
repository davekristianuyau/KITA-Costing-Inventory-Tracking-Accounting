#!/usr/bin/env bash
# US4 verification (T026, SC-005): the client's Terraform actually applied against LocalStack (resources
# exist), NO real AWS credentials were used, and the login flow reaches the client's imitated deployment
# (its running feature-008 stack — the compute stand-in). Run after deploy.sh (+ the sim being up).
# Usage: sim/aws-imitation/verify.sh [client-a]
set -uo pipefail
cd "$(dirname "$0")/../.." # repo root

CLIENT="${1:-client-a}"
ENV="${ENV:-stg}"
NAME="$CLIENT-$ENV"
FRONTEND="http://localhost:${FRONTEND_PORT:-8080}"
if [ -f .env ]; then set -a; . ./.env; set +a; fi
PASSWORD="${IDENTITY_SEED_PASSWORD:-demo-pass}"
# AWS CLI in a container, pointed at Floci (entrypoint is `aws`).
AWS="docker compose -p kita-floci -f sim/aws-imitation/docker-compose.floci.yml run --rm -T awscli --endpoint-url http://floci:4566"

fail() { echo "VERIFY FAIL: $*" >&2; exit 1; }

echo "1) Floci holds the imitated resources for $NAME"
$AWS s3api list-buckets --query 'Buckets[].Name' --output text 2>/dev/null | grep -qw "$NAME-storage" \
  || fail "S3 bucket $NAME-storage not found in Floci"
$AWS secretsmanager list-secrets --query 'SecretList[].Name' --output text 2>/dev/null | grep -qw "$NAME-db-credentials" \
  || fail "Secrets Manager secret $NAME-db-credentials not found"
$AWS ec2 describe-vpcs --filters "Name=tag:Name,Values=$NAME-vpc" --query 'Vpcs[].VpcId' --output text 2>/dev/null | grep -q 'vpc-' \
  || fail "VPC $NAME-vpc not found"
echo "   S3 bucket, DB-credentials secret, and VPC all present."

echo "2) no real AWS credentials were used (SC-005)"
[ "${AWS_ACCESS_KEY_ID:-test}" = "test" ] || fail "a non-'test' AWS_ACCESS_KEY_ID is set in the environment"
# The secret's DB URL points at the local compute/DB stand-in, not a real RDS endpoint.
url="$($AWS secretsmanager get-secret-value --secret-id "$NAME-db-credentials" --query SecretString --output text 2>/dev/null)"
echo "$url" | grep -q "kita-$CLIENT-postgres" || fail "DB secret does not point at the local stand-in"
echo "   only the Floci dummy creds were used; secret points at the local stand-in."

echo "3) the login flow reaches $CLIENT's imitated deployment"
jar="$(mktemp)"
user="$([ "$CLIENT" = "client-a" ] && echo alice || echo bob)"
code="$(curl -s -o /dev/null -w '%{http_code}' -c "$jar" -H 'Content-Type: application/json' \
  -d "{\"company\":\"$CLIENT\",\"username\":\"$user\",\"password\":\"$PASSWORD\"}" \
  "$FRONTEND/auth/login" 2>/dev/null)"
[ "$code" = "200" ] || { rm -f "$jar"; fail "login for $CLIENT returned $code (is the sim up? run sim/sim-up.sh)"; }
api="$(curl -s -o /dev/null -w '%{http_code}' -b "$jar" --max-time 10 "$FRONTEND/api/operations/actuator/health" 2>/dev/null)"
rm -f "$jar"
case "$api" in
  401|403|000) fail "authenticated /api for $CLIENT returned $api (did not reach its deployment)";;
  *) echo "   $CLIENT: login=200, /api=$api (routed to its running stand-in deployment).";;
esac

echo "VERIFY PASS"
