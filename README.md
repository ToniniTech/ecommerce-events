# Event-Driven E-Commerce

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat&logo=springboot&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12-FF6600?style=flat&logo=rabbitmq&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT-000000?style=flat&logo=jsonwebtokens&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat&logo=springsecurity&logoColor=white)

Sistema de microservicios e-commerce con arquitectura basada en eventos, comunicación 100% asíncrona mediante RabbitMQ y autenticación JWT stateless.

---

## ¿Por qué este proyecto?

La mayoría de los tutoriales de microservicios usan REST entre servicios, lo que crea acoplamiento oculto. Este proyecto demuestra cómo construir servicios que se comunican **sin conocerse entre sí**, tolerando fallos parciales sin afectar al sistema completo.

Si Payment Service se cae, las órdenes siguen creándose. Cuando vuelve, procesa todos los eventos pendientes automáticamente. Eso es resiliencia real.

---

## Stack técnico

| Capa | Tecnología |
|------|------------|
| Lenguaje | Java 17 |
| Framework | Spring Boot 3.2 |
| Mensajería | RabbitMQ 3.12 + Spring AMQP |
| Seguridad | Spring Security + JWT (JJWT 0.12) |
| Persistencia | Spring Data JPA + MySQL 8.0 |
| Infraestructura | Docker + Docker Compose |
| Build | Maven 3 |

---

## Arquitectura

```
CLIENT (browser / Postman)
    │
    ▼
┌─────────────────┐              ┌──────────────────────┐
│  AUTH SERVICE   │              │   ORDER SERVICE      │
│     :8084       │◄────JWT─────►│       :8081          │
│   DB: auth_db   │  secreto     │   DB: order_db       │
│                 │  compartido  │                      │
│ /register       │              │ - extrae customerId  │
│ /login          │              │   del token JWT      │
│ /refresh        │              │ - resuelve precios   │
│ /logout         │              │   del catálogo       │
└─────────────────┘              └──────────────────────┘
                                          │
                                          │ publica OrderCreated
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
```

---

## Flujo de eventos

| Evento | Publicado por | Consumido por |
|--------|--------------|---------------|
| `OrderCreated` | Order Service | Payment Service, Notification Service |
| `PaymentProcessed` | Payment Service | Order Service, Notification Service |
| `PaymentFailed` | Payment Service | Order Service, Notification Service |

---

## Decisiones de diseño

**¿Por qué RabbitMQ y no REST entre servicios?**

La comunicación HTTP síncrona crea acoplamiento temporal, si Payment Service cae, Order Service falla también. Con RabbitMQ, Order Service publica el evento y continúa independientemente. Los mensajes esperan en la queue hasta que Payment Service vuelva.

**¿Por qué una base de datos por servicio?**

Permite que cada servicio evolucione, escale y falle de forma completamente independiente. El costo es consistencia eventual, manejada mediante eventos y la tabla `processed_events` para idempotencia.

**¿Por qué JWT stateless y no sesiones?**

En microservicios, las sesiones crean acoplamiento, todos los servicios necesitarían compartir el mismo store de sesiones. Con JWT, cada servicio valida el token localmente usando el secreto compartido. Sin llamadas HTTP al Auth Service por cada request.

**¿Por qué el cliente no envía el precio?**

Si el cliente enviara el precio, podría poner $0.01 en cualquier producto. El servidor resuelve nombre y precio desde el catálogo interno. El cliente solo envía `productId` + `quantity`.

---

## Patrones de producción implementados

| Patrón | Dónde | Propósito |
|--------|-------|-----------|
| **Database per Service** | 4 instancias MySQL | Autonomía e independencia total entre servicios |
| **Idempotencia** | Los 4 servicios | Tabla `processed_events` con `UNIQUE(event_id)` — evita doble procesamiento |
| **Dead Letter Queue** | Cada queue | Mensajes fallidos tras 3 retries → DLQ para inspección manual |
| **Retry con backoff** | Todos los consumers | 2s → 4s → 10s exponencial |
| **Manual ACK** | Todos los consumers | Mensaje removido de la queue solo cuando se procesa exitosamente |
| **Publisher confirms** | RabbitTemplate | Garantía de entrega al broker |
| **JWT + Refresh Token** | Auth Service | Autenticación stateless con rotación de tokens |
| **BCrypt passwords** | Auth Service | Hash con salt automático — nunca se guarda la contraseña |

---

## Estructura del proyecto

```
ecommerce-events/
├── auth-service/               # JWT: register, login, refresh, logout
│   ├── domain/                 # User, RefreshToken, Role
│   ├── security/               # JwtService, JwtAuthenticationFilter
│   ├── service/                # AuthService, UserDetailsServiceImpl
│   └── controller/             # AuthController + DTOs
│
├── order-service/              # REST API protegida con JWT
│   ├── domain/                 # Order, OrderItem, OrderStatus
│   ├── messaging/              # OrderEventPublisher, PaymentEventConsumer
│   ├── service/                # OrderService, ProductCatalogService
│   └── security/               # JwtService, JwtAuthenticationFilter
│
├── payment-service/            # Consume OrderCreated, simula gateway
│   ├── domain/                 # Payment, PaymentStatus
│   ├── messaging/              # OrderEventConsumer, PaymentEventPublisher
│   └── service/                # PaymentService, PaymentGatewaySimulator
│
├── notification-service/       # Consume todos los eventos, envía emails
│   ├── domain/                 # Notification, NotificationType
│   ├── messaging/              # NotificationEventConsumer
│   └── service/                # NotificationService, EmailTemplateBuilder
│
├── infrastructure/
│   ├── rabbitmq/rabbitmq.conf
│   └── mysql/                  # Init scripts por base de datos
│
├── docker-compose.yml          # Orquestación completa (9 contenedores)
└── test-flow.sh                # Script E2E automatizado
```

---

## Quick Start

**Requisitos:** Docker Desktop instalado.

```bash
# 1. Clonar el repositorio
git clone https://github.com/ToniniTech/Event-Driven-E-commerce.git
cd ecommerce-events

# 2. Levantar toda la infraestructura
docker-compose up --build -d

# 3. Verificar que todo está healthy (~2 minutos)
docker-compose ps

# 4. Ver logs en tiempo real
docker-compose logs -f order-service payment-service notification-service
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

El `customerId` se extrae del JWT automáticamente. El cliente solo envía `productId` + `quantity`.

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "customerEmail": "juan@example.com",
    "currency": "USD",
    "items": [
      { "productId": "prod-001", "quantity": 1 },
      { "productId": "prod-002", "quantity": 2 }
    ]
  }'
```

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

### Forzar un pago fallido

`prod-009` tiene precio $1200 — el gateway siempre lo rechaza:

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "customerEmail": "juan@example.com",
    "currency": "USD",
    "items": [{ "productId": "prod-009", "quantity": 1 }]
  }'
```

---

## Catálogo de productos

| productId | Nombre | Precio | Resultado |
|-----------|--------|--------|-----------|
| prod-001 | Teclado Mecánico | $129.99 | ~80% éxito |
| prod-002 | Mouse Inalámbrico | $49.99 | ~80% éxito |
| prod-003 | Monitor 27" | $399.99 | ~80% éxito |
| prod-004 | Auriculares Bluetooth | $89.99 | ~80% éxito |
| prod-005 | Webcam HD | $74.99 | ~80% éxito |
| prod-006 | Hub USB-C | $39.99 | ~80% éxito |
| prod-007 | Laptop Stand | $34.99 | ~80% éxito |
| prod-008 | SSD Externo 1TB | $109.99 | ~80% éxito |
| prod-009 | Servidor Premium | $1200.00 | Siempre falla |

---

## Servicios y puertos

| Servicio | URL |
|----------|-----|
| Auth Service | http://localhost:8084 |
| Order Service | http://localhost:8081 |
| Payment Service | http://localhost:8082 |
| Notification Service | http://localhost:8083 |
| RabbitMQ Management UI | http://localhost:15672 — guest/guest |
| Auth DB | localhost:3310 — authuser/authpass |
| Order DB | localhost:3307 — orderuser/orderpass |
| Payment DB | localhost:3308 — paymentuser/paymentpass |
| Notification DB | localhost:3309 — notifuser/notifpass |

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
```

---

## Parar el sistema

```bash
# Parar contenedores (conserva datos)
docker-compose down

# Inicio limpio — borra todos los datos
docker-compose down -v
```
