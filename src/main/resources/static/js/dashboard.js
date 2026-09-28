(function () {
    "use strict";

    const $ = (selector, root) => (root || document).querySelector(selector);
    const $$ = (selector, root) => Array.from((root || document).querySelectorAll(selector));

    window.setProfileAvatar = function (name, imageUrl) {
        const avatarEl = document.getElementById("profileAvatar");
        const imgEl = document.getElementById("topbar-avatar");

        const showInitials = () => {
            if (imgEl) {
                imgEl.src = "";
                imgEl.hidden = true;
            }
            if (avatarEl) {
                avatarEl.textContent = initialsOf(name);
                avatarEl.style.backgroundImage = avatarGradient(name);
            }
        };

        if (imageUrl) {
            if (imgEl) {
                imgEl.onload = () => {
                    if (avatarEl) avatarEl.textContent = "";
                };
                imgEl.onerror = () => showInitials();
                imgEl.src = imageUrl;
                imgEl.hidden = false;
                if (avatarEl) avatarEl.textContent = "";
                return;
            }
            showInitials();
            return;
        }
        showInitials();
    };

    const initialsOf = (name) => String(name || "?")
        .trim()
        .split(/\s+/)
        .map((part) => part.charAt(0))
        .join("")
        .slice(0, 2)
        .toUpperCase() || "?";

    const avatarGradient = (name) => {
        let hash = 0;
        const key = String(name || "?");
        for (let i = 0; i < key.length; i++) {
            hash = (hash * 31 + key.charCodeAt(i)) >>> 0;
        }
        const hue = hash % 360;
        return `linear-gradient(135deg, hsl(${hue}, 55%, 48%), hsl(${(hue + 45) % 360}, 60%, 38%))`;
    };

    window.applyProfileToShell = function (profile) {
        if (!profile) return;
        const nameEl = document.getElementById("topbar-username");
        const badgeEl = document.getElementById("roleBadge");
        if (nameEl && profile.name) nameEl.textContent = profile.name;
        if (badgeEl && profile.role) badgeEl.textContent = Shell.formatRole(profile.role);
        const menuNameEl = document.getElementById("profileMenuName");
        const menuRoleEl = document.getElementById("profileMenuRole");
        if (menuNameEl && profile.name) menuNameEl.textContent = profile.name;
        if (menuRoleEl && profile.role) menuRoleEl.textContent = Shell.formatRole(profile.role);
        window.setProfileAvatar(profile.name, profile.profileImageUrl);
    };

    window.mergeStoredUser = function (profile) {
        if (!(window.Api && Api.getUser && Api.setUser) || !profile) return;
        const current = Api.getUser();
        if (!current) return;
        Api.setUser(Object.assign({}, current, {
            name: profile.name || current.name,
            phone: profile.phone !== undefined ? profile.phone : current.phone,
            location: profile.location !== undefined ? profile.location : current.location,
            profileImagePublicId: profile.profileImagePublicId !== undefined ? profile.profileImagePublicId : current.profileImagePublicId,
        }));
    };

    const Shell = {
        user: null,

        init() {
            this.user = window.Api && Api.getUser ? Api.getUser() : null;
            if (!this.user) {
                window.location.replace("/");
                return;
            }

            this.renderProfile();
            this.renderSidebar();
            this.bindTopbar();
            this.bindDrawer();
            this.bindTheme();
            this.bindIdleLogout();
            if (window.I18n) {
                I18n.init();
                document.addEventListener("langchange", () => {
                    this.renderProfile();
                    this.renderSidebar();
                });
            }

            this.refreshProfile();

            Router.start();
        },

        async refreshProfile() {
            try {
                const res = await Api.get("/users/me");
                const profile = res.data || {};
                this.user = Object.assign({}, this.user, profile);
                window.mergeStoredUser(profile);
                this.renderProfile();
            } catch (error) {
                // Shell already renders from the stored user; failure here is non-fatal.
            }
        },

        renderProfile() {
            const name = this.user.name || this.user.email || "User";
            const role = this.user.role || "BUYER";

            const nameEl = $("#topbar-username");
            const badgeEl = $("#roleBadge");
            const menuNameEl = $("#profileMenuName");
            const menuRoleEl = $("#profileMenuRole");

            if (nameEl) nameEl.textContent = name;
            if (badgeEl) badgeEl.textContent = this.formatRole(role);
            if (menuNameEl) menuNameEl.textContent = name;
            if (menuRoleEl) menuRoleEl.textContent = this.formatRole(role);
            window.setProfileAvatar(name, this.user.profileImageUrl);
        },

        formatRole(role) {
            const map = { SUPER_ADMIN: "role.superAdmin", ADMIN: "role.admin", FARMER: "role.farmer", BUYER: "role.buyer" };
            if (window.I18n && map[role]) return I18n.t(map[role]);
            return role
                .toLowerCase()
                .split("_")
                .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
                .join(" ");
        },

        initials(name) {
            const parts = name.trim().split(/\s+/);
            const first = parts[0] ? parts[0].charAt(0) : "";
            const second = parts.length > 1 ? parts[parts.length - 1].charAt(0) : "";
            return (first + second).toUpperCase() || "?";
        },

        renderSidebar() {
            const nav = $("#sidebarNav");
            if (!nav) return;

            const routes = Router.allowedRoutes();
            const menu = routes
                .map((route) => {
                    // The dashboard entry is named after the view the backend serves this
                    // role, so an admin sees "Operations" rather than a generic "Overview".
                    const icon = Router.iconFor ? Router.iconFor(route) : "";
                    const label = Router.labelFor ? Router.labelFor(route) : "";
                    if (!label) return "";
                    return `
                        <a href="#${route}" class="sidebar-link" data-route="${route}">
                            <span aria-hidden="true">${icon}</span>
                            <span>${label}</span>
                        </a>`;
                })
                .join("");

            nav.innerHTML = `<div class="sidebar-section-label">${window.t ? t("shell.menu") : "Menu"}</div>${menu}`;

            // Rebuilding wipes the active link (e.g. after a language switch),
            // so highlight the current route again right away.
            if (Router.highlight && Router.parseRoute) {
                Router.highlight(Router.parseRoute());
            }
        },

        bindTopbar() {
            const logout = $("#logoutBtn");
            if (logout) {
                logout.addEventListener("click", () => this.logout());
            }

            this.bindProfileMenu();

            const search = $("#globalSearch");
            if (search) {
                search.addEventListener("keydown", (event) => {
                    if (event.key !== "Enter") return;
                    event.preventDefault();
                    const query = search.value.trim();
                    sessionStorage.setItem("agrolink_search", query);
                    document.dispatchEvent(
                        new CustomEvent("agrolink:search", { detail: { query } })
                    );
                    if (window.location.hash !== "#produce") {
                        Router.go("produce");
                    }
                });
            }
        },

        logout() {
            window.Api.logout();
            Toast.success("Logged out");
            setTimeout(() => {
                window.location.href = "/";
            }, 300);
        },

        /**
         * Shared/public computer safety: 30 minutes without any click, key,
         * scroll or touch logs the account out (1-minute warning first).
         * Each tab tracks itself; activity is throttled so mouse wiggles
         * don't churn timers.
         */
        bindIdleLogout() {
            const TIMEOUT = 30 * 60 * 1000;
            const WARN_BEFORE = 60 * 1000;
            let warnTimer = null;
            let outTimer = null;
            let lastMove = 0;

            const clear = () => {
                if (warnTimer) clearTimeout(warnTimer);
                if (outTimer) clearTimeout(outTimer);
                warnTimer = null;
                outTimer = null;
            };

            const logout = () => {
                clear();
                window.Api.logout();
                if (window.Toast) Toast.info(window.t ? t("shell.idleOut") : "Logged out due to inactivity", 8000);
                setTimeout(() => window.location.replace("/"), 1500);
            };

            const schedule = () => {
                clear();
                warnTimer = setTimeout(() => {
                    if (window.Toast) Toast.info(window.t ? t("shell.idleWarn") : "No activity for a while - logging out in 1 minute. Click anywhere to stay signed in.", 55000);
                    outTimer = setTimeout(logout, WARN_BEFORE);
                }, Math.max(1000, TIMEOUT - WARN_BEFORE));
            };

            const bump = () => {
                if (!window.Api || !Api.isAuthenticated()) return;
                schedule();
            };

            ["click", "keydown", "scroll", "touchstart"].forEach((event) =>
                document.addEventListener(event, bump, { capture: true, passive: true }));
            document.addEventListener("mousemove", () => {
                const now = Date.now();
                if (now - lastMove > 60 * 1000) {
                    lastMove = now;
                    bump();
                }
            }, { passive: true });

            schedule();
        },

        bindProfileMenu() {
            const menu = $("#profileMenu");
            const toggle = $("#profileMenuToggle");
            const panel = $("#profileMenuPanel");
            if (!menu || !toggle || !panel) return;

            const open = () => {
                panel.hidden = false;
                toggle.setAttribute("aria-expanded", "true");
                toggle.classList.add("profile-open");
            };
            const close = () => {
                panel.hidden = true;
                toggle.setAttribute("aria-expanded", "false");
                toggle.classList.remove("profile-open");
            };

            toggle.addEventListener("click", (event) => {
                event.stopPropagation();
                if (panel.hidden) open();
                else close();
            });

            document.addEventListener("click", (event) => {
                if (!event.target.closest("#profileMenu")) close();
            });

            document.addEventListener("keydown", (event) => {
                if (event.key === "Escape") close();
            });

            const navItem = menu.querySelector("[data-profile-menu-nav]");
            if (navItem) {
                navItem.addEventListener("click", (event) => {
                    event.preventDefault();
                    close();
                    Router.go("settings");
                });
            }

            const logoutItem = menu.querySelector("[data-profile-menu-logout]");
            if (logoutItem) {
                logoutItem.addEventListener("click", (event) => {
                    event.stopPropagation();
                    this.logout();
                });
            }
        },

        bindDrawer() {
            const sidebar = $("#sidebar");
            const overlay = $("#drawerOverlay");
            const toggle = $("#drawerToggle");

            const open = () => {
                sidebar.classList.add("open");
                overlay.classList.add("open");
            };
            const close = () => {
                sidebar.classList.remove("open");
                overlay.classList.remove("open");
            };

            if (toggle) toggle.addEventListener("click", open);
            if (overlay) overlay.addEventListener("click", close);

            document.addEventListener("click", (event) => {
                if (event.target.closest("[data-route]")) close();
            });

            document.addEventListener("keydown", (event) => {
                if (event.key === "Escape") close();
            });
        },

        bindTheme() {
            $$("[data-theme-toggle]").forEach((button) => {
                button.addEventListener("click", () => {
                    window.Theme.toggle();
                    this.renderThemeIcons();
                });
            });
            document.addEventListener("themechange", () => this.renderThemeIcons());
            this.renderThemeIcons();
        },

        renderThemeIcons() {
            if (!window.Theme) return;
            const dark = Theme.isDark();
            $$("[data-theme-icon]").forEach((icon) => {
                const isMoon = icon.dataset.themeIcon === "moon";
                icon.classList.toggle("hidden", dark ? isMoon : !isMoon);
            });
        },
    };

    document.addEventListener("DOMContentLoaded", () => {
        Shell.init();
    });
})();