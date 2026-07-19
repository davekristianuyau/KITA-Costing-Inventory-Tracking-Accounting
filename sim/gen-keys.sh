#!/usr/bin/env bash
# Generate the SHARED session keys and print them as env exports:
#   • RSA 2048 keypair — identity SIGNS with the private key (RS256); the edge + every client gateway
#     VERIFY with the public key only (asymmetric → no backend can mint a token).
#   • 32-byte AES key — JWE encryption of the token at rest in the cookie.
# Private = base64 PKCS8 DER; public = base64 X509 DER; enc = base64 32 bytes. Single-line for env use.
# These are SECRETS: never commit. sim-up.sh writes them to sim/.env.keys (gitignored).
set -euo pipefail

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/priv.pem" 2>/dev/null

priv="$(openssl pkey -in "$tmp/priv.pem" -outform DER | base64 -w0)"
pub="$(openssl pkey -in "$tmp/priv.pem" -pubout -outform DER | base64 -w0)"
enc="$(openssl rand 32 | base64 -w0)"

echo "IDENTITY_JWT_PRIVATE_KEY=$priv"
echo "IDENTITY_JWT_PUBLIC_KEY=$pub"
echo "IDENTITY_JWT_ENC_KEY=$enc"
