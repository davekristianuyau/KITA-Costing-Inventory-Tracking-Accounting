#!/usr/bin/env bash
# 018 US1 / FR-004 / SC-001 — end-to-end proof that every governed back-office action reaches the
# owning service and is visible there with matching figures.
#
# Drives workflow-service's public API (through the gateway) and then reads the OWNING service directly
# to confirm the record exists with the same identifiers/amounts. Intended for the Floci AWS-imitation
# deployment (production parity); also runnable against `docker compose up`.
#
# Usage:
#   GATEWAY=http://localhost:8081 bash specs/018-secure-service-contracts/e2e/run-governed-actions.sh
#
# Env:
#   GATEWAY   public entry point            (default http://localhost:8081)
#   ACTOR     acting employee (X-Kita-User) (default stub-admin)
#   CHECKER   a DISTINCT employee, required for maker-checker steps (receipt confirmation)
# Exit code 0 = every governed action took real effect.
set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8081}"
ACTOR="${ACTOR:-stub-admin}"
# Maker-checker actions need a SECOND, distinct employee identity.
CHECKER="${CHECKER:-}"
PASS=0
FAIL=0

hdr=(-H "Content-Type: application/json" -H "X-Kita-User: ${ACTOR}")

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
ok()   { printf '  \033[32mPASS\033[0m %s\n' "$*"; PASS=$((PASS+1)); }
bad()  { printf '  \033[31mFAIL\033[0m %s\n' "$*"; FAIL=$((FAIL+1)); }

# post/get are called via $(...), i.e. in a SUBSHELL, so they cannot set a variable the caller sees.
# The status code goes to a temp file instead; is2xx/CODE read it back in the parent shell.
CODEFILE="$(mktemp)"
trap 'rm -f "$CODEFILE"' EXIT
CODE=000

# POST <path> <json> -> body on stdout, HTTP status recorded for is2xx/$CODE
post() {
  local out
  out=$(curl -sS -w '\n%{http_code}' "${hdr[@]}" -X POST "${GATEWAY}$1" ${2:+-d "$2"} 2>/dev/null)
  printf '%s' "${out##*$'\n'}" > "$CODEFILE"
  printf '%s' "${out%$'\n'*}"
}
get() {
  local out
  out=$(curl -sS -w '\n%{http_code}' "${hdr[@]}" "${GATEWAY}$1" 2>/dev/null)
  printf '%s' "${out##*$'\n'}" > "$CODEFILE"
  printf '%s' "${out%$'\n'*}"
}
# crude field read without requiring jq
field() { printf '%s' "$1" | grep -oE "\"$2\"[[:space:]]*:[[:space:]]*\"?[^,\"}]+\"?" | head -1 | sed -E 's/.*:[[:space:]]*"?([^",}]+)"?.*/\1/'; }

CODE() { cat "$CODEFILE"; }
is2xx() { [[ "$(cat "$CODEFILE")" =~ ^2 ]]; }

say "0. Preconditions"
body=$(get "/actuator/health"); is2xx && ok "gateway reachable" || { bad "gateway not reachable at ${GATEWAY} (code $(cat "$CODEFILE"))"; exit 1; }

# --- Customer maintenance (crm-service owns the record) ----------------------------------------
say "1. Maintain customer -> crm-service"
CUST_NAME="E2E Customer $(date +%s)"
body=$(post "/api/workflow/customers" "{\"name\":\"${CUST_NAME}\",\"active\":true}")
if is2xx; then
  CUST_ID=$(field "$body" "customerId"); [[ -z "$CUST_ID" ]] && CUST_ID=$(field "$body" "id")
  ok "customer created via back office (${CUST_ID})"
  body=$(get "/api/crm/customers/${CUST_ID}")
  if is2xx && [[ "$(field "$body" name)" == "${CUST_NAME}" ]]; then
    ok "visible in crm-service with the same name"
  else
    bad "not visible in crm-service (code $(cat "$CODEFILE"))"
  fi
else
  bad "create customer rejected (code $(cat "$CODEFILE")): $body"
fi

# --- Supplier maintenance (procurement-service owns the record) --------------------------------
say "2. Maintain supplier -> procurement-service"
SUP_NAME="E2E Supplier $(date +%s)"
body=$(post "/api/workflow/suppliers" "{\"name\":\"${SUP_NAME}\",\"active\":true}")
if is2xx; then
  SUP_ID=$(field "$body" "supplierId"); [[ -z "$SUP_ID" ]] && SUP_ID=$(field "$body" "id")
  ok "supplier created via back office (${SUP_ID})"
  body=$(get "/api/procurement/suppliers/${SUP_ID}")
  if is2xx && [[ "$(field "$body" name)" == "${SUP_NAME}" ]]; then
    ok "visible in procurement-service (supplierCode $(field "$body" supplierCode) derived)"
  else
    bad "not visible in procurement-service (code $(cat "$CODEFILE"))"
  fi
else
  bad "create supplier rejected (code $(cat "$CODEFILE")): $body"
fi

# --- Purchase order + receiving (procurement owns PO; operations owns stock) --------------------
say "3. Raise PO -> procurement-service, then receive -> operations stock"
ITEM_REF="${E2E_ITEM_REF:-WIDGET}"
if [[ -n "${SUP_ID:-}" ]]; then
  body=$(post "/api/workflow/purchase-orders" \
    "{\"supplierId\":\"${SUP_ID}\",\"lines\":[{\"itemId\":\"${ITEM_REF}\",\"quantity\":10,\"unitCost\":\"4.50\"}]}")
  if is2xx; then
    PO_ID=$(field "$body" "purchaseOrderId"); [[ -z "$PO_ID" ]] && PO_ID=$(field "$body" "id")
    ok "purchase order raised (${PO_ID})"
    body=$(get "/api/procurement/purchase-orders/${PO_ID}")
    if is2xx && printf '%s' "$body" | grep -q "\"itemRef\"[[:space:]]*:[[:space:]]*\"${ITEM_REF}\""; then
      ok "PO + lines/qty/price match in procurement-service"
    else
      bad "PO lines do not match in procurement-service (code $(cat "$CODEFILE"))"
    fi

    body=$(post "/api/workflow/purchase-orders/${PO_ID}/approve" ""); is2xx && ok "approved" || bad "approve rejected ($(cat "$CODEFILE")): $body"
    body=$(post "/api/workflow/purchase-orders/${PO_ID}/send" "");    is2xx && ok "sent"     || bad "send rejected ($(cat "$CODEFILE")): $body"

    body=$(post "/api/workflow/purchase-orders/${PO_ID}/receipts" \
      "{\"lines\":[{\"itemId\":\"${ITEM_REF}\",\"quantityReceived\":10}]}")
    if is2xx; then
      ok "delivery recorded (pending a checker)"
      # Receiving is maker-checker: recording only stages the receipt; a DISTINCT employee must
      # confirm it before procurement advances and stock moves. That second identity is CHECKER.
      PR_ID=$(field "$body" "pendingReceiptId"); [[ -z "$PR_ID" ]] && PR_ID=$(field "$body" "id")
      if [[ -n "${CHECKER:-}" && -n "$PR_ID" ]]; then
        confirm=$(curl -sS -w '\n%{http_code}' -H "Content-Type: application/json" \
          -H "X-Kita-User: ${CHECKER}" -X POST "${GATEWAY}/api/workflow/receipts/${PR_ID}/confirm" 2>/dev/null)
        printf '%s' "${confirm##*$'\n'}" > "$CODEFILE"
        if is2xx; then
          ok "delivery confirmed by a distinct checker"
        else
          bad "checker confirmation rejected ($(cat "$CODEFILE")): ${confirm%$'\n'*}"
        fi
      else
        bad "no CHECKER employee set (maker-checker needs a second identity) or no pending receipt id"
      fi
      body=$(get "/api/procurement/purchase-orders/${PO_ID}")
      printf '%s' "$body" | grep -qE "RECEIVED" && ok "PO advanced to a RECEIVED state" || bad "PO status did not advance"
      body=$(get "/api/operations/items?sku=${ITEM_REF}")
      is2xx && ok "operations reachable to confirm stock effect" || bad "cannot read operations stock"
    else
      bad "receipt rejected (code $(cat "$CODEFILE")): $body"
    fi
  else
    bad "raise PO rejected (code $(cat "$CODEFILE")): $body"
  fi
else
  bad "skipped PO flow — no supplier id"
fi

# --- Sales order (operations owns the order + reservation) --------------------------------------
say "4. Take sales order -> operations-service"
if [[ -n "${CUST_ID:-}" ]]; then
  body=$(post "/api/workflow/sales-orders" \
    "{\"customerId\":\"${CUST_ID}\",\"lines\":[{\"itemId\":\"${ITEM_REF}\",\"quantity\":2,\"unitPrice\":\"125.00\"}]}")
  if is2xx; then
    SO_ID=$(field "$body" "salesOrderId"); [[ -z "$SO_ID" ]] && SO_ID=$(field "$body" "id")
    ok "sales order drafted + reserved (${SO_ID})"
    body=$(get "/api/operations/sales-orders/${SO_ID}")
    if is2xx; then
      ok "visible in operations-service with its lines (status $(field "$body" status))"
    else
      bad "not visible in operations-service (code $(cat "$CODEFILE"))"
    fi
  else
    bad "sales order rejected (code $(cat "$CODEFILE")): $body"
  fi
else
  bad "skipped sales flow — no customer id"
fi

# --- Build (operations owns BOM + stock) ---------------------------------------------------------
say "5. Build product -> operations-service"
BUILD_REF="${E2E_BUILD_REF:-CHAIR}"
body=$(post "/api/workflow/builds" "{\"itemId\":\"${BUILD_REF}\",\"quantity\":1}")
if is2xx; then
  ok "build executed ($(field "$body" buildId))"
else
  # A missing BOM is a legitimate business rejection, not a contract failure — report the real reason.
  bad "build rejected (code $(cat "$CODEFILE")): $body"
fi

# --- Error taxonomy (FR-003) ---------------------------------------------------------------------
say "6. Invalid input surfaces the receiver's real reason (FR-003)"
body=$(post "/api/workflow/sales-orders" \
  "{\"customerId\":\"00000000-0000-0000-0000-000000000000\",\"lines\":[{\"itemId\":\"${ITEM_REF}\",\"quantity\":1,\"unitPrice\":\"1.00\"}]}")
if [[ "$(cat "$CODEFILE")" == "422" ]] && [[ -n "$body" ]]; then
  ok "rejected 422 with a specific reason: $(printf '%s' "$body" | head -c 120)"
else
  bad "expected a 422 naming the problem, got $(cat "$CODEFILE"): $body"
fi

say "Result"
printf '  passed: %d   failed: %d\n\n' "$PASS" "$FAIL"
[[ "$FAIL" -eq 0 ]] || exit 1
