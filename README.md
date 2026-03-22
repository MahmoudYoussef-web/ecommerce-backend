# E-commerce Backend built with Java Spring Boot, MySQL, Spring Security & JWT

I built this project to develop a production-ready backend system using Spring Boot. The goal was to implement a complete e-commerce backend with authentication, authorization, and real-world business flows such as cart, orders, and payments.

This project helped me understand how to design scalable REST APIs, implement JWT authentication, and build a clean layered architecture.

---

## Complete Tech Stack

* Java 21+
* Spring Boot
* Spring Data JPA (Hibernate)
* Spring Security
* JWT Authentication
* MySQL Database
* Maven
* Lombok
* MapStruct (DTO Mapping)
* Swagger / OpenAPI
* Postman for API Testing

---

## Architecture

The project follows a clean layered architecture:

Controller → Service → Repository → Entity
DTOs + Mappers are used to separate API contracts from database models.

---

## Features Summary

### Authentication & Security

* JWT-based Authentication (Access + Refresh Tokens)
* Secure Login / Register / Logout
* Token Refresh Flow
* Role-based Authorization (Admin / User)
* Stateless Security using Spring Security

---

### Product & Category

* Create / Update / Delete Products (Admin only)
* Pagination for products
* Filter products by category
* Category management (Admin)

---

### Cart System

* Each user has a single cart
* Add items to cart
* Update item quantity
* Remove items
* Clear cart

---

### Orders

* Create order from cart
* Store order snapshot (price, product info)
* Retrieve user orders

---

### Payments

* Create payment for order
* Payment status tracking
* Supported statuses:

    * INITIATED
    * COMPLETED
    * FAILED
    * REFUNDED

---

### Wishlist

* Add product to wishlist
* Remove product
* Retrieve wishlist

---

### Reviews

* Add review for product
* Get product reviews

---

## Database Design

<p align="center">
  <img src="https://github.com/user-attachments/assets/fd86ce51-0360-4806-9d3c-19afde15fb5d" width="900"/>
</p>

This diagram represents the relational database design of the system, including users, roles, authentication (refresh tokens), products, categories, cart, orders, payments, wishlist, and reviews.

---

## REST API Endpoints

All endpoints are prefixed with:

```
/api
```

---

### AuthController

| Method | Endpoint       | Description               |
| ------ | -------------- | ------------------------- |
| POST   | /auth/register | Register new user         |
| POST   | /auth/login    | Login and get tokens      |
| POST   | /auth/refresh  | Refresh access token      |
| POST   | /auth/logout   | Logout (invalidate token) |

---

### ProductController

| Method | Endpoint                        | Description                  |
| ------ | ------------------------------- | ---------------------------- |
| GET    | /products                       | Get all products (paginated) |
| GET    | /products/{id}                  | Get product by ID            |
| POST   | /products                       | Create product (ADMIN)       |
| PUT    | /products/{id}                  | Update product (ADMIN)       |
| GET    | /products/category/{categoryId} | Get by category              |

---

### CategoryController

| Method | Endpoint           | Description        |
| ------ | ------------------ | ------------------ |
| GET    | /categories        | Get all categories |
| GET    | /categories/{slug} | Get by slug        |
| POST   | /categories        | Create (ADMIN)     |
| PUT    | /categories/{id}   | Update (ADMIN)     |
| DELETE | /categories/{id}   | Delete (ADMIN)     |

---

### CartController

| Method | Endpoint                | Description     |
| ------ | ----------------------- | --------------- |
| GET    | /cart                   | Get user cart   |
| POST   | /cart/items             | Add item        |
| PUT    | /cart/items/{productId} | Update quantity |
| DELETE | /cart/items/{productId} | Remove item     |
| DELETE | /cart                   | Clear cart      |

---

### OrderController

| Method | Endpoint | Description     |
| ------ | -------- | --------------- |
| POST   | /orders  | Create order    |
| GET    | /orders  | Get user orders |

---

### PaymentController

| Method | Endpoint              | Description           |
| ------ | --------------------- | --------------------- |
| POST   | /payments             | Create payment        |
| PUT    | /payments/{id}/status | Update payment status |

---

### WishlistController

| Method | Endpoint                    | Description    |
| ------ | --------------------------- | -------------- |
| GET    | /wishlist                   | Get wishlist   |
| POST   | /wishlist/items/{productId} | Add product    |
| DELETE | /wishlist/items/{productId} | Remove product |

---

### ReviewController

| Method | Endpoint                     | Description         |
| ------ | ---------------------------- | ------------------- |
| POST   | /reviews                     | Add review          |
| GET    | /reviews/product/{productId} | Get product reviews |

---

## How to Run

1. Clone the repository

2. Configure application.properties

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ecommerce_db
spring.datasource.username=your_user
spring.datasource.password=your_password

spring.jpa.hibernate.ddl-auto=update

auth.jwt.secret=YOUR_SECRET
auth.jwt.expiration=3600000
```

3. Run the project

```bash
mvn spring-boot:run
```

4. Open Swagger

```
http://localhost:8080/swagger-ui/index.html
```

---

## Reflection

This project helped me:

* Build a complete real-world backend system
* Understand JWT authentication deeply
* Design scalable REST APIs
* Work with relational database modeling
* Apply clean architecture principles

---

## Author

Mahmoud
Backend Developer (Spring Boot)
