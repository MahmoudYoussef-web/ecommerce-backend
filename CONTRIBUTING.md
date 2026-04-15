# Contributing Guide

Thanks for your interest in contributing! Here's everything you need to get up and running.

---

## Prerequisites

- Java 21
- Maven 3.8+
- MySQL 8
- Redis
- Docker (optional but recommended)

---

## Local Setup

```bash
git clone https://github.com/MahmoudYoussef-web/ecommerce-backend.git
cd ecommerce-backend
```

Create the database:

```sql
CREATE DATABASE ecommerce_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'dev_user'@'localhost' IDENTIFIED BY 'Dev@2026#';
GRANT ALL PRIVILEGES ON ecommerce_db.* TO 'dev_user'@'localhost';
FLUSH PRIVILEGES;
```

Run the app:

```bash
mvn spring-boot:run
```

Swagger UI will be available at `http://localhost:8080/swagger-ui/index.html`.

---

## Branch Naming

| Type | Pattern | Example |
|---|---|---|
| Feature | `feature/<short-description>` | `feature/discount-codes` |
| Bug fix | `fix/<short-description>` | `fix/cart-price-calculation` |
| Refactor | `refactor/<short-description>` | `refactor/payment-service` |
| Docs | `docs/<short-description>` | `docs/api-examples` |

Always branch off `main`.

---

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add discount code support to cart
fix: prevent duplicate payment for same order
refactor: extract webhook validation into interface
docs: add payment flow diagram to README
test: add integration test for order cancellation
```

---

## Architecture Rules

- **Controllers** are thin — they validate input and delegate to services. No business logic.
- **Services** own business logic. Always program to the interface (`OrderService`, not `OrderServiceImpl`).
- **Entities** never cross the service boundary — use DTOs and MapStruct mappers at the API layer.
- **Cache logic** belongs in `ProductCacheService`, not scattered in the main service.
- **Events** (`PaymentCompletedEvent`, `OrderCreatedEvent`) are used for cross-domain side effects — don't call services directly across domain boundaries.

---

## Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=EcommerceIntegrationTest

# Skip tests during build
mvn clean package -DskipTests
```

---

## Submitting a Pull Request

1. Fork the repo and create your branch from `main`
2. Make your changes following the architecture rules above
3. Ensure `mvn clean verify` passes
4. Open a PR — the template will guide you through the required checklist
5. A review will be done within a few days

---

## Reporting Issues

Use the [Bug Report](.github/ISSUE_TEMPLATE/bug_report.yml) or [Feature Request](.github/ISSUE_TEMPLATE/feature_request.yml) templates on GitHub Issues.

Please include the full error response body and relevant logs when reporting bugs — it makes triage much faster.