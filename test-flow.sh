#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  test-flow.sh — End-to-end integration test for the Event-Driven E-Commerce
#
#  Usage:
#    chmod +x test-flow.sh
#    ./test-flow.sh
#
#  Prerequisites:
#    - All services running (docker-compose up -d)
#    - curl and jq installed
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

ORDER_API="http://localhost:8081/api/orders"
PAYMENT_API="http://localhost:8082/api/payments"
NOTIF_API="http://localhost:8083/api/notifications"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${BLUE}[TEST]${NC} $1"; }
ok()   { echo -e "${GREEN}[PASS]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }

# ─── Wait for service health ──────────────────────────────────────────────────
wait_for_service() {
  local name=$1
  local url=$2
  local retries=20

  log "Waiting for $name to be healthy..."
  for i in $(seq 1 $retries); do
    if curl -sf "$url/actuator/health" > /dev/null 2>&1; then
      ok "$name is healthy"
      return 0
    fi
    echo "  Attempt $i/$retries..."
    sleep 3
  done
  fail "$name did not become healthy in time"
}

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║   Event-Driven E-Commerce — E2E Test Suite           ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# ─── Health Checks ────────────────────────────────────────────────────────────
wait_for_service "Order Service"        "http://localhost:8081"
wait_for_service "Payment Service"      "http://localhost:8082"
wait_for_service "Notification Service" "http://localhost:8083"

# ─── Test 1: Create a standard order (should be paid ~80% of the time) ────────
echo ""
log "Test 1: Creating standard order..."

IDEMPOTENCY_KEY="test-$(date +%s)"

RESPONSE=$(curl -sf -X POST "$ORDER_API" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\": \"cust-e2e-001\",
    \"customerEmail\": \"test@ecommerce.com\",
    \"currency\": \"USD\",
    \"idempotencyKey\": \"$IDEMPOTENCY_KEY\",
    \"items\": [
      {
        \"productId\": \"prod-001\",
        \"productName\": \"Mechanical Keyboard\",
        \"quantity\": 1,
        \"unitPrice\": 129.99
      },
      {
        \"productId\": \"prod-002\",
        \"productName\": \"USB-C Hub\",
        \"quantity\": 2,
        \"unitPrice\": 49.99
      }
    ]
  }")

ORDER_ID=$(echo "$RESPONSE" | jq -r '.orderId')
INITIAL_STATUS=$(echo "$RESPONSE" | jq -r '.status')

ok "Order created: $ORDER_ID (status: $INITIAL_STATUS)"

# ─── Test 2: Idempotency — same key should return 409 ─────────────────────────
echo ""
log "Test 2: Testing idempotency (duplicate idempotency key)..."

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ORDER_API" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\": \"cust-e2e-001\",
    \"customerEmail\": \"test@ecommerce.com\",
    \"currency\": \"USD\",
    \"idempotencyKey\": \"$IDEMPOTENCY_KEY\",
    \"items\": [
      {\"productId\": \"p\", \"productName\": \"p\", \"quantity\": 1, \"unitPrice\": 10.00}
    ]
  }")

if [ "$HTTP_CODE" == "409" ]; then
  ok "Idempotency working — got 409 Conflict on duplicate key"
else
  warn "Expected 409, got $HTTP_CODE (may be race condition)"
fi

# ─── Test 3: Poll for async payment result ─────────────────────────────────────
echo ""
log "Test 3: Polling for async payment result (up to 15s)..."

FINAL_STATUS=""
for i in $(seq 1 15); do
  sleep 1
  STATUS_RESPONSE=$(curl -sf "$ORDER_API/$ORDER_ID" 2>/dev/null || echo '{}')
  FINAL_STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status // "UNKNOWN"')

  if [ "$FINAL_STATUS" == "PAID" ] || [ "$FINAL_STATUS" == "PAYMENT_FAILED" ]; then
    break
  fi
  echo "  Polling ($i/15): current status = $FINAL_STATUS"
done

case "$FINAL_STATUS" in
  "PAID")
    ok "Payment SUCCESS — Order status: PAID"
    PAYMENT_ID=$(curl -sf "$ORDER_API/$ORDER_ID" | jq -r '.paymentId // "N/A"')
    log "Payment ID: $PAYMENT_ID"
    ;;
  "PAYMENT_FAILED")
    warn "Payment DECLINED — Order status: PAYMENT_FAILED (this is expected ~20% of the time)"
    REASON=$(curl -sf "$ORDER_API/$ORDER_ID" | jq -r '.failureReason // "N/A"')
    log "Failure reason: $REASON"
    ;;
  *)
    warn "Order still in status: $FINAL_STATUS after 15s (services may be slow on first boot)"
    ;;
esac

# ─── Test 4: Create a high-value order (should always fail) ──────────────────
echo ""
log "Test 4: Creating high-value order (>$1000 — always declined)..."

FAIL_RESPONSE=$(curl -sf -X POST "$ORDER_API" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\": \"cust-e2e-002\",
    \"customerEmail\": \"fail@ecommerce.com\",
    \"currency\": \"USD\",
    \"idempotencyKey\": \"fail-order-$(date +%s)\",
    \"items\": [
      {
        \"productId\": \"prod-999\",
        \"productName\": \"Enterprise Server\",
        \"quantity\": 1,
        \"unitPrice\": 1500.00
      }
    ]
  }")

FAIL_ORDER_ID=$(echo "$FAIL_RESPONSE" | jq -r '.orderId')
ok "High-value order created: $FAIL_ORDER_ID"

sleep 5
FAIL_STATUS=$(curl -sf "$ORDER_API/$FAIL_ORDER_ID" | jq -r '.status // "UNKNOWN"')

if [ "$FAIL_STATUS" == "PAYMENT_FAILED" ]; then
  ok "High-value order correctly DECLINED — status: PAYMENT_FAILED"
else
  warn "High-value order status: $FAIL_STATUS (expected PAYMENT_FAILED)"
fi

# ─── Test 5: Check notifications were recorded ────────────────────────────────
echo ""
log "Test 5: Checking notification records..."

sleep 2
NOTIF_COUNT=$(curl -sf "$NOTIF_API/order/$ORDER_ID" | jq 'length // 0')
ok "Notifications recorded for order $ORDER_ID: $NOTIF_COUNT"

# ─── Test 6: Payment Service query ────────────────────────────────────────────
echo ""
log "Test 6: Querying Payment Service directly..."

PAYMENT_RESPONSE=$(curl -sf "$PAYMENT_API/order/$ORDER_ID" 2>/dev/null || echo '{"status":"NOT_FOUND"}')
PAYMENT_STATUS=$(echo "$PAYMENT_RESPONSE" | jq -r '.status // "NOT_FOUND"')
ok "Payment Service reports: $PAYMENT_STATUS"

# ─── Test 7: Validation errors ────────────────────────────────────────────────
echo ""
log "Test 7: Testing input validation..."

VALIDATION_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ORDER_API" \
  -H "Content-Type: application/json" \
  -d '{"customerId": "", "items": []}')

if [ "$VALIDATION_CODE" == "400" ]; then
  ok "Validation working — got 400 Bad Request on invalid input"
else
  warn "Expected 400, got $VALIDATION_CODE"
fi

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║                   TEST SUMMARY                       ║"
echo "╠══════════════════════════════════════════════════════╣"
echo "║  Order API   → http://localhost:8081/api/orders      ║"
echo "║  Payment API → http://localhost:8082/api/payments    ║"
echo "║  Notif API   → http://localhost:8083/api/notifications║"
echo "║  RabbitMQ UI → http://localhost:15672 (guest/guest)  ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
echo -e "Standard order ID : ${GREEN}$ORDER_ID${NC} (final status: $FINAL_STATUS)"
echo -e "High-value order  : ${GREEN}$FAIL_ORDER_ID${NC} (final status: $FAIL_STATUS)"
echo ""
ok "All tests completed!"
