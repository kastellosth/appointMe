#!/bin/bash
# Creates a demo business through the public API, so it works on any deployment without database access.
# Usage: scripts/demo-seed.sh <base-url>
# Stops with an error if the demo owner already exists.
set -u

BASE="${1:?usage: scripts/demo-seed.sh <base-url>}"
EMAIL="demo-owner@example.com"
PASSWORD="demo-owner-pass"
BODY=""
STATUS=""

request() { # method path token json idempotency-key
    local args=(-s -o /tmp/demo-body.$$ -w '%{http_code}' -X "$1" "$BASE$2")
    [ -n "${3:-}" ] && args+=(-H "Authorization: Bearer $3")
    [ -n "${4:-}" ] && args+=(-H 'Content-Type: application/json' -d "$4")
    [ -n "${5:-}" ] && args+=(-H "Idempotency-Key: $5")
    STATUS="$(curl "${args[@]}")"
    BODY="$(cat /tmp/demo-body.$$ 2>/dev/null)"
    rm -f /tmp/demo-body.$$
}

must() { # expected-status step-name
    if [ "$STATUS" != "$1" ]; then
        echo "FAILED at '$2': expected $1, got $STATUS: $BODY"
        exit 1
    fi
    echo "ok  $2"
}

field() { # name
    sed -nE "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"?([^\",}]+).*/\1/p" <<< "$BODY" | head -1
}

request POST /auth/register "" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Demo Owner\"}"
must 201 "register the demo owner"

request POST /auth/login "" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
must 200 "log in"
USER_TOKEN="$(field accessToken)"

request POST /tenants "$USER_TOKEN" '{"name":"Demo Barber","slug":"demo-barber","timezone":"Europe/Athens"}'
must 201 "create the business"
TENANT_ID="$(field id)"

request POST "/auth/select-tenant/$TENANT_ID" "$USER_TOKEN"
must 200 "select it"
TOKEN="$(field accessToken)"

request PUT /tenants/current/billing "$TOKEN" '{"taxRate":0.24,"currency":"EUR"}'
must 200 "set billing"

request GET /staff "$TOKEN"
must 200 "find the owner's staff id"
STAFF_ID="$(field membershipId)"

INTERVALS=""
for DAY in MONDAY TUESDAY WEDNESDAY THURSDAY FRIDAY SATURDAY SUNDAY; do
    INTERVALS="$INTERVALS${INTERVALS:+,}{\"dayOfWeek\":\"$DAY\",\"start\":\"09:00\",\"end\":\"18:00\"}"
done
request PUT "/staff/$STAFF_ID/working-hours" "$TOKEN" "{\"intervals\":[$INTERVALS]}"
must 200 "set working hours"

request POST /services "$TOKEN" '{"name":"Haircut","durationMinutes":30,"price":25.00}'
must 201 "add a service"
HAIRCUT="$(field id)"
request POST /services "$TOKEN" '{"name":"Beard Trim","durationMinutes":15,"price":10.00}'
must 201 "add a second service"

request POST /customers "$TOKEN" '{"fullName":"Demo Customer One","email":"customer-one@example.com"}'
must 201 "add a customer"
CUSTOMER="$(field id)"
request POST /customers "$TOKEN" '{"fullName":"Demo Customer Two","email":"customer-two@example.com"}'
must 201 "add a second customer"

# 10:00 UTC is inside working hours in Athens all year.
for DAYS in 2 3 4; do
    DATE="$(date -u -d "+$DAYS days" +%F)"
    request POST /appointments "$TOKEN" "{\"staffId\":$STAFF_ID,\"customerId\":$CUSTOMER,\"serviceId\":$HAIRCUT,\"startsAt\":\"${DATE}T10:00:00Z\"}"
    must 201 "book an appointment on $DATE"
done

request POST /invoices "$TOKEN" "{\"customerId\":$CUSTOMER,\"lines\":[{\"description\":\"Haircut\",\"quantity\":1,\"unitPrice\":25.00}]}"
must 201 "create an invoice"
INVOICE="$(field id)"
request POST "/invoices/$INVOICE/issue" "$TOKEN"
must 200 "issue it"
request POST "/invoices/$INVOICE/payments" "$TOKEN" '{"amount":31.00,"method":"CARD"}' "demo-payment-1"
must 201 "pay it"

echo "Demo ready. Log in as $EMAIL / $PASSWORD, then select business $TENANT_ID."
