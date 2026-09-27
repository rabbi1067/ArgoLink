window.Pages = window.Pages || {};

window.Pages.settings = {
    container: null,

    init(container) {
        this.container = container;
        this.bindProfileForm();
        this.bindPasswordForm();
        this.bindAvatarInput();
        this.bindPasswordToggles();
        this.loadProfile();
    },

    async loadProfile() {
        try {
            const res = await Api.get("/users/me");
            const profile = res.data || {};
            this.setValue("#settingsName", profile.name);
            this.setValue("#settingsEmail", profile.email);
            this.setValue("#settingsPhone", profile.phone);
            this.setValue("#settingsLocation", profile.location);
            this.renderAvatar(profile.profileImageUrl, profile.name);
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, window.t ? t("set.loadFail") : "Could not load profile");
        }
    },

    renderAvatar(imageUrl, name) {
        const preview = this.container.querySelector("#avatarPreview");
        const initialsEl = this.container.querySelector("#avatarInitials");
        const imgEl = this.container.querySelector("#avatarImg");
        if (!preview || !initialsEl) return;
        if (preview.dataset.previewUrl === imageUrl) return;
        preview.dataset.previewUrl = imageUrl || "";

        const initials = String(name || "?")
            .trim()
            .split(/\s+/)
            .map((part) => part.charAt(0))
            .join("")
            .slice(0, 2)
            .toUpperCase() || "?";

        if (imageUrl) {
            if (imgEl) {
                imgEl.onload = () => { initialsEl.textContent = ""; };
                imgEl.onerror = () => { this.showInitials(initialsEl, name); };
                imgEl.src = imageUrl;
                imgEl.hidden = false;
                initialsEl.textContent = "";
            }
        } else {
            if (imgEl) {
                imgEl.removeAttribute("src");
                imgEl.hidden = true;
            }
            this.showInitials(initialsEl, name);
        }
    },

    showInitials(initialsEl, name) {
        if (!initialsEl) return;
        const key = String(name || "?");
        const initials = key
            .trim()
            .split(/\s+/)
            .map((part) => part.charAt(0))
            .join("")
            .slice(0, 2)
            .toUpperCase() || "?";
        initialsEl.textContent = initials;
        let hash = 0;
        for (let i = 0; i < key.length; i++) {
            hash = (hash * 31 + key.charCodeAt(i)) >>> 0;
        }
        const hue = hash % 360;
        const preview = this.container.querySelector("#avatarPreview");
        if (preview) {
            preview.style.backgroundImage =
                `linear-gradient(135deg, hsl(${hue}, 55%, 48%), hsl(${(hue + 45) % 360}, 60%, 38%))`;
        }
    },

    bindAvatarInput() {
        const input = this.container.querySelector("#avatarFileInput");
        if (!input) return;
        input.addEventListener("change", () => this.handleAvatarSelected(input.files && input.files[0]));
    },

    async handleAvatarSelected(file) {
        if (!file) return;
        const error = this.validateAvatar(file);
        if (error) {
            if (window.Toast) Toast.error(error);
            this.resetAvatarInput();
            return;
        }

        const imgEl = this.container.querySelector("#avatarImg");
        const initialsEl = this.container.querySelector("#avatarInitials");
        const objectUrl = URL.createObjectURL(file);
        if (imgEl) {
            imgEl.onload = () => { if (initialsEl) initialsEl.textContent = ""; };
            imgEl.src = objectUrl;
            imgEl.hidden = false;
            if (initialsEl) initialsEl.textContent = "";
        }

        try {
            const res = await Api.upload("/users/upload-avatar", file);
            const profile = res.data || {};
            if (imgEl && imgEl.src) URL.revokeObjectURL(imgEl.src);
            this.renderAvatar(profile.profileImageUrl, profile.name);
            this.syncFreshProfile(profile);
            this.syncShell(profile);
            if (window.Toast) Toast.success(window.t ? t("set.photoUpdated") : "Profile photo updated");
        } catch (uploadError) {
            if (imgEl) {
                imgEl.removeAttribute("src");
                imgEl.hidden = true;
                const stored = Api.getUser();
                const name = stored && stored.name ? stored.name : "";
                this.showInitials(initialsEl, name);
            }
            if (window.Toast) Toast.fromResponse(uploadError, window.t ? t("set.uploadFail") : "Could not upload photo");
        } finally {
            this.resetAvatarInput();
        }
    },

    validateAvatar(file) {
        const allowed = ["image/jpeg", "image/png", "image/webp"];
        if (!allowed.includes(file.type)) {
            return window.t ? t("set.avatarType") : "Only JPEG, PNG and WebP images are allowed";
        }
        if (file.size > 5 * 1024 * 1024) {
            return window.t ? t("set.avatarSize") : "Avatar must be 5 MB or smaller";
        }
        return null;
    },

    resetAvatarInput() {
        const input = this.container.querySelector("#avatarFileInput");
        if (input) input.value = "";
    },

    bindProfileForm() {
        const form = this.container.querySelector("#profileForm");
        if (!form) return;
        form.addEventListener("submit", (event) => this.handleProfileSubmit(event));
    },

    async handleProfileSubmit(event) {
        const form = this.container.querySelector("#profileForm");
        if (!form) return;
        event.preventDefault();

        const name = this.value("#settingsName").trim();
        const phone = this.value("#settingsPhone").trim();
        const location = this.value("#settingsLocation").trim();

        if (!name || name.length < 2) {
            if (window.Toast) Toast.error(window.t ? t("set.nameShort") : "Name must be at least 2 characters");
            return;
        }

        const submit = form.querySelector("button[type='submit']");
        const original = submit ? submit.textContent : "";
        if (submit) {
            submit.disabled = true;
            submit.textContent = window.t ? t("set.saving") : "Saving...";
        }

        try {
            const res = await Api.put("/users/me", {
                name,
                phone: phone || null,
                location: location || null,
            });
            const updated = res.data || {};
            this.setValue("#settingsName", updated.name || name);
            this.syncFreshProfile(updated);
            this.syncShell(updated);
            if (window.Toast) Toast.success(window.t ? t("set.saved") : "Profile updated");
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, window.t ? t("set.updateFail") : "Could not update profile");
        } finally {
            if (submit) {
                submit.disabled = false;
                submit.textContent = original;
            }
        }
    },

    syncFreshProfile(profile) {
        if (!(window.Api && Api.getUser && Api.setUser)) return;
        const current = Api.getUser();
        if (!current) return;
        const next = Object.assign({}, current, {
            name: profile.name || current.name,
            phone: profile.phone !== undefined ? profile.phone : current.phone,
            location: profile.location !== undefined ? profile.location : current.location,
            profileImagePublicId: profile.profileImagePublicId !== undefined ? profile.profileImagePublicId : current.profileImagePublicId,
        });
        localStorage.setItem("agrolink_user", JSON.stringify(next));
    },

    syncShell(profile) {
        if (window.applyProfileToShell) {
            window.applyProfileToShell(profile);
            return;
        }
        const nameEl = document.getElementById("topbar-username");
        if (nameEl && profile.name) nameEl.textContent = profile.name;
        if (window.setProfileAvatar) window.setProfileAvatar(profile.name, profile.profileImageUrl);
    },

    bindPasswordForm() {
        const form = this.container.querySelector("#passwordForm");
        if (!form) return;
        form.addEventListener("submit", (event) => this.handlePasswordSubmit(event));
    },

    async handlePasswordSubmit(event) {
        const form = this.container.querySelector("#passwordForm");
        if (!form) return;
        event.preventDefault();

        const currentPassword = this.value("#settingsCurrentPassword");
        const newPassword = this.value("#settingsNewPassword");

        if (!currentPassword) {
            if (window.Toast) Toast.error(window.t ? t("set.curRequired") : "Current password is required");
            return;
        }
        if (!newPassword || newPassword.length < 8) {
            if (window.Toast) Toast.error(window.t ? t("set.newShort") : "New password must be at least 8 characters");
            return;
        }
        if (!/(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/.test(newPassword)) {
            if (window.Toast) Toast.error(window.t ? t("set.newWeak") : "New password must include an uppercase letter, a lowercase letter and a number");
            return;
        }
        if (currentPassword === newPassword) {
            if (window.Toast) Toast.error(window.t ? t("set.newSame") : "New password must be different from the current one");
            return;
        }

        const submit = form.querySelector("button[type='submit']");
        const original = submit ? submit.textContent : "";
        if (submit) {
            submit.disabled = true;
            submit.textContent = window.t ? t("set.updating") : "Updating...";
        }

        try {
            await Api.post("/users/change-password", { currentPassword, newPassword });
            if (window.Toast) Toast.success(window.t ? t("set.pwChanged") : "Password changed");
            form.reset();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, window.t ? t("set.pwFail") : "Could not change password");
        } finally {
            if (submit) {
                submit.disabled = false;
                submit.textContent = original;
            }
        }
    },

    bindPasswordToggles() {
        const toggles = this.container.querySelectorAll("[data-toggle-password]");
        toggles.forEach((button) => {
            button.addEventListener("click", () => {
                const target = this.container.querySelector(`#${button.dataset.togglePassword}`);
                if (!target) return;
                const show = target.type === "password";
                target.type = show ? "text" : "password";
                button.setAttribute("aria-pressed", String(show));
                button.setAttribute("aria-label", show ? t("auth.hidePassword") : t("auth.showPassword"));
                const showIcon = button.querySelector(".pw-show");
                const hideIcon = button.querySelector(".pw-hide");
                if (showIcon) showIcon.classList.toggle("hidden", show);
                if (hideIcon) hideIcon.classList.toggle("hidden", !show);
            });
        });
    },

    value(selector) {
        const el = this.container.querySelector(selector);
        return el ? el.value : "";
    },

    setValue(selector, value) {
        const el = this.container.querySelector(selector);
        if (el && value !== undefined && value !== null) el.value = String(value);
    },
};