const Api = (() => {
    const TOKEN_KEY = "agrolink_token";
    const USER_KEY = "agrolink_user";
    const BASE_URL = "/api/v1";

    const getToken = () => localStorage.getItem(TOKEN_KEY);
    const setToken = (token) => localStorage.setItem(TOKEN_KEY, token);
    const clearToken = () => localStorage.removeItem(TOKEN_KEY);

    const getUser = () => {
        const raw = localStorage.getItem(USER_KEY);
        if (!raw) return null;
        try {
            return JSON.parse(raw);
        } catch {
            localStorage.removeItem(USER_KEY);
            return null;
        }
    };
    const setUser = (user) => localStorage.setItem(USER_KEY, JSON.stringify(user));
    const clearUser = () => localStorage.removeItem(USER_KEY);

    const logout = () => {
        clearToken();
        clearUser();
    };

    const handleUnauthorized = () => {
        logout();
        const path = window.location.pathname;
        if (path !== "/" && path !== "/login") {
            window.location.href = "/";
        }
    };

    const buildUrl = (path, params) => {
        const fullPath = `${BASE_URL}${path}`;
        if (!params || Object.keys(params).length === 0) return fullPath;
        const query = new URLSearchParams(
            Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== "")
        ).toString();
        return query ? `${fullPath}?${query}` : fullPath;
    };

    const request = async (path, { method = "GET", body = null, params = null, headers = {}, publicCall = false } = {}) => {
        const config = {
            method,
            headers: { ...headers },
        };

        // Public landing-page calls never carry a token: a stale/expired token
        // would turn into a 401 and bounce the visitor with a "Session expired"
        // redirect. Authenticated pages keep sending it as before.
        const token = publicCall ? null : getToken();
        if (token) {
            config.headers["Authorization"] = `Bearer ${token}`;
        }

        if (body !== undefined && body !== null) {
            if (body instanceof FormData) {
                config.body = body;
            } else {
                config.headers["Content-Type"] = "application/json";
                config.body = JSON.stringify(body);
            }
        }

        let response;
        try {
            response = await fetch(buildUrl(path, params), config);
        } catch {
            throw { success: false, message: "Network error", data: null };
        }

        if (response.status === 401) {
            const contentType = response.headers.get("content-type") || "";
            const payload = contentType.includes("application/json") ? await response.json() : null;
            const isAuthCall = path === "/auth/login" || path === "/auth/register";
            if (!isAuthCall && !publicCall) {
                handleUnauthorized();
            }
            throw {
                success: false,
                message: payload && payload.message ? payload.message : (isAuthCall ? "Invalid email or password" : "Session expired"),
                data: payload || null,
            };
        }

        const contentType = response.headers.get("content-type") || "";
        const payload = contentType.includes("application/json")
            ? await response.json()
            : { success: response.ok, data: await response.text() };

        if (!response.ok) {
            const message = payload && payload.message ? payload.message : `Request failed (${response.status})`;
            throw { success: false, message, data: payload };
        }

        return payload;
    };

    const get = (path, params) => request(path, { method: "GET", params });
    const getPublic = (path, params) => request(path, { method: "GET", params, publicCall: true });
    const post = (path, body) => request(path, { method: "POST", body });
    const put = (path, body, params) => request(path, { method: "PUT", body, params });
    const patch = (path, body) => request(path, { method: "PATCH", body });
    const del = (path) => request(path, { method: "DELETE" });
    const upload = (path, file, fieldName = "file") => {
        const formData = new FormData();
        formData.append(fieldName, file);
        return request(path, { method: "POST", body: formData });
    };

    const login = async (credentials) => {
        const payload = await post("/auth/login", credentials);
        if (payload && payload.data && payload.data.token) {
            setToken(payload.data.token);
            setUser(payload.data);
        }
        return payload;
    };

    const register = async (details) => {
        const payload = await post("/auth/register", details);
        if (payload && payload.data && payload.data.token) {
            setToken(payload.data.token);
            setUser(payload.data);
        }
        return payload;
    };

    return {
        get,
        getPublic,
        post,
        put,
        patch,
        del,
        upload,
        login,
        register,
        logout,
        getToken,
        setToken,
        clearToken,
        getUser,
        setUser,
        clearUser,
        isAuthenticated: () => Boolean(getToken()),
        BASE_URL,
    };
})();

window.Api = Api;