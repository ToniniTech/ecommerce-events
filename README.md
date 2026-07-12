# Event-Driven E-Commerce

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat&logo=springboot&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12-FF6600?style=flat&logo=rabbitmq&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT-000000?style=flat&logo=jsonwebtokens&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat&logo=springsecurity&logoColor=white)
![Zipkin](https://img.shields.io/badge/Tracing-Zipkin-FF6C37?style=flat&logo=jaeger&logoColor=white)

Sistema de microservicios e-commerce con arquitectura basada en eventos, comunicación mayoritariamente asíncrona mediante RabbitMQ, un catálogo síncrono aislado por REST, autenticación JWT stateless y trazabilidad distribuida con OpenTelemetry + Zipkin.

---

## ¿Por qué este proyecto?

La mayoría de los tutoriales de microservicios usan REST entre servicios, lo que crea acoplamiento oculto. Este proyecto demuestra cómo construir servicios que se comunican **sin conocerse entre sí**, tolerando fallos parciales sin afectar al sistema completo.

Si Payment Service se cae, las órdenes siguen creándose. Cuando vuelve, procesa todos los eventos pendientes automáticamente. Eso es resiliencia real.

El único acoplamiento síncrono es **deliberado**: Order Service consulta a Product Service por REST para resolver precio y disponibilidad en tiempo real, de modo que el cliente nunca dicta el precio. Es la excepción que confirma la regla, y está aislada con timeouts explícitos para evitar fallos en cascada.

---

## Stack técnico

| Capa | Tecnología |
|------|------------|
| Lenguaje | Java 17 |
| Framework | Spring Boot 3.2 |
| Mensajería | RabbitMQ 3.12 + Spring AMQP |
| Comunicación síncrona | Spring `RestClient` (Order → Product) con timeouts explícitos |
| Seguridad | Spring Security + JWT (JJWT 0.12) |
| Persistencia | Spring Data JPA + MySQL 8.0 |
| Observabilidad | Micrometer Tracing + OpenTelemetry + Zipkin |
| Infraestructura | Docker + Docker Compose |
| Testing | JUnit 5 + Testcontainers |
| Build | Maven 3 |

---

## Arquitectura

```
CLIENT (browser / Postman)
    │
    ▼
┌─────────────────┐              ┌──────────────────────┐        ┌─────────────────────┐
│  AUTH SERVICE   │              │   ORDER SERVICE      │──REST─►│  PRODUCT SERVICE    │
│     :8084       │◄────JWT─────►│       :8081          │ precio │       :8085         │
│   DB: auth_db   │  secreto     │   DB: order_db       │  +stock│   DB: product_db    │
│                 │  compartido  │                      │◄───────│                     │
│ /register       │              │ - extrae customerId  │        │ catálogo 300 prod.  │
│ /login          │              │   del token JWT      │        │ (CRUD + optimistic  │
│ /refresh        │              │ - resuelve precio    │        │  locking @Version)  │
│ /logout         │              │   desde el catálogo  │        └─────────────────────┘
│ /admin/**       │              │ - Outbox Pattern     │
└─────────────────┘              └──────────────────────┘
                                          │
                                          │ publica OrderCreated (vía Outbox)
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
               │   éxito simulado │              │ - payment success     │
               └──────────────────┘              │ - payment failure     │
                          │                      └───────────────────────┘
                          │ publica PaymentProcessed / PaymentFailed
                          ▼
               ┌──────────────────┐
               │  ORDER SERVICE   │
               │ (actualiza estado│
               │  PAID / FAILED)  │
               └──────────────────┘

  Todos los servicios exportan trazas a ZIPKIN :9411 (traceId propagado por HTTP y por headers AMQP)
```

---

## Flujo de eventos

| Evento | Publicado por | Consumido por |
|--------|--------------|---------------|
| `OrderCreated` | Order Service (vía Outbox) | Payment Service, Notification Service |
| `PaymentProcessed` | Payment Service | Order Service, Notification Service |
| `PaymentFailed` | Payment Service | Order Service, Notification Service |

---

## Decisiones de diseño

**¿Por qué RabbitMQ y no REST entre servicios?**

La comunicación HTTP síncrona crea acoplamiento temporal, si Payment Service cae, Order Service falla también. Con RabbitMQ, Order Service publica el evento y continúa independientemente. Los mensajes esperan en la queue hasta que Payment Service vuelva.

**¿Por qué entonces Product Service SÍ es síncrono (REST)?**

El precio y la disponibilidad deben resolverse **en el momento** de crear la orden, no de forma eventual: no puede existir una orden "provisional" sin precio confiable. Por eso Order Service consulta a Product Service por REST. El acoplamiento se acota con timeouts explícitos (connect 2s / read 3s) para que un Product Service lento nunca agote el thread pool de Order Service.

**¿Por qué una base de datos por servicio?**

Permite que cada servicio evolucione, escale y falle de forma completamente independiente. El costo es consistencia eventual, manejada mediante eventos y la tabla `processed_events` para idempotencia.

**¿Por qué el patrón Outbox?**

Guardar la orden en MySQL y publicar el evento en RabbitMQ son dos sistemas distintos: si el segundo falla, se pierde el evento (dual-write problem). Con Outbox, el evento se persiste en la misma transacción que la orden y un poller lo publica después. La entrega queda garantizada aunque el broker esté caído en ese instante.

**¿Por qué JWT stateless y no sesiones?**

En microservicios, las sesiones crean acoplamiento, todos los servicios necesitarían compartir el mismo store de sesiones. Con JWT, cada servicio valida el token localmente usando el secreto compartido. Sin llamadas HTTP al Auth Service por cada request.


---

## Patrones de producción implementados

| Patrón | Dónde | Propósito |
|--------|-------|-----------|
| **Database per Service** | 5 instancias MySQL | Autonomía e independencia total entre servicios |
| **Outbox Pattern** | Order Service | Evento persistido en la misma transacción que la orden; poller lo publica — resuelve el dual-write |
| **Saga Choreography** | Order → Payment → Order | Coordinación distribuida por eventos, sin orquestador central |
| **Idempotencia** | Servicios event-driven | Tabla `processed_events` con `UNIQUE(event_id)` — evita doble procesamiento |
| **Optimistic Locking** | Product Service | `@Version` en `Product` — detecta lost updates de stock y falla con 409 en vez de sobrescribir |
| **Dead Letter Queue** | Cada queue | Mensajes fallidos tras 3 retries → DLQ para inspección manual |
| **Retry con backoff** | Todos los consumers | 2s → 4s → 10s exponencial |
| **Manual ACK** | Todos los consumers | Mensaje removido de la queue solo cuando se procesa exitosamente |
| **Publisher confirms** | RabbitTemplate | Garantía de entrega al broker |
| **Distributed Tracing** | Los 5 servicios | Micrometer + OpenTelemetry + Zipkin — un `traceId` cruza HTTP y AMQP |
| **Autoridad de precio server-side** | Order + Product Service | El precio se resuelve desde el catálogo; el cliente nunca lo decide |
| **Soft delete** | Product Service | `active = false` — preserva integridad referencial con órdenes existentes |
| **JWT + Refresh Token** | Auth Service | Autenticación stateless con rotación de tokens |
| **BCrypt passwords** | Auth Service | Hash con salt automático — nunca se guarda la contraseña |
| **Account Locking** | Auth Service | Tabla `failed_attempts` — bloqueo automático tras 3 intentos fallidos |
| **Role-Based Access (RBAC)** | Auth Service | Rutas `/api/auth/admin/**` restringidas al rol `ADMIN` |

---

## Estructura del proyecto

```
ecommerce-events/
├── auth-service/               # JWT: register, login, refresh, admin
│   ├── domain/                 # User, RefreshToken, Role
│   ├── security/               # JwtService, JwtAuthenticationFilter
│   ├── service/                # AuthService, UserDetailsServiceImpl
│   └── controller/             # AuthController, AuthAdmController + DTOs
│
├── order-service/              # REST API protegida con JWT
│   ├── domain/                 # Order, OrderItem, OutboxEvent, ProcessedEvent
│   ├── client/                 # ProductCatalogClient (RestClient hacia Product Service)
│   ├── messaging/              # OrderEventPublisher, OutboxProcessor, PaymentEventConsumer
│   ├── service/                # OrderService
│   └── security/               # JwtService, JwtAuthenticationFilter
│
├── product-service/            # Catálogo síncrono (REST-only), sin eventos
│   ├── domain/                 # Product (@Version), ProductRepository
│   ├── mapper/                 # ProductMapper (entity ↔ DTO)
│   ├── config/                 # CatalogCsvLoader (carga UPSERT del CSV al arrancar)
│   ├── service/                # ProductService (CRUD, stock, soft delete)
│   └── controller/             # ProductController + DTOs
│
├── payment-service/            # Consume OrderCreated, simula gateway
│   ├── domain/                 # Payment, PaymentStatus, ProcessedEvent
│   ├── messaging/              # OrderEventConsumer, PaymentEventPublisher
│   └── service/                # PaymentService, PaymentGatewaySimulator
│
├── notification-service/       # Consume todos los eventos, envía emails
│   ├── domain/                 # Notification, NotificationType, ProcessedEvent
│   ├── messaging/              # NotificationEventConsumer
│   └── service/                # NotificationService, EmailTemplateBuilder
│
├── infrastructure/
│   ├── rabbitmq/rabbitmq.conf
│   └── mysql/                  # Init scripts por base de datos
│
├── docker-compose.yml          # Orquestación completa (12 contenedores)
└── test-flow.sh                # Script E2E automatizado
```

---

## Quick Start

**Requisitos:** Docker Desktop instalado.

```bash
# 1. Clonar el repositorio
git clone https://github.com/ToniniTech/Event-Driven-E-commerce.git
cd Event-Driven-E-commerce

# 2. Levantar toda la infraestructura
docker-compose up --build -d

# 3. Verificar que todo está healthy (~2 minutos)
docker-compose ps

# 4. Ver logs en tiempo real
docker-compose logs -f order-service payment-service notification-service product-service

# 5. Explorar las trazas distribuidas
#    Zipkin UI → http://localhost:9411
```

---

## Cómo testear el flujo completo

### Paso 1 — Registrar un usuario

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

Respuesta:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-...",
  "customerId": "cust-a1b2c3d4",
  "email": "juan@example.com",
  "role": "CUSTOMER"
}
```

### Paso 2 — Crear una orden

El `customerId` y el `customerEmail` se extraen del JWT automáticamente. El cliente solo envía `productId` + `quantity`; el precio y el nombre se resuelven desde Product Service.

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

> **Idempotencia (opcional):** para protegerte contra doble-click en el frontend, envía un header `Idempotency-Key`. Si repites la misma key, la segunda petición se rechaza con `409 Conflict`.
>
> ```bash
>   -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000"
> ```

### Paso 3 — Consultar estado de la orden

```bash
curl http://localhost:8081/api/orders/{orderId} \
  -H "Authorization: Bearer <accessToken>"
```

Estado asíncrono: `PENDING` → `PAYMENT_PROCESSING` → `PAID` o `PAYMENT_FAILED`

### Paso 4 — Consultar el pago

```bash
curl http://localhost:8082/api/payments/order/{orderId}
```

### Paso 5 — Ver notificaciones enviadas

```bash
curl http://localhost:8083/api/notifications/order/{orderId}
```

### Forzar un pago fallido (determinista)

El gateway simulado rechaza el pago cuando el **monto total** cumple alguna de estas reglas de negocio:

- **Monto > $1000** → `AMOUNT_EXCEEDS_LIMIT` (necesitas mucha cantidad, ya que ningún producto supera ~$7).
- **Monto que termina en `.13`** → `CARD_EXPIRED`.

El producto `P0001` (*Cebolla 1kg*, $1.13) con `quantity: 1` da un total de $1.13 y **siempre** falla por tarjeta expirada:

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "items": [{ "productId": "P0001", "quantity": 1 }]
  }'
```

En cualquier otro caso, el gateway aprueba ~80% de las veces (`payment.gateway.success-rate`).

---

## Endpoints de administración

Requieren JWT con rol `ADMIN`. El usuario admin se crea automáticamente al iniciar el servicio (ver `DataSeeder`).

> El email y la contraseña del admin deberían inyectarse por variables de entorno (`ADMIN_EMAIL` / `ADMIN_PASSWORD`) antes de exponer el repo; en el estado actual vienen con un valor por defecto en el código.

### Login como admin

```bash
curl -X POST http://localhost:8084/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "anthony.viveros@admin.com",
    "password": "<ADMIN_PASSWORD>"
  }'
```

### Bloquear una cuenta

Desactiva el usuario y revoca todos sus refresh tokens. La cuenta queda bloqueada tras **3 intentos de login fallidos** o manualmente con este endpoint.

```bash
curl -X PATCH http://localhost:8084/api/auth/admin/lockUser/{customerId} \
  -H "Authorization: Bearer <adminAccessToken>"
```

### Desbloquear una cuenta

```bash
curl -X PATCH http://localhost:8084/api/auth/admin/unlockUser/{customerId} \
  -H "Authorization: Bearer <adminAccessToken>"
```

Ambos endpoints retornan `204 No Content` si la operación fue exitosa.

---

## Catálogo de productos

El catálogo lo sirve **Product Service** (`:8085`) y se carga desde `products_300.csv` al arrancar, mediante un import idempotente (UPSERT por `productId`): reiniciar el servicio no duplica filas.

- **300 productos** de abarrotes, con IDs `P0001` … `P0300`.
- Precios entre **$0.99 y $7.47**.
- Cada producto tiene `stock`, un flag `active` (soft delete) y una versión `@Version` para optimistic locking.

Ejemplos reales del catálogo:

| productId | Nombre | Precio | Stock |
|-----------|--------|--------|-------|
| P0001 | Cebolla 1kg | $1.13 | 7 |
| P0002 | Chocolate 100g | $2.20 | 63 |
| P0003 | Queso gauda 200g | $3.15 | 189 |
| P0149 | Leche entera 1L | $2.64 | 10 |
| P0300 | Carne molida 500g | $6.04 | 91 |

Explorar el catálogo por API:

```bash
# Listado paginado (?active=true|false opcional)
curl "http://localhost:8085/api/products?page=0&size=10"

# Un producto puntual
curl http://localhost:8085/api/products/P0002
```

---

## Servicios y puertos

| Servicio | URL |
|----------|-----|
| Auth Service | http://localhost:8084 |
| Order Service | http://localhost:8081 |
| Payment Service | http://localhost:8082 |
| Notification Service | http://localhost:8083 |
| Product Service | http://localhost:8085 |
| Zipkin (trazas) | http://localhost:9411 |
| RabbitMQ Management UI | http://localhost:15672 — guest/guest |
| Auth DB | localhost:3310 — authuser/authpass |
| Order DB | localhost:3307 — orderuser/orderpass |
| Payment DB | localhost:3308 — paymentuser/paymentpass |
| Notification DB | localhost:3309 — notifuser/notifpass |
| Product DB | localhost:3311 — productuser/productpass |

---

## Revisar las bases de datos

```sql
-- Auth DB (puerto 3310)
SELECT customer_id, email, first_name, role, created_at FROM users;

-- Order DB (puerto 3307)
SELECT o.order_id, o.customer_id, o.status, o.total_amount, o.created_at,
       GROUP_CONCAT(i.product_name SEPARATOR ', ') AS productos
FROM orders o
LEFT JOIN order_items i ON o.id = i.order_id
GROUP BY o.id ORDER BY o.created_at DESC;

-- Payment DB (puerto 3308)
SELECT payment_id, order_id, status, amount, failure_reason, created_at
FROM payments ORDER BY created_at DESC;

-- Notification DB (puerto 3309)
SELECT notification_type, status, recipient_email, sent_at
FROM notifications ORDER BY created_at DESC;

-- Product DB (puerto 3311)
SELECT product_id, name, price, stock, is_active
FROM products ORDER BY product_id LIMIT 20;
```

---

## Parar el sistema

```bash
# Parar contenedores (conserva datos)
docker-compose down

# Inicio limpio — borra todos los datos
docker-compose down -v
```
