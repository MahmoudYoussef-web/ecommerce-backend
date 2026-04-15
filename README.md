# E-Commerce Backend System

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-brightgreen?logo=springboot)
![MySQL](https://img.shields.io/badge/MySQL-8-blue?logo=mysql)
![Redis](https://img.shields.io/badge/Redis-Cache-red?logo=redis)
![Stripe](https://img.shields.io/badge/Payments-Stripe-635BFF?logo=stripe)
![JWT](https://img.shields.io/badge/Auth-JWT-yellow)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

A production-grade RESTful e-commerce backend built with **Spring Boot 3** and **Java 21**. Covers the full commerce lifecycle — authentication, catalog management, cart, orders, Stripe payments, inventory, and financial reporting — with an emphasis on consistency, security, and real-world reliability patterns.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Local Setup](#local-setup)
    - [Docker Setup](#docker-setup)
    - [Environment Variables](#environment-variables)
- [API Reference](#api-reference)
- [Payment Flow](#payment-flow)
- [Security Model](#security-model)
- [Caching Strategy](#caching-strategy)
- [Inventory & Reservations](#inventory--reservations)
- [Error Handling](#error-handling)
- [Project Structure](#project-structure)
- [Contributing](#contributing)

---

## Features

### Authentication & Authorization
- JWT-based stateless authentication with separate **access** and **refresh** tokens
- Role-based access control: `ADMIN`, `CUSTOMER`, `WAREHOUSE`
- Multi-device session management — logout from one or all devices
- Secure endpoint protection via `@PreAuthorize` annotations

### Product Catalog
- Full CRUD for products and categories
- Multi-image support per product
- Variant-aware product model (size, color, etc.)
- Paginated listing and rich search filters: name, price range, category, and stock availability

### Shopping Cart
- Persistent, user-scoped cart
- Variant-aware line items
- Real-time price calculation and stock validation on add/update

### Wishlist
- Add and remove products; retrieve the full wishlist in a single call

### Order Management
- One-click order creation from the active cart
- **Address snapshot** — shipping address is frozen at purchase time, immune to future address edits
- Full order lifecycle with controlled state transitions:

```
PENDING → PAID → SHIPPED → DELIVERED
                    ↘ CANCELLED
```

- Role-scoped operations: customers create orders; admins and warehouse staff advance status

### Payment Processing (Stripe)
- Stripe Checkout Session creation
- Secure webhook ingestion with HMAC and Stripe-Signature verification
- Idempotent payment handling — duplicate webhooks are safely ignored
- Server-side validation: amount, currency, and ownership are verified before order confirmation

### Inventory Management
- Event-driven stock reservation on order creation (`OrderCreatedEvent`)
- Scheduled cleanup of expired reservations via `ReservationScheduler`
- `StockMovement` audit trail for every inventory change

### Financial Reporting
- Double-entry bookkeeping via `ChartOfAccount` and `JournalEntry` entities
- Revenue reporting by date range
- Dashboard aggregation endpoint for admin analytics

### Operational Readiness
- Redis caching for product reads with smart cache invalidation
- HikariCP connection pool, tuned defaults
- Structured JSON logging via Logstash Logback encoder
- Swagger / OpenAPI docs available out of the box
- Dockerfile for two-stage builds (build + minimal runtime image)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security + JJWT 0.11.5 |
| Persistence | Spring Data JPA / Hibernate |
| Database | MySQL 8 |
| Cache | Redis (Spring Cache abstraction) |
| Payments | Stripe Java SDK 25.2.0 |
| Mapping | MapStruct 1.5.5 |
| Validation | Jakarta Bean Validation |
| Logging | Logstash Logback Encoder |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven 3 |
| Runtime | Eclipse Temurin JDK 21 |

---

## Architecture

The application follows a classic **layered architecture** with an event layer for cross-cutting concerns:

```
┌─────────────────────────────────────────┐
│             REST Controllers             │
│  (request validation, response mapping)  │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│              Service Layer               │
│    (business logic, transaction mgmt)    │
└───────┬──────────────────────┬──────────┘
        │                      │
┌───────▼───────┐    ┌─────────▼─────────┐
│  Repository   │    │    Event Layer      │
│  (JPA / MySQL)│    │  PaymentCompleted   │
└───────────────┘    │  OrderCreated       │
                     └─────────┬──────────┘
                               │
                  ┌────────────▼────────────┐
                  │   Listeners / Handlers   │
                  │ Order sync, Inventory,   │
                  │ Finance journal entries  │
                  └──────────────────────────┘
```

**Design principles applied:**
- DTOs at the API boundary; entities never leave the service layer
- Centralized exception handling via `@RestControllerAdvice`
- Separation of cache logic into a dedicated `ProductCacheService`
- Interface-first services for testability and extensibility
- Immutable address snapshots for order integrity

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- MySQL 8+
- Redis (any recent version)
- A [Stripe account](https://stripe.com) (for payment features)

### Local Setup

**1. Clone the repository**
```bash
git clone https://github.com/your-username/ecommerce-backend.git
cd ecommerce-backend
```

**2. Create the database**
```sql
CREATE DATABASE ecommerce_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'dev_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON ecommerce_db.* TO 'dev_user'@'localhost';
FLUSH PRIVILEGES;
```

**3. Configure the application**

Copy `src/main/resources/application.properties` and fill in your values (see [Environment Variables](#environment-variables) below).

**4. Build and run**
```bash
mvn clean package -DskipTests
java -jar target/ecommerce-backend-0.0.1-SNAPSHOT.jar
```

Or with the Maven wrapper:
```bash
mvn spring-boot:run
```

The server starts on `http://localhost:8080`.

### Docker Setup

```bash
# Build the image
docker build -t ecommerce-backend .

# Run (pass secrets via environment variables — never bake them into the image)
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/ecommerce_db \
  -e SPRING_DATASOURCE_USERNAME=dev_user \
  -e SPRING_DATASOURCE_PASSWORD=your_password \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  -e AUTH_JWT_SECRET=your_base64_secret \
  -e STRIPE_SECRET_KEY=sk_live_... \
  -e STRIPE_WEBHOOK_SECRET=whsec_... \
  ecommerce-backend
```

> **Tip:** Use Docker Compose to spin up MySQL and Redis alongside the app in a single command. A `docker-compose.yml` is a natural next addition to this repo.

### Environment Variables

| Property | Description | Example |
|---|---|---|
| `spring.datasource.url` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/ecommerce_db` |
| `spring.datasource.username` | DB username | `dev_user` |
| `spring.datasource.password` | DB password | `secret` |
| `spring.data.redis.host` | Redis host | `localhost` |
| `spring.data.redis.port` | Redis port | `6379` |
| `auth.jwt.secret` | Base64-encoded JWT signing key | `VGhpc0lz...` |
| `auth.jwt.expiration` | Access token TTL (ms) | `86400000` (24 h) |
| `stripe.secret.key` | Stripe secret key | `sk_live_...` |
| `stripe.webhook.secret` | Stripe webhook signing secret | `whsec_...` |
| `payment.webhook.secret` | Internal HMAC webhook secret | `some_random_secret` |
| `app.base-url` | Public base URL of this service | `https://api.example.com` |

> ⚠️ **Never commit real secrets.** Use environment variables, a secrets manager, or Spring Cloud Config in production.

---

## API Reference

Interactive documentation is available at:

```
http://localhost:8080/swagger-ui/index.html
```

All endpoints are prefixed with `/api`.

### Auth — `/api/auth`

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/register` | Create a new account | Public |
| `POST` | `/login` | Obtain access + refresh tokens | Public |
| `POST` | `/refresh` | Rotate tokens using a refresh token | Public |
| `POST` | `/logout` | Revoke refresh token(s) | Bearer |

### Users — `/api/users`

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/me` | Get current user profile | Bearer |
| `PUT` | `/me` | Update current user profile | Bearer |

### Addresses — `/api/addresses`

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/` | List all addresses for current user | Bearer |
| `POST` | `/` | Add a new address | Bearer |
| `DELETE` | `/{id}` | Delete an address | Bearer |

### Products — `/api/products`

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/` | Paginated product listing | Public |
| `GET` | `/{id}` | Get single product | Public |
| `GET` | `/search` | Filter by name, price, category, stock | Public |
| `GET` | `/category/{categoryId}` | Products in a category | Public |
| `POST` | `/` | Create product | Admin |
| `PUT` | `/{id}` | Update product | Admin |
| `DELETE` | `/{id}` | Delete product | Admin |

### Categories — `/api/categories`

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/` | List all categories | Public |
| `GET` | `/{slug}` | Get category by slug | Public |
| `POST` | `/` | Create category | Admin |
| `PUT` | `/{id}` | Update category | Admin |
| `DELETE` | `/{id}` | Delete category | Admin |

### Cart — `/api/cart`

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/` | Get current cart | Bearer |
| `POST` | `/items` | Add item to cart | Bearer |
| `PUT` | `/items/{productId}` | Update item quantity | Bearer |
| `DELETE` | `/items/{productId}` | Remove item | Bearer |
| `DELETE` | `/` | Clear entire cart | Bearer |

### Wishlist — `/api/wishlist`

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/` | Get wishlist | Bearer |
| `POST` | `/items/{productId}` | Add product | Bearer |
| `DELETE` | `/items/{productId}` | Remove product | Bearer |

### Orders — `/api/orders`

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/` | List current user's orders | Bearer |
| `GET` | `/{id}` | Get order detail | Bearer |
| `POST` | `/` | Create order from cart | Bearer |
| `PATCH` | `/{id}/ship` | Mark as shipped | Admin / Warehouse |
| `PATCH` | `/{id}/deliver` | Mark as delivered | Admin / Warehouse |
| `PATCH` | `/{id}/cancel` | Cancel order | Bearer |

### Payments — `/api/payments`

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/` | Create payment record for an order | Bearer |
| `POST` | `/checkout/{paymentId}` | Generate Stripe Checkout URL | Bearer |
| `POST` | `/webhook` | Stripe / provider webhook endpoint | Signature |

### Reviews — `/api/reviews`

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/product/{productId}` | Get all reviews for a product | Public |
| `POST` | `/` | Submit a review | Bearer |

### Reports — `/api/reports`

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/revenue?from=&to=` | Total revenue in date range | Admin |
| `GET` | `/dashboard?from=&to=` | Aggregated dashboard metrics | Admin |

---

## Payment Flow

```
Client                    Backend                      Stripe
  │                          │                            │
  ├─ POST /api/payments ─────►│                            │
  │                          │  Create Payment (PENDING)  │
  │◄─ { paymentId } ─────────┤                            │
  │                          │                            │
  ├─ POST /checkout/{id} ────►│                            │
  │                          ├─ Create Checkout Session ──►│
  │◄─ { checkoutUrl } ───────┤◄────────────────────────── │
  │                          │                            │
  ├─ Redirect to Stripe ─────────────────────────────────►│
  │◄─────────────────── Payment completed ────────────────┤
  │                          │                            │
  │                          │◄── Webhook (signed) ───────┤
  │                          │  Verify signature           │
  │                          │  Validate amount/currency   │
  │                          │  Publish PaymentCompletedEvent
  │                          │      │                      │
  │                          │  ┌───▼──────────────────┐  │
  │                          │  │  Update Order → PAID  │  │
  │                          │  │  Commit Stock         │  │
  │                          │  │  Post Journal Entries │  │
  │                          │  └──────────────────────-┘  │
```

---

## Security Model

- **Access tokens** are short-lived JWTs signed with a base64-encoded secret. They carry the user's ID, email, and roles.
- **Refresh tokens** are persisted to the database and scoped to a device ID, enabling selective revocation.
- **Endpoint protection** is handled at the controller method level using `@PreAuthorize("hasRole('ADMIN')")` etc., keeping security rules co-located with the endpoints they protect.
- **Webhook security** uses two layers: Stripe's own `Stripe-Signature` header for Stripe events, and an HMAC-based `X-Signature` / `X-Timestamp` scheme for internal webhooks, both validated via the `WebhookValidator` abstraction.

---

## Caching Strategy

Product reads are cached in Redis through a dedicated `ProductCacheService`, keeping cache logic out of the main service:

| Cache Name | Key Pattern | Eviction Trigger |
|---|---|---|
| `products` | `product:{id}` | Product update or delete |
| `products_page` | `page:{n}:size:{n}:status:ACTIVE` | Any product write (all entries) |

`@CachePut` on save keeps the individual entry warm after a write, while `@CacheEvict(allEntries=true)` on the page cache prevents stale pagination results.

---

## Inventory & Reservations

Stock is managed through two complementary mechanisms:

**Reservations** — when an order is created, an `OrderCreatedEvent` is published. `InventoryEventListener` picks it up and creates `StockReservation` records, temporarily holding stock while payment is pending. A `ReservationScheduler` runs periodically to release reservations for orders that never complete payment.

**Committed movements** — once `PaymentCompletedEvent` fires, reservations are converted to permanent `StockMovement` records, decrementing available stock definitively.

This two-phase approach prevents overselling without requiring pessimistic locks on product rows.

---

## Error Handling

All exceptions are caught by a global `@RestControllerAdvice`. Every error response follows the same envelope:

```json
{
  "success": false,
  "status": 404,
  "message": "Product not found with id: 42",
  "errorCode": "RESOURCE_NOT_FOUND",
  "path": "/api/products/42",
  "timestamp": "2026-04-15T10:30:00Z"
}
```

Business exceptions (e.g., `ResourceNotFoundException`, `BusinessException`) map to appropriate HTTP status codes. Validation errors are unwrapped from `MethodArgumentNotValidException` and returned as a structured list of field errors.

---

## Project Structure

```
src/main/java/com/mahmoud/ecommerce_backend/
├── config/          # Spring Security, Redis, and app-wide beans
├── controller/      # REST controllers (thin, delegate to services)
├── dto/             # Request and response DTOs
├── entity/          # JPA entities
├── enums/           # Domain enumerations (OrderStatus, PaymentMethod, …)
├── event/           # Application events and listeners
│   ├── inventory/   # OrderCreatedEvent, InventoryEventListener
│   └── payment/     # PaymentCompletedEvent
├── exception/       # Custom exception classes + global handler
├── mapper/          # MapStruct mappers
├── repository/      # Spring Data JPA repositories
├── scheduler/       # Scheduled tasks (ReservationScheduler)
├── security/        # JWT filter, token provider, UserDetails impl
└── service/
    ├── auth/        # Authentication and token management
    ├── cart/        # Cart operations
    ├── category/    # Category CRUD
    ├── common/      # Email and file storage
    ├── inventory/   # Stock reservation and movement
    ├── order/       # Order lifecycle
    ├── payment/     # Payment + Stripe integration
    ├── product/     # Product CRUD + caching
    ├── report/      # Revenue and dashboard reporting
    ├── review/      # Product reviews
    ├── security/    # SecurityContext helper
    ├── user/        # User profile management
    └── wishlist/    # Wishlist operations
```

---

## Contributing

1. Fork the repository and create a feature branch: `git checkout -b feature/your-feature`
2. Make your changes with clear, focused commits
3. Ensure the project builds cleanly: `mvn clean verify`
4. Open a pull request against `main` with a description of what changed and why

---

*Built by **Mahmoud Youssef** — Backend Developer (Spring Boot)*