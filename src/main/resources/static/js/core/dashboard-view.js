/* ------------------------------------------------------------------
 * DashboardView - renders the DTOs served by /dashboard. Shared by the
 * Overview, Operations and Control Center pages so a card, a chart and
 * an activity row look identical everywhere.
 *
 * The backend sends finished strings for cards and raw numbers for chart
 * points, so this file only formats chart values and never invents data.
 * ------------------------------------------------------------------ */
window.DashboardView = (function () {
    "use strict";

    const TAKA = "৳";
    const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

    /** Backend icon names -> emoji. Unknown names fall back to a neutral dot. */
    const ICONS = {
        money: "💰", wallet: "👛", shield: "🛡️", check: "✅", box: "📦",
        flag: "🚩", seedling: "🌾", users: "👥", cart: "🛒", inbox: "📥",
        truck: "🚚", chat: "💬", clock: "⏱️", chart: "📈", box_open: "📭",
    };

    const TONES = {
        info: "info", success: "success", warning: "warning",
        // "danger" is the backend's tone name; the design system calls that colour error.
        danger: "error", neutral: "info",
    };

    const STATUS_TONES = {
        PENDING: "info", PAYMENT_PENDING: "warning", OFFER_ACCEPTED: "info",
        PAID_CONFIRMED: "success", ESCROW_HELD: "info", PROCESSING: "info",
        CONFIRMED: "info", IN_TRANSIT: "info", DELIVERED: "success",
        CANCELLED: "danger", DISPUTED: "warning", REFUNDED: "info",
        FAILED: "danger", EXPIRED: "neutral", REJECTED: "danger",
        ACCEPTED: "success", OFFERED: "info", WITHDRAWN: "neutral",
    };

    const SERIES = ["#10B981", "#3B82F6", "#F59E0B", "#8B5CF6", "#EC4899", "#14B8A6", "#F97316", "#64748B"];

    /* ------------------------------------------------------------ helpers */

    const esc = (value) =>
        String(value === null || value === undefined ? "" : value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#039;");

    const icon = (name) => ICONS[name] || "•";

    const toneClass = (tone) => "badge-" + (TONES[tone] || "info");

    const statusBadge = (status) =>
        status
            ? `<span class="badge ${toneClass(STATUS_TONES[status] || "info")}">${esc(String(status).replace(/_/g, " "))}</span>`
            : "";

    /** "2026-09" -> "Sep 2026"; anything else is passed through untouched. */
    const monthLabel = (value) => {
        const match = /^(\d{4})-(\d{2})$/.exec(String(value || ""));
        if (!match) return String(value === null || value === undefined ? "" : value);
        return `${MONTHS[Number(match[2]) - 1]} ${match[1]}`;
    };

    const labelsFor = (spec) => (spec.labels || []).map(monthLabel);

    /**
     * Instants arrive as ISO strings because application.yml sets
     * write-dates-as-timestamps: false, but a number is tolerated so the
     * page still renders if that setting is ever reverted.
     */
    const parseInstant = (value) => {
        if (value === null || value === undefined || value === "") return null;
        if (typeof value === "number") return new Date(value > 1e11 ? value : value * 1000);
        const parsed = new Date(value);
        return Number.isNaN(parsed.getTime()) ? null : parsed;
    };

    const when = (value) => {
        const date = parseInstant(value);
        if (!date) return "";
        const seconds = Math.round((Date.now() - date.getTime()) / 1000);
        const future = seconds < 0;
        const abs = Math.abs(seconds);
        let text;
        if (abs < 60) text = "just now";
        else if (abs < 3600) text = `${Math.round(abs / 60)}m`;
        else if (abs < 86400) text = `${Math.round(abs / 3600)}h`;
        else if (abs < 604800) text = `${Math.round(abs / 86400)}d`;
        else text = date.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
        if (text === "just now") return text;
        return future ? `in ${text}` : `${text} ago`;
    };

    const number = (value) =>
        Number(value || 0).toLocaleString("en-US", { maximumFractionDigits: 2 });

    /** Chart tooltip/tick formatting, driven by the chart's own currency/unit flags. */
    const valueLabel = (value, spec) => {
        const amount = Number(value || 0);
        if (spec && spec.currency) {
            const abs = Math.abs(amount);
            if (abs >= 1e7) return `${TAKA} ${(amount / 1e7).toFixed(1)}Cr`;
            if (abs >= 1e5) return `${TAKA} ${(amount / 1e5).toFixed(1)}L`;
            if (abs >= 1e3) return `${TAKA} ${(amount / 1e3).toFixed(1)}K`;
            return `${TAKA} ${amount.toFixed(0)}`;
        }
        const base = Number.isInteger(amount) ? amount.toLocaleString("en-US") : number(amount);
        return spec && spec.unitLabel ? `${base} ${esc(spec.unitLabel)}` : base;
    };

    /* ------------------------------------------------------------ palette */

    const cssVar = (name, fallback) => {
        const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return value || fallback;
    };

    const palette = () => ({
        text: cssVar("--color-text", "#0f172a"),
        muted: cssVar("--color-text-muted", "#64748b"),
        grid: cssVar("--color-border", "#e2e8f0"),
        surface: cssVar("--color-surface", "#ffffff"),
        brand: cssVar("--color-brand-600", "#059669"),
    });

    const alpha = (hex, opacity) => {
        const clean = String(hex || "").replace("#", "");
        const full = clean.length === 3 ? clean.replace(/./g, (c) => c + c) : clean;
        if (full.length !== 6) return hex;
        const r = parseInt(full.slice(0, 2), 16);
        const g = parseInt(full.slice(2, 4), 16);
        const b = parseInt(full.slice(4, 6), 16);
        if ([r, g, b].some(Number.isNaN)) return hex;
        return `rgba(${r}, ${g}, ${b}, ${opacity})`;
    };

    /* ------------------------------------------------------------ charts */

    let chartPromise = null;
    let pluginsRegistered = false;
    const registry = [];

    const destroyChart = (entry) => {
        if (entry && entry.chart) {
            try {
                entry.chart.destroy();
            } catch (ignored) {
                /* a canvas removed from the DOM can already be disposed */
            }
        }
    };

    const destroyAll = () => {
        while (registry.length) destroyChart(registry.pop());
    };

    const destroyIn = (root) => {
        for (let i = registry.length - 1; i >= 0; i -= 1) {
            if (registry[i].root === root) {
                destroyChart(registry[i]);
                registry.splice(i, 1);
            }
        }
    };

    const loadChartJs = () => {
        if (window.Chart) return Promise.resolve(window.Chart);
        if (chartPromise) return chartPromise;

        chartPromise = new Promise((resolve, reject) => {
            const script = document.createElement("script");
            script.src = "https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js";
            script.async = true;
            script.onload = () => resolve(window.Chart);
            script.onerror = () => {
                chartPromise = null;
                reject(new Error("Chart.js failed to load"));
            };
            document.head.appendChild(script);
        });
        return chartPromise;
    };

    /** A chart with no non-zero point gets an explanation instead of a flat, misleading line. */
    const isEmpty = (spec) => {
        if (!spec || !spec.labels || !spec.labels.length) return true;
        const datasets = spec.datasets || [];
        if (!datasets.length) return true;
        return datasets.every((ds) =>
            !ds.data || !ds.data.length || ds.data.every((v) => v === null || Number(v) === 0));
    };

    /**
     * Chart.js knows "bar" with indexAxis "y" for horizontal bars; "hbar" is the
     * backend's shorthand, never a real Chart.js type (passing it through used to
     * throw and the ranked charts rendered as "could not be drawn").
     */
    const chartType = (spec) =>
        spec.type === "area" ? "line" : spec.type === "hbar" ? "bar" : spec.type;

    const isHorizontal = (spec) => spec.type === "hbar";
    const isPie = (spec) => chartType(spec) === "doughnut" || chartType(spec) === "pie";
    const isLine = (spec) => chartType(spec) === "line";

    /**
     * Diagonal stripes for the bars that are not the peak. A solid bar for the month the
     * server marked and stripes for the rest reads faster than a legend or a tooltip, and
     * it makes the highlight honest: only one bar is ever emphasised.
     */
    const stripeCache = new Map();

    const stripePattern = (colour) => {
        if (!colour) return null;
        if (stripeCache.has(colour)) return stripeCache.get(colour);
        let pattern = null;
        try {
            const tile = document.createElement("canvas");
            tile.width = 8;
            tile.height = 8;
            const ctx = tile.getContext("2d");
            ctx.fillStyle = alpha(colour, 0.3);
            ctx.fillRect(0, 0, 8, 8);
            ctx.strokeStyle = alpha(colour, 0.85);
            ctx.lineWidth = 3;
            ctx.beginPath();
            ctx.moveTo(-2, 10);
            ctx.lineTo(10, -2);
            ctx.moveTo(2, 14);
            ctx.lineTo(14, 2);
            ctx.stroke();
            const probe = document.createElement("canvas").getContext("2d");
            pattern = probe.createPattern(tile, "repeat");
        } catch (ignored) {
            pattern = null;
        }
        stripeCache.set(colour, pattern);
        return pattern;
    };

    /**
     * Raw highlightIndex straight from the DTO. Number(null) is 0, so a naive
     * Number() cast would emphasise the first bar of every chart that has no
     * highlight - this guard keeps "no highlight" meaning no highlight.
     */
    const peakIndexOf = (spec, length) => {
        const hi = spec.highlightIndex;
        return Number.isInteger(hi) && hi >= 0 && hi < length ? hi : -1;
    };

    const buildDataset = (ds, index, spec, colors, totalDatasets) => {
        const colour = ds.color || SERIES[index % SERIES.length];
        const data = (ds.data || []).map((v) => (v === null || v === undefined ? null : Number(v)));

        const dataset = {
            label: ds.label || "",
            data,
        };

        if (ds.axis) dataset.yAxisID = ds.axis;
        if (ds.stack) dataset.stack = ds.stack;

        if (isPie(spec)) {
            dataset.backgroundColor = SERIES.slice(0, Math.max(data.length, 1));
            dataset.hoverOffset = 6;
            dataset.borderColor = colors.surface;
            dataset.borderWidth = 3;
            return dataset;
        }

        if (isLine(spec)) {
            dataset.borderColor = colour;
            dataset.backgroundColor = alpha(colour, 0.16);
            dataset.fill = !!ds.fill;
            dataset.tension = 0.4;
            dataset.pointRadius = data.length > 12 ? 2 : 3.5;
            dataset.pointHoverRadius = 5;
            dataset.pointBackgroundColor = colour;
            dataset.pointBorderColor = colors.surface;
            dataset.pointBorderWidth = 2;
            dataset.borderWidth = 2.5;
            return dataset;
        }

        const solid = alpha(colors.brand, 0.9);
        const peak = peakIndexOf(spec, data.length);

        if (peak >= 0 && !isHorizontal(spec)) {
            const stripes = stripePattern(colors.brand);
            dataset.backgroundColor = data.map((_, i) =>
                i === peak ? solid : (stripes || alpha(colors.brand, 0.3)));
            dataset.borderColor = data.map((_, i) => (i === peak ? colors.brand : "transparent"));
            dataset.borderWidth = data.map((_, i) => (i === peak ? 2 : 0));
            dataset.borderSkipped = "bottom";
            dataset.borderRadius = { topLeft: 8, topRight: 8 };
        } else if (totalDatasets > 1) {
            // Multi-series bars (GMV vs escrow) each take their own palette colour.
            dataset.backgroundColor = alpha(colour, 0.85);
            dataset.borderColor = colour;
            dataset.borderWidth = 1.5;
            dataset.borderSkipped = isHorizontal(spec) ? "start" : "bottom";
            dataset.borderRadius = 6;
        } else {
            dataset.backgroundColor = ds.color ? alpha(colour, 0.85) : solid;
            dataset.borderColor = ds.color ? colour : "transparent";
            dataset.borderWidth = ds.color ? 1.5 : 0;
            dataset.borderSkipped = isHorizontal(spec) ? "start" : "bottom";
            dataset.borderRadius = 6;
        }
        dataset.maxBarThickness = 44;
        dataset.categoryPercentage = 0.62;
        dataset.barPercentage = 0.9;
        return dataset;
    };

    const legendLabels = (colors) => ({
        color: colors.text,
        boxWidth: 12,
        boxHeight: 12,
        padding: 14,
        usePointStyle: true,
        pointStyle: "circle",
    });

    const buildOptions = (spec, colors) => {
        const horizontal = isHorizontal(spec);
        const stacked = (spec.datasets || []).some((ds) => !!ds.stack);
        const secondAxis = (spec.datasets || []).some((ds) => ds.axis && ds.axis !== "y");

        const valueAxis = (axisId) => ({
            beginAtZero: true,
            position: axisId === "y1" ? "right" : "left",
            grid: { color: colors.grid, drawOnChartArea: axisId !== "y1" },
            border: { display: false },
            ticks: {
                color: colors.muted,
                maxTicksLimit: 6,
                callback: (value) => valueLabel(value, spec),
            },
        });

        if (isPie(spec)) {
            return {
                responsive: true,
                maintainAspectRatio: false,
                cutout: "70%",
                plugins: {
                    legend: {
                        position: "bottom",
                        labels: legendLabels(colors),
                    },
                    tooltip: tooltipConfig(colors, spec),
                },
            };
        }

        const categoryAxis = {
            stacked,
            grid: { display: horizontal, color: colors.grid },
            border: { display: false },
            ticks: { color: colors.muted, autoSkip: true, maxRotation: 0 },
        };
        const valueAxisConfig = valueAxis("y");
        if (secondAxis) {
            valueAxisConfig.grid.drawOnChartArea = true;
            categoryAxis.grid.color = colors.grid;
        }

        const scales = secondAxis
            ? { x: categoryAxis, y: valueAxisConfig, y1: valueAxis("y1") }
            : { x: categoryAxis, y: valueAxisConfig };

        if (horizontal) {
            // For a horizontal bar the value axis is x and the category axis is y.
            scales.x = valueAxisConfig;
            scales.y = { ...categoryAxis, grid: { display: false } };
        }

        const options = {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: "index", intersect: false },
            plugins: {
                legend: {
                    display: (spec.datasets || []).length > 1,
                    position: "bottom",
                    labels: legendLabels(colors),
                },
                tooltip: tooltipConfig(colors, spec),
            },
            scales,
        };

        if (isHorizontal(spec)) options.indexAxis = "y";

        const peak = peakIndexOf(spec, (spec.datasets && spec.datasets[0] && spec.datasets[0].data || []).length);
        if (peak >= 0 && !horizontal) {
            const raw = spec.datasets[0].data[peak];
            options.plugins.agrolinkPeak = {
                index: peak,
                text: valueLabel(raw, spec),
            };
        }
        return options;
    };

    const tooltipConfig = (colors, spec) => ({
        backgroundColor: colors.surface,
        titleColor: colors.text,
        bodyColor: colors.text,
        borderColor: colors.grid,
        borderWidth: 1,
        padding: 10,
        callbacks: {
            label: (ctx) => {
                const parsed = ctx.parsed;
                const amount = parsed !== null && typeof parsed === "object"
                    ? (parsed.y !== null && parsed.y !== undefined ? parsed.y : parsed.x)
                    : parsed;
                return ` ${ctx.dataset.label || ""}: ${valueLabel(amount, spec)}`;
            },
            afterBody: (items) => {
                if (!isPie(spec) || !items.length) return "";
                const total = items.reduce((sum, item) => sum + Number(item.parsed || 0), 0);
                if (!total) return "";
                const share = (Number(items[0].parsed || 0) / total) * 100;
                return ` ${share.toFixed(1)}% of total`;
            },
        },
    });

    /**
     * Draws the peak month's value above its bar, like the demo's "$22,430" pill.
     * Reads its config from options.plugins.agrolinkPeak, set by buildOptions.
     */
    const peakLabelPlugin = {
        id: "agrolinkPeak",
        afterDatasetsDraw(chart) {
            const cfg = chart.config.options
                && chart.config.options.plugins
                && chart.config.options.plugins.agrolinkPeak;
            if (!cfg) return;
            const meta = chart.getDatasetMeta(0);
            const bar = meta && meta.data && meta.data[cfg.index];
            if (!bar) return;
            const colors = palette();
            const ctx = chart.ctx;
            ctx.save();
            ctx.font = "700 12px system-ui, -apple-system, sans-serif";
            ctx.fillStyle = colors.text;
            ctx.textAlign = "center";
            ctx.textBaseline = "bottom";
            ctx.fillText(String(cfg.text || ""), bar.x, bar.y - 6);
            ctx.restore();
        },
    };

    /** Big total in the middle of every doughnut, with a small caption under it. */
    const centerTextPlugin = {
        id: "agrolinkCenter",
        afterDraw(chart) {
            if (chart.config.type !== "doughnut") return;
            const ds = chart.data.datasets && chart.data.datasets[0];
            if (!ds || !ds.data || !ds.data.length) return;
            const total = ds.data.reduce((sum, v) => sum + Number(v || 0), 0);
            if (!total) return;
            const meta = chart.getDatasetMeta(0);
            const arc = meta && meta.data && meta.data[0];
            if (!arc) return;
            const colors = palette();
            const ctx = chart.ctx;
            const big = valueLabel(total, { currency: !!chart.$isCurrency });
            ctx.save();
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillStyle = colors.text;
            ctx.font = "800 20px system-ui, -apple-system, sans-serif";
            ctx.fillText(big, arc.x, arc.y - 8);
            ctx.fillStyle = colors.muted;
            ctx.font = "500 11px system-ui, -apple-system, sans-serif";
            ctx.fillText(chart.$centerSub || "total", arc.x, arc.y + 12);
            ctx.restore();
        },
    };

    /**
     * Charts arrive in a fixed order (trend first, breakdowns after) but the backend never
     * says how wide to draw them. A bar/line series reads best wide, next to the narrower
     * breakdowns (doughnut, ranked bars) it explains - the same "hero chart plus side
     * rail" layout as any professional analytics dashboard. This is presentation only:
     * every number still comes straight from the DTO the server computed from the database.
     *
     * Grid is 12 columns: wide charts take 8, side charts take 4. A row that ends
     * with one empty 4-column slot gives the slot to its last chart instead of
     * leaving a gap on the right: a lone wide chart goes full width, and the
     * second of two side charts stretches beside its twin.
     */
    const layoutSpans = (list) => {
        const spans = list.map((spec) => (["line", "bar", "area"].includes(spec.type) ? 8 : 4));
        // Fill a trailing 4-column gap with the row's last chart so the right
        // rail never sits empty ([8] -> [12], [4,4] -> [4,8]).
        const fillRow = (rowStart, end) => {
            let sum = 0;
            for (let k = rowStart; k < end; k++) sum += spans[k];
            if (sum === 8) spans[end - 1] += 4;
        };
        let cursor = 0;
        let rowStart = 0;
        spans.forEach((span, i) => {
            if (cursor + span > 12) {
                fillRow(rowStart, i);
                cursor = 0;
                rowStart = i;
            }
            cursor += span;
            if (i === spans.length - 1) fillRow(rowStart, spans.length);
        });
        return spans;
    };

    const spanClass = (span) =>
        span >= 12 ? "chart-card-full" : span > 4 ? "chart-card-wide" : "chart-card-side";

    const chartCard = (spec, span, height) => {
        const subtitle = spec.subtitle
            ? `<p class="chart-subtitle">${esc(spec.subtitle)}</p>`
            : "";
        return `
            <div class="card chart-card ${spanClass(span)}">
                <div class="card-header">
                    <div>
                        <h2 class="card-title">${esc(spec.title || "")}</h2>
                        ${subtitle}
                    </div>
                </div>
                <div class="chart-body" data-chart-body="${esc(spec.key || "")}"
                     style="min-height:${height || Number(spec.height) || 240}px"></div>
            </div>`;
    };

    const emptyChart = (spec, span) => `
        <div class="card chart-card ${spanClass(span)}">
            <div class="card-header">
                <div>
                    <h2 class="card-title">${esc(spec.title || "")}</h2>
                    ${spec.subtitle ? `<p class="chart-subtitle">${esc(spec.subtitle)}</p>` : ""}
                </div>
            </div>
            <div class="chart-body chart-body-empty" data-chart-body="${esc(spec.key || "")}">
                <div class="empty-state">
                    <p class="mb-1">${window.t ? t("dv.noDataPeriod") : "No data for this period yet."}</p>
                    <p class="mb-0">${esc(spec.subtitle || "Records will appear here as soon as there is activity.")}</p>
                </div>
            </div>
        </div>`;

    /**
     * Renders every chart in a grid. Charts without data are replaced by an
     * empty state so the page never implies activity that did not happen.
     *
     * <p>{@code opts.compact} shrinks heights for data-dense pages like
     * Analytics, where ten charts share one scroll.</p>
     */
    const renderCharts = async (root, specs, opts) => {
        if (!root) return;
        destroyIn(root);
        const list = specs || [];
        if (!list.length) return;

        const compact = !!(opts && opts.compact);
        const heightFor = (spec) => compact
            ? Math.max(150, Math.round((Number(spec.height) || 240) * 0.72))
            : (Number(spec.height) || 240);

        root.className = "grid grid-cols-1 dashboard-chart-grid" + (compact ? " chart-grid-compact" : "");
        // Charts with no data are left out entirely so the page stays compact -
        // the cards above already show the zero honestly.
        const live = list.filter((spec) => !isEmpty(spec));
        if (!live.length) {
            root.innerHTML = "";
            return;
        }
        const spans = layoutSpans(live);
        root.innerHTML = live.map((spec, i) => chartCard(spec, spans[i], heightFor(spec))).join("");

        let Chart;
        try {
            Chart = await loadChartJs();
        } catch (error) {
            root.querySelectorAll(".chart-body").forEach((body) => {
                body.classList.add("chart-body-empty");
                body.innerHTML = `<div class="empty-state">${window.t ? t("dv.chartsDown") : "Charts are unavailable right now."}</div>`;
            });
            return;
        }

        if (!pluginsRegistered) {
            try {
                Chart.register(peakLabelPlugin, centerTextPlugin);
            } catch (ignored) {
                /* plugins are decoration; the chart itself still draws */
            }
            pluginsRegistered = true;
        }

        const colors = palette();
        live.forEach((spec, index) => {
            const body = root.querySelector(`[data-chart-body="${CSS.escape(spec.key || String(index))}"]`);
            if (!body) return;
            const canvas = document.createElement("canvas");
            canvas.setAttribute("role", "img");
            canvas.setAttribute("aria-label", spec.title || "chart");
            body.appendChild(canvas);
            try {
                const chart = new Chart(canvas.getContext("2d"), {
                    type: chartType(spec),
                    data: {
                        labels: labelsFor(spec),
                        datasets: (spec.datasets || []).map((ds, i) =>
                            buildDataset(ds, i, spec, colors, (spec.datasets || []).length)),
                    },
                    options: buildOptions(spec, colors),
                });
                chart.$isCurrency = !!spec.currency;
                chart.$centerSub = spec.currency ? "total value" : "total";
                registry.push({ root, chart, spec });
            } catch (error) {
                body.classList.add("chart-body-empty");
                body.innerHTML = `<div class="empty-state">${window.t ? t("dv.chartBroken") : "This chart could not be drawn."}</div>`;
            }
        });
    };

    /* ------------------------------------------------------------ cards */

    /**
     * The server only sends a percentage when it had a real previous period to divide by,
     * so an absent trend is rendered as nothing at all rather than as "0%".
     */
    const trendMarkup = (card) => {
        const pct = card.trendPercent;
        if (pct === null || pct === undefined || Number.isNaN(Number(pct))) return "";
        const value = Number(pct);
        const flat = Math.abs(value) < 0.05;
        const direction = flat ? "flat" : value > 0 ? "up" : "down";
        const arrow = flat ? "→" : value > 0 ? "↑" : "↓";
        const magnitude = Math.abs(value).toFixed(1);
        return `<span class="pro-pill trend-${direction}"><span aria-hidden="true">${arrow}</span> ${esc(magnitude)}%</span>`;
    };

    /**
     * Demo-style stat card: small label, big value, then the trend pill next to
     * "vs previous N days". Nothing here is computed client-side - value, pill
     * and caption all arrive from the backend's live-database aggregation.
     */
    const renderCards = (root, cards, opts) => {
        if (!root) return;
        const list = cards || [];
        const compact = !!(opts && opts.compact);
        if (!list.length) {
            root.className = "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 dashboard-card-grid";
            root.innerHTML = `<div class="empty-state">${window.t ? t("dv.noMetrics") : "No metrics yet."}</div>`;
            return;
        }
        let columns;
        if (compact) {
            // Analytics: nine compact cards in two rows of five and four.
            columns = "md:grid-cols-3 xl:grid-cols-5 analytics-card-grid";
        } else {
            columns = list.length <= 4 ? "lg:grid-cols-4"
                : list.length === 5 ? "md:grid-cols-3 xl:grid-cols-5"
                    : "md:grid-cols-3 lg:grid-cols-3";
        }
        root.className = `grid grid-cols-2 sm:grid-cols-2 ${columns} dashboard-card-grid`;
        root.innerHTML = list
            .map((card) => {
                const hasTrend = card.trendPercent !== null
                    && card.trendPercent !== undefined
                    && !Number.isNaN(Number(card.trendPercent));
                return `
                <article class="stat-card pro-card${compact ? " pro-compact" : ""} tone-${esc(card.tone || "neutral")}">
                    <div class="pro-card-head">
                        <span class="pro-icon" aria-hidden="true">${icon(card.icon)}</span>
                        ${card.badge ? `<span class="badge ${toneClass(card.tone)}">${esc(card.badge)}</span>` : ""}
                    </div>
                    <p class="pro-label">${esc(card.label)}</p>
                    <p class="pro-value">${esc(card.value)}</p>
                    <div class="pro-sub">
                        ${trendMarkup(card)}
                        ${card.trendCaption && hasTrend ? `<span class="pro-caption">${esc(card.trendCaption)}</span>` : ""}
                        ${!hasTrend && card.hint ? `<span class="pro-caption">${esc(card.hint)}</span>` : ""}
                    </div>
                </article>`;
            })
            .join("");
    };

    /* ------------------------------------------------------------ attention */

    const renderAttention = (root, items) => {
        if (!root) return;
        const list = items || [];
        root.hidden = list.length === 0;
        if (!list.length) {
            root.innerHTML = "";
            return;
        }
        root.innerHTML = list
            .map((item) => `
                <div class="attention-item tone-${esc(item.tone || "info")}">
                    <span class="attention-count">${Number(item.count || 0).toLocaleString("en-US")}</span>
                    <div class="attention-text">
                        <p class="attention-label">${esc(item.label)}</p>
                    </div>
                    ${item.action && item.route
                ? `<button type="button" class="btn btn-ghost btn-sm" data-attention-route="${esc(item.route)}">${esc(item.action)}</button>`
                : ""}
                </div>`)
            .join("");

        root.querySelectorAll("[data-attention-route]").forEach((button) => {
            button.addEventListener("click", () => {
                if (window.Router) Router.go(button.dataset.attentionRoute);
            });
        });
    };

    /* ------------------------------------------------------------ activity */

    const renderActivity = (root, items) => {
        if (!root) return;
        const list = items || [];
        if (!list.length) {
            root.innerHTML = `<div class="empty-state">${window.t ? t("dv.noActivity") : "Nothing has happened yet. New orders will show up here."}</div>`;
            return;
        }
        root.innerHTML = list
            .map((item) => `
                <div class="activity-row">
                    <span class="icon-box" aria-hidden="true">${icon(item.kind === "order" ? "box" : item.kind)}</span>
                    <div class="activity-main">
                        <p class="activity-title">${esc(item.title || "Order")}</p>
                        <p class="activity-meta">
                            ${item.actor ? `<span>${esc(item.actor)}</span>` : ""}
                            ${item.subtitle ? `<span>${esc(item.subtitle)}</span>` : ""}
                            ${item.at ? `<span title="${esc(String(item.at))}">${esc(when(item.at))}</span>` : ""}
                        </p>
                    </div>
                    <div class="activity-tail">
                        ${item.amount ? `<span class="activity-amount">${esc(item.amount)}</span>` : ""}
                        ${statusBadge(item.status)}
                    </div>
                </div>`)
            .join("");
    };

    /* ------------------------------------------------------------ tables */

    const tableCard = (spec) => {
        const columns = spec.columns || [];
        const align = (column) => (column.align === "right" ? ' class="align-right"'
            : column.align === "center" ? ' class="align-center"' : "");

        const search = spec.searchable
            ? `<div class="table-toolbar">
                   <label class="sr-only" for="search-${esc(spec.key)}">Filter ${esc(spec.title || "rows")}</label>
                   <input type="search" id="search-${esc(spec.key)}" class="input input-sm"
                          data-table-search="${esc(spec.key)}"
                          placeholder="${esc(spec.searchHint || "Filter rows")}">
                   <span class="table-count" data-table-count="${esc(spec.key)}"></span>
               </div>`
            : `<div class="table-toolbar">
                   <span class="table-count" data-table-count="${esc(spec.key)}"></span>
               </div>`;

        const body = (spec.rows || []).length
            ? `<div class="table-scroll">
                   <table class="table" data-table="${esc(spec.key)}">
                       <thead>
                           <tr>${columns.map((c) => `<th scope="col"${align(c)}>${esc(c.label)}</th>`).join("")}</tr>
                       </thead>
                        <tbody>${spec.rows.map((row) => rowMarkup(row, columns)).join("")}</tbody>
                   </table>
               </div>`
            : `<div class="empty-state">${esc(spec.emptyMessage || (window.t ? t("dv.emptyTable") : "Nothing to show yet."))}</div>`;

        const limit = spec.rowLimitNote
            ? `<p class="table-note">${esc(spec.rowLimitNote)}</p>`
            : "";

        const more = spec.viewMoreRoute
            ? `<button type="button" class="pro-viewmore" data-viewmore-route="${esc(spec.viewMoreRoute)}">${esc(spec.viewMoreLabel || (window.t ? t("dv.viewMore") : "View more"))} <span aria-hidden="true">→</span></button>`
            : "";

        return `
            <article class="card table-card pro-table" data-table-card="${esc(spec.key)}" id="${esc(spec.key)}">
                <div class="card-header">
                    <div>
                        <h2 class="card-title">${esc(spec.title || "")}</h2>
                        ${spec.subtitle ? `<p class="chart-subtitle">${esc(spec.subtitle)}</p>` : ""}
                    </div>
                    ${search}
                </div>
                ${body}
                ${limit}
                ${more}
            </article>`;
    };

    const rowMarkup = (row, columns) => {
        const cells = (row.cells || []).map((cell, i) => {
            const column = columns[i] || {};
            const className = column.align === "right" ? ' class="align-right"'
                : column.align === "center" ? ' class="align-center"' : "";
            // Status-like cells are rendered as badges so a table row reads as quickly
            // as a card; everything else stays the server's own formatting.
            const value = looksLikeStatus(cell)
                ? statusBadge(cell)
                : esc(cell);
            return `<td${className}>${value}</td>`;
        }).join("");
        if (row.route) {
            return `<tr data-row-route="${esc(row.route)}" tabindex="0" role="link"
                        aria-label="Open ${esc((row.cells && row.cells[0]) || "row")}">${cells}</tr>`;
        }
        return `<tr>${cells}</tr>`;
    };

    const STATUS_CELLS = new Set([
        "ACTIVE", "ARCHIVED", "DRAFT", "SUSPENDED", "UP", "DOWN",
        "CANCELLED", "DISPUTED", "REFUNDED", "FAILED", "EXPIRED", "REJECTED",
        "ACCEPTED", "OFFERED", "WITHDRAWN", "PAID_CONFIRMED", "PAYMENT_PENDING",
        "OFFER_ACCEPTED", "ESCROW_HELD", "PROCESSING", "CONFIRMED", "IN_TRANSIT",
        "DELIVERED", "NONE", "RELEASED", "ESCROW HELD",
    ]);

    const looksLikeStatus = (cell) => typeof cell === "string" && STATUS_CELLS.has(cell.trim().toUpperCase());

    const bindTable = (root, spec) => {
        const card = root.querySelector(`[data-table-card="${CSS.escape(spec.key || "")}"]`);
        if (!card) return;
        const count = card.querySelector(`[data-table-count="${CSS.escape(spec.key || "")}"]`);
        const body = card.querySelector("tbody");
        const rows = Array.from(card.querySelectorAll("tbody tr"));
        if (count) {
            count.textContent = rows.length
                ? `${rows.length} ${rows.length === 1 ? "row" : "rows"}`
                : "";
        }
        if (!rows.length) return;

        const more = card.querySelector("[data-viewmore-route]");
        if (more) {
            more.addEventListener("click", () => {
                if (window.Router) Router.go(more.dataset.viewmoreRoute);
            });
        }

        rows.forEach((row) => {
            const open = () => {
                const route = row.dataset.rowRoute;
                if (route && window.Router) Router.go(route);
            };
            row.addEventListener("click", open);
            row.addEventListener("keydown", (event) => {
                if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    open();
                }
            });
        });

        const search = card.querySelector(`[data-table-search="${CSS.escape(spec.key || "")}"]`);
        if (search && body) {
            search.addEventListener("input", () => {
                const needle = search.value.trim().toLowerCase();
                let visible = 0;
                rows.forEach((row) => {
                    const match = !needle || row.textContent.toLowerCase().includes(needle);
                    row.hidden = !match;
                    if (match) visible += 1;
                });
                if (count) {
                    count.textContent = visible
                        ? `${visible} of ${rows.length} ${rows.length === 1 ? "row" : "rows"}`
                        : `No match for "${search.value.trim()}"`;
                }
            });
        }
    };

    const renderTables = (root, tables) => {
        if (!root) return;
        const list = (tables || []).filter(Boolean);
        root.className = "grid grid-cols-1 dashboard-table-grid";
        if (!list.length) {
            root.innerHTML = "";
            return;
        }
        root.innerHTML = list.map(tableCard).join("");
        list.forEach((spec) => bindTable(root, spec));
    };

    /* ------------------------------------------------------------ health */

    const healthItem = (label, value, tone) => `
        <div class="health-item">
            <p class="health-label">${esc(label)}</p>
            <p class="health-value${tone ? ` tone-${esc(tone)}` : ""}">${esc(value)}</p>
        </div>`;

    /** The backend already reports heap in megabytes, so this only adds one decimal. */
    const megabytes = (value) => `${number(Number(value || 0).toFixed(1))} MB`;

    const renderHealth = (root, health) => {
        if (!root) return;
        if (!health) {
            root.hidden = true;
            root.innerHTML = "";
            return;
        }
        root.hidden = false;
        const up = String(health.databaseStatus).toUpperCase() === "UP";
        const items = [
            healthItem("Database", up ? "Connected" : "Unreachable", up ? "success" : "danger"),
            healthItem("Database latency", `${number(Math.round(Number(health.databaseLatencyMs || 0)))} ms`),
            healthItem("This query", `${number(Math.round(Number(health.queryLatencyMs || 0)))} ms`),
            healthItem("Uptime", uptime(Number(health.uptimeSeconds || 0))),
            healthItem("JVM heap", `${megabytes(health.heapUsedMb)} / ${megabytes(health.heapMaxMb)}`),
            healthItem("Active accounts", number(health.activeUsers || 0)),
        ];
        if (!up && health.error) {
            items.push(healthItem("Last error", String(health.error), "danger"));
        }
        root.innerHTML = `
            <div class="card-header">
                <div>
                    <h2 class="card-title">System health</h2>
                    <p class="chart-subtitle">Measured live, not read from a stored snapshot.</p>
                </div>
            </div>
            <div class="health-grid">${items.join("")}</div>`;
    };

    const uptime = (seconds) => {
        const days = Math.floor(seconds / 86400);
        const hours = Math.floor((seconds % 86400) / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        if (days) return `${days}d ${hours}h`;
        if (hours) return `${hours}h ${minutes}m`;
        return `${minutes}m`;
    };

    /* ------------------------------------------------------------ csv */

    const csvCell = (value) => {
        const text = String(value === null || value === undefined ? "" : value);
        return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
    };

    /** Builds one CSV out of a table spec, or out of every chart series when none is given. */
    const buildCsv = (tables, charts) => {
        const lines = [];
        (tables || []).filter(Boolean).forEach((table) => {
            const columns = table.columns || [];
            if (!columns.length) return;
            lines.push(csvCell(table.title || table.key || "Table"));
            lines.push(columns.map((c) => csvCell(c.label)).join(","));
            (table.rows || []).forEach((row) => {
                lines.push((row.cells || []).map(csvCell).join(","));
            });
            lines.push("");
        });
        if (!lines.length) {
            (charts || []).filter(Boolean).forEach((chart) => {
                const datasets = chart.datasets || [];
                if (!datasets.length) return;
                lines.push(csvCell(chart.title || chart.key || "Series"));
                lines.push(["Period", ...datasets.map((d) => d.label || "Value")].map(csvCell).join(","));
                const rows = (chart.labels || []).map((label, i) => [label,
                    ...datasets.map((d) => (d.data && d.data[i] !== null && d.data[i] !== undefined ? d.data[i] : ""))]);
                rows.forEach((row) => lines.push(row.map(csvCell).join(",")));
                lines.push("");
            });
        }
        return lines.join("\r\n");
    };

    const downloadCsv = (filename, csv) => {
        const blob = new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8;" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 0);
    };

    /* ------------------------------------------------------------ theme */

    let themeBound = false;

    const bindTheme = () => {
        if (themeBound) return;
        themeBound = true;
        document.addEventListener("themechange", () => {
            // Re-render from the stored specs instead of patching Chart.js internals,
            // which is where chart theming usually breaks.
            const colors = palette();
            [...registry].reverse().forEach((entry) => {
                if (!entry.root || !entry.root.isConnected) {
                    destroyChart(entry);
                    const at = registry.indexOf(entry);
                    if (at >= 0) registry.splice(at, 1);
                    return;
                }
                try {
                    entry.chart.data.datasets.forEach((ds, i) => {
                        const rebuilt = buildDataset(
                            (entry.spec.datasets || [])[i] || { data: [] }, i, entry.spec, colors,
                            (entry.spec.datasets || []).length);
                        Object.assign(ds, rebuilt);
                    });
                    entry.chart.options = buildOptions(entry.spec, colors);
                    entry.chart.update();
                } catch (error) {
                    /* leave the chart as-is rather than blanking the page */
                }
            });
        });
    };

    bindTheme();

    return {
        TAKA, SERIES,
        esc, icon, when, number, valueLabel, monthLabel,
        loadChartJs, renderCharts, renderCards, renderAttention, renderActivity,
        renderTables, renderHealth, buildCsv, downloadCsv,
        isEmpty, destroyIn, destroyAll,
    };
})();
