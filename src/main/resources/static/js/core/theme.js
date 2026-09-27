const Theme = (() => {
    const THEME_KEY = "agrolink_theme";
    const DARK = "dark";
    const LIGHT = "light";

    const storage = {
        get: () => localStorage.getItem(THEME_KEY),
        set: (value) => localStorage.setItem(THEME_KEY, value),
    };

    const systemPrefersDark = () =>
        window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;

    const getTheme = () => {
        const saved = storage.get();
        if (saved === DARK || saved === LIGHT) return saved;
        return systemPrefersDark() ? DARK : LIGHT;
    };

    const applyTheme = (theme) => {
        document.documentElement.setAttribute("data-theme", theme);
        storage.set(theme);
        document.dispatchEvent(new CustomEvent("themechange", { detail: { theme } }));
    };

    const init = () => {
        applyTheme(getTheme());
        return getTheme();
    };

    const toggle = () => {
        const next = getTheme() === DARK ? LIGHT : DARK;
        applyTheme(next);
        return next;
    };

    const isDark = () => getTheme() === DARK;

    return { init, toggle, getTheme, applyTheme, isDark };
})();

window.Theme = Theme;