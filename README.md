# 🌾 AgroLink

**AgroLink** — a B2B **agro-supply-chain** platform for **Bangladesh**. It connects **farmers**, **buyers**, and **admins** for produce listing, purchase requests, offer negotiation, order management, delivery tracking, BDT payments, and dispute resolution.

Backend: **Java 25 · Spring Boot 3.4+ · Spring Data MongoDB · Spring Security + JWT · Caffeine Cache · BigDecimal BDT pricing**.
Frontend: **vanilla HTML / CSS / JavaScript** (no frameworks).

> All monetary values are in **Bangladeshi Taka (৳ / BDT)** and stored as `BigDecimal`. All locations use Bangladeshi **divisions → districts**.

---

## ✨ Features

| Module | Capabilities |
| --- | --- |
| **Auth & RBAC** | Stateless JWT, BCrypt, roles `SUPER_ADMIN / ADMIN / FARMER / BUYER`, route security per role, ownership + status checks, self-service password change |
| **Produce Listings** | Farmers create/update/deactivate own listings; search/filter by crop, category, district, division, price, quantity; pagination; optimistic-lock stock reservation (no overselling) |
| **Purchase Requests** | Buyers create/update/cancel own requests; set required quantity, max budget (BDT), BD delivery location, target date; farmers browse open requests |
| **Offers** | Buyer/farmer counter → accept/reject/withdraw; only parties involved; expired/rejected offers can't be accepted; no double fulfillment |
| **Orders** | Created on offer acceptance (buyer or farmer may accept); `@Version` optimistic locking; reserved quantity; `PENDING → CONFIRMED → IN_TRANSIT → DELIVERED` transitions |
| **Delivery** | Assign personnel/partner, pickup → transit → delivered, estimated delivery date, delivery history, unauthorized-update prevention |
| **Payments** | `CASH_ON_DELIVERY / BKASH / NAGAD / BANK_TRANSFER / OTHER`; status history, paid/outstanding amounts, refunds; validated against order totals |
| **Disputes** | Buyer/farmer raise with reason + evidence; admin resolves/rejects with resolution notes; tracks responsible admin |
| **Admin Dashboard** | Stats cards (users, farmers, buyers, listings, requests, orders by status, sales/outstanding in ৳, categories, district stats), user management, audit logs, reports |
| **Analytics** | Monthly order volume, sales in BDT, crop-wise sales, district listing stats, farmer performance, buyer history |

---

## 🧱 Tech Stack

- **Java 25**
- **Spring Boot 3.4+**
- **Spring Security + jjwt** (stateless JWT)
- **Spring Data MongoDB** (MongoDB Atlas)
- **Spring Cache + Caffeine** (Lombok + validation)
- **Lombok**
- **Frontend**: HTML5 / CSS3 / Vanilla JS (modular `core/` + `pages/`)

---

## 📁 Package Structure (backend: `com.agrolink.app`)

```
src/main/java/com/agrolink/app/
├── AgroLinkApplication.java        # entry point
├── config/
│   ├── SecurityConfig.java         # stateless security, BCrypt, JWT filter, CORS, role rules
│   ├── JwtAuthenticationFilter.java# per-request JWT parsing → SecurityContext
│   ├── DataInitializer.java        # bootstrap: SUPER_ADMIN + categories only (no demo data)
│   ├── CacheConfig.java            # Caffeine caches: listings, categories, dashboard stats
│   ├── RestAuthenticationEntryPoint / RestAccessDeniedHandler (JSON 401/403)
├── controller/
│   ├── api/                        # REST (Auth, User, Produce, Offer, Order, Delivery, Payment, Dispute, Admin, Analytics)
│   └── web/                        # server-rendered MVC shell (login/landing)
├── service/ (+ impl/)
│   ├── AuthService / UserService
│   ├── ProduceService / OfferService / OrderService
│   ├── AdminDashboardService / AnalyticsService
│   └── JwtUtils / CustomUserDetailsService
├── repository/                     # MongoRepository interfaces (+ custom queries, projections)
├── model/                          # @Document entities + enums (Role, ListingStatus, …)
├── dto/                            # immutable records (requests/responses) — never entities
├── projection/                     # Mongo aggregation projections
├── exception/                      # typed exceptions + GlobalExceptionHandler
```

Frontend: `src/main/resources/static/` — `index.html`, `login.html`, `register.html`, `dashboard.html`, `listings.html`, `orders.html`, `purchase-requests.html`, `admin-dashboard.html`, `produce.html`, `overview.html`, `settings.html`, `users.html`, `analytics.html` + `js/core/*` + `js/pages/*` (vanilla JS).

---

## 🚀 Local Setup

Prereqs: JDK **25**, Maven 3.9+, MongoDB (local or Atlas).

```bash
mvn clean package
mvn spring-boot:run
```

On startup `DataInitializer` creates ONLY:
- **Super admin** — `superadmin@agrolink.com` / `admin123`
- **Categories** (Grains, Vegetables, Fruits, Dairy)

No demo users / listings / offers / orders are seeded.

---

## ⚙️ Environment Variables

| Variable | Default | Required | Purpose |
| --- | --- | --- | --- |
| `MONGODB_URI` | *(none — must set)* | ✅ prod | MongoDB Atlas connection string |
| `MONGODB_DATABASE` | from URI | – | Optional explicit DB name |
| `PORT` / `SERVER_PORT` | `8080` | ✅ prod | Deployment-provided HTTP port |
| `JWT_SECRET` | *(none — must set)* | ✅ prod | HMAC signing secret (≥32 chars, random) |
| `JWT_EXPIRATION_MS` | `86400000` | – | Access-token lifetime |
| `CORS_ALLOWED_ORIGINS` | dev defaults | – | Comma-separated frontend origins |

> **Never commit real values.** The repo ships `application-prod.yml` + `.env.example` only. Real secrets live in your hosting provider's environment / Atlas.

---

## ☁️ Deployment (Render)

1. Push the backend to GitHub.
2. In Render → **New Web Service** → connect the repo.
3. **Environment** → Java; **Build Command** `mvn clean package -DskipTests`; **Start Command** `java -jar target/agrolink-1.0.0.jar`.
4. Set env vars: `MONGODB_URI`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` (Render injects `PORT` automatically).
5. MongoDB Atlas → **Network Access** → allow the Render service IP / 0.0.0.0 (dev only).
6. Deploy, then verify: `GET https://<app>.onrender.com/api/health`.

Frontend (vanilla static): deploy the `static/` folder to *Render Static Site* / GitHub Pages / Netlify, then set one **API base URL** value (see `js/core/api.js`) pointing at the Render backend. Backend CORS must include the frontend origin.

---

## 🔒 Authentication & Roles

- BCrypt hashing; no passwords in responses.
- Stateless JWT; secret + expiry from env.
- Roles: `SUPER_ADMIN` manages admins; `ADMIN` manages users/listings/orders/disputes; `FARMER` + `BUYER` act only on their own data.
- Ownership + role checks on every protected operation; arbitrary status jumps rejected.

---

## 🧪 Testing

```bash
mvn test       # unit tests (services, BDT/pricing, validation)
```

---

## 🧭 Roadmap / Future Work

- Real payment gateway integration (bKash / Nagad) behind the extensible `PaymentService`.
- Push/WebSocket notifications for offers & orders.
- SMS (Bangladeshi operator) alerts.
- Multi-language UI (বাংলা / English).
- Advanced logistics partner management + route optimization.
</content>
