/* ------------------------------------------------------------------
 * analytics.js - admin/super-admin deep dive.
 *
 * Reads GET /dashboard/analytics?months=N. The backend owns every
 * aggregation; this file only picks the window and draws the result,
 * so the page can never disagree with the overview.
 * ------------------------------------------------------------------ */
window.Pages = window.Pages || {};

window.Pages.analytics = {
    container: null,
    months: 6,

    init(container) {
        this.container = container;

        const user = window.Api && Api.getUser ? Api.getUser() : null;
        if (!user || (user.role !== "ADMIN" && user.role !== "SUPER_ADMIN")) {
            this.deny();
            return;
        }

        this.restoreWindow();
        this.bind();
        this.load();
    },

    deny() {
        this.container.innerHTML =
            '<div class="empty-state">Insights are available to administrators only.</div>';
    },

    restoreWindow() {
        // Remember the window for this browser only; the default stays 6 months.
        const stored = Number(window.localStorage ? window.localStorage.getItem("agrolink.analytics.months") : NaN);
        const select = this.container.querySelector("#analyticsMonths");
        if (select) {
            if ([3, 6, 12, 24].includes(stored)) {
                select.value = String(stored);
                this.months = stored;
            } else {
                this.months = Number(select.value) || 6;
            }
        }
    },

    bind() {
        const select = this.container.querySelector("#analyticsMonths");
        if (select) {
            select.addEventListener("change", () => {
                this.months = Number(select.value) || 6;
                if (window.localStorage) {
                    window.localStorage.setItem("agrolink.analytics.months", String(this.months));
                }
                this.load();
            });
        }
        const button = this.container.querySelector("[data-analytics-refresh]");
        if (button) {
            button.addEventListener("click", () => this.load(true));
        }
    },

    async load() {
        const stats = this.container.querySelector("#analyticsStats");
        const charts = this.container.querySelector("#analyticsCharts");
        const attention = this.container.querySelector("#analyticsAttention");
        const label = this.container.querySelector("#analyticsWindowLabel");

        if (label) {
            label.textContent = `Platform performance over the last ${this.months} months`;
        }

        stats.innerHTML = new Array(4)
            .fill('<div class="stat-card"><div class="skeleton skeleton-card"></div></div>')
            .join("");
        charts.innerHTML = "";
        if (attention) attention.hidden = true;

        try {
            const res = await Api.get("/dashboard/analytics", { months: this.months });
            const dto = res.data || {};

            DashboardView.renderCards(stats, dto.cards, { compact: true });
            DashboardView.renderAttention(attention, dto.attention);
            await DashboardView.renderCharts(charts, dto.charts, { compact: true });

            const chartsHeading = this.container.querySelector("#analytics-charts-heading");
            if (chartsHeading) {
                chartsHeading.hidden = !(dto.charts || [])
                    .some((spec) => !DashboardView.isEmpty(spec));
            }
        } catch (error) {
            stats.innerHTML = '<div class="empty-state">Could not load analytics.</div>';
            if (window.Toast) Toast.fromResponse(error, "Could not load analytics");
        }
    },
};
