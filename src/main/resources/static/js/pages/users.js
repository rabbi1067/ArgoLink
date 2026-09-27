window.Pages = window.Pages || {};

window.Pages.users = {
    container: null,
    isAdmin: false,
    isSuperAdmin: false,
    users: [],
    selected: new Set(),
    page: 1,
    pageSize: 10,
    CACHE_PREFIX: "agrolink_users_cache_",

    ROLE_CLASS: {
        FARMER: "badge-success",
        BUYER: "badge-info",
        ADMIN: "badge-warning",
        SUPER_ADMIN: "badge-error",
    },

    init(container) {
        this.container = container;
        this.selected = new Set();
        this.page = 1;
        const user = window.Api && Api.getUser ? Api.getUser() : null;
        this.isAdmin = user && (user.role === "ADMIN" || user.role === "SUPER_ADMIN");
        this.isSuperAdmin = user && user.role === "SUPER_ADMIN";

        if (!this.isAdmin) {
            const wrap = this.container.querySelector("#usersTableWrap");
            if (wrap) wrap.innerHTML = '<div class="empty-state">Admins only.</div>';
            const addButton = this.container.querySelector("#usersAddBtn");
            if (addButton) addButton.hidden = true;
            return;
        }

        // Both Admin and Super Admin can open the create form; the label/options differ by role.
        const addButton = this.container.querySelector("#usersAddBtn");
        if (addButton) addButton.textContent = this.isSuperAdmin ? "+ Create Admin / User" : "+ Create User";

        // A plain Admin never sees or filters by Admin/Super Admin roles.
        if (!this.isSuperAdmin) {
            const filter = this.container.querySelector("#userRoleFilter");
            if (filter) {
                Array.from(filter.options).forEach((opt) => {
                    if (opt.value === "ADMIN" || opt.value === "SUPER_ADMIN") opt.remove();
                });
            }
        }

        this.bindControls();
        this.load();
    },

    // Renders instantly from the last cached list (if any) for a fast first paint,
    // then always refetches from the server in the background and updates the view + cache.
    async load() {
        const wrap = this.container.querySelector("#usersTableWrap");
        const filter = this.container.querySelector("#userRoleFilter");
        const role = filter ? filter.value : "";
        const cacheKey = this.CACHE_PREFIX + (role || "ALL");

        const cached = this.readCache(cacheKey);
        if (cached) {
            this.users = cached;
            this.selected.clear();
            this.page = 1;
            this.render();
        }

        try {
            const res = role
                ? await Api.get(`/users/by-role/${role}`)
                : await Api.get("/users");
            this.users = res.data || [];
            this.writeCache(cacheKey, this.users);
            this.selected.clear();
            if (!cached) this.page = 1;
            this.render();
        } catch (error) {
            if (!cached) {
                wrap.innerHTML = '<div class="empty-state">Could not load users</div>';
                if (window.Toast) Toast.fromResponse(error, "Could not load users");
            }
        }
    },

    readCache(key) {
        try {
            const raw = sessionStorage.getItem(key);
            return raw ? JSON.parse(raw) : null;
        } catch (e) {
            return null;
        }
    },

    writeCache(key, data) {
        try {
            sessionStorage.setItem(key, JSON.stringify(data));
        } catch (e) { /* ignore - cache is a best-effort speed-up only */ }
    },

    bindControls() {
        const addButton = this.container.querySelector("#usersAddBtn");
        if (addButton) addButton.addEventListener("click", () => this.openCreateModal());

        const refresh = this.container.querySelector("[data-users-refresh]");
        if (refresh) refresh.addEventListener("click", () => this.load());

        const filter = this.container.querySelector("#userRoleFilter");
        if (filter) filter.addEventListener("change", () => this.load());

        const search = this.container.querySelector("#usersSearch");
        if (search) search.addEventListener("input", () => {
            this.page = 1;
            this.render();
        });

        const modal = this.container.querySelector("#userModal");
        const modalClose = this.container.querySelector("[data-user-modal-close]");
        if (modalClose) modalClose.addEventListener("click", () => this.closeModal(modal));
        if (modal) {
            modal.addEventListener("click", (event) => {
                if (event.target === modal) this.closeModal(modal);
            });
        }

        const editModal = this.container.querySelector("#userEditModal");
        const editModalClose = this.container.querySelector("[data-user-edit-modal-close]");
        if (editModalClose) editModalClose.addEventListener("click", () => this.closeModal(editModal));
        if (editModal) {
            editModal.addEventListener("click", (event) => {
                if (event.target === editModal) this.closeModal(editModal);
            });
        }

        const form = this.container.querySelector("#userForm");
        if (form) form.addEventListener("submit", (event) => this.handleCreateSubmit(event));

        const editForm = this.container.querySelector("#userEditForm");
        if (editForm) editForm.addEventListener("submit", (event) => this.handleEditSubmit(event));

        const wrap = this.container.querySelector("#usersTableWrap");
        if (wrap) wrap.addEventListener("click", (event) => this.handleTableClick(event));
        if (wrap) wrap.addEventListener("change", (event) => this.handleTableChange(event));

        const bulkDeleteBtn = this.container.querySelector("#usersBulkDeleteBtn");
        if (bulkDeleteBtn) bulkDeleteBtn.addEventListener("click", () => this.handleBulkDelete());

        const prevBtn = this.container.querySelector("#usersPrevPage");
        const nextBtn = this.container.querySelector("#usersNextPage");
        if (prevBtn) prevBtn.addEventListener("click", () => {
            if (this.page > 1) {
                this.page -= 1;
                this.render();
            }
        });
        if (nextBtn) nextBtn.addEventListener("click", () => {
            this.page += 1;
            this.render();
        });

        this.state = this.state || {};
        this.state.escHandler = (event) => {
            if (event.key !== "Escape") return;
            const open = this.container.querySelector(".modal-backdrop.open");
            if (open) this.closeModal(open);
        };
        document.addEventListener("keydown", this.state.escHandler);
    },

    // Custom in-page confirm dialog (replaces window.confirm) used before every
    // edit-save, deactivate/activate, delete and bulk-delete action.
    confirmAction(message) {
        const modal = this.container.querySelector("#userConfirmModal");
        const messageEl = this.container.querySelector("#userConfirmMessage");
        const okBtn = this.container.querySelector("#userConfirmOkBtn");
        const cancelBtn = this.container.querySelector("#userConfirmCancelBtn");
        if (!modal) return Promise.resolve(true);

        messageEl.textContent = message;
        this.openModal(modal);

        return new Promise((resolve) => {
            const cleanup = (result) => {
                okBtn.removeEventListener("click", onOk);
                cancelBtn.removeEventListener("click", onCancel);
                modal.removeEventListener("click", onBackdrop);
                this.closeModal(modal);
                resolve(result);
            };
            const onOk = () => cleanup(true);
            const onCancel = () => cleanup(false);
            const onBackdrop = (event) => {
                if (event.target === modal) cleanup(false);
            };
            okBtn.addEventListener("click", onOk);
            cancelBtn.addEventListener("click", onCancel);
            modal.addEventListener("click", onBackdrop);
        });
    },

    // A row can be managed here only if: it's not SUPER_ADMIN, and (I'm Super Admin OR the row isn't ADMIN).
    canManage(user) {
        if (user.role === "SUPER_ADMIN") return false;
        if (user.role === "ADMIN" && !this.isSuperAdmin) return false;
        return true;
    },

    handleTableChange(event) {
        const checkbox = event.target.closest("[data-user-select]");
        if (!checkbox) return;
        const id = checkbox.dataset.id;
        if (checkbox.checked) this.selected.add(id);
        else this.selected.delete(id);
        this.updateBulkDeleteButton();
    },

    updateBulkDeleteButton() {
        const btn = this.container.querySelector("#usersBulkDeleteBtn");
        const countEl = this.container.querySelector("#usersSelectedCount");
        if (countEl) countEl.textContent = String(this.selected.size);
        if (btn) btn.hidden = this.selected.size === 0;
    },

    async handleBulkDelete() {
        const ids = Array.from(this.selected);
        if (!ids.length) return;
        const ok = await this.confirmAction(`Delete ${ids.length} selected user(s)? This cannot be undone.`);
        if (!ok) return;

        try {
            await Api.post("/users/bulk-delete", { ids });
            if (window.Toast) Toast.success(`${ids.length} user(s) deleted`);
            this.load();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not delete selected users");
        }
    },

    async handleTableClick(event) {
        const toggleBtn = event.target.closest("[data-user-toggle]");
        if (toggleBtn) {
            const id = toggleBtn.dataset.id;
            const active = toggleBtn.dataset.active === "true";
            const actionText = active ? "deactivate" : "activate";

            const ok = await this.confirmAction(`Do you want to ${actionText} this user?`);
            if (!ok) return;

            Api.patch(`/users/${id}/status`, { active: !active })
                .then(() => {
                    if (window.Toast) Toast.success(`User ${actionText}d`);
                    this.load();
                })
                .catch((error) => {
                    if (window.Toast) Toast.fromResponse(error, `Could not ${actionText} user`);
                });
            return;
        }

        const editBtn = event.target.closest("[data-user-edit]");
        if (editBtn) {
            const user = this.users.find((u) => u.id === editBtn.dataset.id);
            if (user) this.openEditModal(user);
            return;
        }

        const deleteBtn = event.target.closest("[data-user-delete]");
        if (deleteBtn) {
            const id = deleteBtn.dataset.id;
            const user = this.users.find((u) => u.id === id);
            const ok = await this.confirmAction(`Delete ${user ? this.esc(user.name || user.email) : "this user"}? This cannot be undone.`);
            if (!ok) return;

            try {
                await Api.del(`/users/${id}`);
                if (window.Toast) Toast.success("User deleted");
                this.selected.delete(id);
                this.load();
            } catch (error) {
                if (window.Toast) Toast.fromResponse(error, "Could not delete user");
            }
        }
    },

    // SUPER_ADMIN rows always first; within each group, newest-created first (descending).
    sortedUsers() {
        return [...this.users].sort((a, b) => {
            const aSuper = a.role === "SUPER_ADMIN";
            const bSuper = b.role === "SUPER_ADMIN";
            if (aSuper !== bSuper) return aSuper ? -1 : 1;
            const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
            const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
            return tb - ta;
        });
    },

    renderStats() {
        const total = this.users.length;
        const admins = this.users.filter((u) => u.role === "ADMIN" || u.role === "SUPER_ADMIN").length;
        const active = this.users.filter((u) => u.active !== false).length;
        const inactive = total - active;

        const set = (id, value) => {
            const el = this.container.querySelector(id);
            if (el) el.textContent = String(value);
        };
        set("#usersStatTotal", total);
        set("#usersStatAdmins", admins);
        set("#usersStatActive", active);
        set("#usersStatInactive", inactive);
    },

    render() {
        const wrap = this.container.querySelector("#usersTableWrap");
        const count = this.container.querySelector("#usersCount");
        const search = this.container.querySelector("#usersSearch");
        const pagination = this.container.querySelector("#usersPagination");
        if (!wrap) return;

        this.renderStats();

        const term = (search ? search.value : "").trim().toLowerCase();
        const filtered = this.sortedUsers().filter((user) => {
            if (!term) return true;
            return `${user.name || ""} ${user.email || ""}`.toLowerCase().includes(term);
        });

        if (count) count.textContent = String(filtered.length);
        this.updateBulkDeleteButton();

        if (!filtered.length) {
            wrap.innerHTML = '<div class="empty-state">No users found.</div>';
            if (pagination) pagination.hidden = true;
            return;
        }

        const totalPages = Math.max(1, Math.ceil(filtered.length / this.pageSize));
        if (this.page > totalPages) this.page = totalPages;
        const start = (this.page - 1) * this.pageSize;
        const pageUsers = filtered.slice(start, start + this.pageSize);

        const rows = pageUsers
            .map((user) => {
                const initials = (user.name || "?")
                    .split(" ")
                    .map((part) => part[0])
                    .filter(Boolean)
                    .slice(0, 2)
                    .join("")
                    .toUpperCase();
                const roleClass = this.ROLE_CLASS[user.role] || "badge-info";
                const manageable = this.canManage(user);

                return `
                    <tr>
                        <td>
                            ${manageable
                    ? `<input type="checkbox" data-user-select data-id="${this.esc(user.id)}" ${this.selected.has(user.id) ? "checked" : ""} aria-label="Select ${this.esc(user.name || "user")}">`
                    : ""}
                        </td>
                        <td>
                            <div class="flex items-center gap-3">
                                <span class="avatar">${this.esc(initials)}</span>
                                <div>
                                    <p class="mb-0 font-semibold" style="font-weight: 600;">${this.esc(user.name || "—")}</p>
                                </div>
                            </div>
                        </td>
                        <td><span class="font-mono" style="font-size: 0.8rem;">${this.esc(user.email || "—")}</span></td>
                        <td><span class="badge ${roleClass}">${this.esc(user.role || "")}</span></td>
                        <td>${this.esc(user.phone || "—")}</td>
                        <td>${this.esc(user.location || "—")}</td>
                        <td>
                            ${user.active === false
                    ? '<span class="badge badge-error">Inactive</span>'
                    : '<span class="badge badge-success">Active</span>'}
                        </td>
                        <td>
                            ${manageable
                    ? `
                                <div class="flex gap-2">
                                    <button type="button" class="btn btn-sm btn-secondary" data-user-edit data-id="${this.esc(user.id)}">Edit</button>
                                    <button type="button" class="btn btn-sm ${user.active === false ? "btn-primary" : "btn-danger"}" data-user-toggle data-id="${this.esc(user.id)}" data-active="${user.active === false ? "false" : "true"}">
                                        ${user.active === false ? "Activate" : "Deactivate"}
                                    </button>
                                    <button type="button" class="btn btn-sm btn-danger" data-user-delete data-id="${this.esc(user.id)}">Delete</button>
                                </div>`
                    : '<span class="text-muted">—</span>'}
                        </td>
                    </tr>`;
            })
            .join("");

        wrap.innerHTML = `
            <div class="table-scroll">
                <table class="table">
                    <thead>
                        <tr>
                            <th></th>
                            <th>User</th>
                            <th>Email</th>
                            <th>Role</th>
                            <th>Phone</th>
                            <th>Location</th>
                            <th>Status</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>`;

        if (pagination) {
            pagination.hidden = totalPages <= 1;
            const info = this.container.querySelector("#usersPageInfo");
            if (info) info.textContent = `Page ${this.page} of ${totalPages}`;
            const prevBtn = this.container.querySelector("#usersPrevPage");
            const nextBtn = this.container.querySelector("#usersNextPage");
            if (prevBtn) prevBtn.disabled = this.page <= 1;
            if (nextBtn) nextBtn.disabled = this.page >= totalPages;
        }
    },

    openCreateModal() {
        const modal = this.container.querySelector("#userModal");
        const form = this.container.querySelector("#userForm");
        const title = this.container.querySelector("#userModalTitle");
        if (title) title.textContent = this.isSuperAdmin ? "Create Admin / User" : "Create User";
        if (form) form.reset();
        this.populateRoles();
        if (modal) this.openModal(modal);
    },

    populateRoles() {
        const roleSelect = this.container.querySelector("#userRole");
        if (!roleSelect) return;
        const current = roleSelect.value;
        roleSelect.innerHTML =
            '<option value="">Select role</option>' +
            '<option value="FARMER">Farmer</option>' +
            '<option value="BUYER">Buyer</option>' +
            (this.isSuperAdmin ? '<option value="ADMIN">Admin</option>' : "");
        roleSelect.value = current;
    },

    openEditModal(user) {
        const modal = this.container.querySelector("#userEditModal");
        this.container.querySelector("#userEditId").value = user.id;
        this.container.querySelector("#userEditName").value = user.name || "";
        this.container.querySelector("#userEditEmail").value = user.email || "";
        this.container.querySelector("#userEditPhone").value = user.phone || "";
        this.container.querySelector("#userEditLocation").value = user.location || "";

        // Only a Super Admin may reassign a role, and never to/from SUPER_ADMIN through this form.
        const roleField = this.container.querySelector("#userEditRoleField");
        const roleSelect = this.container.querySelector("#userEditRole");
        if (this.isSuperAdmin && user.role !== "SUPER_ADMIN") {
            roleField.hidden = false;
            roleSelect.value = user.role;
        } else {
            roleField.hidden = true;
        }

        if (modal) this.openModal(modal);
    },

    async handleEditSubmit(event) {
        event.preventDefault();
        const id = this.container.querySelector("#userEditId").value;
        const name = this.container.querySelector("#userEditName").value.trim();
        const phone = this.container.querySelector("#userEditPhone").value.trim() || null;
        const location = this.container.querySelector("#userEditLocation").value.trim() || null;

        if (name.length < 2) {
            if (window.Toast) Toast.error("Name must be at least 2 characters");
            return;
        }

        const ok = await this.confirmAction("Save changes to this user?");
        if (!ok) return;

        const form = this.container.querySelector("#userEditForm");
        const submit = form.querySelector("button[type='submit']");
        const original = submit ? submit.textContent : "";
        if (submit) {
            submit.disabled = true;
            submit.textContent = "Saving...";
        }

        try {
            await Api.put(`/users/${id}`, { name, phone, location });

            const roleField = this.container.querySelector("#userEditRoleField");
            if (!roleField.hidden) {
                const newRole = this.container.querySelector("#userEditRole").value;
                const user = this.users.find((u) => u.id === id);
                if (user && newRole && newRole !== user.role) {
                    await Api.patch(`/users/${id}/role`, { role: newRole });
                }
            }

            if (window.Toast) Toast.success("User updated");
            this.closeModal(this.container.querySelector("#userEditModal"));
            this.load();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not update user");
        } finally {
            if (submit) {
                submit.disabled = false;
                submit.textContent = original;
            }
        }
    },

    async handleCreateSubmit(event) {
        const form = this.container.querySelector("#userForm");
        if (!form) return;
        event.preventDefault();

        const role = this.value("#userRole");
        const body = {
            name: this.value("#userName"),
            email: this.value("#userEmail"),
            password: this.value("#userPassword"),
            role,
            phone: this.value("#userPhone") || null,
            location: this.value("#userLocation") || null,
        };

        if (!body.name || !body.email || body.password.length < 8) {
            if (window.Toast) Toast.error("Fill all required fields");
            return;
        }

        const submit = form.querySelector("button[type='submit']");
        const original = submit ? submit.textContent : "";
        if (submit) {
            submit.disabled = true;
            submit.textContent = "Creating...";
        }

        try {
            await Api.post("/users", body);
            if (window.Toast) Toast.success("User created");
            this.closeModal(this.container.querySelector("#userModal"));
            this.load();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not create user");
        } finally {
            if (submit) {
                submit.disabled = false;
                submit.textContent = original;
            }
        }
    },

    openModal(modal) {
        if (!modal) return;
        modal.classList.add("open");
        modal.setAttribute("aria-hidden", "false");
    },

    closeModal(modal) {
        if (!modal) return;
        modal.classList.remove("open");
        modal.setAttribute("aria-hidden", "true");
    },

    value(selector) {
        const el = this.container.querySelector(selector);
        return el ? el.value : "";
    },

    esc(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    },
};