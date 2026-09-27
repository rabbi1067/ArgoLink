<div align="center">

# 🌾 AgroLink v2

### Farm to Market, Directly & Transparently — খামার থেকে বাজার, সরাসরি ও স্বচ্ছভাবে

**A complete B2B agro-supply-chain platform for Bangladesh** — farmers sell directly, buyers source fresh stock, admins govern everything. No middlemen.

[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MongoDB](https://img.shields.io/badge/MongoDB-Atlas-47A248?style=for-the-badge&logo=mongodb&logoColor=white)](https://www.mongodb.com/atlas)
[![JWT](https://img.shields.io/badge/JWT-Security-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)](https://jwt.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](./Dockerfile)
[![Tests](https://img.shields.io/badge/Tests-113_passing-brightgreen?style=for-the-badge)](#-testing)
[![Bangla](https://img.shields.io/badge/বাংলা-UI_Ready-red?style=for-the-badge)](#-bangla--english-toggle)

[✨ Live Demo](#-demo-mode-one-click-login) • [🚀 Deploy](#-deployment-render-free-tier) • [📖 Setup](#-local-setup)

</div>

---

## ✨ What makes AgroLink v2 special

<table>
<tr>
<td width="50%">

### 🤖 AgroLink AI Assist
Gemini-powered assistant built into the app. Ask about crops, orders, prices, or how to use the platform — with quick-question chips, chat history, and per-minute/per-day rate limits. Speaks **Bangla and English**.

</td>
<td width="50%">

### 🎯 AI Suggest (buyer marketplace)
A personalised strip on the buyer marketplace: **Best Selling · Best Value · Most Repurchased · Lowest Price**. Computed from *your* order + offer history and live platform demand — real sold quantities, real repeat-buyer rates. No history? The section hides itself. No fake ratings, ever.

</td>
</tr>
<tr>
<td>

### 🌐 Full Bangla UI toggle
One pill button next to day/night mode flips the **entire project** — landing, all 12 dashboard pages, sidebar, forms, validation messages, weather, toasts — between English and Bangla. Choice persists across visits.

</td>
<td>

### 👀 Read-only demo mode
One-click logins: **Super Admin / Admin / Buyer / Farmer** (`Demo1234!`). Zero database footprint, full-project visibility with live data, AI Assist fully working, writes blocked with a friendly popup. One env flag turns it off. Delete the `demo` package + `demo.js` and it's gone.

</td>
</tr>
</table>

---

## 📦 Feature map

| Area | Highlights |
|---|---|
| 👨‍🌾 Farmer | Cloudinary photo supply listings (auto-resize + JPEG optimise) · publish/archive/stock · offer counter & accept · live GPS delivery tracking · escrow protection · sales dashboard · district weather |
| 🛒 Buyer | Live marketplace (search, filters, sort, pagination) · AI Suggest strip · offers + bKash/escrow payments · invoices · order tracking · spending analytics |
| 🛡️ Admin | Operations dashboard · Control Center · platform Analytics · user management (roles, activate/deactivate) · dispute resolution · reported-chat moderation · system health |
| 🔐 Security | JWT + BCrypt-12 · per-IP + global rate limits (429) · 256 KB JSON cap (413) · **spam accounts auto-suspend** (admin re-activates) · 6-digit email reset codes · registration flood cap |
| 🌦️ Weather | Open-Meteo district forecasts · "Weather today" follows your registered location (Dhaka default) · 7-day outlook with farming advisories, all bilingual |
| 💬 More | Realtime WebSocket chat · bKash tokenised checkout · PDF-friendly invoices · CSV export everywhere · dark mode |

---

## 🧪 Demo mode — one-click login

No signup needed. On the login card click **Super Admin / Admin / Buyer / Farmer** (all use password `Demo1234!`):

- Browse the **full live project** — real listings, orders, messages, dashboards
- Demo farmer/buyer overviews showcase a real account's live numbers
- Anything you try to change answers **"Demo mode is read-only"**
- Set `AGROLINK_DEMO_ENABLED=false` to switch it off on a public deploy

---

## 🛠️ Tech stack

| Layer | Choice |
|---|---|
| Language / Framework | Java 25 · Spring Boot 3.4 · Spring Security 6 + JWT |
| Database | MongoDB Atlas (Spring Data, Caffeine-cached aggregations) |
| Realtime | WebSocket STOMP chat · live delivery tracking |
| Frontend | Thymeleaf shell + **vanilla JS** (`core/` engine + `pages/` modules + `i18n.js` EN/BN dictionary) |
| Services | Cloudinary (images) · EmailJS (transactional mail) · Open-Meteo (weather) · Gemini (AI) |
| Deploy | Multi-stage **Docker** (Maven + JDK 25 → slim JRE, `-Xmx300m`) + `render.yaml` Blueprint |

---

## 💻 Local setup

Prereqs: **JDK 25**, Maven 3.9+, MongoDB (local or Atlas).

```bash
mvn clean package
mvn spring-boot:run
```

First boot seeds only the super admin (`superadmin@agrolink.com` / `admin123`) and categories — **change that password immediately**. No demo data is seeded.

### Key environment variables

| Variable | Purpose |
|---|---|
| `MONGODB_URI` | Atlas connection string (**required** — local default points at localhost) |
| `JWT_SECRET` | 64+ random chars (**required** — the app refuses to start without it) |
| `EMAILJS_SERVICE_ID/_TEMPLATE_ID/_PUBLIC_KEY/_PRIVATE_KEY` | Forgot-password emails (console mode without) |
| `CLOUDINARY_CLOUD_NAME/_API_KEY/_API_SECRET` | Image uploads (MongoDB fallback without) |
| `GEMINI_API_KEY` | AI assistant (disabled without) |
| `AGROLINK_DEMO_ENABLED` | `true` = public demo logins |
| `AGROLINK_RATE_LIMIT_*` / `AGROLINK_AUTO_SUSPEND_*` | Abuse tuning |

> Copy `.env.example` locally. **No real secrets are committed** — `application.yml`
> ships only blank placeholders and safe local fallbacks. If a secret ever
> leaks into git, **rotate it** (Atlas password, Cloudinary secret, JWT secret)
> because history never forgets.

---

## 🚀 Deployment (Render, free tier)

1. Push to GitHub → Render → **New → Blueprint** (uses `render.yaml`)
2. Fill prompted secrets: `MONGODB_URI`, `JWT_SECRET`, EmailJS keys
3. Atlas → Network Access → allow `0.0.0.0/0`
4. Deploy ✅ — free boxes sleep after 15 min idle, so first load is slow (normal)

Java 25 needs Docker (Render native Java stops at 21) — already handled. The app reads `$PORT` automatically and serves frontend + API from one origin (no CORS work).

---

## ✅ Testing

```bash
mvn test   # 113 tests green: services, dashboards, security filters, demo mode, suggestions, i18n
```

---

## 🗺️ Roadmap

⭐ Buyer **ratings & reviews** (real stars for AI Suggest) · 🔁 one-click **reorder** · 📱 Bangla-first **PWA/offline** · 📲 SMS alerts (SSL Wireless) · 📊 farmer **demand forecasts** · 💳 partial/advance payments · 🚚 delivery-partner module

---

<div align="center">

**Developed by [Md Fazley Rabbi](https://www.linkedin.com/in/fazleyrabbi1067/)** · [GitHub](https://github.com/rabbi1067) · [X](https://x.com/FazleRabbi56251) · [Facebook](https://www.facebook.com/fazleyrabbi1067/)

© 2026 AgroLink · Farm to market, directly & transparently 🌾

</div>
