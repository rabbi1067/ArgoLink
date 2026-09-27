/* ------------------------------------------------------------------
 * DashboardPage - the shared controller behind the three dashboards:
 * Overview (farmer/buyer), Operations (admin) and Control Center
 * (super admin).
 *
 * All three render the same payload shape from GET /dashboard?from&to
 * and the same renderer, so a card, a chart or a table looks identical
 * everywhere. What differs is the DOM id prefix, the nav name and which
 * sections are meaningful - the backend still decides the view from the
 * stored role, never from a query parameter, so visiting the wrong
 * dashboard URL cannot hand someone the wrong numbers.
 * ------------------------------------------------------------------ */
window.DashboardPage = (function () {
    "use strict";

    const isoDay = (offsetDays) => {
        const day = new Date();
        day.setDate(day.getDate() - offsetDays);
        return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
    };

    const skeletonCards = (count) =>
        new Array(count || 4)
            .fill('<div class="stat-card"><div class="skeleton skeleton-card"></div></div>')
            .join("");

    const create = (config) => {
        const prefix = config.prefix;
        const q = (selector) => document.querySelector(`${selector}`.replace("%PREFIX%", prefix));

        const state = {
            prefix,
            role: (window.Api && Api.getUser && Api.getUser() ? Api.getUser().role : "BUYER") || "BUYER",
            summary: null,
            weather: null,
            _onDistrict: null,
        };

        const isPlatform = () => state.role === "ADMIN" || state.role === "SUPER_ADMIN";

        const rangeQuery = () => {
            const from = q("[data-%PREFIX%-from]");
            const to = q("[data-%PREFIX%-to]");
            if (!from || !to || !from.value || !to.value) return "";
            return `?from=${encodeURIComponent(from.value)}&to=${encodeURIComponent(to.value)}`;
        };

        const rangeSlug = () => {
            const from = q("[data-%PREFIX%-from]");
            const to = q("[data-%PREFIX%-to]");
            return `agrolink-${prefix}-${(from && from.value) || "start"}-to-${(to && to.value) || "today"}`;
        };

        const bindRange = () => {
            const from = q("[data-%PREFIX%-from]");
            const to = q("[data-%PREFIX%-to]");
            if (!from || !to) return;

            to.value = isoDay(0);
            from.value = isoDay(29);
            if (config.defaultDays && q(`[data-range="${config.defaultDays}"]`)) {
                q(`[data-range="${config.defaultDays}"]`).classList.add("is-active");
            }

            from.addEventListener("change", load);
            to.addEventListener("change", load);

            document.querySelectorAll(`[data-range][data-page="${prefix}"]`).forEach((button) => {
                button.addEventListener("click", () => {
                    const days = Number(button.dataset.range) || 30;
                    from.value = isoDay(days - 1);
                    to.value = isoDay(0);
                    document.querySelectorAll(`[data-range][data-page="${prefix}"]`)
                        .forEach((other) => other.classList.toggle("is-active", other === button));
                    load();
                });
            });

            const csv = q("[data-%PREFIX%-csv]");
            if (csv) {
                csv.addEventListener("click", () => {
                    if (!state.summary) return;
                    const content = DashboardView.buildCsv(state.summary.tables, state.summary.charts);
                    if (!content) {
                        if (window.Toast) Toast.error("There is nothing to export yet");
                        return;
                    }
                    DashboardView.downloadCsv(`${rangeSlug()}.csv`, content);
                });
            }
        };

        const bindRefresh = () => {
            const button = q("[data-%PREFIX%-refresh]");
            if (button) {
                button.addEventListener("click", () => {
                    load();
                    if (state.weather) state.weather.load(true);
                });
            }
        };

        const setGreeting = () => {
            const user = window.Api && Api.getUser ? Api.getUser() : null;
            const name = user && user.name ? String(user.name).trim() : "";
            const greeting = q("#%PREFIX%-greeting");
            const dateEl = q("#%PREFIX%-date");
            if (greeting) {
                greeting.textContent = name ? `Welcome back, ${name}.` : "Welcome back";
            }
            if (dateEl) {
                dateEl.textContent = new Date().toLocaleDateString("en-IN", {
                    weekday: "long", day: "numeric", month: "long", year: "numeric",
                });
            }
        };

        const renderScope = (dto) => {
            const badge = q("#%PREFIX%-scope");
            if (!badge) return;
            const platform = dto.scope === "platform";
            badge.hidden = !platform;
            if (platform) {
                badge.textContent = "Whole platform";
                badge.title = "These totals cover every farmer, buyer and order, not just your own.";
            }
        };

        const renderHealth = (dto) => {
            const host = q("#%PREFIX%-health");
            const heading = q("#%PREFIX%-health-heading");
            if (heading) heading.hidden = !dto.health;
            DashboardView.renderHealth(host, dto.health);
        };

        const load = async () => {
            const stats = q("#%PREFIX%-stats");
            const charts = q("#%PREFIX%-charts");
            const tables = q("#%PREFIX%-tables");
            const activity = q("#%PREFIX%-activity");
            const attention = q("#%PREFIX%-attention");
            const tablesHeading = q("#%PREFIX%-tables-heading");
            const health = q("#%PREFIX%-health");
            const rangeLabel = q("#%PREFIX%-range-label");

            if (stats) stats.innerHTML = skeletonCards(config.cardCount);
            if (charts) charts.innerHTML = "";
            if (tables) tables.innerHTML = "";
            if (tablesHeading) tablesHeading.hidden = true;
            if (health) { health.hidden = true; health.innerHTML = ""; }
            if (attention) attention.hidden = true;

            try {
                const res = await Api.get(`/dashboard${rangeQuery()}`);
                const dto = res.data || {};
                state.summary = dto;

                if (rangeLabel) rangeLabel.textContent = dto.rangeLabel || "";
                document.title = dto.title ? dto.title + " - AgroLink" : "AgroLink - Dashboard";
                renderScope(dto);
                renderHealth(dto);

                DashboardView.renderCards(stats, dto.cards);
                DashboardView.renderAttention(attention, dto.attention);
                DashboardView.renderActivity(activity, dto.activity);

                // Pages with a lead table (Operations) lift one table above the
                // charts so the action item reads first; the rest stay below.
                const allTables = dto.tables || [];
                const lead = config.leadTableKey
                    ? allTables.find((t) => t && t.key === config.leadTableKey)
                    : null;
                const rest = lead ? allTables.filter((t) => t !== lead) : allTables;
                const leadHost = q("#%PREFIX%-lead-table");
                const leadHeading = q("#%PREFIX%-lead-heading");
                if (leadHost) {
                    DashboardView.renderTables(leadHost, lead ? [lead] : []);
                    if (leadHeading) leadHeading.hidden = !lead;
                }
                DashboardView.renderTables(tables, rest);
                if (tablesHeading) tablesHeading.hidden = !rest.length;

                // No blank "Trends" section when every chart is empty.
                const trendsHeading = q("#%PREFIX%-trends-heading");
                if (trendsHeading) {
                    trendsHeading.hidden = !(dto.charts || [])
                        .some((s) => !DashboardView.isEmpty(s));
                }

                const count = q("#%PREFIX%-activity-count");
                if (count) {
                    const n = (dto.activity || []).length;
                    count.textContent = n ? `Showing the ${n} most recent.` : "";
                }

                await DashboardView.renderCharts(charts, dto.charts);
            } catch (error) {
                if (stats) stats.innerHTML = '<div class="empty-state">Could not load this dashboard.</div>';
                if (activity) activity.innerHTML = '<div class="empty-state">Could not load activity.</div>';
                if (window.Toast) Toast.fromResponse(error, `Could not load the ${config.name.toLowerCase()}`);
            }
        };

        state.init = (container) => {
            state.container = container;
            state.role = (window.Api && Api.getUser && Api.getUser() ? Api.getUser().role : "BUYER") || "BUYER";

            bindRefresh();
            bindRange();
            setGreeting();
            load();

            const host = q("#%PREFIX%-weather");
            if (host && window.WeatherWidget) {
                state.weather = WeatherWidget.mount(host, prefix, isPlatform());
            }
        };

        return state;
    };

    return { create, isoDay, skeletonCards };
})();
