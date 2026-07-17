# Event-Driven E-Commerce

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat&logo=springboot&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12-FF6600?style=flat&logo=rabbitmq&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT-000000?style=flat&logo=jsonwebtokens&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat&logo=springsecurity&logoColor=white)
![Zipkin](https://img.shields.io/badge/Tracing-Zipkin-FF6C37?style=flat&logo=jaeger&logoColor=white)

E-commerce microservices system with an event-driven architecture, mostly asynchronous communication over RabbitMQ, a synchronous catalog isolated via REST, stateless JWT authentication, and distributed tracing with OpenTelemetry + Zipkin.

---

## Why this project?

Most microservices tutorials use REST between services, which creates hidden coupling. This project demonstrates how to build services that communicate **without knowing about each other**, tolerating partial failures without affecting the whole system.

If Payment Service goes down, orders keep being created. When it comes back, it processes all pending events automatically. That's real resilience.

The only synchronous coupling is **deliberate**: Order Service queries Product Service over REST to resolve price and availability in real time, so the client never dictates the price. It's the exception that proves the rule, and it's isolated with explicit timeouts to prevent cascading failures.

---

## Tech stack

| Layer | Technology |
|------|------------|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Messaging | RabbitMQ 3.12 + Spring AMQP |
| Synchronous communication | Spring `RestClient` (Order → Product) with explicit timeouts |
| Security | Spring Security + JWT (JJWT 0.12) |
| Persistence | Spring Data JPA + MySQL 8.0 |
| Observability | Micrometer Tracing + OpenTelemetry + Zipkin |
| Infrastructure | Docker + Docker Compose |
| Testing | JUnit 5 + Testcontainers |
| Build | Maven 3 |

---

## Architecture

```
CLIENT (browser / Postman)
    │
    ▼
┌─────────────────┐              ┌──────────────────────┐        ┌─────────────────────┐
│  AUTH SERVICE   │              │   ORDER SERVICE      │──REST─►│  PRODUCT SERVICE    │
│     :8084       │◄────JWT─────►│       :8081          │ price  │       :8085         │
│   DB: auth_db   │  shared      │   DB: order_db       │  +stock│   DB: product_db    │
│                 │  secret      │                      │◄───────│                     │
│ /register       │              │ - extracts customerId│        │ 300-product catalog │
│ /login          │              │   from the JWT       │        │ (CRUD + optimistic  │
│ /refresh        │              │ - resolves price     │        │  locking @Version)  │
│ /logout         │              │   from the catalog   │        └─────────────────────┘
│ /admin/**       │              │ - Outbox Pattern     │
└─────────────────┘              └──────────────────────┘
                                          │
                                          │ publishes OrderCreated (via Outbox)
                                          ▼
                                 ┌─────────────────────┐
                                 │   RABBITMQ :5672    │
                                 │  orders.exchange    │
                                 │  payments.exchange  │
                                 │  dlx.exchange (DLQ) │
                                 └─────────────────────┘
                                    │              │
                          ┌─────────┘              └──────────┐
                          ▼                                   ▼
               ┌──────────────────┐              ┌───────────────────────┐
               │ PAYMENT SERVICE  │              │ NOTIFICATION SERVICE  │
               │     :8082        │              │       :8083           │
               │ DB: payment_db   │              │ DB: notification_db   │
               │                  │              │                       │
               │ - gateway 80%    │              │ - order confirmation  │
               │   simulated      │              │ - payment success     │
               │   success        │              │ - payment failure     │
               └──────────────────┘              └───────────────────────┘
                          │                      
                          │ publishes PaymentProcessed / PaymentFailed
                          ▼
               ┌──────────────────┐
               │  ORDER SERVICE   │
               │ (updates status  │
               │  PAID / FAILED)  │
               └──────────────────┘

  All services export traces to ZIPKIN :9411 (traceId propagated over HTTP and via AMQP headers)
```

---

## Event flow

| Event | Published by | Consumed by |
|--------|--------------|---------------|
| `OrderCreated` | Order Service (via Outbox) | Payment Service, Notification Service |
| `PaymentProcessed` | Payment Service | Order Service, Notification Service |
| `PaymentFailed` | Payment Service | Order Service, Notification Service |

---

## Design decisions

**Why RabbitMQ and not REST between services?**

Synchronous HTTP communication creates temporal coupling — if Payment Service goes down, Order Service fails too. With RabbitMQ, Order Service publishes the event and continues independently. Messages wait in the queue until Payment Service comes back.

**Why is Product Service synchronous (REST), then?**

Price and availability must be resolved **at the moment** the order is created, not eventually: a "provisional" order without a reliable price cannot exist. That's why Order Service queries Product Service over REST. The coupling is bounded with explicit timeouts (connect 2s / read 3s) so a slow Product Service never exhausts Order Service's thread pool.

**Why a database per service?**

It lets each service evolve, scale, and fail completely independently. The cost is eventual consistency, handled through events and the `processed_events` table for idempotency.

**Why the Outbox pattern?**

Saving the order to MySQL and publishing the event to RabbitMQ are two separate systems: if the second one fails, the event is lost (dual-write problem). With Outbox, the event is persisted in the same transaction as the order, and a poller publishes it afterward. Delivery is guaranteed even if the broker is down at that instant.

**Why stateless JWT and not sessions?**

In microservices, sessions create coupling — every service would need to share the same session store. With JWT, each service validates the token locally using the shared secret. No HTTP calls to Auth Service on every request.

---

## Production patterns implemented

| Pattern | Where | Purpose |
|--------|-------|-----------|
| **Database per Service** | 5 MySQL instances | Full autonomy and independence between services |
| **Outbox Pattern** | Order Service | Event persisted in the same transaction as the order; poller publishes it — solves the dual-write problem |
| **Saga Choreography** | Order → Payment → Order | Distributed coordination through events, with no central orchestrator |
| **Idempotency** | Event-driven services | `processed_events` table with `UNIQUE(event_id)` — prevents double processing |
| **Optimistic Locking** | Product Service | `@Version` on `Product` — detects lost stock updates and fails with 409 instead of overwriting |
| **Dead Letter Queue** | Every queue | Messages failing after 3 retries → DLQ for manual inspection |
| **Retry with backoff** | All consumers | 2s → 4s → 10s exponential |
| **Manual ACK** | All consumers | Message removed from the queue only when processed successfully |
| **Publisher confirms** | RabbitTemplate | Delivery guarantee to the broker |
| **Distributed Tracing** | All 5 services | Micrometer + OpenTelemetry + Zipkin — one `traceId` crosses HTTP and AMQP |
| **Server-side price authority** | Order + Product Service | Price is resolved from the catalog; the client never decides it |
| **Soft delete** | Product Service | `active = false` — preserves referential integrity with existing orders |
| **JWT + Refresh Token** | Auth Service | Stateless authentication with token rotation |
| **BCrypt passwords** | Auth Service | Hash with automatic salt — the password is never stored |
| **Account Locking** | Auth Service | `failed_attempts` table — automatic lock after 3 failed attempts |
| **Role-Based Access (RBAC)** | Auth Service | `/api/auth/admin/**` routes restricted to the `ADMIN` role |

---

## Project structure

```
ecommerce-events/
├── auth-service/               # JWT: register, login, refresh, admin
│   ├── domain/                 # User, RefreshToken, Role
│   ├── security/               # JwtService, JwtAuthenticationFilter
│   ├── service/                # AuthService, UserDetailsServiceImpl
│   └── controller/             # AuthController, AuthAdmController + DTOs
│
├── order-service/              # JWT-protected REST API
│   ├── domain/                 # Order, OrderItem, OutboxEvent, ProcessedEvent
│   ├── client/                 # ProductCatalogClient (RestClient to Product Service)
│   ├── messaging/              # OrderEventPublisher, OutboxProcessor, PaymentEventConsumer
│   ├── service/                # OrderService
│   └── security/               # JwtService, JwtAuthenticationFilter
│
├── product-service/            # Synchronous catalog (REST-only), no events
│   ├── domain/                 # Product (@Version), ProductRepository
│   ├── mapper/                 # ProductMapper (entity ↔ DTO)
│   ├── config/                 # CatalogCsvLoader (UPSERT load from CSV at startup)
│   ├── service/                # ProductService (CRUD, stock, soft delete)
│   └── controller/             # ProductController + DTOs
│
├── payment-service/            # Consumes OrderCreated, simulates gateway
│   ├── domain/                 # Payment, PaymentStatus, ProcessedEvent
│   ├── messaging/              # OrderEventConsumer, PaymentEventPublisher
│   └── service/                # PaymentService, PaymentGatewaySimulator
│
├── notification-service/       # Consumes all events, sends emails
│   ├── domain/                 # Notification, NotificationType, ProcessedEvent
│   ├── messaging/              # NotificationEventConsumer
│   └── service/                # NotificationService, EmailTemplateBuilder
│
├── infrastructure/
│   ├── rabbitmq/rabbitmq.conf
│   └── mysql/                  # Init scripts per database
│
├── docker-compose.yml          # Full orchestration (12 containers)
└── test-flow.sh                # Automated E2E script
```

---

## Quick Start

**Requirements:** Docker Desktop installed.

```bash
# 1. Clone the repository
git clone https://github.com/ToniniTech/Event-Driven-E-commerce.git
cd Event-Driven-E-commerce

# 2. Bring up the whole infrastructure
docker-compose up --build -d

# 3. Check everything is healthy (~2 minutes)
docker-compose ps

# 4. Follow logs in real time
docker-compose logs -f order-service payment-service notification-service product-service

# 5. Explore the distributed traces
#    Zipkin UI → http://localhost:9411
```

---

## Test the flow with Postman (quick path)

The fastest way to see the whole system in action. The collection chains the entire flow automatically: it captures the JWT at login and the `orderId` from the created order, so you never copy tokens by hand.

**Requirement:** the system must be running (`docker-compose up --build -d`).

1. Import the two files from the [`/postman`](./postman) folder into Postman:
    - `ecommerce-events.postman_collection.json`
    - `ecommerce-events-local.postman_environment.json`
2. Select the **ecommerce-events-local** environment from the dropdown (top right). *Without this, the variables won't resolve.*
3. Open the **End-to-End Flow** folder and run it in order (or use the **Runner** to fire the whole sequence):

   | # | Request | What it does |
      |---|---------|--------------|
   | 1 | register user | Creates the user and **stores the JWT** automatically |
   | 2 | create order | Creates the order and **stores the `orderId`** automatically |
   | 3 | get order by id | Order status (`PENDING` → `PAID`) |
   | 4 | get payment by id | Payment result |
   | 5 | get notification by orderId | Notifications generated from events |

> **Note on the async flow:** steps 4 and 5 depend on events traveling through RabbitMQ after the order is created. If you query immediately and they aren't there yet, wait 1–2 seconds and retry — that small delay **is** the event-driven nature of the system, not a bug.

Prefer curl? The same flow, step by step, is below.

---

## How to test the full flow (curl)

### Step 1 — Register a user

```bash
curl -X POST http://localhost:8084/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Juan",
    "lastName": "Pérez",
    "email": "juan@example.com",
    "password": "12345678"
  }'
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-...",
  "customerId": "cust-a1b2c3d4",
  "email": "juan@example.com",
  "role": "CUSTOMER"
}
```

### Step 2 — Create an order

The `customerId` and `customerEmail` are extracted from the JWT automatically. The client only sends `productId` + `quantity`; the price and name are resolved from Product Service.

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "items": [
      { "productId": "P0002", "quantity": 1 },
      { "productId": "P0003", "quantity": 2 }
    ]
  }'
```

> **Idempotency (optional):** to protect against a double-click on the frontend, send an `Idempotency-Key` header. If you repeat the same key, the second request is rejected with `409 Conflict`.
>
> ```bash
>   -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000"
> ```

### Step 3 — Check the order status

```bash
curl http://localhost:8081/api/orders/{orderId} \
  -H "Authorization: Bearer <accessToken>"
```

Async status: `PENDING` → `PAYMENT_PROCESSING` → `PAID` or `PAYMENT_FAILED`

### Step 4 — Check the payment

```bash
curl http://localhost:8082/api/payments/order/{orderId}
```

### Step 5 — See sent notifications

```bash
curl http://localhost:8083/api/notifications/order/{orderId}
```

### Force a failed payment (deterministic)

The simulated gateway rejects the payment when the **total amount** matches one of these business rules:

- **Amount > $1000** → `AMOUNT_EXCEEDS_LIMIT` (you'd need a large quantity, since no product exceeds ~$7).
- **Amount ending in `.13`** → `CARD_EXPIRED`.

Product `P0001` (*Cebolla 1kg*, $1.13) with `quantity: 1` gives a total of $1.13 and **always** fails with card expired:

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "items": [{ "productId": "P0001", "quantity": 1 }]
  }'
```

In any other case, the gateway approves ~80% of the time (`payment.gateway.success-rate`).

---

## Admin endpoints

Require a JWT with the `ADMIN` role. The admin user is created automatically when the service starts (see `DataSeeder`).

### Admin credentials (demo)

The admin user is created at startup with these default credentials:

- **Email:** `admin@ecommerce.local`
- **Password:** `changeme123`

For a real deployment, override them with the `ADMIN_EMAIL` and `ADMIN_PASSWORD` environment variables.

### Login as admin

```bash
curl -X POST http://localhost:8084/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@ecommerce.local",
    "password": "changeme123"
  }'
```

### Lock an account

Deactivates the user and revokes all their refresh tokens. The account is locked after **3 failed login attempts** or manually with this endpoint.

```bash
curl -X PATCH http://localhost:8084/api/auth/admin/lockUser/{customerId} \
  -H "Authorization: Bearer <adminAccessToken>"
```

### Unlock an account

```bash
curl -X PATCH http://localhost:8084/api/auth/admin/unlockUser/{customerId} \
  -H "Authorization: Bearer <adminAccessToken>"
```

Both endpoints return `204 No Content` if the operation succeeds.

---

## Product catalog

The catalog is served by **Product Service** (`:8085`) and is loaded from `products_300.csv` at startup via an idempotent import (UPSERT by `productId`): restarting the service does not duplicate rows.

- **300 products** (grocery items), with IDs `P0001` … `P0300`.
- Prices between **$0.99 and $7.47**.
- Each product has `stock`, an `active` flag (soft delete), and a `@Version` for optimistic locking.

Real examples from the catalog:

| productId | Name | Price | Stock |
|-----------|--------|--------|-------|
| P0001 | Cebolla 1kg | $1.13 | 7 |
| P0002 | Chocolate 100g | $2.20 | 63 |
| P0003 | Queso gauda 200g | $3.15 | 189 |
| P0149 | Leche entera 1L | $2.64 | 10 |
| P0300 | Carne molida 500g | $6.04 | 91 |

Explore the catalog via API:

```bash
# Paginated list (?active=true|false optional)
curl "http://localhost:8085/api/products?page=0&size=10"

# A single product
curl http://localhost:8085/api/products/P0002
```

---

## Services and ports

| Service | URL |
|----------|-----|
| Auth Service | http://localhost:8084 |
| Order Service | http://localhost:8081 |
| Payment Service | http://localhost:8082 |
| Notification Service | http://localhost:8083 |
| Product Service | http://localhost:8085 |
| Zipkin (traces) | http://localhost:9411 |
| RabbitMQ Management UI | http://localhost:15672 — guest/guest |
| Auth DB | localhost:3310 — authuser/authpass |
| Order DB | localhost:3307 — orderuser/orderpass |
| Payment DB | localhost:3308 — paymentuser/paymentpass |
| Notification DB | localhost:3309 — notifuser/notifpass |
| Product DB | localhost:3311 — productuser/productpass |

---

## Inspect the databases

```sql
-- Auth DB (port 3310)
SELECT customer_id, email, first_name, role, created_at FROM users;

-- Order DB (port 3307)
SELECT o.order_id, o.customer_id, o.status, o.total_amount, o.created_at,
       GROUP_CONCAT(i.product_name SEPARATOR ', ') AS products
FROM orders o
LEFT JOIN order_items i ON o.id = i.order_id
GROUP BY o.id ORDER BY o.created_at DESC;

-- Payment DB (port 3308)
SELECT payment_id, order_id, status, amount, failure_reason, created_at
FROM payments ORDER BY created_at DESC;

-- Notification DB (port 3309)
SELECT notification_type, status, recipient_email, sent_at
FROM notifications ORDER BY created_at DESC;

-- Product DB (port 3311)
SELECT product_id, name, price, stock, is_active
FROM products ORDER BY product_id LIMIT 20;
```

---

## Stop the system

```bash
# Stop containers (keeps data)
docker-compose down

# Clean start — deletes all data
docker-compose down -v
```
