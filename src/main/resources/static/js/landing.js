(function () {
    "use strict";

    const $ = (selector, root) => (root || document).querySelector(selector);
    const $$ = (selector, root) => Array.from((root || document).querySelectorAll(selector));

    const CACHE_KEY = "agrolink_crops_cache";
    const CACHE_TTL = 10 * 60 * 1000;

    const Explorer = {
        categories: [],
        listings: [],
        pageSize: 6,

        getCache() {
            try {
                const raw = localStorage.getItem(CACHE_KEY);
                if (!raw) return null;
                const parsed = JSON.parse(raw);
                if (!parsed || !parsed.ts || Date.now() - parsed.ts > CACHE_TTL) {
                    localStorage.removeItem(CACHE_KEY);
                    return null;
                }
                return parsed;
            } catch {
                return null;
            }
        },

        setCache(data) {
            try {
                localStorage.setItem(CACHE_KEY, JSON.stringify({ ts: Date.now(), ...data }));
            } catch {
                return;
            }
        },

        async init(force = false) {            const cached = force ? null : this.getCache();

            if (cached) {
                this.categories = cached.categories || [];
                this.listings = cached.listings || [];
                this.refreshCategories();
                this.applyFilter();
                return;
            }

            const grid = $("#cropGrid");
            if (grid && window.Loader) {
                Loader.render(grid, Loader.skeletonCards(6));
            }

            try {
                // Public catalog: no token is sent, so a stale login can never
                // break the marketplace with a 401 redirect.
                const [categoriesRes, listingsRes] = await Promise.all([
                    Api.getPublic("/produce/categories"),
                    Api.getPublic("/produce/listings"),
                ]);

                this.categories = categoriesRes && categoriesRes.data ? categoriesRes.data : [];
                this.listings = listingsRes && listingsRes.data ? listingsRes.data : [];

                this.setCache({ categories: this.categories, listings: this.listings });
                this.refreshCategories();
                this.applyFilter();
            } catch (error) {
                if (window.Toast) {
                    Toast.error(error && error.message ? error.message : "Could not load produce listings");
                }
                if (grid) {
                    grid.innerHTML =
                        '<div class="empty-state">No produce available right now.<br>Please try again later.</div>';
                }
            }
        },

        /**
         * Refresh means "start over": clear the search box and the category
         * filter, then reload everything fresh from the server so all
         * categories and listings show again.
         */
        async refresh() {
            const search = $("#cropSearch");
            if (search) search.value = "";
            const filter = $("#categoryFilter");
            if (filter) filter.value = "";
            await this.init(true);
        },

        refreshCategories() {
            const select = $("#categoryFilter");
            if (!select) return;
            const current = select.value;
            select.innerHTML = '<option value="">All categories</option>'
                + this.categories
                    .map((category) => `<option value="${escapeHtml(category.name)}">${escapeHtml(category.name)}</option>`)
                    .join("");
            if (current) {
                select.value = [...select.options].some((o) => o.value === current) ? current : "";
            }
        },

        applyFilter() {
            const query = $("#cropSearch") ? $("#cropSearch").value.trim().toLowerCase() : "";
            const category = $("#categoryFilter") ? $("#categoryFilter").value : "";

            const filtered = this.listings.filter((listing) => {
                const matchesCategory = !category || listing.category === category;
                const matchesQuery = !query
                    || (listing.cropName && listing.cropName.toLowerCase().includes(query));
                return matchesCategory && matchesQuery;
            });

            this.renderGrid(filtered);
        },

        renderGrid(listings) {
            const grid = $("#cropGrid");
            if (!grid) return;

            if (!listings.length) {
                grid.innerHTML = '<div class="empty-state">No produce listings match your filters.</div>';
                this.setSeeMore(0);
                return;
            }

            // The public page teases the first page only; the rest sits behind login.
            const shown = listings.slice(0, this.pageSize);
            grid.innerHTML = shown.map((listing) => this.cardMarkup(listing)).join("");
            this.setSeeMore(listings.length - shown.length);
        },

        setSeeMore(hiddenCount) {
            const wrap = $("#cropSeeMore");
            if (!wrap) return;
            if (!hiddenCount || hiddenCount <= 0) {
                wrap.innerHTML = "";
                wrap.hidden = true;
                return;
            }
            wrap.hidden = false;
            wrap.innerHTML = `
                <button type="button" class="btn btn-secondary btn-lg" data-auth-open>
                    See more (${hiddenCount} more) &mdash; Login to view all
                </button>`;
        },

        cardMarkup(listing) {
            const price = Number(listing.pricePerUnit || 0).toLocaleString("en-IN", {
                minimumFractionDigits: 0,
                maximumFractionDigits: 2,
            });
            const harvest = listing.harvestDate ? escapeHtml(listing.harvestDate) : "N/A";
            const category = escapeHtml(listing.category || "Produce");
            const location = escapeHtml(listing.location || "Location unknown");
            const cropName = escapeHtml(listing.cropName || "Unnamed produce");
            const quantity = Number(listing.availableQuantity || 0).toLocaleString("en-IN");
            const unit = escapeHtml(listing.unit || "kg");

            return `
                <div class="card crop-card">
                    <div class="flex items-start justify-between gap-2">
                        <h3 class="crop-name">${cropName}</h3>
                        <span class="badge badge-success">${category}</span>
                    </div>
                    <p class="text-muted my-0">📍 ${location}</p>
                    <div>
                        <span class="crop-price">৳${price}</span>
                        <span class="text-muted"> / ${unit}</span>
                    </div>
                    <div class="crop-meta">
                        <span class="chip">Qty ${quantity} ${unit}</span>
                        <span class="chip">Harvest ${harvest}</span>
                    </div>
                    <button type="button" class="btn btn-primary btn-sm crop-offer" data-auth-open>
                        Make an Offer
                    </button>
                </div>
            `;
        },
    };

    const Auth = {
        modal: null,

        init() {
            this.modal = $("#authModal");
            if (!this.modal) return;

            $$("[data-auth-close]").forEach((button) => {
                button.addEventListener("click", () => this.close());
            });

            $$("[data-auth-tab]").forEach((tab) => {
                tab.addEventListener("click", () => this.setTab(tab.dataset.authTab));
            });

            $$("input[name='registerRole']", $("#registerForm")).forEach((input) => {
                input.addEventListener("change", () => this.updateRoleSelection());
            });

            document.addEventListener("click", (event) => {
                const trigger = event.target.closest("[data-auth-open], [data-auth-open-register]");
                if (trigger) {
                    this.open(trigger.hasAttribute("data-auth-open-register") ? "register" : "login");
                }

                if (this.modal && event.target === this.modal) {
                    this.close();
                }
            });

            document.addEventListener("keydown", (event) => {
                if (event.key === "Escape" && this.isOpen()) this.close();
            });

            $("#loginForm").addEventListener("submit", (event) => this.handleLogin(event));
            $("#registerForm").addEventListener("submit", (event) => this.handleRegister(event));
            $("#forgotEmailForm").addEventListener("submit", (event) => this.handleForgotEmail(event));
            $("#forgotCodeForm").addEventListener("submit", (event) => this.handleForgotCode(event));
            $("#forgotPasswordForm").addEventListener("submit", (event) => this.handleForgotPassword(event));
            $$("[data-auth-forgot]", this.modal).forEach((button) => {
                button.addEventListener("click", () => this.showForgot());
            });
            $$("[data-auth-back-login]", this.modal).forEach((button) => {
                button.addEventListener("click", () => this.setTab("login"));
            });
            $$("[data-auth-resend]", this.modal).forEach((button) => {
                button.addEventListener("click", () => this.resendCode(button));
            });
            this.bindPasswordToggles();
        },

        bindPasswordToggles() {
            $$("[data-toggle-password]", this.modal).forEach((button) => {
                button.addEventListener("click", () => {
                    const target = document.getElementById(button.dataset.togglePassword);
                    if (!target) return;
                    const show = target.type === "password";
                    target.type = show ? "text" : "password";
                    button.setAttribute("aria-pressed", String(show));
                    button.setAttribute("aria-label", show ? "Hide password" : "Show password");
                    const showIcon = button.querySelector(".pw-show");
                    const hideIcon = button.querySelector(".pw-hide");
                    if (showIcon) showIcon.classList.toggle("hidden", show);
                    if (hideIcon) hideIcon.classList.toggle("hidden", !show);
                });
            });
        },

        isOpen() {
            return this.modal && this.modal.classList.contains("open");
        },

        open(tab = "login") {
            this.modal.classList.add("open");
            this.modal.setAttribute("aria-hidden", "false");
            this.setTab(tab);
            document.body.style.overflow = "hidden";
        },

        close() {
            this.modal.classList.remove("open");
            this.modal.setAttribute("aria-hidden", "true");
            document.body.style.overflow = "";
        },

        setTab(tab) {
            const target = tab === "register" ? "register" : "login";
            $$(".tab", this.modal).forEach((node) => {
                const active = node.dataset.authTab === target;
                node.classList.toggle("active", active);
                node.setAttribute("aria-selected", String(active));
            });
            $(".tabs", this.modal).hidden = false;
            $("#panel-forgot", this.modal).classList.remove("active");
            $("#panel-login", this.modal).classList.toggle("active", target === "login");
            $("#panel-register", this.modal).classList.toggle("active", target === "register");
        },

        showForgot() {
            $("#panel-login", this.modal).classList.remove("active");
            $("#panel-register", this.modal).classList.remove("active");
            $(".tabs", this.modal).hidden = true;
            $("#panel-forgot", this.modal).classList.add("active");
            this.forgotStep("email");
        },

        forgotStep(step) {
            $$("[data-forgot-step]", this.modal).forEach((node) => {
                node.hidden = node.dataset.forgotStep !== step;
            });
        },

        updateRoleSelection() {
            $$(".role-option", $("#registerForm")).forEach((label) => {
                label.classList.toggle("active", label.querySelector("input").checked);
            });
        },

        activeRole() {
            const checked = $("input[name='registerRole']:checked", $("#registerForm"));
            return checked ? checked.value : "FARMER";
        },

        async handleLogin(event) {
            event.preventDefault();
            const form = $("#loginForm");
            if (!form.checkValidity()) {
                form.reportValidity();
                return;
            }
            const payload = {
                email: $("#loginEmail").value.trim(),
                password: $("#loginPassword").value,
            };
            await this.submit(form, () => Api.post("/auth/login", payload), "Welcome back!", true);
        },

        async handleRegister(event) {
            event.preventDefault();
            const form = $("#registerForm");
            if (!form.checkValidity()) {
                form.reportValidity();
                return;
            }
            const email = $("#registerEmail").value.trim();
            const payload = {
                name: $("#registerName").value.trim(),
                email,
                password: $("#registerPassword").value,
                role: this.activeRole(),
                phone: $("#registerPhone").value.trim() || null,
                location: $("#registerLocation").value.trim(),
            };
            const done = await this.submit(form, () => Api.post("/auth/register", payload),
                "Account created! Please log in with your email and password.", false);
            if (done) {
                form.reset();
                this.updateRoleSelection();
                this.setTab("login");
            }
        },

        async handleForgotEmail(event) {
            event.preventDefault();
            const form = $("#forgotEmailForm");
            if (!form.checkValidity()) {
                form.reportValidity();
                return;
            }
            this.resetEmail = $("#forgotEmail").value.trim();
            const done = await this.submit(form,
                () => Api.post("/auth/password-reset/request", { email: this.resetEmail }),
                "Code sent to your email", false);
            if (done) {
                $("#forgotEmailEcho").textContent = this.resetEmail;
                this.forgotStep("code");
            }
        },

        async resendCode(button) {
            if (!this.resetEmail) return;
            button.disabled = true;
            try {
                const response = await Api.post("/auth/password-reset/request", { email: this.resetEmail });
                if (response && response.success) Toast.success("A fresh code is on its way");
                else Toast.error(response && response.message ? response.message : "Something went wrong");
            } catch (error) {
                Toast.error(error && error.message ? error.message : "Something went wrong");
            } finally {
                button.disabled = false;
            }
        },

        handleForgotCode(event) {
            event.preventDefault();
            const form = $("#forgotCodeForm");
            if (!form.checkValidity()) {
                form.reportValidity();
                return;
            }
            const code = $("#forgotCode").value.trim();
            if (!/^\d{6}$/.test(code)) {
                Toast.error("Code must be 6 digits");
                return;
            }
            this.resetCode = code;
            this.forgotStep("password");
        },

        async handleForgotPassword(event) {
            event.preventDefault();
            const form = $("#forgotPasswordForm");
            if (!form.checkValidity()) {
                form.reportValidity();
                return;
            }
            const next = $("#forgotNewPassword").value;
            if (next !== $("#forgotConfirmPassword").value) {
                Toast.error("Passwords do not match");
                return;
            }
            const done = await this.submit(form,
                () => Api.post("/auth/password-reset/confirm", {
                    email: this.resetEmail,
                    code: this.resetCode,
                    newPassword: next,
                }),
                "Password reset successful. Please log in.", false);
            if (done) {
                form.reset();
                $("#forgotEmailForm").reset();
                $("#forgotCodeForm").reset();
                this.resetEmail = "";
                this.resetCode = "";
                this.setTab("login");
            }
        },

        async submit(form, request, successMessage, autoLogin) {
            const button = form.querySelector("button[type='submit']");
            const original = button.textContent;
            button.disabled = true;
            button.textContent = "Please wait...";
            Loader.show("Working...");

            try {
                const response = await request();
                if (response && response.success) {
                    // Login signs the user straight in; a fresh registration only
                    // shows a notification and hands over to the login tab, so the
                    // user always signs in with email + password explicitly.
                    if (autoLogin) {
                        window.Api.setToken(response.data.token);
                        window.Api.setUser(response.data);
                        Toast.success(successMessage);
                        setTimeout(() => {
                            window.location.href = "/dashboard";
                        }, 400);
                    } else {
                        Toast.success(successMessage);
                    }
                    return true;
                }
                Toast.error(response && response.message ? response.message : "Something went wrong");
            } catch (error) {
                Toast.error(error && error.message ? error.message : "Something went wrong");
            } finally {
                button.disabled = false;
                button.textContent = original;
                Loader.hide();
            }
            return false;
        },
    };

    const ThemeControls = {
        init() {
            $$("[data-theme-toggle]").forEach((button) => {
                button.addEventListener("click", () => {
                    Theme.toggle();
                    this.renderIcons();
                });
            });
            document.addEventListener("themechange", () => this.renderIcons());
            this.renderIcons();
        },

        renderIcons() {
            const dark = Theme.isDark();
            $$("[data-theme-icon]").forEach((icon) => {
                const isMoon = icon.dataset.themeIcon === "moon";
                icon.classList.toggle("hidden", dark ? isMoon : !isMoon);
            });
        },
    };

    function escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    /**
     * Hero counters come from the live database, never from hard-coded
     * marketing numbers. If the call fails the markup keeps its fallback text.
     */
    const HeroStats = {
        async init() {
            try {
                const res = await Api.getPublic("/public/stats");
                const data = (res && res.data) || {};
                this.set("#statFarmers", data.farmers);
                this.set("#statBuyers", data.buyers);
                this.set("#statListings", data.listings);
            } catch {
                return;
            }
        },

        set(selector, value) {
            const node = $(selector);
            if (!node) return;
            const count = Number(value);
            if (Number.isFinite(count) && count >= 0) {
                node.textContent = count.toLocaleString("en-US");
            }
        },
    };

    /**
     * Highlights the nav link of the section currently in view and also on
     * manual clicks, so Home / Explore / About / Contact always reflect where
     * the visitor is.
     */
    const NavSpy = {
        init() {
            const links = $$(".navbar-links .nav-link");
            if (!links.length) return;
            const setActive = (link) => {
                links.forEach((node) => {
                    const on = node === link;
                    node.classList.toggle("active", on);
                    if (on) node.setAttribute("aria-current", "true");
                    else node.removeAttribute("aria-current");
                });
            };
            links.forEach((link) => link.addEventListener("click", () => setActive(link)));
            const sections = links
                .map((link) => document.querySelector(link.getAttribute("href")))
                .filter(Boolean);
            if (!("IntersectionObserver" in window) || !sections.length) {
                setActive(links[0]);
                return;
            }
            const observer = new IntersectionObserver((entries) => {
                entries.forEach((entry) => {
                    if (!entry.isIntersecting) return;
                    const link = links.find(
                        (node) => node.getAttribute("href") === `#${entry.target.id}`);
                    if (link) setActive(link);
                });
            }, { rootMargin: "-40% 0px -55% 0px" });
            sections.forEach((section) => observer.observe(section));
        },
    };

    document.addEventListener("DOMContentLoaded", () => {
        if (window.Theme) ThemeControls.init();

        NavSpy.init();
        HeroStats.init();
        Explorer.init();

        const searchInput = $("#cropSearch");
        const categoryFilter = $("#categoryFilter");
        const refreshButton = $("[data-explore-refresh]");

        if (searchInput) {
            searchInput.addEventListener("input", () => Explorer.applyFilter());
        }
        if (categoryFilter) {
            categoryFilter.addEventListener("change", () => Explorer.applyFilter());
        }
        if (refreshButton) {
            refreshButton.addEventListener("click", () => Explorer.refresh());
        }

        $$("[data-explore]").forEach((button) => {
            button.addEventListener("click", () => {
                const target = $("#explore");
                if (target) target.scrollIntoView({ behavior: "smooth" });
            });
        });

        Auth.init();
    });
})();