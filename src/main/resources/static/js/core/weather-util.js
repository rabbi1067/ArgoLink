/* ------------------------------------------------------------------
 * WeatherUtil - shared helpers for anything that shows weather.
 *
 * overview.js, operations.js and control.js all render the same widget,
 * so the district list, the WMO icon mapping and the agricultural
 * advisories live here instead of being copied per page.
 * ------------------------------------------------------------------ */
window.WeatherUtil = (function () {
    "use strict";

    if (window.WeatherUtil && window.WeatherUtil.__shared) return window.WeatherUtil;

    const DAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
    const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    const EVT = "agrolink:weather-district";
    const DEFAULT_DISTRICT = "Dhaka";
    let districtsPromise = null;

    const esc = (v) =>
        String(v === null || v === undefined ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#039;");

    const fmt = (v, digits = 1) =>
        v === null || v === undefined || Number.isNaN(Number(v)) ? "--" : Number(v).toFixed(digits);

    function toDate(iso) {
        const [y, m, d] = String(iso || "").slice(0, 10).split("-").map(Number);
        const dt = new Date(y, (m || 1) - 1, d || 1);
        return Number.isNaN(dt.getTime()) ? null : dt;
    }

    /** "Wed, 23 Sep" */
    function shortLabel(iso) {
        const dt = toDate(iso);
        return dt ? `${DAYS[dt.getDay()].slice(0, 3)}, ${dt.getDate()} ${MONTHS[dt.getMonth()]}` : "—";
    }

    /** "Wednesday, 23 Sep" */
    function longLabel(iso) {
        const dt = toDate(iso);
        return dt ? `${DAYS[dt.getDay()]}, ${dt.getDate()} ${MONTHS[dt.getMonth()]}` : "—";
    }

    function todayIso() {
        const n = new Date();
        return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, "0")}-${String(n.getDate()).padStart(2, "0")}`;
    }

    function updatedLabel(iso) {
        const dt = iso ? new Date(iso) : null;
        if (!dt || Number.isNaN(dt.getTime())) return "—";
        return `${dt.getDate()} ${MONTHS[dt.getMonth()]}, ${String(dt.getHours()).padStart(2, "0")}:${String(dt.getMinutes()).padStart(2, "0")}`;
    }

    /** Open-Meteo WMO weather code -> emoji icon. */
    function icon(code, isDay) {
        if (code === null || code === undefined) return "🌡️";
        const c = Number(code);
        if (c === 0) return isDay === false ? "🌙" : "☀️";
        if (c === 1) return isDay === false ? "🌙" : "🌤️";
        if (c === 2) return "⛅";
        if (c === 3) return "☁️";
        if (c === 45 || c === 48) return "🌫️";
        if (c >= 51 && c <= 57) return "🌦️";
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return "🌧️";
        if ((c >= 71 && c <= 77) || c === 85 || c === 86) return "❄️";
        if (c >= 95) return "⛈️";
        return "🌡️";
    }

    /** Smart agricultural badge for one forecast day (priority order). */
    function advisory(day) {
        const rain = day.rainSumMm, wind = day.windMaxKmh, tmax = day.tempMaxC, code = day.weatherCode;
        if (rain === null && tmax === null) return null;
        if ((code !== null && code >= 95) || (wind !== null && wind >= 40)) {
            return { label: "⚠️ Storm Alert", cls: "badge-error" };
        }
        if (rain !== null && rain > 5) return { label: "🌧️ Rain Warning", cls: "badge-warning" };
        if (tmax !== null && tmax >= 36) return { label: "🔥 Heat Stress", cls: "badge-warning" };
        if (rain !== null && rain <= 1) return { label: "🌾 Good for Harvest", cls: "badge-success" };
        return { label: "🌦️ Light Rain – Plan Ahead", cls: "badge-info" };
    }

    /** Every time the page opens it starts on Dhaka (no remembered district). */
    function getDistrict() {
        return DEFAULT_DISTRICT;
    }

    /** Broadcast so any other mounted weather view updates instantly (nothing is remembered). */
    function setDistrict(district, source) {
        window.dispatchEvent(new CustomEvent(EVT, { detail: { district, source: source || "" } }));
    }

    function getDistricts() {
        if (!districtsPromise) {
            districtsPromise = Api.get("/weather/districts")
                .then((res) => res.data || [])
                .catch(() => { districtsPromise = null; return []; });
        }
        return districtsPromise;
    }

    function fallbackDistrict(list) {
        return list.includes(DEFAULT_DISTRICT) ? DEFAULT_DISTRICT : (list[0] || DEFAULT_DISTRICT);
    }

    function matchDistrict(list, wanted) {
        const w = String(wanted || "").trim().toLowerCase();
        if (!w) return fallbackDistrict(list);
        const exact = list.find((d) => d.toLowerCase() === w);
        if (exact) return exact;
        // Free-text profile locations ("Mirpur, Dhaka") still resolve when they
        // contain a district name. Short fragments are ignored so a one-letter
        // location cannot match the first district containing that letter.
        if (w.length >= 4) {
            const contained = list.find((d) => w.includes(d.toLowerCase()) || d.toLowerCase().includes(w));
            if (contained) return contained;
        }
        return fallbackDistrict(list);
    }

    /** Resolve a possibly-unknown district name to a valid one. */
    async function resolveDistrict(wanted) {
        const list = await getDistricts();
        return list.length ? matchDistrict(list, wanted) : (wanted || DEFAULT_DISTRICT);
    }

    /** Raw location text from the signed-in profile, e.g. "Mirpur, Dhaka". */
    function userLocation() {
        const user = window.Api && Api.getUser ? Api.getUser() : null;
        return user && user.location ? String(user.location).trim() : "";
    }

    /**
     * District for the signed-in user: their registered location when it
     * resolves to a real district, Dhaka otherwise. Profile saves update the
     * cached user, so a changed location applies on the next page visit.
     */
    async function userDistrict() {
        const loc = userLocation();
        if (!loc) return DEFAULT_DISTRICT;
        return resolveDistrict(loc);
    }

    /** Fill a <select> with all districts and select the best match; returns the effective district. */
    async function fillDistricts(select, wanted) {
        const list = await getDistricts();
        select.innerHTML = "";
        if (!list.length) {
            const opt = new Option("Districts unavailable", "");
            opt.disabled = true;
            select.add(opt);
            return wanted;
        }
        list.forEach((d) => select.add(new Option(d, d)));
        const chosen = matchDistrict(list, wanted);
        select.value = chosen;
        return chosen;
    }

    const util = {
        __shared: true,
        EVT, DEFAULT_DISTRICT, esc, fmt, shortLabel, longLabel, todayIso, updatedLabel,
        icon, advisory, getDistrict, setDistrict, resolveDistrict, fillDistricts,
        userLocation, userDistrict,
    };
    return util;
})();
