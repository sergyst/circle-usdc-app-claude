#!/usr/bin/env bash
#
# Quick smoke test for the Circle webhook endpoints.
#
# It does NOT need real Circle traffic or valid signatures. Instead it checks
# that the endpoints are wired correctly and that the security guards behave:
#   - webhook paths bypass the X-App-Api-Key filter (not 401'd by that filter)
#   - unsigned / badly-signed bodies are rejected with 401 invalid_signature
#   - the SNS host allow-list refuses non-amazonaws.com confirmation URLs
#
# A genuinely signed notification can only come from Circle, so "signature
# rejected" here is the CORRECT, passing outcome.
#
# Usage:
#   ./scripts/webhook-smoke-test.sh                 # defaults to http://localhost:8080
#   BASE_URL=https://YOUR-ID.ngrok-free.app ./scripts/webhook-smoke-test.sh
#
set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
WALLETS_URL="$BASE_URL/api/webhooks/circle/wallets"
MINT_URL="$BASE_URL/api/webhooks/circle/mint"

pass=0
fail=0

green() { printf '\033[32m%s\033[0m' "$1"; }
red()   { printf '\033[31m%s\033[0m' "$1"; }

# check <name> <expected-status> <actual-status> [expected-substring] [body]
check() {
  local name="$1" expected="$2" actual="$3" needle="${4:-}" body="${5:-}"
  local ok=1
  [ "$actual" = "$expected" ] || ok=0
  if [ -n "$needle" ] && [[ "$body" != *"$needle"* ]]; then ok=0; fi
  if [ "$ok" = 1 ]; then
    echo "  $(green PASS) $name (HTTP $actual)"
    pass=$((pass + 1))
  else
    echo "  $(red FAIL) $name — expected HTTP $expected${needle:+ containing '$needle'}, got HTTP $actual"
    [ -n "$body" ] && echo "         body: $body"
    fail=$((fail + 1))
  fi
}

# request <method> <url> [data] [extra curl args...]
# echoes: "<http_status>\t<body>"
request() {
  local method="$1" url="$2" data="${3:-}"; shift 3 || true
  local out
  if [ -n "$data" ]; then
    out=$(curl -sS -X "$method" "$url" -H 'Content-Type: application/json' \
      --data "$data" -w $'\n%{http_code}' "$@" 2>/dev/null)
  else
    out=$(curl -sS -X "$method" "$url" -w $'\n%{http_code}' "$@" 2>/dev/null)
  fi
  local code="${out##*$'\n'}"
  local body="${out%$'\n'*}"
  printf '%s\t%s' "$code" "$body"
}

echo "Target: $BASE_URL"
echo

# 1. App is up (health endpoint is public).
IFS=$'\t' read -r code body < <(request GET "$BASE_URL/api/health")
check "app health reachable" 200 "$code" "" "$body"

# 2. HEAD /wallets — Circle's health probe should get 200.
code=$(curl -sS -o /dev/null -I -w '%{http_code}' "$WALLETS_URL" 2>/dev/null)
check "HEAD /wallets health check" 200 "$code"

# 3. Unsigned POST /wallets — must be rejected (401), proving:
#    (a) the X-App-Api-Key filter is skipped for webhook paths, and
#    (b) signature verification runs and fails closed.
IFS=$'\t' read -r code body < <(request POST "$WALLETS_URL" \
  '{"notificationType":"transactions.inbound","notification":{"id":"smoke"}}')
check "unsigned /wallets rejected" 401 "$code" "invalid_signature" "$body"

# 4. POST /wallets with junk signature headers — still 401 (bad key/sig).
IFS=$'\t' read -r code body < <(request POST "$WALLETS_URL" \
  '{"notificationType":"transactions.inbound","notification":{"id":"smoke"}}' \
  -H 'X-Circle-Key-Id: 00000000-0000-0000-0000-000000000000' \
  -H 'X-Circle-Signature: not-a-real-signature')
check "bad-signature /wallets rejected" 401 "$code" "invalid_signature" "$body"

# 5. Mint SNS SubscriptionConfirmation with a NON-amazonaws host — the host
#    allow-list must refuse it (200 with status confirmation_failed).
IFS=$'\t' read -r code body < <(request POST "$MINT_URL" \
  '{"Type":"SubscriptionConfirmation","SubscribeURL":"https://evil.example.com/confirm"}')
check "SNS confirm rejects untrusted host" 200 "$code" "confirmation_failed" "$body"

# 6. Mint Notification with an untrusted SigningCertURL — must be 401.
IFS=$'\t' read -r code body < <(request POST "$MINT_URL" \
  '{"Type":"Notification","MessageId":"m1","Message":"{}","Signature":"AAAA","SigningCertURL":"https://evil.example.com/cert.pem"}')
check "SNS notif untrusted cert rejected" 401 "$code" "invalid_signature" "$body"

echo
echo "Result: $(green "$pass passed"), $([ "$fail" -eq 0 ] && echo "$fail failed" || red "$fail failed")"
[ "$fail" -eq 0 ]
