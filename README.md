# 🛒 E-commerce Backend System (Spring Boot)

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.x-green)
![MySQL](https://img.shields.io/badge/MySQL-8-blue)
![JWT](https://img.shields.io/badge/Auth-JWT-yellow)
![Stripe](https://img.shields.io/badge/Payment-Stripe-purple)
![Status](https://img.shields.io/badge/Status-Production--Ready-brightgreen)

A **production-grade E-commerce backend system** built with Spring Boot, designed to simulate real-world business workflows including **authentication, product management, cart operations, order lifecycle, and secure payment processing**.

---

## 📌 Overview

This system represents a real-world E-commerce platform where:

* Users can browse and search products with filters
* Manage cart and wishlist
* Place orders and track them
* Perform secure payments via Stripe
* System guarantees **data consistency, idempotency, and security**

---

## 🚀 Key Features

### 🔐 Authentication & Security

* JWT Authentication (Access + Refresh Tokens)
* Role-Based Access Control (ADMIN / CUSTOMER / WAREHOUSE)
* Stateless security architecture
* Secure endpoints using `@PreAuthorize`

---

### 🛍️ Product & Catalog

* Product CRUD operations
* Category management
* Search with filters:

    * name
    * price range
    * category
    * stock
* Pagination support

---

### 🛒 Cart System

* Add / Update / Remove items
* Variant-aware cart (product + variant)
* Full cart lifecycle
* Price calculation

---

### ❤️ Wishlist

* Add/remove products
* Retrieve user wishlist

---

### 📦 Order Management

* Create order from cart
* Address snapshot stored at purchase
* Order lifecycle:

```
PENDING → PAID → SHIPPED → DELIVERED
           ↘ CANCELLED
```

* Role-based order actions:

    * Customer → create orders
    * Admin / Warehouse → manage status

---

### 💳 Payment System (Stripe)

* Create payment for order
* Stripe Checkout integration
* Webhook handling
* Payment validation:

    * Amount check
    * Currency check
    * Ownership check

---

### ⚙️ System Reliability

* Idempotent payment processing
* Event-driven architecture:

```
Payment → Event → Order / Inventory / Finance
```

* Optimistic locking + retry
* Strict business rules enforcement

---

## 🏗️ Architecture

```text
Controller → Service → Repository → Entity
                ↓
            Event Layer
                ↓
        Payment / Order Sync
```

---

### Design Principles

* Clean architecture (layered)
* DTO-based API contracts
* Separation of concerns
* Centralized exception handling
* Event-driven processing

---

## 🔄 Payment Flow

```text
User → Backend → Stripe Checkout
                    ↓
                 Stripe UI
                    ↓
            Payment Completed
                    ↓
        Webhook → Backend
                    ↓
         PaymentService
                    ↓
        PaymentCompletedEvent
                    ↓
     Order updated + Inventory + Finance
```

---

## 📊 Business Rules

* Users can only access their own data
* Orders must be created from valid cart
* Payment allowed only for `PENDING` orders
* Each order has one payment only
* Payment is verified using backend logic (NOT client)
* Duplicate processing prevented (idempotency)
* Order state transitions are controlled

---

## ❗ Exception Handling

Centralized using `@RestControllerAdvice`

### Example Response

```json
{
  "success": false,
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_ERROR",
  "path": "/api/example",
  "timestamp": "2026-04-14T10:00:00Z"
}
```

---

## 📁 Project Structure

```text
src/main/java/com/mahmoud/ecommerce_backend
├── controller
├── service
├── repository
├── dto
├── mapper
├── entity
├── security
├── exception
├── config
├── event
```

---

## 🔗 API Modules

All endpoints are prefixed with:

```
/api
```

### Core Modules

* Auth → `/api/auth`
* Products → `/api/products`
* Categories → `/api/categories`
* Cart → `/api/cart`
* Orders → `/api/orders`
* Payments → `/api/payments`
* Wishlist → `/api/wishlist`
* Reviews → `/api/reviews`
* Users → `/api/users`

---

## 📘 API Examples

### Create Order

```http
POST /api/orders
```

```json
{
  "addressId": 1,
  "customerNotes": "Leave at door"
}
```

---

### Create Payment

```http
POST /api/payments
```

```json
{
  "orderId": 1,
  "method": "CREDIT_CARD"
}
```

---

### Checkout Session

```http
POST /api/payments/checkout/{paymentId}
```

---

## 🧪 Running the Project

```bash
git clone https://github.com/your-username/ecommerce-backend.git
cd ecommerce-backend
mvn spring-boot:run
```

---

## 📄 API Documentation

Swagger UI:

```
http://localhost:8080/swagger-ui/index.html
```

---

## 🧰 Tech Stack

| Category   | Technology            |
| ---------- | --------------------- |
| Language   | Java 21               |
| Framework  | Spring Boot 3         |
| Security   | Spring Security + JWT |
| ORM        | Spring Data JPA       |
| Database   | MySQL                 |
| Payment    | Stripe                |
| Mapping    | MapStruct             |
| Build Tool | Maven                 |
| Docs       | Swagger OpenAPI       |

---

## 🧠 System Highlights

* Event-driven payment processing
* Idempotent webhook handling
* Secure payment validation
* Clean layered architecture
* Production-ready backend design

---

## 👨‍💻 Author

**Mahmoud Youssef**
Backend Developer (Spring Boot)

---

## 🎯 Final Result

A backend system that is:

* ✅ Secure
* ✅ Scalable
* ✅ Consistent
* ✅ Production-ready
* ✅ Real-world applicable
