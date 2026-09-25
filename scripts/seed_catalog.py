"""Generate a fresh demo catalog (categories, products, images, demo customers).

Restores a working, shoppable catalog for the dev database after the data was
wiped by the integration-test cleanup. Deterministic (fixed seed), idempotent:

    python scripts/seed_catalog.py          # dry-run: prints the plan only
    python scripts/seed_catalog.py --run    # executes inside one transaction

Database credentials are read from the backend .env (DB_URL / DB_USERNAME /
DB_PASSWORD) — never hardcoded here.

Writes only rows:
  - 16 categories (upsert by slug)
  - 133 products (upsert by sku, status ACTIVE)
  - 2 product_images per product (curated Unsplash photos, refreshed for
    ALL active products on every run — replaces older placeholder URLs)
  - 20 demo customers reviewer01..20@test.com (password 123456, ROLE_CUSTOMER,
    verified) so scripts/seed_reviews.py has a reviewer pool
"""

import os
import re
import random
import sys

import pymysql

TENANT_ID = 1
BCRYPT_HASH = "$2a$10$5z70ZbbLjcm7Mxn9FXI60uzxccL1XrSwofdzAQVxYZt"  # "123456"

# (name, slug, blurb) — 16 top-level categories
CATEGORIES = [
    ("Women's Fashion", "womens-fashion", "Dresses, tops, and everyday wardrobe staples."),
    ("Men's Fashion", "mens-fashion", "Shirts, polos, and smart-casual menswear."),
    ("Beauty & Cosmetics", "beauty-cosmetics", "Makeup, nails, and beauty essentials."),
    ("Skincare", "skincare", "Cleansers, serums, and daily skincare routines."),
    ("Footwear", "footwear", "Sneakers, sandals, and shoes for every occasion."),
    ("Bags & Accessories", "bags-accessories", "Handbags, backpacks, belts, and scarves."),
    ("Watches & Jewelry", "watches-jewelry", "Watches, bracelets, and everyday jewelry."),
    ("Home & Living", "home-living", "Décor, bedding, and home essentials."),
    ("Kitchen & Dining", "kitchen-dining", "Cookware, tableware, and kitchen tools."),
    ("Electronics", "electronics", "Audio, smart devices, and everyday electronics."),
    ("Mobile Accessories", "mobile-accessories", "Cases, chargers, and phone accessories."),
    ("Fitness & Sports", "fitness-sports", "Workout gear, sports equipment, and activewear."),
    ("Health & Personal Care", "health-personal-care", "Wellness, grooming, and personal care."),
    ("Baby & Kids", "baby-kids", "Clothing, toys, and nursery essentials."),
    ("Books & Stationery", "books-stationery", "Books, notebooks, and office supplies."),
    ("Groceries & Gourmet", "groceries-gourmet", "Coffee, pantry staples, and gourmet treats."),
]

# (category index, product name, base price USD, brand)
PRODUCTS = [
    # Women's Fashion
    (0, "Floral Midi Wrap Dress", 29.99, "Sara Couture"),
    (0, "Ribbed Knit Bodycon Dress", 24.50, "Moda Lux"),
    (0, "High-Waisted Denim Jeans", 34.99, "Denim Co"),
    (0, "Chiffon Blouse with Puff Sleeves", 19.99, "Silk Road"),
    (0, "Pleated A-Line Skirt", 22.00, "Moda Lux"),
    (0, "Oversized Cotton T-Shirt", 12.99, "Urban Basics"),
    (0, "Tailored Blazer — Lightweight", 45.00, "Sara Couture"),
    (0, "Satin Slip Skirt", 21.50, "Silk Road"),
    (0, "Striped Linen Shirt", 27.99, "Coastal"),
    (0, "Cropped Denim Jacket", 38.00, "Denim Co"),
    # Men's Fashion
    (1, "Classic Oxford Shirt", 28.00, "Menswear Co"),
    (1, "Slim-Fit Chino Pants", 32.50, "Urban Tailor"),
    (1, "Casual Polo Shirt — Pima Cotton", 22.99, "Menswear Co"),
    (1, "Lightweight Bomber Jacket", 48.00, "Aero"),
    (1, "Regular-Fit Denim Jeans", 35.00, "Denim Co"),
    (1, "Linen Short Sleeve Shirt", 24.99, "Coastal"),
    (1, "Crew Neck Cotton Sweatshirt", 26.50, "Urban Basics"),
    (1, "Formal Trousers — Stretch Fit", 29.99, "Urban Tailor"),
    # Beauty & Cosmetics
    (2, "Velvet Matte Lipstick Set (3 pcs)", 15.99, "GlowUp"),
    (2, "24H Longwear Liquid Foundation", 18.50, "Glamora"),
    (2, "HD Blush & Highlighter Palette", 16.00, "GlowUp"),
    (2, "Volumizing Mascara — Waterproof", 11.99, "LashLab"),
    (2, "Gel Eyeliner Pen — Smudge Proof", 9.50, "Glamora"),
    (2, "Nail Polish Duo — Pastel Set", 8.99, "GlowUp"),
    (2, "Setting Spray — Matte Finish", 13.00, "LashLab"),
    (2, "Eyeshadow Palette — Neutral Tones", 19.99, "Glamora"),
    # Skincare
    (3, "Vitamin C Brightening Serum", 21.00, "DermaPure"),
    (3, "Hyaluronic Acid Moisturizer", 17.50, "DermaPure"),
    (3, "Gentle Foaming Facial Cleanser", 12.99, "PureSkin"),
    (3, "Sunscreen SPF 50 — Invisible Finish", 14.50, "DermaPure"),
    (3, "Retinol Night Cream", 24.00, "ReviveLab"),
    (3, "Clay Mask — Deep Pore Cleanse", 13.99, "PureSkin"),
    (3, "Eye Cream — Dark Circle Care", 19.00, "ReviveLab"),
    (3, "Exfoliating Body Scrub", 11.50, "PureSkin"),
    (3, "Rose Water Face Mist", 9.99, "PureSkin"),
    # Footwear
    (4, "Classic White Sneakers", 39.99, "Stride"),
    (4, "Leather Derby Shoes", 59.00, "Oxford & Co"),
    (4, "Comfort Slide Sandals", 18.99, "BeachWalk"),
    (4, "Running Shoes — Cushioned", 49.50, "Stride"),
    (4, "Ankle Leather Boots", 64.00, "Oxford & Co"),
    (4, "Canvas Low-Top Sneakers", 26.99, "Canvas Lab"),
    (4, "Elegant Heeled Pumps", 42.00, "Femme"),
    (4, "Flat Espadrilles", 24.50, "BeachWalk"),
    # Bags & Accessories
    (5, "Structured Leather Handbag", 55.00, "Vera Line"),
    (5, "Everyday Canvas Backpack", 29.99, "UrbanPack"),
    (5, "Reversible Leather Belt", 21.00, "Vera Line"),
    (5, "Silk Square Scarf", 16.50, "Silk Road"),
    (5, "Compact Crossbody Bag", 34.99, "Vera Line"),
    (5, "Card Holder Wallet", 14.00, "UrbanPack"),
    (5, "Oversized Tote Bag", 26.00, "Coastal"),
    (5, "Travel Duffle Bag", 44.50, "UrbanPack"),
    # Watches & Jewelry
    (6, "Minimalist Mesh Strap Watch", 39.99, "TimeWear"),
    (6, "Chronograph Leather Watch", 59.00, "TimeWear"),
    (6, "Gold-Plated Hoop Earrings", 12.99, "Aurum"),
    (6, "Stainless Steel Chain Bracelet", 18.50, "Aurum"),
    (6, "Pearl Stud Earrings", 15.00, "Aurum"),
    (6, "Classic Analog Dress Watch", 49.99, "TimeWear"),
    (6, "Layered Necklace Set", 22.00, "Aurum"),
    (6, "Silicone Sport Watch", 24.99, "TimeWear"),
    # Home & Living
    (7, "Percale Cotton Sheet Set", 32.99, "HomeNest"),
    (7, "Fragrance Candle — Vanilla", 12.50, "Aroma Home"),
    (7, "Soft Throw Blanket", 19.99, "HomeNest"),
    (7, "Decorative Cushion Set (2 pcs)", 17.00, "Nest & Co"),
    (7, "Ceramic Table Vase", 14.99, "Nest & Co"),
    (7, "Cotton Bath Towel Set", 23.50, "HomeNest"),
    (7, "LED Table Lamp", 21.99, "Luma"),
    (7, "Wall Art Poster — Abstract", 16.00, "Nest & Co"),
    # Kitchen & Dining
    (8, "Non-Stick Frying Pan 28cm", 27.99, "ChefPro"),
    (8, "Stainless Steel Cookware Set", 89.00, "ChefPro"),
    (8, "Ceramic Dinnerware Set (16 pc)", 49.50, "TableCraft"),
    (8, "Glass Food Storage Containers (5 pc)", 22.99, "FridgeFresh"),
    (8, "Wooden Cutting Board", 15.50, "ChefPro"),
    (8, "Electric Kettle 1.7L", 19.99, "HomeBrew"),
    (8, "Stainless Steel Water Bottle", 13.99, "Hydra"),
    (8, "Coffee French Press 1L", 24.00, "HomeBrew"),
    # Electronics
    (9, "Wireless Bluetooth Earbuds", 34.99, "SoundWave"),
    (9, "Smart Fitness Band", 29.50, "PulseTech"),
    (9, "Portable Bluetooth Speaker", 26.99, "SoundWave"),
    (9, "2-in-1 Power Bank 20000mAh", 21.00, "ChargeX"),
    (9, "HD Webcam with Mic", 18.99, "VisionPro"),
    (9, "Mechanical Gaming Keyboard", 45.00, "KeyMaster"),
    (9, "Wireless Optical Mouse", 14.99, "VisionPro"),
    (9, "Smart LED Light Strip", 16.50, "Luma"),
    (9, "USB-C Hub 7-in-1", 23.99, "VisionPro"),
    # Mobile Accessories
    (10, "Tempered Glass Screen Protector", 7.99, "ShieldPro"),
    (10, "Silicone Phone Case — Clear", 9.99, "Caseify"),
    (10, "Magnetic Wireless Charger", 17.50, "ChargeX"),
    (10, "Braided USB-C Cable 2m", 8.50, "ChargeX"),
    (10, "Car Phone Mount", 12.99, "RoadMate"),
    (10, "Phone Grip & Stand", 6.99, "Caseify"),
    (10, "Smart Watch Protective Case", 9.00, "ShieldPro"),
    (10, "50W Fast Charging Adapter", 14.99, "ChargeX"),
    # Fitness & Sports
    (11, "Yoga Mat — Non-Slip 6mm", 19.99, "FlexFit"),
    (11, "Adjustable Dumbbell Set", 59.00, "IronCore"),
    (11, "Resistance Bands Set (5 pcs)", 14.99, "FlexFit"),
    (11, "Sports Water Bottle 1L", 9.99, "Hydra"),
    (11, "Jump Rope — Ball Bearing", 11.50, "IronCore"),
    (11, "Running Shorts — Quick Dry", 15.99, "ActiveWear"),
    (11, "Foam Roller", 13.50, "FlexFit"),
    (11, "Gym Drawstring Bag", 10.99, "ActiveWear"),
    (11, "Ankle Weights 2kg", 12.50, "IronCore"),
    # Health & Personal Care
    (12, "Digital Body Scale", 16.99, "CarePlus"),
    (12, "Electric Toothbrush", 27.50, "SmileLab"),
    (12, "Hair Dryer — Ionic 2200W", 24.99, "StylePro"),
    (12, "Beard Trimmer Kit", 21.00, "GroomKing"),
    (12, "Essential Oil Diffuser", 18.99, "Aroma Home"),
    (12, "Digital Thermometer", 8.99, "CarePlus"),
    (12, "Shaving Kit with Stand", 25.50, "GroomKing"),
    (12, "Pill Organizer — Weekly", 6.50, "CarePlus"),
    # Baby & Kids
    (13, "Organic Cotton Baby Bodysuit (3 pc)", 14.99, "TinyTot"),
    (13, "Soft Plush Teddy Bear", 11.99, "Toyland"),
    (13, "Baby Muslin Swaddle Pack", 16.50, "TinyTot"),
    (13, "Wooden Stacking Blocks", 13.00, "Toyland"),
    (13, "Kids Waterproof Raincoat", 17.99, "TinyTot"),
    (13, "Educational Building Blocks (100 pc)", 22.50, "Toyland"),
    (13, "Kids Insulated Lunch Bag", 12.99, "SnackPak"),
    (13, "Baby Monitor — Audio", 29.00, "CarePlus"),
    # Books & Stationery
    (14, "Hardcover Journal — Lined", 9.99, "PaperCo"),
    (14, "Gel Pen Set (12 colors)", 7.50, "Inkwell"),
    (14, "Desk Organizer Tray", 11.99, "WorkWise"),
    (14, "Highlighter Pack (6 pcs)", 6.99, "Inkwell"),
    (14, "A5 Planner — Weekly Layout", 12.50, "PaperCo"),
    (14, "Wireless Bluetooth Keyboard", 24.99, "WorkWise"),
    (14, "Notebook Bundle (3 pcs)", 8.99, "PaperCo"),
    (14, "Sticky Notes & Tabs Set", 5.50, "Inkwell"),
    # Groceries & Gourmet
    (15, "Specialty Coffee Beans 500g", 13.99, "RoastLine"),
    (15, "Extra Virgin Olive Oil 1L", 15.50, "PantryPlus"),
    (15, "Gourmet Chocolate Gift Box", 12.99, "SweetCraft"),
    (15, "Himalayan Pink Salt 500g", 7.99, "PantryPlus"),
    (15, "Green Tea Assortment (20 bags)", 6.50, "TeaLeaf"),
    (15, "Organic Honey Jar 350g", 11.99, "SweetCraft"),
    (15, "Spice Blend Gift Set (5 jars)", 14.50, "PantryPlus"),
    (15, "Dark Roast Instant Coffee 200g", 9.99, "RoastLine"),
]

# ---------------------------------------------------------------------------
# Product photography — curated, product-appropriate Unsplash photos.
# Every URL below was HEAD-verified (HTTP 200) before being added here.
# Served as square crops (600x600) matching the storefront's aspect-square
# image slots. Assigned deterministically per product id so re-runs are
# stable and each product gets a stable photo.
# ---------------------------------------------------------------------------
IMG_URL = "https://images.unsplash.com/photo-{}?auto=format&fit=crop&w=600&h=600&q=80"

CATEGORY_IMAGES = {
    "womens-fashion": [
        "1572804013309-59a88b7e92f1", "1515372039744-b8f02a3ae446",
        "1490481651871-ab68de25d43d", "1483985988355-763728e1935b",
        "1469334031218-e382a71b716b", "1487222477894-8943e31ef7b2",
        "1496747611176-843222e1e57c", "1434389677669-e08b4cac3105",
    ],
    "mens-fashion": [
        "1521572163474-6864f9cf17ab", "1602810318383-e386cc2a3ccf",
        "1594938298603-c8148c4dae35", "1488161628813-04466f872be2",
        "1620012253295-c15cc3e65df4", "1490114538077-0a7f8cb49891",
        "1598554747436-c9293d6a588f", "1516257984-b1b4d707412e",
    ],
    "beauty-cosmetics": [
        "1596462502278-27bfdc403348", "1522335789203-aabd1fc54bc9",
        "1512496015851-a90fb38ba796", "1487412947147-5cebf100ffc2",
        "1586495777744-4413f21062fa", "1631729371254-42c2892f0e6e",
    ],
    "skincare": [
        "1556228578-8c89e6adf883", "1570172619644-dfd03ed5d881",
        "1620916566398-39f1143ab7be", "1612817288484-6f916006741a",
        "1598440947619-2c35fc9aa908",
    ],
    "footwear": [
        "1549298916-b41d501d3772", "1560769629-975ec94e6a86",
        "1595950653106-6c9ebd614d3a", "1606107557195-0e29a4b5b4aa",
        "1603808033192-082d6919d3e1", "1543163521-1bf539c55dd2",
        "1525966222134-fcfa99b8ae77", "1608256246200-53e635b5b65f",
        "1600185365483-26d7a4cc7519",
    ],
    "bags-accessories": [
        "1548036328-c9fa89d128fa", "1553062407-98eeb64c6a62",
        "1584917865442-de89df76afd3", "1547949003-9792a18a2601",
        "1622560480605-d83c853bc5c3", "1590874103328-eac38a683ce7",
        "1566150905458-1bf1fc113f0d",
    ],
    "watches-jewelry": [
        "1524592094714-0f0654e20314", "1522312346375-d1a52e2b99b3",
        "1523170335258-f5ed11844a49", "1573408301185-9146fe634ad0",
        "1611591437281-460bfbe1220a", "1599643478518-a784e5dc4c8f",
        "1611652022419-a9419f74343d",
    ],
    "home-living": [
        "1522708323590-d24dbb6b0267", "1583847268964-b28dc8f51f92",
        "1586023492125-27b2c045efd7", "1493663284031-b7e3aefcae8e",
        "1513694203232-719a280e022f", "1567016432779-094069958ea5",
        "1616486338812-3dadae4b4ace",
    ],
    "kitchen-dining": [
        "1556909114-f6e7ad7d3136", "1590794056226-79ef3a8147e1",
        "1610701596007-11502861dcfa", "1594223274512-ad4803739b7c",
    ],
    "electronics": [
        "1590658268037-6bf12165a8df", "1572569511254-d8f925fe2cbb",
        "1583394838336-acd977736f90", "1546868871-7041f2a55e12",
        "1526738549149-8e07eca6c147", "1527814050087-3793815479db",
        "1615663245857-ac93bb7c39e7", "1588872657578-7efd1f1555ed",
    ],
    "mobile-accessories": [
        "1601784551446-20c9e07cdbdb", "1601593346740-925612772716",
        "1583863788434-e58a36330cf0", "1588508065123-287b28e013da",
        "1580910051074-3eb694886505",
    ],
    "fitness-sports": [
        "1517836357463-d25dfeac3438", "1571019613454-1cb2f99b2d8b",
        "1518611012118-696072aa579a", "1584735935682-2f2b69dff9d2",
        "1593079831268-3381b0db4a77", "1583454110551-21f2fa2afe61",
    ],
    "health-personal-care": [
        "1585771724684-38269d6639fd", "1583947215259-38e31be8751f",
        "1626784215021-2e39ccf971cd", "1629909613654-28e377c37b09",
    ],
    "baby-kids": [
        "1522771930-78848d9293e8", "1515488042361-ee00e0ddd4e4",
        "1596461404969-9ae70f2830c1", "1585435557343-3b092031a831",
    ],
    "books-stationery": [
        "1512820790803-83ca734da794", "1544716278-ca5e3f4abd8c",
        "1456735190827-d1262f71b8a3", "1497633762265-9d179a990aa6",
        "1507842217343-583bb7270b66", "1481627834876-b7833e8f5570",
    ],
    "groceries-gourmet": [
        "1550507992-eb63ffee0847", "1447933601403-0c6688de566e",
        "1474979266404-7eaacbcd87c5", "1490474418585-ba9bad8fd0ea",
        "1542838132-92c53300491e", "1506806732259-39c2d0268443",
        "1509440159596-0249088772ff", "1550989460-0adf9ea622e2",
    ],
}


def product_image_urls(pid, slug):
    """Deterministic (thumbnail, gallery) Unsplash URLs for a product."""
    pool = CATEGORY_IMAGES.get(slug) or list(IMAGE_FALLBACK)
    thumb = IMG_URL.format(pool[pid % len(pool)])
    gallery = IMG_URL.format(pool[(pid * 7 + 3) % len(pool)])
    return thumb, gallery


# Used only if a category slug is missing from CATEGORY_IMAGES (shouldn't happen)
IMAGE_FALLBACK = [CATEGORY_IMAGES["womens-fashion"][0]]


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


def slugify(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def make_sku(name, index=None):
    base = name.upper().replace(" ", "-").replace("'", "")[:12]
    return f"{base}-{index + 1:03d}" if index is not None else base


def main():
    run = "--run" in sys.argv
    rng = random.Random(20260816)

    conn = pymysql.connect(**load_db_config(), autocommit=False)
    cur = conn.cursor()

    cur.execute("SELECT id FROM tenants ORDER BY id LIMIT 1")
    tenant_row = cur.fetchone()
    tenant_id = tenant_row[0] if tenant_row else TENANT_ID
    print(f"tenant_id={tenant_id}")

    # ---- plan ----
    cur.execute("SELECT slug FROM categories WHERE is_deleted=0")
    existing_cats = {r[0] for r in cur.fetchall()}
    new_cats = [c for c in CATEGORIES if slugify(c[1]) not in existing_cats]
    cur.execute("SELECT sku FROM products WHERE is_deleted=0")
    existing_skus = {r[0] for r in cur.fetchall()}
    new_products = [p for p in enumerate(PRODUCTS) if make_sku(p[1][1], p[0]) not in existing_skus]
    cur.execute("SELECT email FROM users WHERE is_deleted=0")
    existing_emails = {r[0] for r in cur.fetchall()}

    print(f"categories: {len(new_cats)} new (of {len(CATEGORIES)})")
    print(f"products:   {len(new_products)} new (of {len(PRODUCTS)})")

    if not run:
        print("DRY-RUN: pass --run to execute.")
        conn.close()
        return

    # ---- categories ----
    cat_ids = {}
    for idx, (name, slug, blurb) in enumerate(CATEGORIES, start=1):
        if slug in existing_cats:
            cur.execute("SELECT id FROM categories WHERE slug=%s AND is_deleted=0", (slug,))
            cat_ids[slug] = cur.fetchone()[0]
            continue
        cur.execute(
            "INSERT INTO categories (created_at, updated_at, is_deleted, active, description, display_order, image_url, name, slug, tenant_id) "
            "VALUES (NOW(), NOW(), 0, 1, %s, %s, %s, %s, %s, %s)",
            (blurb, idx * 10, IMG_URL.format(CATEGORY_IMAGES[slug][0]), name, slug, tenant_id),
        )
        cat_ids[slug] = cur.lastrowid
    print(f"categories inserted: {len(new_cats)}")

    # ---- products + images ----
    products_inserted = 0
    images_inserted = 0
    for pindex, (cat_idx, name, price, brand) in enumerate(PRODUCTS):
        cat_name, cat_slug, _ = CATEGORIES[cat_idx]
        sku = make_sku(name, pindex)
        slug = slugify(name) + "-" + sku.lower()[-6:]
        if sku in existing_skus:
            continue
        featured = 1 if rng.random() < 0.15 else 0
        discounted = price * (1 - rng.choice([0.10, 0.15, 0.20, 0.25]))
        has_discount = 1 if rng.random() < 0.35 else 0
        stock = rng.choice([15, 25, 40, 60, 100])
        short = f"{name} — {brand}."
        desc = f"{name} by {brand}. Quality construction and a dependable everyday choice across the {cat_name} range."
        cur.execute(
            "INSERT INTO products (created_at, updated_at, is_deleted, average_rating, brand, description, discounted_price, featured, "
            "low_stock_threshold, name, price, review_count, short_description, sku, slug, status, stock_quantity, weight_kg, category_id, version, tenant_id) "
            "VALUES (NOW(), NOW(), 0, NULL, %s, %s, %s, %s, 5, %s, %s, 0, %s, %s, %s, 'ACTIVE', %s, %s, %s, 0, %s)",
            (brand, desc, discounted if has_discount else None, featured, name, price, short, sku, slug, stock,
             rng.choice([0.15, 0.3, 0.5, 0.8, 1.2]), cat_ids[cat_slug], tenant_id),
        )
        pid = cur.lastrowid
        products_inserted += 1
        thumb, gallery = product_image_urls(pid, cat_slug)
        cur.execute(
            "INSERT INTO product_images (created_at, updated_at, is_deleted, alt_text, display_order, image_type, primary_image, url, product_id, tenant_id) "
            "VALUES (NOW(), NOW(), 0, %s, 0, 'THUMBNAIL', 1, %s, %s, %s)",
            (name, thumb, pid, tenant_id),
        )
        images_inserted += 1
        cur.execute(
            "INSERT INTO product_images (created_at, updated_at, is_deleted, alt_text, display_order, image_type, primary_image, url, product_id, tenant_id) "
            "VALUES (NOW(), NOW(), 0, %s, 1, 'GALLERY', 0, %s, %s, %s)",
            (name, gallery, pid, tenant_id),
        )
        images_inserted += 1

    # ---- refresh images for ALL active products -------------------------------
    # Rewrites thumbnail + first gallery image with the curated Unsplash pools,
    # including rows created by earlier runs that still carry picsum
    # placeholders. Deterministic per product id, so re-runs are stable.
    cur.execute(
        """SELECT p.id, c.slug FROM products p
           JOIN categories c ON c.id = p.category_id
           WHERE p.is_deleted = 0 AND c.is_deleted = 0
           ORDER BY p.id"""
    )
    refreshed = 0
    for pid, slug in cur.fetchall():
        if slug not in CATEGORY_IMAGES:
            continue
        thumb, gallery = product_image_urls(pid, slug)
        cur.execute(
            "SELECT id FROM product_images WHERE product_id=%s AND is_deleted=0 AND display_order=0",
            (pid,),
        )
        row = cur.fetchone()
        if row:
            cur.execute(
                "UPDATE product_images SET url=%s, updated_at=NOW() WHERE id=%s",
                (thumb, row[0]),
            )
        else:
            cur.execute(
                "INSERT INTO product_images (created_at, updated_at, is_deleted, alt_text, display_order, image_type, primary_image, url, product_id, tenant_id) "
                "VALUES (NOW(), NOW(), 0, 'product', 0, 'THUMBNAIL', 1, %s, %s, %s)",
                (thumb, pid, tenant_id),
            )
        cur.execute(
            "SELECT id FROM product_images WHERE product_id=%s AND is_deleted=0 AND display_order=1",
            (pid,),
        )
        row = cur.fetchone()
        if row:
            cur.execute(
                "UPDATE product_images SET url=%s, updated_at=NOW() WHERE id=%s",
                (gallery, row[0]),
            )
        else:
            cur.execute(
                "INSERT INTO product_images (created_at, updated_at, is_deleted, alt_text, display_order, image_type, primary_image, url, product_id, tenant_id) "
                "VALUES (NOW(), NOW(), 0, 'product', 1, 'GALLERY', 0, %s, %s, %s)",
                (gallery, pid, tenant_id),
            )
        refreshed += 1
    print(f"images refreshed:   {refreshed}")

    # ---- demo customers (reviewer pool for seed_reviews.py) ----
    customers_inserted = 0
    cur.execute("SELECT id FROM roles WHERE name='ROLE_CUSTOMER'")
    customer_role_id = cur.fetchone()[0]
    for i in range(1, 21):
        email = f"reviewer{i:02d}@test.com"
        if email in existing_emails:
            continue
        cur.execute(
            "INSERT INTO users (created_at, updated_at, is_deleted, account_non_locked, email, email_verified, enabled, first_name, last_name, "
            "password_hash, phone_number, status, tenant_id, token_version) "
            "VALUES (NOW(), NOW(), 0, 1, %s, 1, 1, %s, %s, %s, %s, 'ACTIVE', %s, 0)",
            (email, f"Reviewer{i}", "User", BCRYPT_HASH, f"+2010{i:08d}", tenant_id),
        )
        uid = cur.lastrowid
        cur.execute(
            "INSERT INTO user_roles (created_at, updated_at, is_deleted, role_id, user_id, tenant_id) "
            "VALUES (NOW(), NOW(), 0, %s, %s, %s)",
            (customer_role_id, uid, tenant_id),
        )
        customers_inserted += 1

    conn.commit()
    print(f"products inserted:  {products_inserted}")
    print(f"images inserted:    {images_inserted}")
    print(f"customers inserted: {customers_inserted}")
    print("COMMITTED.")
    conn.close()


if __name__ == "__main__":
    main()
