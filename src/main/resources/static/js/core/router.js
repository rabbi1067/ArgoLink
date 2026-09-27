const Router = (() => {
    const ROUTES = {
        overview: { label: "Overview", icon: "📊" },
        operations: { label: "Operations", icon: "🧭" },
        control: { label: "Control Center", icon: "🛡️" },
        produce: { label: "Produce Supply", icon: "🌾" },
        orders: { label: "Purchase Orders", icon: "📦" },
        weather: { label: "Weather", icon: "🌦️" },
        invoices: { label: "Invoices", icon: "🧾" },
        messages: { label: "Messages", icon: "💬" },
        users: { label: "User Management", icon: "👥" },
        analytics: { label: "Analytics", icon: "📈" },
        settings: { label: "Settings", icon: "⚙️" },
    };

    /**
     * Each role gets exactly one dashboard. This is deliberate: the backend
     * builds the numbers from the stored role, so letting a super admin open
     * "Overview" would show platform-wide figures inside a page written for
     * a single farmer - the same numbers, the wrong story.
     */
    const ROLE_ROUTES = {
        SUPER_ADMIN: ["control", "produce", "orders", "weather", "invoices", "messages", "users", "analytics", "settings"],
        ADMIN: ["operations", "produce", "orders", "weather", "invoices", "messages", "users", "analytics", "settings"],
        FARMER: ["overview", "produce", "orders", "weather", "invoices", "messages", "settings"],
        BUYER: ["overview", "produce", "orders", "weather", "invoices", "messages", "settings"],
    };

    const DEFAULT_ROUTE = "overview";

    const ROLE_HOME = {
        SUPER_ADMIN: "control",
        ADMIN: "operations",
        FARMER: "overview",
        BUYER: "overview",
    };

    /** Landing page after login, and fallback for any URL the role may not open. */
    const defaultRoute = () => ROLE_HOME[role()] || DEFAULT_ROUTE;

    const labelFor = (route) => {
        if (!ROUTES[route]) return "";
        if (window.I18n) return I18n.t("route." + route);
        return ROUTES[route].label;
    };
    const iconFor = (route) => (ROUTES[route] ? ROUTES[route].icon : "");

    const loadedControllers = new Set();
    const pendingControllers = new Map();
    const segmentCache = new Map();

    let container = null;
    let navSequence = 0;

    const role = () => {
        const user = window.Api && Api.getUser ? Api.getUser() : null;
        return user && user.role ? user.role : "BUYER";
    };

    const allowedRoutes = () => ROLE_ROUTES[role()] || ROLE_ROUTES.BUYER;

    const parseRoute = () => {
        const raw = (window.location.hash || "").replace(/^#\/?/, "").split("/")[0];
        return ROUTES[raw] ? raw : defaultRoute();
    };

    const go = (route) => {
        if (!ROUTES[route]) route = defaultRoute();
        if (window.location.hash === `#${route}`) {
            navigate(route);
        } else {
            window.location.hash = route;
        }
    };

    const highlight = (route) => {
        document.querySelectorAll("[data-route]").forEach((link) => {
            link.classList.toggle("active", link.dataset.route === route);
        });
    };

    const fetchSegment = async (route) => {
        if (segmentCache.has(route)) {
            return segmentCache.get(route);
        }

        const token = window.Api && Api.getToken ? Api.getToken() : null;
        const response = await fetch(`/templates/pages/${route}`, {
            headers: token ? { Authorization: `Bearer ${token}` } : {},
        });

        if (response.status === 401) {
            segmentCache.clear();
            if (window.Api && Api.logout) Api.logout();
            window.location.replace("/");
            throw new Error("Session expired");
        }

        if (!response.ok) {
            throw new Error(`Could not load page (${response.status})`);
        }

        const html = await response.text();
        segmentCache.set(route, html);
        return html;
    };

    const loadController = (route) => {
        if (loadedControllers.has(route)) return Promise.resolve();

        if (pendingControllers.has(route)) return pendingControllers.get(route);

        const promise = new Promise((resolve, reject) => {
            const script = document.createElement("script");
            script.src = `/js/pages/${route}.js`;
            script.async = true;
            script.onload = () => resolve();
            script.onerror = () => reject(new Error(`Could not load page script (${route})`));
            document.head.appendChild(script);
        }).then(() => {
            loadedControllers.add(route);
            pendingControllers.delete(route);
        });

        pendingControllers.set(route, promise);
        return promise;
    };

    const runController = (route) => {
        if (
            window.Pages &&
            window.Pages[route] &&
            typeof window.Pages[route].init === "function"
        ) {
            window.Pages[route].init(container);
        }
    };

    const loadingState = () => {
        container.innerHTML = '<div class="empty-state">Loading...</div>';
    };

    const errorState = (message) => {
        container.innerHTML = `<div class="empty-state">${message}</div>`;
    };

    const navigate = async (route) => {
        if (!container) container = document.getElementById("page-content");
        if (!container) return;

        if (!ROUTES[route]) route = defaultRoute();

        if (!allowedRoutes().includes(route)) {
            if (window.Toast) Toast.error("You do not have access to that page");
            history.replaceState(null, "", `#${defaultRoute()}`);
            route = defaultRoute();
        }

        const seq = ++navSequence;
        loadingState();
        highlight(route);
        document.title = `${labelFor(route)} · AgroLink`;

        try {
            const html = await fetchSegment(route);
            if (seq !== navSequence) return;
            container.innerHTML = html;
        } catch (error) {
            if (seq !== navSequence) return;
            errorState(error && error.message ? error.message : "Something went wrong");
            return;
        }

        try {
            await loadController(route);
        } catch (error) {
            if (seq !== navSequence) return;
            if (window.Toast) Toast.error(error.message);
        }

        if (seq !== navSequence) return;
        runController(route);
        document.dispatchEvent(new CustomEvent("agrolink:navigate", { detail: { route } }));
    };

    const start = () => {
        container = document.getElementById("page-content");

        window.addEventListener("hashchange", () => navigate(parseRoute()));

        document.addEventListener("click", (event) => {
            const link = event.target.closest("[data-route]");
            if (!link) return;
            event.preventDefault();
            go(link.dataset.route);
        });

        navigate(parseRoute());
    };

    return {
        ROUTES,
        ROLE_ROUTES,
        DEFAULT_ROUTE,
        defaultRoute,
        labelFor,
        iconFor,
        allowedRoutes,
        parseRoute,
        highlight,
        go,
        navigate,
        start,
    };
})();

window.Router = Router;