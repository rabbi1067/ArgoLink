/* ------------------------------------------------------------------
 * weather.js - the 7-day forecast page.
 *
 * The shared helpers live in core/weather-util.js, which dashboard.html
 * loads before any page script.
 * ------------------------------------------------------------------ */

window.Pages = window.Pages || {};

window.Pages.weather = {
    container: null,
    role: "BUYER",
    district: "Dhaka",
    reqId: 0,
    _onDistrict: null,
    _onDocClick: null,

    $(sel) {
        return this.container ? this.container.querySelector(sel) : null;
    },

    async init(container) {
        this.container = container;
        const user = window.Api && Api.getUser ? Api.getUser() : null;
        this.role = user && user.role ? user.role : "BUYER";

        const U = window.WeatherUtil;
        const select = this.$("#weather-district-select");
        this.district = select ? await U.fillDistricts(select, await U.userDistrict()) : await U.userDistrict();

        this.bind();
        this.bindLang();
        this.loadForecast();
    },

    bind() {
        const U = window.WeatherUtil;

        const refreshBtn = this.$("#weather-refresh-btn");
        if (refreshBtn) refreshBtn.addEventListener("click", () => this.loadForecast(true));

        const select = this.$("#weather-district-select");
        if (select) {
            select.addEventListener("change", () => {
                if (!select.value) return;
                this.district = select.value;
                U.setDistrict(this.district, "weather");
                this.loadForecast();
            });
        }

        this.bindSearch();

        // Instant sync when the district is changed from the Overview dashboard (no page reload).
        if (this._onDistrict) window.removeEventListener(U.EVT, this._onDistrict);
        this._onDistrict = (e) => {
            if (!this.container || !this.container.isConnected) {
                window.removeEventListener(U.EVT, this._onDistrict);
                return;
            }
            if (!e.detail || e.detail.source === "weather" || !e.detail.district) return;
            this.district = e.detail.district;
            const sel = this.$("#weather-district-select");
            if (sel) sel.value = this.district;
            this.loadForecast();
        };
        window.addEventListener(U.EVT, this._onDistrict);
    },

    /** Live district search: type to filter, Up/Down arrows to move, Enter or click to pick, Esc to clear. */
    bindSearch() {
        const U = window.WeatherUtil;
        const input = this.$("#weather-district-search");
        const list = this.$("#weather-search-results");
        const select = this.$("#weather-district-select");
        if (!input || !list || !select) return;

        const all = Array.from(select.options).map((o) => o.value).filter(Boolean);
        const norm = (s) => String(s).toLowerCase().replace(/[^a-z0-9]/g, "");
        let matches = [];
        let active = -1;

        const close = () => {
            list.hidden = true;
            input.setAttribute("aria-expanded", "false");
            active = -1;
        };

        const paint = () => {
            list.innerHTML = matches.length
                ? matches.map((d, i) =>
                    `<li role="option" class="wx-search-item${i === active ? " is-active" : ""}" data-district="${U.esc(d)}" aria-selected="${i === active}">${U.esc(d)}</li>`
                ).join("")
                : `<li class="wx-search-empty">${window.t ? t("wx.noDistrict") : "No district found"}</li>`;
            list.hidden = false;
            input.setAttribute("aria-expanded", "true");
            const el = list.querySelector(".is-active");
            if (el) el.scrollIntoView({ block: "nearest" });
        };

        const search = () => {
            const raw = input.value.trim().toLowerCase();
            if (!raw) { close(); return; }
            const q = norm(raw);
            matches = all
                .filter((d) => d.toLowerCase().includes(raw) || (q && norm(d).includes(q)))
                .sort((a, b) => Number(norm(b).startsWith(q)) - Number(norm(a).startsWith(q)) || a.localeCompare(b));
            active = matches.length ? 0 : -1;
            paint();
        };

        const choose = (district) => {
            input.value = "";
            close();
            select.value = district;
            this.district = district;
            U.setDistrict(district, "weather");
            this.loadForecast();
        };

        input.addEventListener("input", search);
        input.addEventListener("focus", () => { if (input.value) search(); });
        input.addEventListener("keydown", (e) => {
            if (e.key === "ArrowDown" || e.key === "ArrowUp") {
                e.preventDefault();
                if (list.hidden) { search(); return; }
                if (!matches.length) return;
                active = (active + (e.key === "ArrowDown" ? 1 : -1) + matches.length) % matches.length;
                paint();
            } else if (e.key === "Enter") {
                if (active >= 0 && matches[active]) { e.preventDefault(); choose(matches[active]); }
            } else if (e.key === "Escape") {
                input.value = "";
                close();
            }
        });
        // mousedown (not click) so the input keeps focus and the pick isn't lost on blur
        list.addEventListener("mousedown", (e) => {
            const li = e.target.closest("[data-district]");
            if (!li) return;
            e.preventDefault();
            choose(li.dataset.district);
        });

        if (this._onDocClick) document.removeEventListener("click", this._onDocClick);
        this._onDocClick = (e) => {
            if (!this.container || !this.container.isConnected) {
                document.removeEventListener("click", this._onDocClick);
                return;
            }
            if (!e.target.closest(".wx-search")) close();
        };
        document.addEventListener("click", this._onDocClick);
    },

    setBusy(busy, refreshing) {
        const btn = this.$("#weather-refresh-btn");
        if (!btn) return;
        btn.disabled = busy;
        const spinner = btn.querySelector(".wx-spinner");
        const label = btn.querySelector(".wx-refresh-label");
        if (spinner) spinner.hidden = !busy;
        if (label) label.textContent = busy && refreshing
            ? (window.t && window.I18n && I18n.getLang() === "bn" ? "রিফ্রেশ হচ্ছে..." : "Refreshing...")
            : (window.t ? t("common.refresh") : "Refresh");
    },

    async loadForecast(refresh = false) {
        const U = window.WeatherUtil;
        const id = ++this.reqId;
        const currentEl = this.$("#weatherCurrent");
        const dailyEl = this.$("#weatherDaily");

        this.setBusy(true, refresh);
        if (currentEl) currentEl.innerHTML = `<div class="empty-state">${window.t ? t("wx.loading") : "Loading forecast..."}</div>`;
        if (dailyEl) dailyEl.innerHTML = "";

        try {
            const url = `/weather/forecast?district=${encodeURIComponent(this.district)}&days=7` + (refresh ? "&refresh=true" : "");
            const res = await Api.get(url);
            if (id !== this.reqId) return; // a newer request superseded this one
            const d = res.data;
            if (!d || d.fallback || !d.daily || !d.daily.length) {
                this.renderUnavailable(d);
            } else {
                this.renderCurrent(d);
                this.renderDaily(d);
            }
            this.renderMeta(d);
        } catch (e) {
            if (id !== this.reqId) return;
            this.renderUnavailable(null, e && e.message ? e.message : "");
            this.renderMeta(null);
        } finally {
            if (id === this.reqId) this.setBusy(false, refresh);
        }
    },

    renderUnavailable(d, detail) {
        const U = window.WeatherUtil;
        const currentEl = this.$("#weatherCurrent");
        const dailyEl = this.$("#weatherDaily");
        const T = (en, key) => (window.t ? t(key) : en);
        const msg = (d && d.disclaimer) || `${T("Could not load the forecast", "wx.unavailable")}${detail ? ": " + detail : "."}`;
        if (currentEl) {
            currentEl.innerHTML = `
                <div class="empty-state">
                    <p class="mb-2">${U.esc(msg)}</p>
                    <button type="button" class="btn btn-primary" data-wx-retry>${T("Try again", "wx.tryAgain")}</button>
                </div>`;
            const retry = currentEl.querySelector("[data-wx-retry]");
            if (retry) retry.addEventListener("click", () => this.loadForecast(true));
        }
        if (dailyEl) dailyEl.innerHTML = `<div class="empty-state">${T("7-day outlook unavailable right now.", "wx.noData")}</div>`;
    },

    renderCurrent(d) {
        const U = window.WeatherUtil;
        const el = this.$("#weatherCurrent");
        if (!el) return;
        const today = d.daily[0];
        const cur = d.current || {};
        const code = cur.weatherCode !== null && cur.weatherCode !== undefined ? cur.weatherCode : today.weatherCode;
        const desc = cur.description || today.description || "";
        const wind = cur.windKmh !== null && cur.windKmh !== undefined ? cur.windKmh : today.windMaxKmh;
        const place = [d.district, d.division && d.division !== d.district ? d.division : null].filter(Boolean).join(", ");
        const T = (en, key) => (window.t ? t(key) : en);

        el.innerHTML = `
            <div class="flex flex-wrap items-center justify-between gap-4">
                <div class="flex items-center gap-4">
                    <span style="font-size: 2.75rem;">${U.icon(code, cur.day)}</span>
                    <div>
                        <h2 class="section-title mb-0" style="font-size: 1.75rem;">${U.fmt(cur.temperatureC)} C</h2>
                        <p class="mb-0" style="font-weight: 600;">${U.esc(U.longLabel(today.date))}</p>
                        <p class="text-muted mb-0">${U.esc(place)} - ${U.esc(desc)} - ${T("High", "wx.high")} ${U.fmt(today.tempMaxC)} C / ${T("Low", "wx.low")} ${U.fmt(today.tempMinC)} C</p>
                    </div>
                </div>
                <div class="text-right">
                    <p class="mb-0">${T("Rain", "wx.rain")} ${U.fmt(today.rainSumMm)} mm</p>
                    <p class="mb-0">${T("Wind", "wx.wind")} ${U.fmt(wind)} km/h</p>
                    <p class="text-muted mb-0" style="font-size: 0.75rem;">${T("Updated", "wx.updated")} ${U.esc(U.updatedLabel(d.generatedAt))}${d.stale ? (window.t ? ` - ${t("wx.stale")}` : " - last saved data") : ""}</p>
                </div>
            </div>`;
    },

    renderDaily(d) {
        const U = window.WeatherUtil;
        const wrap = this.$("#weatherDaily");
        const tpl = this.$("#weatherDailyTpl");
        if (!wrap || !tpl) return;
        wrap.innerHTML = "";

        d.daily.forEach((day, i) => {
            const node = tpl.content.firstElementChild.cloneNode(true);
            const q = (s) => node.querySelector(s);

            q(".weather-day-icon").textContent = U.icon(day.weatherCode);
            q(".weather-day").textContent = (i === 0 ? (window.t ? `${t("wx.today")} - ` : "Today - ") : "") + U.shortLabel(day.date);
            q(".weather-temp").textContent = `${U.fmt(day.tempMaxC)} C / ${U.fmt(day.tempMinC)} C`;
            q(".weather-desc").textContent = day.description || "";
            const prob = day.rainProbabilityPct === null || day.rainProbabilityPct === undefined ? "--" : day.rainProbabilityPct;
            q(".weather-meta").textContent = `${U.fmt(day.rainSumMm)} mm (${prob}%) - ${U.fmt(day.windMaxKmh, 0)} km/h`;

            const adv = U.advisory(day);
            const badge = q(".weather-advice");
            if (adv) {
                badge.textContent = adv.label;
                badge.classList.add(adv.cls);
                badge.hidden = false;
            }
            wrap.appendChild(node);
        });
    },

    renderMeta(d) {
        const U = window.WeatherUtil;
        const disclaimer = this.$("#weatherDisclaimer");
        if (disclaimer) disclaimer.textContent = d && d.disclaimer ? d.disclaimer : "";

        const badge = this.$("#weatherSummaryBadge");
        if (!badge) return;
        const T = (en, key) => (window.t ? t(key) : en);
        if (!d || d.fallback || !d.daily || !d.daily.length) {
            badge.textContent = T("Unavailable", "wx.unavailableBadge");
        } else if (d.stale) {
            badge.textContent = T("Last saved data", "wx.lastSaved");
        } else {
            const total = d.daily.reduce((sum, day) => sum + (Number(day.rainSumMm) || 0), 0);
            badge.textContent = window.t && window.I18n && I18n.getLang() === "bn"
                ? `মোট বৃষ্টি ${total.toFixed(1)} মিমি`
                : `Total rain ${total.toFixed(1)} mm`;
        }
    },

    bindLang() {
        if (this._langBound) return;
        this._langBound = true;
        document.addEventListener("langchange", () => {
            if (this.container && this.container.isConnected) this.loadForecast();
        });
    },
};