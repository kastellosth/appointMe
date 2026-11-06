#!/bin/bash
# Checks a running deployment from the outside. Creates one throw-away user, business and customer.
# Usage: scripts/smoke-test.sh [base-url]
set -u

BASE="${1:-http://localhost:8080}"
UNIQUE="$(date +%s)"
EMAIL="smoke-$UNIQUE@example.com"
PASSWORD="smoke-pass-$UNIQUE"
FAILED=0
BODY=""
STATUS=""

request() { # method path token json
    local args=(-s -o /tmp/smoke-body.$$ -w '%{http_code}' -X "$1" "$BASE$2")
    [ -n "${3:-}" ] && args+=(-H "Authorization: Bearer $3")
    [ -n "${4:-}" ] && args+=(-H 'Content-Type: application/json' -d "$4")
    [ -n "${5:-}" ] && args+=(-H "Idempotency-Key: $5")
    STATUS="$(curl "${args[@]}")"
    BODY="$(cat /tmp/smoke-body.$$ 2>/dev/null)"
    rm -f /tmp/smoke-body.$$
}

check() { # name expected-status
    if [ "$STATUS" = "$2" ]; then
        echo "PASS  $1 ($STATUS)"
    else
        echo "FAIL  $1: expected $2, got $STATUS: $BODY"
        FAILED=1
    fi
}

field() { # name
    sed -nE "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"?([^\",}]+).*/\1/p" <<< "$BODY" | head -1
}

request GET /actuator/health
check "health is public" 200

request GET /actuator/env
check "other actuator endpoints need a login" 401

request GET /customers
check "the API needs a login" 401

request POST /auth/register "" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Smoke Test\"}"
check "register" 201

request POST /auth/login "" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
check "login" 200
USER_TOKEN="$(field accessToken)"

request POST /tenants "$USER_TOKEN" "{\"name\":\"Smoke $UNIQUE\",\"slug\":\"smoke-$UNIQUE\",\"timezone\":\"Europe/Athens\"}"
check "create a business" 201
TENANT_ID="$(field id)"

request POST "/auth/select-tenant/$TENANT_ID" "$USER_TOKEN"
check "select the business" 200
TENANT_TOKEN="$(field accessToken)"

request POST /customers "$TENANT_TOKEN" '{"fullName":"Smoke Customer"}'
check "write through the application role" 201
CUSTOMER_ID="$(field id)"

request GET /customers "$TENANT_TOKEN"
check "read it back" 200

request POST /invoices "$TENANT_TOKEN" "{\"customerId\":$CUSTOMER_ID,\"lines\":[{\"description\":\"Smoke\",\"quantity\":1,\"unitPrice\":10.00}]}"
check "create an invoice" 201
INVOICE_ID="$(field id)"

request POST "/invoices/$INVOICE_ID/issue" "$TENANT_TOKEN"
check "issue it (takes a number)" 200

request POST "/invoices/$INVOICE_ID/payments" "$TENANT_TOKEN" '{"amount":10.00,"method":"CASH"}' "smoke-$UNIQUE"
check "pay it" 201

request POST "/invoices/$INVOICE_ID/payments" "$TENANT_TOKEN" '{"amount":10.00,"method":"CASH"}' "smoke-$UNIQUE"
check "same key replays the payment" 201

request GET "/audit-events" "$TENANT_TOKEN"
check "audit log" 200

[ "$FAILED" = 0 ] && echo "ALL PASSED" || echo "SOME FAILED"
exit "$FAILED"
