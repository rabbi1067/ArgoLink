/* ------------------------------------------------------------------
 * WeatherWidget - the "weather today" card shared by all three
 * dashboards and driven entirely by the real forecast API.
 *
 * Every reading shown here came back from the provider. A field the
 * provider did not return is omitted rather than rendered as a zero,
 * because "0 mm rain" and "we do not know the rain" are different
 * statements and a farmer acts on the difference.
 * ------------------------------------------------------------------ */
window.WeatherWidget = (function () {
    "use strict";

    const U = window.WeatherUtil;

    const shell = (prefix) => `
        <div class="wx-head">
            <div class="wx-head-title">
                <p class="section-eyebrow weather-widget-title">Weather today</p>
                <p class="wx-place" id="${prefix}-weather-place"></p>
            </div>
            <div class="wx-widget-actions">
                <label class="sr-only" for="${prefix}-district-select">District</label>
                <select id="${prefix}-district-select" class="select wx-select" aria-label="District" hidden></select>
                <a href="#weather" class="btn btn-ghost btn-sm">7-day forecast →</a>
            </div>
        </div>
        <div id="${prefix}-weather-body" class="wx-body">
            <div class="empty-state">Loading weather...</div>
        </div>`;

    const render = (body, d, placeEl) => {
        const today = d.daily[0];
        const cur = d.current || {};
        const code = cur.weatherCode !== null && cur.weatherCode !== undefined ? cur.weatherCode : today.weatherCode;
        const desc = cur.description || today.description || "";
        const wind = cur.windKmh !== null && cur.windKmh !== undefined ? cur.windKmh : today.windMaxKmh;
        const place = [d.district, d.division && d.division !== d.district ? d.division : null]
            .filter(Boolean).join(", ");
        const badge = U.advisory(today);

        if (placeEl) {
            placeEl.textContent = `${place} · ${U.longLabel(today.date || U.todayIso())}`;
        }

        const num = (v) => v !== null && v !== undefined && !Number.isNaN(Number(v));
        const stats = [
            num(today.rainSumMm) ? ["Rain", `${U.fmt(today.rainSumMm)} mm`] : null,
            num(wind) ? ["Wind", `${U.fmt(wind, 0)} km/h`] : null,
            num(cur.humidity) ? ["Humidity", `${U.fmt(cur.humidity, 0)}%`] : null,
            num(cur.feelsLikeC) ? ["Feels like", `${U.fmt(cur.feelsLikeC)}°`] : null,
            num(cur.windGustKmh) ? ["Gusts", `${U.fmt(cur.windGustKmh, 0)} km/h`] : null,
            num(cur.pressureHpa) ? ["Pressure", `${U.fmt(cur.pressureHpa, 0)} hPa`] : null,
        ].filter(Boolean).slice(0, 6);

        body.innerHTML = `
            <div class="wx-hero">
                <span class="wx-hero-icon" aria-hidden="true">${U.icon(code, cur.day)}</span>
                <div>
                    <div class="wx-hero-temp">${U.fmt(cur.temperatureC)}°C</div>
                    <div class="wx-hero-desc">${U.esc(desc)}</div>
                    ${badge ? `<span class="badge ${badge.cls}">${U.esc(badge.label)}</span>` : ""}
                </div>
            </div>
            ${stats.length ? `<div class="wx-stats">${stats.map(([label, value]) => `
                <div class="wx-stat">
                    <span class="wx-stat-label">${U.esc(label)}</span>
                    <span class="wx-stat-value">${U.esc(value)}</span>
                </div>`).join("")}</div>` : ""}
            ${d.stale ? '<p class="text-muted weather-note">Live update failed — showing the last saved forecast.</p>' : ""}`;
    };

    /**
     * Mounts the widget inside {@code host}. {@code canPickDistrict} reveals the
     * district picker, which only administrators get.
     */
    const mount = (host, prefix, canPickDistrict) => {
        if (!host) return null;
        host.className = "card weather-widget";
        host.setAttribute("aria-live", "polite");
        host.innerHTML = shell(prefix);

        const state = {
            prefix,
            district: U.DEFAULT_DISTRICT,
            request: 0,
            onDistrict: null,
        };

        const body = host.querySelector(`#${prefix}-weather-body`);
        const select = host.querySelector(`#${prefix}-district-select`);
        const placeEl = host.querySelector(`#${prefix}-weather-place`);

        const load = async (refresh) => {
            if (!body || !host.isConnected) return;
            const id = ++state.request;
            body.innerHTML = '<div class="empty-state">Loading weather...</div>';
            try {
                const url = `/weather/forecast?district=${encodeURIComponent(state.district)}&days=7`
                    + (refresh ? "&refresh=true" : "");
                const res = await Api.get(url);
                if (id !== state.request) return;
                const d = res.data;
                if (!d || d.fallback || !d.daily || !d.daily.length) {
                    body.innerHTML = `
                        <div class="empty-state">
                            <p class="weather-empty-text">${U.esc((d && d.disclaimer) || "Weather is unavailable right now.")}</p>
                            <button type="button" class="btn btn-primary" data-wx-retry>Try again</button>
                        </div>`;
                    const retry = body.querySelector("[data-wx-retry]");
                    if (retry) retry.addEventListener("click", () => load(true));
                    return;
                }
                render(body, d, placeEl);
            } catch (error) {
                if (id !== state.request) return;
                body.innerHTML = `
                    <div class="empty-state">
                        <p class="weather-empty-text">Weather is unavailable right now.</p>
                        <button type="button" class="btn btn-primary" data-wx-retry>Try again</button>
                    </div>`;
                const retry = body.querySelector("[data-wx-retry]");
                if (retry) retry.addEventListener("click", () => load(true));
            }
        };

        state.load = load;

        (async () => {
            // Farmer/buyer overview weather follows the registered profile
            // location (Dhaka when none is set); admins may also pick a district.
            const home = await U.userDistrict();
            if (select && canPickDistrict) {
                select.hidden = false;
                state.district = await U.fillDistricts(select, home);
                select.addEventListener("change", () => {
                    if (!select.value) return;
                    state.district = select.value;
                    U.setDistrict(state.district, prefix);
                    load();
                });
            } else {
                if (select) select.hidden = true;
                state.district = home;
            }
            load();
        })();

        state.onDistrict = (event) => {
            if (!host.isConnected) {
                window.removeEventListener(U.EVT, state.onDistrict);
                return;
            }
            if (!event.detail || event.detail.source === prefix || !event.detail.district) return;
            state.district = event.detail.district;
            const picker = host.querySelector(`#${prefix}-district-select`);
            if (picker && !picker.hidden) picker.value = state.district;
            load();
        };
        window.addEventListener(U.EVT, state.onDistrict);

        return state;
    };

    return { mount };
})();
