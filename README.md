# AgroLink v2

**AgroLink** is a B2B agro-supply-chain platform for Bangladesh. It connects
**farmers**, **buyers** and **admins** in one place: produce listings, offers,
orders, escrow payments, delivery tracking, disputes, analytics, an AI
assistant, and buyer-personalised AI suggestions.

- Backend: **Java 25, Spring Boot 3.4, Spring Data MongoDB, Spring Security + JWT**
- Frontend: **vanilla HTML / CSS / JavaScript** (no frameworks)
- All money is **Bangladeshi Taka (BDT)** stored as `BigDecimal`.
- All locations use Bangladeshi divisions and districts.

---

## Features

| Module | What it does |
| --- | --- |
| **Auth & roles** | Stateless JWT, BCrypt-12, roles `SUPER_ADMIN / ADMIN / FARMER / BUYER`. Signup needs name, email, password and location; login shows/hides password; forgot-password via 6-digit email code (EmailJS) with resend, attempts limit and 10-minute expiry |
| **Demo mode** | One-click demo logins (Super Admin / Admin / Buyer / Farmer, password `Demo1234!`). Zero database footprint, fully read-only (writes get a friendly popup), toggleable with `AGROLINK_DEMO_ENABLED`. Delete the `demo` package + `demo.js` and it is gone without a trace |
| **Produce Supply** | Farmers add supply with Cloudinary photo upload, resize + JPEG optimisation; search/filter by crop, category, district, price; pagination; optimistic-lock stock (no overselling) |
| **AI Suggest (buyer)** | "AI Suggest" strip on the buyer marketplace: Best Selling, Best Value, Most Repurchased, Lowest Price - computed from the buyer's own orders/offers plus live platform demand (real sold quantities, real repeat-buyer rates). Hidden automatically when the buyer has no history |
| **AI Assistant** | Gemini-powered chat assistant (`POST /api/v1/assistant/chat`) with per-minute/per-day rate limits, available in-app |
| **Offers** | Buyer/farmer counter, accept, reject, withdraw; only the involved parties; expired offers cannot be accepted; no double fulfilment |
| **Orders & escrow** | Created on offer acceptance; escrow hold/release/refund states; `PENDING > CONFIRMED > IN_TRANSIT > DELIVERED` transitions; optimistic locking |
| **Payments** | bKash tokenised checkout, simulated gateway for sandbox, capabilities endpoint; paid/outstanding/refund tracking validated against order totals |
| **Delivery** | Live GPS tracking updates, personnel assignment, delivery history |
| **Disputes** | Raise with reason + evidence; admin resolve/reject with notes |
| **Messages & chat** | Order/listing conversations, realtime WebSocket chat, admin moderation queue with reports |
| **Invoices** | Auto-generated per order, PDF-friendly view, admin management |
| **Weather** | Open-Meteo district forecasts for Bangladesh: today widget (follows the user's registered location, Dhaka default) + 7-day outlook with farming advisories |
| **Dashboards** | Role-based Overview (farmer/buyer), Operations (admin), Control Center (super-admin), platform Analytics - cards, charts, tables, attention items, activity |
| **User management** | Admin staff hierarchy, activate/deactivate accounts, managed creation |
| **Abuse protection** | Per-IP + global rate limits (429), 256 KB JSON cap (413), spam accounts auto-suspend after strikes/write-floods (only an admin can re-activate), registration flood cap, small Tomcat/Mongo pools for 512 MB boxes |

---

## Tech stack

- Java 25, Spring Boot 3.4, Spring Security 6 (jjwt), Spring Data MongoDB (Atlas)
- Spring Cache + Caffeine, Bean Validation, Lombok, WebSocket (STOMP)
- Cloudinary (image uploads), EmailJS (transactional email), Open-Meteo (weather), Gemini (AI assistant)
- Thymeleaf shell + vanilla JS (`static/js/core/*` shared, `static/js/pages/*` per page)

---

## Project structure

```
src/main/java/com/agrolink/app/
  AgroLinkApplication.java
  config/          # SecurityConfig, JwtAuthenticationFilter, AbuseProtectionFilter,
                   # MongoPoolConfig, CacheConfig, DataInitializer, WebSocketConfig ...
  controller/api/  # Auth, User, Produce, Offer, Order, Invoice, Message/Chat,
                   # Dashboard, Analytics, Suggestion, Assistant, Weather, Public ...
  service/ (+impl) # Auth, Produce, Offer, Order, Invoice, Message, Suggestion,
                   # EmailJS/Brevo mail, Cloudinary, FileStorage ...
  demo/            # DemoAccounts, DemoAuthFilter, DemoConfig, DemoReadService (delete to remove demo)
  repository/      # MongoRepository interfaces
  model/           # @Document entities + enums
  dto/             # immutable records, never entities
  exception/       # typed exceptions + GlobalExceptionHandler
src/main/resources/static/js/
  core/            # api, dashboard-page, dashboard-view, weather, toast, router ...
  pages/           # landing, overview, produce, orders, messages, analytics ...
  demo.js          # demo login buttons (delete with the demo package)
Dockerfile + render.yaml   # free-tier Render deploy (Java 25 needs Docker)
```

---

## Local setup

Prereqs: JDK 25, Maven 3.9+, MongoDB (local or Atlas).

```bash
mvn clean package
mvn spring-boot:run
```

On startup `DataInitializer` creates only the super admin
(`superadmin@agrolink.com` / `admin123`) and the category list.
Change the super-admin password immediately.

---

## Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `MONGODB_URI` | dev default in `application.yml` | MongoDB Atlas connection string |
| `JWT_SECRET` | dev default | HMAC secret (use 64+ random chars in prod) |
| `JWT_EXPIRATION_MS` | `86400000` | Token lifetime |
| `EMAILJS_SERVICE_ID/_TEMPLATE_ID/_PUBLIC_KEY/_PRIVATE_KEY` | empty (dev console mode) | Forgot-password emails |
| `CLOUDINARY_CLOUD_NAME/_API_KEY/_API_SECRET` | dev default | Image uploads |
| `GEMINI_API_KEY` | empty | AI assistant (disabled without it) |
| `AGROLINK_DEMO_ENABLED` | `true` | Public demo logins |
| `AGROLINK_RATE_LIMIT_*`, `AGROLINK_AUTO_SUSPEND_*` | tuned defaults | Abuse protection |
| `CORS_ALLOWED_ORIGINS` | localhost | Only needed for a separate frontend domain |

Never commit real secrets - they live in the hosting provider's environment.

---

## Deployment (Render, free tier)

1. Push to GitHub.
2. Render > New > **Blueprint**, connect the repo (`render.yaml` is included).
3. Fill prompted values: `MONGODB_URI`, `JWT_SECRET`, EmailJS keys.
4. Atlas > Network Access > allow `0.0.0.0/0` (or the Render IPs).
5. Deploy. Free boxes sleep after 15 min idle, so the first load is slow - normal.

Notes: the Dockerfile builds with Maven + JDK 25 and runs on a slim JRE
with `-Xmx300m` and `--server.port=$PORT`. The frontend is served by the
same app, so no CORS work is needed.

---

## Testing

```bash
mvn test   # 113 tests: services, dashboards, security filters, demo mode, suggestions
```

---

## Roadmap

- Real-time push notifications for offers and orders
- Demand forecast insights for farmers (seller-side AI)
- Bangla language UI
- Logistics partner management
