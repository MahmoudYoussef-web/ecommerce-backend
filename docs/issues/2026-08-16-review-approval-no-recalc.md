# 🐛 [Bug]: Review approval never triggers recalculation of product.averageRating

> Opened: 2026-08-16 · Severity: **Medium (affects functionality)** · Environment: Local

## 📝 Bug Description

Submitting a review through the public API has **no visible effect**:

- `POST /api/reviews` stores the review with `approved = false` (the entity default), but the only read path — `GET /api/reviews/product/{id}` — filters `approved = true`. A real customer's review is therefore **hidden forever** unless someone flips the flag by direct DB access.
- `products.average_rating` and `products.review_count` are persisted columns that **no code path recalculates** after a review is created, approved, or deleted. They are only ever set by direct SQL (the existing storefront ratings were seeded with `INSERT`/`UPDATE`).

Net effect: the rating widgets on the frontend (product cards, category pages, product detail) work **only on manually seeded data**. Any genuine user interaction leaves no trace.

## 🔁 Steps to Reproduce

1. Register/login a customer (`POST /api/auth/...`).
2. `POST /api/reviews` with `{ "productId": 1, "rating": 4, "comment": "..." }`.
3. `GET /api/reviews/product/1` → the review is **absent** (`approved=false` is filtered out).
4. `GET /api/products?size=300` → `averageRating` and `reviewCount` for product 1 are **unchanged**.

## ✅ Expected Behavior

- A review becomes visible once approved (moderation gate is fine as a concept).
- Product aggregates (`average_rating`, `review_count`) are recalculated when a review is created/approved/deleted — e.g. in the same transaction in `ReviewServiceImpl` or via a recalculation service.

## ❌ Actual Behavior

- Review saved with `approved = false` and never surfaced by the public read path.
- Product aggregates never change; they only reflect rows written outside the application.

```json
POST /api/reviews
{
  "productId": 1,
  "rating": 4,
  "comment": "Testing the rating flow"
}

// 200 OK — but nothing visible afterwards
```

## 🔖 Version / Commit

Current `main` at the time of discovery (frontend ratings feature built against seeded data).

## 🌍 Environment

- Local (bare metal)

## 🚨 Severity

- Medium (affects functionality) — the customer-ratings feature is effectively read-only in production behavior.

## 📜 Relevant Code

- `src/main/java/com/mahmoud/ecommerce_backend/entity/Review.java` — `approved` defaults to `false`.
- `src/main/java/com/mahmoud/ecommerce_backend/service/review/ReviewServiceImpl.java` — `createReview()` never recalculates aggregates; `getProductReviews()` filters `approved = true`.
- `src/main/java/com/mahmoud/ecommerce_backend/entity/Product.java` — `averageRating` / `reviewCount` are persisted columns with no update trigger in the domain.
- `src/main/java/com/mahmoud/ecommerce_backend/controller/ReviewController.java` — no approval/admin endpoint exists.

## 📎 Additional Context

- **Known pre-existing data inconsistency (not caused by this issue):** one product in `products` has `review_count > 0` but no matching review rows (`LEFT JOIN reviews` count = 0). It was already inconsistent before the seeding work and was intentionally left untouched. If it ever shows up as a regression, it is this known artifact — verify before treating it as a new bug.
- There is currently **no approval workflow endpoint** at all; `approved` can only be changed via direct DB access.
- The store's 37 rated products (and later all 170) were seeded with direct SQL (`approved = 1` + `UPDATE products SET average_rating, review_count`), which is why the frontend displays ratings even though the API flow is broken.
- Suggested fix shape (separate task): auto-approve or add an admin approval endpoint, and recalculate `average_rating`/`review_count` transactionally inside the review service. Write a regression test that creates a review through the API and asserts visibility + aggregate update.
