# 🛒 E-commerce Backend System (Spring Boot)

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.x-green)
![MySQL](https://img.shields.io/badge/MySQL-8-blue)
![JWT](https://img.shields.io/badge/Auth-JWT-yellow)
![Status](https://img.shields.io/badge/Status-Production--Ready-brightgreen)

A production-grade E-commerce backend system built with Spring Boot, designed to simulate real-world business workflows including authentication, cart management, order processing, and payment handling.

---

## 🚀 Key Highlights

* Scalable layered architecture (Controller → Service → Repository)
* JWT Authentication with Access & Refresh Tokens
* Role-Based Authorization (ADMIN / USER)
* Advanced product filtering using dynamic queries (Specifications)
* Standardized API response structure (`ApiResponse<T>`)
* Global exception handling
* DTO mapping using MapStruct
* Pagination support
* Stateless and secure backend design

---

## 🧠 Production Improvements

The system has been enhanced with real-world backend practices to ensure reliability, consistency, and security:

* Input validation at service level (beyond annotations)
* Cart validation (empty cart, invalid quantities)
* Stock validation before order creation
* Prevention of negative stock and inconsistent states
* Order ownership validation (user isolation)
* Payment idempotency (prevent duplicate payments)
* Strict payment state transitions
* Order and payment lifecycle synchronization
* Defensive programming against null and invalid data
* Structured logging for business-critical operations

---

## 📊 Business Rules

* A user can only access their own cart, orders, and payments
* Orders can only be created from a non-empty cart
* Products must be available (not DRAFT) to be added to cart or ordered
* Stock is validated before order creation
* Payment can only be created for PENDING orders
* Each order can have only one payment
* Payment status follows strict transitions:
  * INITIATED → COMPLETED / FAILED / CANCELLED
  * COMPLETED → REFUNDED / PARTIALLY_REFUNDED
* Order status is updated automatically after successful payment

---

## 📝 Logging Strategy

The system uses structured logging to track important business events:

* Order creation and validation failures
* Cart operations (add/update/remove)
* Payment lifecycle events
* Security-related actions (unauthorized access attempts)

Logs are designed to support debugging and monitoring in production environments.

---

## 🔄 Core Business Flows

* User authenticates using JWT
* User browses and filters products
* User adds items to cart
* User places an order
* System calculates total and stores snapshot
* Payment is created and updated

---

## 🧰 Tech Stack

* Java 21+
* Spring Boot
* Spring Data JPA
* Spring Security
* JWT
* MySQL
* Maven
* Lombok
* MapStruct
* Swagger

---

## 🏗️ Architecture

Controller → Service → Repository → Entity  
↘ DTO ↔ Mapper ↗

---

## 📁 Project Structure

```bash
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
````

---

## 🔐 Security

* JWT Authentication
* Access & Refresh Tokens
* Role-based authorization
* Stateless session

---

## 🔗 REST API Endpoints

All endpoints are prefixed with:

```
/api
```

---

### 🔐 Auth

| Method | Endpoint       | Description          |
| ------ | -------------- | -------------------- |
| POST   | /auth/register | Register new user    |
| POST   | /auth/login    | Login and get tokens |
| POST   | /auth/refresh  | Refresh token        |
| POST   | /auth/logout   | Logout               |

---

### 📦 Products

| Method | Endpoint                        | Description              |
| ------ | ------------------------------- | ------------------------ |
| GET    | /products                       | Get all products         |
| GET    | /products/{id}                  | Get product by ID        |
| GET    | /products/search                | Advanced filtering       |
| GET    | /products/category/{categoryId} | Get products by category |
| POST   | /products                       | Create product (ADMIN)   |
| PUT    | /products/{id}                  | Update product (ADMIN)   |
| DELETE | /products/{id}                  | Delete product (ADMIN)   |

---

### 🗂️ Categories

| Method | Endpoint           | Description        |
| ------ | ------------------ | ------------------ |
| GET    | /categories        | Get all categories |
| GET    | /categories/{slug} | Get category       |
| POST   | /categories        | Create (ADMIN)     |
| PUT    | /categories/{id}   | Update (ADMIN)     |
| DELETE | /categories/{id}   | Delete (ADMIN)     |

---

### 🛒 Cart

| Method | Endpoint                | Description     |
| ------ | ----------------------- | --------------- |
| GET    | /cart                   | Get cart        |
| POST   | /cart/items             | Add item        |
| PUT    | /cart/items/{productId} | Update quantity |
| DELETE | /cart/items/{productId} | Remove item     |
| DELETE | /cart                   | Clear cart      |

---

### 📦 Orders

| Method | Endpoint     | Description     |
| ------ | ------------ | --------------- |
| POST   | /orders      | Create order    |
| GET    | /orders      | Get user orders |
| GET    | /orders/{id} | Get order by ID |

---

### 💳 Payments

| Method | Endpoint              | Description           |
| ------ | --------------------- | --------------------- |
| POST   | /payments             | Create payment        |
| PUT    | /payments/{id}/status | Update payment status |

---

### ❤️ Wishlist

| Method | Endpoint                    | Description    |
| ------ | --------------------------- | -------------- |
| GET    | /wishlist                   | Get wishlist   |
| POST   | /wishlist/items/{productId} | Add product    |
| DELETE | /wishlist/items/{productId} | Remove product |

---

### ⭐ Reviews

| Method | Endpoint                     | Description         |
| ------ | ---------------------------- | ------------------- |
| POST   | /reviews                     | Add review          |
| GET    | /reviews/product/{productId} | Get product reviews |

---

## 🗄️ Database Design

<p align="center">
  <img src="https://github.com/user-attachments/assets/fd86ce51-0360-4806-9d3c-19afde15fb5d" width="900"/>
</p>

---

## 📄 API Documentation

[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

---

## ⚡ Quick Run

```bash
git clone https://github.com/MahmoudYoussef-web/ecommerce-backend.git
cd ecommerce-backend
mvn spring-boot:run
```

---

## 🚀 Future Improvements

* Docker containerization
* Deployment to cloud (AWS / Render / Railway)
* Integration with payment gateways (Stripe / PayPal)
* Caching (Redis)
* Unit and integration testing

---

## 👨‍💻 Author

Mahmoud Youssef
Backend Developer | Spring Boot

