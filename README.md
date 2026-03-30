# 🛒 E-commerce Backend System (Spring Boot)

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.x-green)
![MySQL](https://img.shields.io/badge/MySQL-8-blue)
![JWT](https://img.shields.io/badge/Auth-JWT-yellow)
![Stripe](https://img.shields.io/badge/Payment-Stripe-purple)
![Status](https://img.shields.io/badge/Status-Production--Ready-brightgreen)

A **production-grade E-commerce backend system** built with Spring Boot, designed to simulate real-world business workflows including **authentication, cart management, order lifecycle, and secure payment processing using Stripe**.

---

## 🎯 Project Overview

This system models a real-world E-commerce platform where:

* Users can browse and search products with advanced filtering
* Products support **variants (price, stock, attributes)**
* Users manage cart and place orders securely
* Payments are processed via **Stripe Checkout**
* System guarantees **data consistency, idempotency, and secure payment handling**

> The goal is to demonstrate **real backend engineering practices**, not just CRUD.

---

## 🚀 Key Features

### 🔐 Authentication & Security

* JWT Authentication (Access & Refresh Tokens)
* Role-Based Authorization (ADMIN / USER)
* Stateless session design

---

### 🛍️ Product & Catalog System

* Product management with categories
* Advanced filtering (name, price range, category, stock)
* Pagination support
* Variant-based product model (price & stock per variant)

---

### 🛒 Cart System

* Variant-aware cart (productId + variantId)
* Quantity validation
* Full cart lifecycle (add / update / remove / clear)

---

### 📦 Order Management

* Order creation from cart snapshot
* Address snapshot stored at purchase time
* Full order lifecycle tracking

---

### 💳 Payment System (Stripe Integration)

* Stripe Checkout Session integration
* Secure Webhook handling
* Payment verification using:

    * Amount validation
    * Currency validation
    * Order ownership validation
* Idempotent payment processing (duplicate-safe)
* Event-driven order updates after payment

---

### ⚙️ System Reliability

* Optimistic locking + retry strategy
* Prevention of negative stock
* Strict state transitions (Order & Payment)
* Defensive programming (null safety, validation)

---

### 📊 API Design

* Standardized response format (`ApiResponse<T>`)
* Clean DTO-based architecture
* Swagger API documentation

---

## 🏗️ Architecture

The system follows a **layered architecture**:

```text
Client
   ↓
Controller → Service → Repository → Database
   ↓
Security Layer (JWT)
   ↓
Exception Handling Layer
   ↓
Event Layer (Payment → Order)
```

---

### Key Design Decisions

* Separation of concerns
* DTO pattern for API contracts
* Centralized exception handling
* Event-driven architecture for payment flow
* Payment provider abstraction (Stripe-ready)

---

## 🔐 Payment Flow (Real-World)

```text
User → Backend → Stripe Checkout
                    ↓
                 Stripe UI
                    ↓
            Payment Completed
                    ↓
        Stripe → Webhook → Backend
                    ↓
          PaymentService
                    ↓
            Event Published
                    ↓
         Order Status Updated
```

---

## 📊 Business Rules

* Users can only access their own cart, orders, and payments
* Orders must be created from a non-empty cart
* Stock is validated before order creation
* Payment allowed only for `PENDING` orders
* Each order has exactly one payment
* Payment is verified using Stripe data (NOT client input)
* Duplicate payment processing is prevented (idempotency)
* Order status updates only through controlled transitions

---

## ⚠️ Exception Handling Strategy

Centralized using `@RestControllerAdvice`

### Example Error Response

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/example",
  "timestamp": "2026-03-27T19:56:01"
}
```

---

### Covered Scenarios

* Validation errors
* Business rule violations
* Unauthorized access
* Payment failures
* Database constraint violations

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
```

---

## 🧰 Tech Stack

| Category   | Technology                  |
| ---------- | --------------------------- |
| Language   | Java 21                     |
| Framework  | Spring Boot                 |
| Security   | Spring Security + JWT       |
| ORM        | Spring Data JPA (Hibernate) |
| Database   | MySQL                       |
| Payment    | Stripe                      |
| Mapping    | MapStruct                   |
| Build Tool | Maven                       |
| Docs       | Swagger (OpenAPI)           |

---

## 🔗 API Overview

All endpoints are prefixed with:

```
/api
```

### Core Modules

* Auth → `/auth/*`
* Products → `/products/*`
* Cart → `/cart/*`
* Orders → `/orders/*`
* Payments → `/payments/*`
* Wishlist → `/wishlist/*`
* Reviews → `/reviews/*`

---

## 🧪 Running the Project

```bash
git clone https://github.com/MahmoudYoussef-web/ecommerce-backend.git
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

## 🧠 System Design Highlights

* Event-driven payment processing
* Idempotent webhook handling
* Variant-based commerce modeling
* Concurrency-safe stock management
* Secure external integration (Stripe)

---

## 💣 Why This Project Matters

* Demonstrates real backend engineering skills
* Handles money-related workflows safely
* Applies production-level patterns:

    * Idempotency
    * Event-driven design
    * Concurrency control
* Goes beyond CRUD into real system design

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
