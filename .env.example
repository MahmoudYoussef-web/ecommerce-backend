# Changelog

All notable changes to this project will be documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned
- Docker Compose setup for local development
- Password reset via email flow
- Admin order listing with filters
- Unit test coverage expansion

---

## [1.0.0] — 2026-04-15

### Added
- JWT authentication with access and refresh tokens
- Multi-device session management and selective logout
- Role-based access control: `ADMIN`, `CUSTOMER`, `WAREHOUSE`
- Full product and category CRUD with pagination and search filters
- Variant-aware shopping cart with real-time price calculation
- Wishlist management
- Order lifecycle management (`PENDING → PAID → SHIPPED → DELIVERED / CANCELLED`)
- Address snapshot on order creation for immutable shipping records
- Stripe Checkout Session integration
- Webhook ingestion with HMAC and Stripe-Signature verification
- Idempotent payment processing
- Event-driven architecture: `PaymentCompletedEvent`, `OrderCreatedEvent`
- Two-phase inventory reservation system with scheduled cleanup
- `StockMovement` audit trail
- Double-entry bookkeeping via `ChartOfAccount` and `JournalEntry`
- Revenue and dashboard reporting endpoints
- Redis caching for product reads with smart invalidation
- Structured JSON logging via Logstash Logback Encoder
- Swagger / OpenAPI documentation at `/swagger-ui/index.html`
- Dockerfile with two-stage build (Maven builder + Temurin runtime)

[Unreleased]: https://github.com/MahmoudYoussef-web/ecommerce-backend/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/MahmoudYoussef-web/ecommerce-backend/releases/tag/v1.0.0