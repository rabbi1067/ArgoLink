<div align="center">

# Ã°Å¸Å’Â¾ AgroLink v2

### Farm to Market, Directly & Transparently Ã¢â‚¬â€ Ã Â¦â€“Ã Â¦Â¾Ã Â¦Â®Ã Â¦Â¾Ã Â¦Â° Ã Â¦Â¥Ã Â§â€¡Ã Â¦â€¢Ã Â§â€¡ Ã Â¦Â¬Ã Â¦Â¾Ã Â¦Å“Ã Â¦Â¾Ã Â¦Â°, Ã Â¦Â¸Ã Â¦Â°Ã Â¦Â¾Ã Â¦Â¸Ã Â¦Â°Ã Â¦Â¿ Ã Â¦â€œ Ã Â¦Â¸Ã Â§ÂÃ Â¦Â¬Ã Â¦Å¡Ã Â§ÂÃ Â¦â€ºÃ Â¦Â­Ã Â¦Â¾Ã Â¦Â¬Ã Â§â€¡

**A complete B2B agro-supply-chain platform for Bangladesh** Ã¢â‚¬â€ farmers sell directly, buyers source fresh stock, admins govern everything. No middlemen.

[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MongoDB](https://img.shields.io/badge/MongoDB-Atlas-47A248?style=for-the-badge&logo=mongodb&logoColor=white)](https://www.mongodb.com/atlas)
[![JWT](https://img.shields.io/badge/JWT-Security-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)](https://jwt.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](./Dockerfile)
[![Tests](https://img.shields.io/badge/Tests-114_passing-brightgreen?style=for-the-badge)](#-testing)
[![Bangla](https://img.shields.io/badge/Ã Â¦Â¬Ã Â¦Â¾Ã Â¦â€šÃ Â¦Â²Ã Â¦Â¾-UI_Ready-red?style=for-the-badge)](#-bangla--english-toggle)

[Ã¢Å“Â¨ Live Demo](https://argolink.onrender.com/) Ã¢â‚¬Â¢ [Ã°Å¸â€™Â» Source Code](https://github.com/rabbi1067/ArgoLink) Ã¢â‚¬Â¢ [Ã°Å¸Å¡â‚¬ Deploy](#-deployment-render-free-tier) Ã¢â‚¬Â¢ [Ã°Å¸â€œâ€“ Setup](#-local-setup)

</div>

---

## Ã¢Å“Â¨ What makes AgroLink v2 special

<table>
<tr>
<td width="50%">

### Ã°Å¸Â¤â€“ AgroLink AI Assist
Gemini-powered assistant built into the app. Ask about crops, orders, prices, or how to use the platform Ã¢â‚¬â€ with quick-question chips, chat history, and per-minute/per-day rate limits. Speaks **Bangla and English**.

</td>
<td width="50%">

### Ã°Å¸Å½Â¯ AI Suggest (buyer marketplace)
A personalised strip on the buyer marketplace: **Best Selling Ã‚Â· Best Value Ã‚Â· Most Repurchased Ã‚Â· Lowest Price**. Computed from *your* order + offer history and live platform demand Ã¢â‚¬â€ real sold quantities, real repeat-buyer rates. No history? The section hides itself. No fake ratings, ever.

</td>
</tr>
<tr>
<td>

### Ã°Å¸Å’Â Full Bangla UI toggle
One pill button next to day/night mode flips the **entire project** Ã¢â‚¬â€ landing, all 12 dashboard pages, sidebar, forms, validation messages, weather, toasts Ã¢â‚¬â€ between English and Bangla. Choice persists across visits.

</td>
<td>

### Ã°Å¸â€˜â‚¬ Read-only demo mode
One-click logins: **Super Admin / Admin / Buyer / Farmer** (`Demo1234!`). Zero database footprint, full-project visibility with live data, AI Assist fully working, writes blocked with a friendly popup. One env flag turns it off. Delete the `demo` package + `demo.js` and it's gone.

</td>
</tr>
</table>

---

## Ã°Å¸â€œÂ¦ Feature map

| Area | Highlights |
|---|---|
| Ã°Å¸â€˜Â¨Ã¢â‚¬ÂÃ°Å¸Å’Â¾ Farmer | Cloudinary photo supply listings (auto-resize + JPEG optimise) Ã‚Â· publish/archive/stock Ã‚Â· offer counter & accept Ã‚Â· live GPS delivery tracking Ã‚Â· escrow protection Ã‚Â· sales dashboard Ã‚Â· district weather |
| Ã°Å¸â€ºâ€™ Buyer | Live marketplace (search, filters, sort, pagination) Ã‚Â· AI Suggest strip Ã‚Â· offers + bKash/escrow payments Ã‚Â· invoices Ã‚Â· order tracking Ã‚Â· spending analytics |
| Ã°Å¸â€ºÂ¡Ã¯Â¸Â Admin | Operations dashboard Ã‚Â· Control Center Ã‚Â· platform Analytics Ã‚Â· user management (roles, activate/deactivate) Ã‚Â· dispute resolution Ã‚Â· reported-chat moderation Ã‚Â· system health |
| Ã°Å¸â€Â Security | JWT + BCrypt-12 Ã‚Â· per-IP + global rate limits (429) Ã‚Â· 256 KB JSON cap (413) Ã‚Â· **spam accounts auto-suspend** (admin re-activates) Ã‚Â· 6-digit email reset codes Ã‚Â· registration flood cap | â€¢ 30-min idle auto-logout
| Ã°Å¸Å’Â¦Ã¯Â¸Â Weather | Open-Meteo district forecasts Ã‚Â· "Weather today" follows your registered location (Dhaka default) Ã‚Â· 7-day outlook with farming advisories, all bilingual | • WeatherAPI.com fallback when Open-Meteo rate-limits |
| Ã°Å¸â€™Â¬ More | Realtime WebSocket chat Ã‚Â· bKash tokenised checkout Ã‚Â· PDF-friendly invoices Ã‚Â· CSV export everywhere Ã‚Â· dark mode |

---

## Ã°Å¸Â§Âª Demo mode Ã¢â‚¬â€ one-click login

No signup needed. On the login card click **Super Admin / Admin / Buyer / Farmer** (all use password `Demo1234!`):

- Browse the **full live project** Ã¢â‚¬â€ real listings, orders, messages, dashboards
- Demo farmer/buyer overviews showcase a real account's live numbers
- Anything you try to change answers **"Demo mode is read-only"**
- Set `AGROLINK_DEMO_ENABLED=false` to switch it off on a public deploy

---

## Ã°Å¸â€ºÂ Ã¯Â¸Â Tech stack

| Layer | Choice |
|---|---|
| Language / Framework | Java 25 Ã‚Â· Spring Boot 3.4 Ã‚Â· Spring Security 6 + JWT |
| Database | MongoDB Atlas (Spring Data, Caffeine-cached aggregations) |
| Realtime | WebSocket STOMP chat Ã‚Â· live delivery tracking |
| Frontend | Thymeleaf shell + **vanilla JS** (`core/` engine + `pages/` modules + `i18n.js` EN/BN dictionary) |
| Services | Cloudinary (images) Ã‚Â· EmailJS (transactional mail) Ã‚Â· Open-Meteo (weather) Ã‚Â· Gemini (AI) |
| Deploy | Multi-stage **Docker** (Maven + JDK 25 Ã¢â€ â€™ slim JRE, `-Xmx300m`) + `render.yaml` Blueprint |

---

## Ã°Å¸â€™Â» Local setup

Prereqs: **JDK 25**, Maven 3.9+, MongoDB (local or Atlas).

```bash
mvn clean package
mvn spring-boot:run
```

First boot seeds only the super admin (`superadmin@agrolink.com` / `admin123`) and categories Ã¢â‚¬â€ **change that password immediately**. No demo data is seeded.

### Key environment variables

| Variable | Purpose |
|---|---|
| `MONGODB_URI` | Atlas connection string (**required** Ã¢â‚¬â€ local default points at localhost) |
| `JWT_SECRET` | 64+ random chars (**required** Ã¢â‚¬â€ the app refuses to start without it) |
| `EMAILJS_SERVICE_ID/_TEMPLATE_ID/_PUBLIC_KEY/_PRIVATE_KEY` | Forgot-password emails (console mode without) |
| `CLOUDINARY_CLOUD_NAME/_API_KEY/_API_SECRET` | Image uploads (MongoDB fallback without) |
| `GEMINI_API_KEY` | AI assistant (disabled without) |
| `AGROLINK_DEMO_ENABLED` | `true` = public demo logins |
| `AGROLINK_RATE_LIMIT_*` / `AGROLINK_AUTO_SUSPEND_*` | Abuse tuning |

> Copy `.env.example` locally. **No real secrets are committed** Ã¢â‚¬â€ `application.yml`
> ships only blank placeholders and safe local fallbacks. If a secret ever
> leaks into git, **rotate it** (Atlas password, Cloudinary secret, JWT secret)
> because history never forgets.

---

## Ã°Å¸Å¡â‚¬ Deployment (Render, free tier)

1. Push to GitHub Ã¢â€ â€™ Render Ã¢â€ â€™ **New Ã¢â€ â€™ Blueprint** (uses `render.yaml`)
2. Fill prompted secrets: `MONGODB_URI`, `JWT_SECRET`, EmailJS keys
3. Atlas Ã¢â€ â€™ Network Access Ã¢â€ â€™ allow `0.0.0.0/0`
4. Deploy Ã¢Å“â€¦ Ã¢â‚¬â€ free boxes sleep after 15 min idle, so first load is slow (normal)

Java 25 needs Docker (Render native Java stops at 21) Ã¢â‚¬â€ already handled. The app reads `$PORT` automatically and serves frontend + API from one origin (no CORS work).

---

## Ã¢Å“â€¦ Testing

```bash
mvn test   # 114 tests green: services, dashboards, security filters, demo mode, suggestions, i18n
```

---

## Ã°Å¸â€”ÂºÃ¯Â¸Â Roadmap

Ã¢Â­Â Buyer **ratings & reviews** (real stars for AI Suggest) Ã‚Â· Ã°Å¸â€Â one-click **reorder** Ã‚Â· Ã°Å¸â€œÂ± Bangla-first **PWA/offline** Ã‚Â· Ã°Å¸â€œÂ² SMS alerts (SSL Wireless) Ã‚Â· Ã°Å¸â€œÅ  farmer **demand forecasts** Ã‚Â· Ã°Å¸â€™Â³ partial/advance payments Ã‚Â· Ã°Å¸Å¡Å¡ delivery-partner module

---

<div align="center">

**Developed by [Md Fazley Rabbi](https://www.linkedin.com/in/fazleyrabbi1067/)** Ã‚Â· [GitHub](https://github.com/rabbi1067) Ã‚Â· [X](https://x.com/FazleRabbi56251) Ã‚Â· [Facebook](https://www.facebook.com/fazleyrabbi1067/)

Ã‚Â© 2026 AgroLink Ã‚Â· Farm to market, directly & transparently Ã°Å¸Å’Â¾

</div>
