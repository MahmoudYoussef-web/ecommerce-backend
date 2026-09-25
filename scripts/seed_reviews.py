"""Seed product reviews for all products that have none.

Replicates the quality pattern of the original seeded reviews (users,
rating distribution, verified-purchase ratio, helpful votes, template pool).
Idempotent-ish: only targets products with review_count = 0.

Usage:
    python scripts/seed_reviews.py          # dry-run: prints the plan only
    python scripts/seed_reviews.py --run    # executes inside one transaction

Database credentials are read from the backend .env (DB_URL / DB_USERNAME /
DB_PASSWORD) — never hardcoded here.

Writes only rows (reviews INSERT) plus products.average_rating /
products.review_count UPDATE. All inserts use approved = 1 so the public
read path (GET /api/reviews/product/{id}, which filters approved = true)
surfaces them.
"""

import os
import re
import random
import sys
import datetime

import pymysql

TENANT_ID = 1

# ---- template pools: original (from the first 90 reviews) + expansions ----
OLD_TITLES = [
    "Worth every penny", "Perfect out of the box", "Best in its class", "Happy so far",
    "Decent but has quirks", "Solid choice", "Can't recommend enough", "Exceeded expectations",
    "Very happy with the purchase", "Almost perfect", "Flawless experience", "Absolutely love it",
    "Mixed feelings", "Great value for money", "Impressive performance", "Fine, nothing special",
    "Good, not great", "Does exactly what it should", "Okay for the price",
]
NEW_TITLES = [
    "Exceeded my expectations", "Reliable and well built", "Worth the upgrade",
    "Happy with my purchase", "Strong performance", "Quality you can feel",
    "Smooth sailing so far", "Hard to beat at this price", "Exactly as described",
    "Simple and effective", "Pleasantly surprised", "Does the job well",
    "Great daily driver", "Premium feel, fair price", "Would buy again",
    "Better than expected", "No complaints here", "Good buy overall",
    "Solid performer, minor flaws", "Handles everything I throw at it",
]
OLD_BODIES = [
    "Zero complaints. Fast, reliable and the packaging was immaculate. Five stars well deserved.",
    "Superb quality and fast delivery. This has quickly become my favorite tech purchase this year.",
    "Really solid product. A couple of minor quirks but overall great value for the money.",
    "It works, but there are better options at this price point. Average performance overall.",
    "Very happy with it. Performance is great, though battery life is slightly less than advertised.",
    "Everything about this product feels premium. Setup was effortless and it performs beyond expectations.",
    "No regrets. Does the job perfectly well and shipping was quick.",
    "Good build quality and it works well. Would have given 5 stars if not for the slightly confusing setup.",
    "Mixed feelings. Hardware is nice but support documentation is lacking.",
    "Blown away by the build quality and performance. Works flawlessly straight out of the box.",
    "Does what it says on the box, just nothing more. Fine for the price.",
    "Decent device but the software experience feels clunky in places. Hoping updates fix it.",
]
NEW_BODIES = [
    "Bought this after a lot of research and I'm glad I did. Does everything I need without any fuss.",
    "Came well packaged and works exactly as advertised. No surprises, just a good product.",
    "Solid performer in every way. The quality is noticeable the moment you unbox it.",
    "Great product for the price. I've been using it daily for a while now and it hasn't missed a beat.",
    "Very happy with this purchase. It feels durable, works reliably, and looks great on my desk.",
    "Does exactly what it promises. Simple, functional, and well made.",
    "Was a bit hesitant at first but the quality won me over. Worth every pound.",
    "Excellent value. Build quality and performance are both above what I expected at this price.",
    "Been using it for a few weeks now and it's been flawless. Would definitely recommend.",
    "The product is great overall. Only minor thing is the initial setup takes a little time.",
    "Impressive quality and fast shipping. Exactly what I needed.",
    "It's a solid product that gets the job done. Happy with the purchase overall.",
    "Great performance and good build quality. A few minor quirks but nothing deal-breaking.",
    "Can't fault it so far. Works perfectly and feels premium in hand.",
    "Really good product. Does what it should and does it well.",
    "Nothing to complain about. Reliable, well made, and fair price.",
    "Good purchase. It's not perfect but it delivers where it matters most.",
]

POS_TITLES = {"Worth every penny", "Perfect out of the box", "Best in its class", "Solid choice",
    "Can't recommend enough", "Exceeded expectations", "Very happy with the purchase", "Flawless experience",
    "Absolutely love it", "Great value for money", "Impressive performance", "Exceeded my expectations",
    "Reliable and well built", "Worth the upgrade", "Happy with my purchase", "Strong performance",
    "Quality you can feel", "Smooth sailing so far", "Hard to beat at this price", "Exactly as described",
    "Simple and effective", "Pleasantly surprised", "Does the job well", "Great daily driver",
    "Premium feel, fair price", "Would buy again", "Better than expected", "No complaints here",
    "Handles everything I throw at it", "Happy so far"}
NEU_TITLES = {"Fine, nothing special", "Okay for the price", "Decent but has quirks", "Good, not great",
    "Mixed feelings", "Almost perfect", "Does exactly what it should", "Solid performer, minor flaws", "Good buy overall"}
POS_BODIES = {"Zero complaints. Fast, reliable and the packaging was immaculate. Five stars well deserved.",
    "Superb quality and fast delivery. This has quickly become my favorite tech purchase this year.",
    "Everything about this product feels premium. Setup was effortless and it performs beyond expectations.",
    "No regrets. Does the job perfectly well and shipping was quick.",
    "Blown away by the build quality and performance. Works flawlessly straight out of the box.",
    "Bought this after a lot of research and I'm glad I did. Does everything I need without any fuss.",
    "Came well packaged and works exactly as advertised. No surprises, just a good product.",
    "Solid performer in every way. The quality is noticeable the moment you unbox it.",
    "Great product for the price. I've been using it daily for a while now and it hasn't missed a beat.",
    "Very happy with this purchase. It feels durable, works reliably, and looks great on my desk.",
    "Does exactly what it promises. Simple, functional, and well made.",
    "Was a bit hesitant at first but the quality won me over. Worth every pound.",
    "Excellent value. Build quality and performance are both above what I expected at this price.",
    "Been using it for a few weeks now and it's been flawless. Would definitely recommend.",
    "Impressive quality and fast shipping. Exactly what I needed.",
    "Can't fault it so far. Works perfectly and feels premium in hand.",
    "Really good product. Does what it should and does it well.",
    "Nothing to complain about. Reliable, well made, and fair price."}
NEU_BODIES = {"It works, but there are better options at this price point. Average performance overall.",
    "Does what it says on the box, just nothing more. Fine for the price.",
    "Mixed feelings. Hardware is nice but support documentation is lacking.",
    "Decent device but the software experience feels clunky in places. Hoping updates fix it.",
    "The product is great overall. Only minor thing is the initial setup takes a little time.",
    "It's a solid product that gets the job done. Happy with the purchase overall.",
    "Great performance and good build quality. A few minor quirks but nothing deal-breaking.",
    "Good purchase. It's not perfect but it delivers where it matters most."}

TITLES_ALL = OLD_TITLES + NEW_TITLES
BODIES_ALL = OLD_BODIES + NEW_BODIES
RATING_POOL = [5] * 49 + [4] * 38 + [3] * 13
VOTES_POOL = [i for i, w in enumerate([1, 2, 4, 5, 7, 8, 10, 9, 8, 7, 6, 4, 3, 2, 1, 1]) for _ in range(w)]


def load_db_config():
    """Read DB settings from the backend .env next to this script."""
    env = {}
    env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env")
    try:
        with open(env_path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, _, v = line.partition("=")
                env[k.strip()] = v.strip()
    except FileNotFoundError:
        pass
    m = re.search(r"jdbc:mysql://([^:/]+):(\d+)/([^?]+)", env.get("DB_URL", ""))
    host, port, db = (m.group(1), int(m.group(2)), m.group(3)) if m else ("localhost", 3306, "ecommerce_db")
    return dict(host=host, port=port, user=env.get("DB_USERNAME", "dev_user"),
                password=env.get("DB_PASSWORD", ""), database=db)


def pick(pool_yes, pool_no, ratio):
    r = random.random()
    return random.choice(tuple(pool_yes)) if r < ratio else random.choice(tuple(pool_no))


def main():
    run = "--run" in sys.argv
    random.seed(20260816)

    conn = pymysql.connect(**load_db_config(), autocommit=False)
    cur = conn.cursor()

    cur.execute(
        """SELECT DISTINCT u.id FROM users u
           JOIN user_roles ur ON ur.user_id = u.id
           JOIN roles r ON r.id = ur.role_id
           WHERE r.name = 'ROLE_CUSTOMER' AND u.is_deleted = 0 AND ur.is_deleted = 0
           ORDER BY u.id"""
    )
    reviewers = [r[0] for r in cur.fetchall()]

    cur.execute("SELECT p.id, p.status FROM products p WHERE p.review_count=0 AND p.is_deleted=0 ORDER BY p.id")
    targets = cur.fetchall()

    print(f"reviewer users: {len(reviewers)}")
    print(f"target products (review_count=0): {len(targets)}")
    if not run:
        print("DRY-RUN: pass --run to execute.")
        conn.close()
        return

    n3 = sum(1 for _ in targets if random.random() < 0.43)
    n2 = len(targets) - n3
    print(f"plan: {n2} products x2, {n3} products x3 = {n3*3 + n2*2} reviews")

    inserted = 0
    ref_end = datetime.datetime(2026, 8, 16)
    for pid, status in targets:
        n = 3 if random.random() < 0.43 else 2
        random.shuffle(reviewers)
        for i in range(n):
            user_id = reviewers[i]
            rating = random.choice(RATING_POOL)
            if rating == 3:
                title = pick(NEU_TITLES, TITLES_ALL, 0.8)
                body = pick(NEU_BODIES, BODIES_ALL, 0.8)
            else:
                title = pick(POS_TITLES, TITLES_ALL, 0.85)
                body = pick(POS_BODIES, BODIES_ALL, 0.85)
            verified = random.random() < 0.9
            votes = random.choice(VOTES_POOL)
            created = ref_end - datetime.timedelta(
                days=random.randint(1, 300), seconds=random.randint(0, 86399))
            cur.execute(
                """INSERT INTO reviews
                   (created_at, updated_at, is_deleted, approved, body, helpful_votes,
                    rating, title, verified_purchase, product_id, user_id, created_by, tenant_id, updated_by)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                (created, created, 0, 1, body, votes, rating, title, 1 if verified else 0,
                 pid, user_id, None, TENANT_ID, None))
            inserted += 1
        cur.execute("SELECT ROUND(AVG(rating),2), COUNT(*) FROM reviews WHERE product_id=%s AND is_deleted=0", (pid,))
        avg, cnt = cur.fetchone()
        cur.execute("UPDATE products SET average_rating=%s, review_count=%s WHERE id=%s", (avg, cnt, pid))

    conn.commit()
    print(f"INSERTED {inserted} reviews + updated {len(targets)} products. COMMITTED.")
    conn.close()


if __name__ == "__main__":
    main()
