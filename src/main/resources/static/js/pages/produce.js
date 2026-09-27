window.Pages = window.Pages || {};

window.Pages.produce = {
    PAGE_SIZE: 12,
    DIVISIONS: ["Dhaka", "Chattogram", "Rajshahi", "Khulna", "Barishal", "Sylhet", "Rangpur", "Mymensingh"],
    MONTHS: ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"],

    container: null,
    role: "BUYER",
    categories: [],
    locations: [],
    listings: [],          // farmer: own listings (filtered in the browser)
    pageData: null,        // buyer/admin: one server-side page
    page: 0,
    reqId: 0,
    searchTimer: null,
    pendingImageUrl: "",
    uploading: false,
    offerListing: null,
    _onKeydown: null,

    /* ------------------------------------------------------------ lifecycle */

    init(container) {
        this.container = container;
        const user = window.Api && Api.getUser ? Api.getUser() : null;
        this.role = user && user.role ? user.role : "BUYER";

        this.categories = [];
        this.locations = [];
        this.listings = [];
        this.pageData = null;
        this.page = 0;
        this.pendingImageUrl = "";
        this.uploading = false;
        this.offerListing = null;
        clearTimeout(this.searchTimer);

        this.bindToolbar();
        this.bindGrid();
        this.bindModals();

        const addButton = this.$("#produceAddBtn");
        if (addButton) addButton.hidden = !this.isFarmer();
        this.container.querySelectorAll("[data-buyer-filter]").forEach((el) => {
            el.hidden = this.isManager();
        });
        this.container.querySelectorAll("[data-admin-filter]").forEach((el) => {
            el.hidden = !this.isAdmin();
        });

        this.load();
    },

    destroy() {
        clearTimeout(this.searchTimer);
        if (this._onKeydown) document.removeEventListener("keydown", this._onKeydown);
        this._onKeydown = null;
    },

    $(selector) {
        return this.container ? this.container.querySelector(selector) : null;
    },

    isFarmer() {
        return this.role === "FARMER";
    },

    isBuyer() {
        return this.role === "BUYER";
    },

    isAdmin() {
        return this.role === "ADMIN" || this.role === "SUPER_ADMIN";
    },

    /** Farmers manage their own listings; admins manage every listing. */
    isManager() {
        return this.isFarmer() || this.isAdmin();
    },

    /* ------------------------------------------------------------ loading */

    async load() {
        const grid = this.$("#produceGrid");
        const context = this.$("#produceContext");
        if (grid) grid.innerHTML = this.skeleton(6);

        try {
            const [categoriesRes, locationsRes] = await Promise.all([
                Api.get("/produce/categories"),
                Api.get("/locations/districts").catch(() => ({ data: [] })),
            ]);

            this.categories = categoriesRes.data || [];
            this.locations = locationsRes.data || [];

            if (context) {
                context.textContent = this.isAdmin()
                    ? "Manage every listing on the platform"
                    : this.isFarmer()
                        ? "Manage your own supply listings"
                        : "Browse marketplace supply";
            }

            this.populateCategoryOptions();
            this.populateLocationOptions();

            if (this.isManager()) {
                await this.loadOwn();
            } else {
                await this.loadPage(this.page);
            }
        } catch (error) {
            this.showError("Could not load produce listings");
            if (window.Toast) Toast.fromResponse(error, "Could not load produce");
        }
    },

    async loadOwn() {
        const res = await Api.get(this.isAdmin() ? "/produce/manage" : "/produce/my");
        this.listings = res.data || [];
        this.render();
    },

    async loadPage(page) {
        const grid = this.$("#produceGrid");
        const id = ++this.reqId;
        const f = this.filters();

        const params = new URLSearchParams();
        Object.entries({
            q: f.q,
            category: f.category,
            division: f.division,
            district: f.district,
            minPrice: f.minPrice,
            maxPrice: f.maxPrice,
            sort: f.sort,
        }).forEach(([key, value]) => {
            if (value !== "" && value !== null && value !== undefined) params.set(key, value);
        });
        params.set("page", String(page));
        params.set("size", String(this.PAGE_SIZE));

        if (grid) grid.setAttribute("aria-busy", "true");
        try {
            const res = await Api.get(`/produce/listings/search?${params.toString()}`);
            if (id !== this.reqId) return;
            this.pageData = res.data;
            this.page = res.data ? res.data.page : 0;
            this.render();
        } catch (error) {
            if (id !== this.reqId) return;
            this.showError("Could not load produce listings");
            if (window.Toast) Toast.fromResponse(error, "Could not load produce");
        } finally {
            if (grid && id === this.reqId) grid.removeAttribute("aria-busy");
        }
    },

    showError(message) {
        const grid = this.$("#produceGrid");
        if (!grid) return;
        grid.innerHTML = `
            <div class="empty-state">
                <p class="mb-2">${this.esc(message)}</p>
                <button type="button" class="btn btn-primary btn-sm" data-action="retry">Try again</button>
            </div>`;
    },

    /* ------------------------------------------------------------ filters */

    filters() {
        const num = (selector) => {
            const raw = this.value(selector).trim();
            return raw === "" || Number.isNaN(Number(raw)) ? "" : raw;
        };
        return {
            q: this.value("#produceSearch").trim(),
            category: this.value("#produceCategoryFilter"),
            division: this.value("#produceDivisionFilter"),
            district: this.value("#produceDistrictFilter"),
            minPrice: num("#producePriceMin"),
            maxPrice: num("#producePriceMax"),
            sort: this.value("#produceSort") || "newest",
            status: this.value("#produceStatusFilter"),
        };
    },

    onFilterChange() {
        if (this.isManager()) {
            this.render();
            return;
        }
        clearTimeout(this.searchTimer);
        this.searchTimer = setTimeout(() => this.loadPage(0), 350);
    },

    clearFilters() {
        ["#produceSearch", "#produceCategoryFilter", "#produceDivisionFilter", "#produceDistrictFilter", "#producePriceMin", "#producePriceMax", "#produceStatusFilter"]
            .forEach((selector) => this.setValue(selector, ""));
        this.setValue("#produceSort", "newest");
        this.populateDistrictFilter();
        this.onFilterChange();
    },

    populateCategoryOptions() {
        const filter = this.$("#produceCategoryFilter");
        const modalSelect = this.$("#produceCategory");
        const values = this.categories.map((c) => c.name || c.category || c).filter(Boolean);

        if (filter) {
            const current = filter.value;
            filter.innerHTML =
                '<option value="">All categories</option>' +
                values.map((v) => `<option value="${this.esc(v)}">${this.esc(v)}</option>`).join("");
            filter.value = values.includes(current) ? current : "";
        }
        if (modalSelect) {
            modalSelect.innerHTML =
                '<option value="">Select category</option>' +
                values.map((v) => `<option value="${this.esc(v)}">${this.esc(v)}</option>`).join("");
        }
    },

    divisionsPresent() {
        const present = new Set(this.locations.map((l) => l.division));
        return this.DIVISIONS.filter((d) => present.has(d));
    },

    populateLocationOptions() {
        const divisionFilter = this.$("#produceDivisionFilter");
        if (divisionFilter) {
            divisionFilter.innerHTML =
                '<option value="">All divisions</option>' +
                this.divisionsPresent().map((d) => `<option value="${this.esc(d)}">${this.esc(d)}</option>`).join("");
        }
        this.populateDistrictFilter();

        const modalDistrict = this.$("#produceDistrict");
        if (modalDistrict) {
            modalDistrict.innerHTML =
                '<option value="">Select district</option>' +
                this.divisionsPresent()
                    .map((division) => {
                        const options = this.locations
                            .filter((l) => l.division === division)
                            .map((l) => `<option value="${this.esc(l.name)}">${this.esc(l.name)}</option>`)
                            .join("");
                        return `<optgroup label="${this.esc(division)}">${options}</optgroup>`;
                    })
                    .join("");
        }
    },

    populateDistrictFilter() {
        const select = this.$("#produceDistrictFilter");
        if (!select) return;
        const division = this.value("#produceDivisionFilter");
        const items = this.locations.filter((l) => !division || l.division === division);
        select.innerHTML =
            '<option value="">All districts</option>' +
            items.map((l) => `<option value="${this.esc(l.name)}">${this.esc(l.name)}</option>`).join("");
    },

    /* ------------------------------------------------------------ binding */

    bindToolbar() {
        const on = (selector, event, handler) => {
            const el = this.$(selector);
            if (el) el.addEventListener(event, handler);
        };

        on("[data-produce-refresh]", "click", () => this.load());
        on("#produceSearch", "input", () => this.onFilterChange());
        on("#produceCategoryFilter", "change", () => this.onFilterChange());
        on("#produceDivisionFilter", "change", () => {
            this.populateDistrictFilter();
            this.onFilterChange();
        });
        on("#produceDistrictFilter", "change", () => this.onFilterChange());
        on("#produceStatusFilter", "change", () => this.onFilterChange());
        on("#producePriceMin", "input", () => this.onFilterChange());
        on("#producePriceMax", "input", () => this.onFilterChange());
        on("#produceSort", "change", () => this.onFilterChange());
        on("#produceClearFilters", "click", () => this.clearFilters());
        on("#produceAddBtn", "click", () => this.openCreateModal());

        on("[data-produce-modal-close]", "click", () => this.closeModal(this.$("#produceModal")));
        on("[data-offer-modal-close]", "click", () => this.closeModal(this.$("#offerModal")));

        on("#producePager", "click", (event) => {
            const button = event.target.closest("[data-page-nav]");
            if (!button || button.disabled) return;
            const next = button.dataset.pageNav === "next" ? this.page + 1 : this.page - 1;
            this.loadPage(Math.max(next, 0));
            const grid = this.$("#produceGrid");
            if (grid && grid.scrollIntoView) grid.scrollIntoView({ behavior: "smooth", block: "start" });
        });
    },

    bindGrid() {
        const grid = this.$("#produceGrid");
        if (!grid) return;
        grid.addEventListener("click", (event) => this.handleGridClick(event));
        grid.addEventListener("submit", (event) => this.handleStockSubmit(event));
    },

    bindModals() {
        this.container.querySelectorAll(".modal-backdrop").forEach((backdrop) => {
            backdrop.addEventListener("click", (event) => {
                if (event.target === backdrop) this.closeModal(backdrop);
            });
        });

        const form = this.$("#produceForm");
        if (form) form.addEventListener("submit", (event) => this.handleCreateSubmit(event));

        const offerForm = this.$("#offerForm");
        if (offerForm) offerForm.addEventListener("submit", (event) => this.handleOfferSubmit(event));

        ["#offerQuantity", "#offerPrice"].forEach((selector) => {
            const el = this.$(selector);
            if (el) el.addEventListener("input", () => this.updateOfferTotal());
        });

        const image = this.$("#produceImage");
        if (image) image.addEventListener("change", (event) => this.handleImageSelected(event));
        const remove = this.$("#produceImageRemove");
        if (remove) remove.addEventListener("click", () => this.clearImage());

        // Remove the handler from a previous visit so listeners never pile up.
        if (this._onKeydown) document.removeEventListener("keydown", this._onKeydown);
        this._onKeydown = (event) => {
            if (event.key !== "Escape" || !this.container || !this.container.isConnected) return;
            const open = this.container.querySelector(".modal-backdrop.open");
            if (open) this.closeModal(open);
        };
        document.addEventListener("keydown", this._onKeydown);
    },

    handleGridClick(event) {
        const button = event.target.closest("[data-action]");
        if (!button) return;
        const { action, id } = button.dataset;

        if (action === "retry") {
            this.load();
            return;
        }
        if (!id) return;

        if (action === "publish" || action === "archive") {
            this.updateStatus(id, action);
        } else if (action === "edit") {
            this.openEditModal(id);
        } else if (action === "delete") {
            this.deleteListing(id);
        } else if (action === "offer") {
            this.openOfferModal(id);
        } else if (action === "message") {
            this.openConversation(id);
        }
    },

    /** Buyer clicked the 💬 button on a listing card -> open (or reuse) the
     *  WhatsApp-style thread with that farmer and jump to the Messages tab. */
    openConversation(listingId) {
        if (!window.Api || !Api.post) return;
        Api.post("/messages/conversations", { listingId })
            .then((res) => {
                window.PendingConversation = (res.data && res.data.id) || null;
                window.location.hash = "messages";
            })
            .catch((error) => {
                if (window.Toast) Toast.fromResponse(error, "Could not open chat");
            });
    },

    /* ------------------------------------------------------------ rendering */

    render() {
        if (this.isManager()) {
            this.renderFarmer();
        } else {
            this.renderBuyer();
        }
    },

    renderBuyer() {
        const grid = this.$("#produceGrid");
        const count = this.$("#produceCount");
        const data = this.pageData;
        if (!grid) return;

        const items = data && data.items ? data.items : [];
        if (count) {
            const total = data ? data.totalItems : 0;
            count.textContent = total === 1 ? "1 listing found" : `${this.number(total)} listings found`;
        }
        grid.innerHTML = items.length
            ? items.map((listing) => this.buyerCard(listing)).join("")
            : '<div class="empty-state">No produce matches your filters. Try clearing some filters.</div>';
        this.renderPager();
    },

    renderFarmer() {
        const grid = this.$("#produceGrid");
        const count = this.$("#produceCount");
        if (!grid) return;

        this.renderSummary();
        const { q, category, status } = this.filters();
        const term = q.toLowerCase();
        const filtered = this.listings.filter((listing) => {
            if (category && listing.category !== category) return false;
            if (status && listing.status !== status) return false;
            if (term) {
                const haystack = `${listing.cropName || ""} ${listing.location || ""} ${listing.district || ""} ${listing.category || ""} ${listing.farmerName || ""}`.toLowerCase();
                if (!haystack.includes(term)) return false;
            }
            return true;
        });

        if (count) count.textContent = `${filtered.length} of ${this.listings.length} listings`;
        grid.innerHTML = filtered.length
            ? filtered.map((listing) => this.farmerCard(listing)).join("")
            : (this.isAdmin() ? '<div class="empty-state">No listings match these filters.</div>' : '<div class="empty-state">No listings found. Use "+ Add Supply" to create one.</div>');
        this.renderPager();
    },

    renderSummary() {
        const box = this.$("#produceSummary");
        if (!box) return;
        const count = (status) => this.listings.filter((l) => l.status === status).length;
        const stockValue = this.listings
            .filter((l) => l.status === "ACTIVE")
            .reduce((sum, l) => sum + Number(l.remainingQuantity || 0) * Number(l.pricePerUnit || 0), 0);
        box.hidden = false;
        box.innerHTML = `
            <span class="pd-chip">Active: ${count("ACTIVE")}</span>
            <span class="pd-chip">Draft: ${count("DRAFT")}</span>
            <span class="pd-chip">Archived: ${count("ARCHIVED")}</span>
            ${this.isAdmin() ? `<span class="pd-chip">Farmers: ${new Set(this.listings.map((l) => l.farmerId)).size}</span>` : ""}
            <span class="pd-chip">Live stock value: ৳${this.money(stockValue)}</span>`;
    },

    renderPager() {
        const pager = this.$("#producePager");
        if (!pager) return;
        const data = this.pageData;
        if (this.isManager() || !data || data.totalPages <= 1) {
            pager.hidden = true;
            pager.innerHTML = "";
            return;
        }
        pager.hidden = false;
        pager.innerHTML = `
            <button type="button" class="btn btn-secondary btn-sm" data-page-nav="prev" ${data.hasPrevious ? "" : "disabled"}>← Previous</button>
            <span class="text-muted">Page ${data.page + 1} of ${data.totalPages}</span>
            <button type="button" class="btn btn-secondary btn-sm" data-page-nav="next" ${data.hasNext ? "" : "disabled"}>Next →</button>`;
    },

    skeleton(count) {
        return Array.from({ length: count })
            .map(() => `
                <div class="card pd-card" aria-hidden="true">
                    <div class="pd-media pd-skel-block"></div>
                    <div class="pd-skel-line" style="width: 70%;"></div>
                    <div class="pd-skel-line" style="width: 45%;"></div>
                    <div class="pd-skel-line" style="width: 85%;"></div>
                </div>`)
            .join("");
    },

    media(listing, statusBadge) {
        const src = this.safeImage(listing.imageUrl);
        const image = src
            ? `<img src="${this.esc(src)}" alt="${this.esc(listing.cropName || "Produce")}" loading="lazy">`
            : '<span class="pd-placeholder" aria-hidden="true">🌾</span>';
        return `
            <div class="pd-media">
                ${image}
                <span class="pd-price">৳${this.money(listing.pricePerUnit)} / ${this.esc(listing.unit || "kg")}</span>
                ${statusBadge ? `<span class="pd-status">${statusBadge}</span>` : ""}
            </div>`;
    },

    placeText(listing) {
        const parts = [];
        if (listing.district) parts.push(listing.district);
        if (listing.division && listing.division !== listing.district) parts.push(listing.division);
        if (!parts.length && listing.location) parts.push(listing.location);
        return parts.join(", ");
    },

    stockOf(listing) {
        return listing.remainingQuantity !== undefined && listing.remainingQuantity !== null
            ? listing.remainingQuantity
            : listing.availableQuantity;
    },

    buyerCard(listing) {
        const canOffer = this.isBuyer();
        return `
            <div class="card listing-card pd-card">
                ${this.media(listing, "")}
                <h3 class="card-title mb-1">${this.esc(listing.cropName || "Unnamed")}</h3>
                <p class="text-muted pd-meta">${this.esc(listing.category || "")} · 📍 ${this.esc(this.placeText(listing))}</p>
                ${listing.farmerName ? `<p class="text-muted pd-meta">👨‍🌾 ${this.esc(listing.farmerName)}</p>` : ""}
                ${listing.description ? `<p class="text-muted pd-desc">${this.esc(listing.description)}</p>` : ""}
                <div class="grid grid-cols-2 gap-3 mb-2">
                    <div>
                        <div class="stat-value">${this.number(this.stockOf(listing))}</div>
                        <div class="stat-label">${this.esc(listing.unit || "kg")} in stock</div>
                    </div>
                    <div>
                        <div class="stat-value" style="font-size: 1rem;">${this.esc(this.formatDate(listing.harvestDate) || "Flexible")}</div>
                        <div class="stat-label">harvest</div>
                    </div>
                </div>
                <div class="flex gap-2 mt-2">
                    ${canOffer ? `<button type="button" class="btn btn-primary btn-block" data-action="offer" data-id="${this.esc(listing.id)}">Make an Offer</button>` : ""}
                    ${canOffer ? `<button type="button" class="btn btn-secondary" data-action="message" data-id="${this.esc(listing.id)}" title="Message the farmer">💬</button>` : ""}
                </div>
            </div>`;
    },

    farmerCard(listing) {
        const statusClass = listing.status === "ACTIVE" ? "badge-success" : listing.status === "ARCHIVED" ? "badge-error" : "badge-warning";
        const badge = `<span class="badge ${statusClass}">${this.esc(listing.status || "")}</span>`;
        const id = this.esc(listing.id);
        return `
            <div class="card listing-card pd-card">
                ${this.media(listing, badge)}
                <h3 class="card-title mb-1">${this.esc(listing.cropName || "Unnamed")}</h3>
                <p class="text-muted pd-meta">${this.esc(listing.category || "")} · 📍 ${this.esc(this.placeText(listing))}</p>
                ${this.isAdmin() ? `<p class="text-muted pd-meta">👨‍🌾 ${this.esc(listing.farmerName || "Unknown farmer")}</p>` : ""}
                <div class="grid grid-cols-2 gap-3 mb-3">
                    <div>
                        <div class="stat-value">${this.number(listing.availableQuantity)}</div>
                        <div class="stat-label">${this.esc(listing.unit || "kg")} available</div>
                    </div>
                    <div>
                        <div class="stat-value">${this.number(listing.reservedQuantity)}</div>
                        <div class="stat-label">reserved</div>
                    </div>
                </div>
                <form data-stock-form data-id="${id}" class="flex gap-2 mb-3">
                    <input class="input input-sm" type="number" data-stock-input value="${this.esc(listing.availableQuantity)}" min="${this.esc(listing.reservedQuantity || 0)}" step="0.01" aria-label="Available quantity">
                    <button type="submit" class="btn btn-secondary btn-sm">Update stock</button>
                </form>
                <div class="flex flex-wrap gap-2">
                    ${listing.status !== "ACTIVE" ? `<button type="button" class="btn btn-primary btn-sm" data-action="publish" data-id="${id}">Publish</button>` : ""}
                    ${listing.status !== "ARCHIVED" ? `<button type="button" class="btn btn-secondary btn-sm" data-action="archive" data-id="${id}">Archive</button>` : ""}
                    <button type="button" class="btn btn-secondary btn-sm" data-action="edit" data-id="${id}">Edit</button>
                    <button type="button" class="btn btn-danger btn-sm" data-action="delete" data-id="${id}">Delete</button>
                </div>
            </div>`;
    },

    /* ------------------------------------------------------------ farmer actions */

    async handleStockSubmit(event) {
        const form = event.target.closest("[data-stock-form]");
        if (!form) return;
        event.preventDefault();

        const input = form.querySelector("[data-stock-input]");
        const quantity = Number(input ? input.value : NaN);
        if (Number.isNaN(quantity) || quantity < 0) {
            if (window.Toast) Toast.error("Enter a valid quantity");
            return;
        }
        try {
            await Api.patch(`/produce/${form.dataset.id}/stock`, { newAvailableQuantity: quantity });
            if (window.Toast) Toast.success("Stock updated");
            this.loadOwn();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not update stock");
        }
    },

    async updateStatus(id, action) {
        try {
            await Api.patch(`/produce/${id}/${action}`);
            if (window.Toast) Toast.success(action === "publish" ? "Listing published" : "Listing archived");
            this.loadOwn();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, `Could not ${action} listing`);
        }
    },

    async deleteListing(id) {
        if (!window.confirm(this.isAdmin() ? "Delete this listing permanently? The farmer will lose it. This cannot be undone." : "Delete this listing? This cannot be undone.")) return;
        try {
            await Api.del(`/produce/${id}`);
            if (window.Toast) Toast.success("Listing deleted");
            this.loadOwn();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not delete listing");
        }
    },

    /* ------------------------------------------------------------ create / edit modal */

    openCreateModal() {
        const form = this.$("#produceForm");
        if (form) form.reset();
        this.setValue("#produceId", "");
        this.setText("#produceModalTitle", "Add Supply");
        this.setText("#produceSubmitBtn", "Add Listing");
        this.populateCategoryOptions();
        this.showImage("");
        this.openModal(this.$("#produceModal"));
    },

    openEditModal(id) {
        const listing = this.listings.find((l) => l.id === id);
        if (!listing) return;

        const form = this.$("#produceForm");
        if (form) form.reset();
        this.populateCategoryOptions();

        this.setValue("#produceId", listing.id);
        this.setText("#produceModalTitle", "Edit Supply");
        this.setText("#produceSubmitBtn", "Save Changes");
        this.setValue("#produceCropName", listing.cropName);
        this.setValue("#produceCategory", listing.category);
        this.setValue("#produceDescription", listing.description || "");
        this.setValue("#produceQuantity", listing.availableQuantity);
        this.setValue("#produceUnit", listing.unit);
        this.setValue("#producePrice", listing.pricePerUnit);
        this.setValue("#produceDistrict", listing.district || "");
        this.setValue("#produceLocation", listing.location && listing.location !== listing.district ? listing.location : "");
        this.setValue("#produceHarvestDate", listing.harvestDate || "");
        this.showImage(listing.imageUrl || "");
        this.openModal(this.$("#produceModal"));
    },

    async handleCreateSubmit(event) {
        const form = this.$("#produceForm");
        if (!form) return;
        event.preventDefault();

        if (this.uploading) {
            if (window.Toast) Toast.error("Please wait for the photo to finish uploading");
            return;
        }

        const id = this.value("#produceId");
        const quantity = Number(this.value("#produceQuantity"));
        const price = Number(this.value("#producePrice"));
        const cropName = this.value("#produceCropName").trim();

        if (!cropName || !this.value("#produceCategory")) {
            if (window.Toast) Toast.error("Crop name and category are required");
            return;
        }
        if (!this.value("#produceDistrict")) {
            if (window.Toast) Toast.error("Please select a district");
            return;
        }
        if (!(quantity > 0) || !(price > 0)) {
            if (window.Toast) Toast.error("Quantity and price must be greater than zero");
            return;
        }

        const description = this.value("#produceDescription").trim();
        const location = this.value("#produceLocation").trim();
        const harvestDate = this.value("#produceHarvestDate");
        const body = {
            cropName,
            category: this.value("#produceCategory"),
            description: description || null,
            availableQuantity: quantity,
            unit: this.value("#produceUnit"),
            pricePerUnit: price,
            imageUrl: this.pendingImageUrl || null,
            district: this.value("#produceDistrict"),
            location: location || null,
            harvestDate: harvestDate || null,
        };

        const submit = this.$("#produceSubmitBtn");
        const original = submit ? submit.textContent : "";
        if (submit) {
            submit.disabled = true;
            submit.textContent = "Saving...";
        }

        try {
            if (id) {
                await Api.put(`/produce/${id}`, body);
                if (window.Toast) Toast.success("Listing updated");
            } else {
                await Api.post("/produce", body);
                if (window.Toast) Toast.success("Listing created (saved as draft — publish it to go live)");
            }
            this.closeModal(this.$("#produceModal"));
            this.loadOwn();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not save listing");
        } finally {
            if (submit) {
                submit.disabled = false;
                submit.textContent = original;
            }
        }
    },

    /* ------------------------------------------------------------ image upload */

    async handleImageSelected(event) {
        const input = event.target;
        const file = input.files && input.files[0];
        if (!file) return;

        if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
            if (window.Toast) Toast.error("Choose a JPEG, PNG or WebP image");
            input.value = "";
            return;
        }
        if (file.size > 15 * 1024 * 1024) {
            if (window.Toast) Toast.error("That photo is too large (max 15 MB)");
            input.value = "";
            return;
        }

        const status = this.$("#produceImageStatus");
        const submit = this.$("#produceSubmitBtn");
        this.uploading = true;
        if (status) status.textContent = "Uploading photo…";
        if (submit) submit.disabled = true;

        try {
            const blob = await this.resizeImage(file);
            const url = await this.uploadImage(blob);
            this.showImage(url);
            if (status) status.textContent = "Photo ready";
        } catch (error) {
            if (status) status.textContent = "";
            input.value = "";
            if (window.Toast) Toast.error(error && error.message ? error.message : "Could not upload the photo");
        } finally {
            this.uploading = false;
            if (submit) submit.disabled = false;
        }
    },

    /** Shrinks the photo to at most 1280 px and re-encodes as JPEG (~100-400 KB) before upload. */
    async resizeImage(file, maxSide = 1280, quality = 0.82) {
        const bitmap = await createImageBitmap(file);
        const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(bitmap.width * scale));
        canvas.height = Math.max(1, Math.round(bitmap.height * scale));
        const ctx = canvas.getContext("2d");
        ctx.fillStyle = "#ffffff";
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
        if (bitmap.close) bitmap.close();
        return new Promise((resolve, reject) => {
            canvas.toBlob(
                (blob) => (blob ? resolve(blob) : reject(new Error("Could not process the photo"))),
                "image/jpeg",
                quality
            );
        });
    },

    async uploadImage(blob) {
        const token = window.Api && Api.getToken ? Api.getToken() : null;
        const form = new FormData();
        form.append("file", blob, "listing.jpg");

        const response = await fetch("/api/v1/files/upload", {
            method: "POST",
            headers: token ? { Authorization: `Bearer ${token}` } : {},
            body: form,
        });

        let payload = null;
        try {
            payload = await response.json();
        } catch (e) {
            payload = null;
        }
        if (!response.ok || !payload || !payload.data || !payload.data.url) {
            throw new Error(payload && payload.message ? payload.message : `Upload failed (${response.status})`);
        }
        return payload.data.url;
    },

    showImage(url) {
        this.pendingImageUrl = url || "";
        const safe = this.safeImage(url);
        const box = this.$("#produceImagePreviewBox");
        const img = this.$("#produceImagePreview");
        const remove = this.$("#produceImageRemove");
        const status = this.$("#produceImageStatus");
        if (img) img.src = safe;
        if (box) box.classList.toggle("has-image", Boolean(safe));
        if (remove) remove.hidden = !safe;
        if (status && !safe) status.textContent = "";
    },

    clearImage() {
        const input = this.$("#produceImage");
        if (input) input.value = "";
        this.showImage("");
    },

    /* ------------------------------------------------------------ offer modal */

    openOfferModal(id) {
        const items = this.pageData && this.pageData.items ? this.pageData.items : [];
        const listing = items.find((l) => l.id === id);
        if (!listing) return;
        this.offerListing = listing;

        const form = this.$("#offerForm");
        if (form) form.reset();
        this.setValue("#offerListingId", listing.id);
        this.setValue("#offerPrice", listing.pricePerUnit);
        this.setText(
            "#offerModalListing",
            `${listing.cropName || "Crop"} · ৳${this.money(listing.pricePerUnit)}/${listing.unit || "kg"} · ${this.number(this.stockOf(listing))} ${listing.unit || ""} in stock`
        );
        this.updateOfferTotal();
        this.openModal(this.$("#offerModal"));
    },

    updateOfferTotal() {
        const quantity = Number(this.value("#offerQuantity"));
        const price = Number(this.value("#offerPrice"));
        const total = quantity > 0 && price > 0 ? quantity * price : 0;
        this.setText("#offerTotal", `৳${this.money(total)}`);
    },

    async handleOfferSubmit(event) {
        const form = this.$("#offerForm");
        if (!form) return;
        event.preventDefault();

        const body = {
            listingId: this.value("#offerListingId"),
            offeredQuantity: Number(this.value("#offerQuantity")),
            offeredPrice: Number(this.value("#offerPrice")),
        };

        if (!(body.offeredQuantity > 0) || !(body.offeredPrice > 0)) {
            if (window.Toast) Toast.error("Enter valid quantity and price");
            return;
        }
        const listing = this.offerListing;
        if (listing && body.offeredQuantity > Number(this.stockOf(listing))) {
            if (window.Toast) Toast.error(`Only ${this.number(this.stockOf(listing))} ${listing.unit || ""} in stock`);
            return;
        }

        const submit = this.$("#offerSubmitBtn");
        if (submit) submit.disabled = true;
        try {
            await Api.post("/offers", body);
            if (window.Toast) Toast.success("Offer submitted");
            this.closeModal(this.$("#offerModal"));
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not submit offer");
        } finally {
            if (submit) submit.disabled = false;
        }
    },

    /* ------------------------------------------------------------ helpers */

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
        const el = this.$(selector);
        return el ? el.value : "";
    },

    setValue(selector, value) {
        const el = this.$(selector);
        if (el) el.value = value === undefined || value === null ? "" : String(value);
    },

    setText(selector, text) {
        const el = this.$(selector);
        if (el) el.textContent = text;
    },

    /** Only our own uploads or https images are rendered (blocks javascript:/data: URLs). */
    safeImage(url) {
        return typeof url === "string" && (url.startsWith("/api/v1/files/") || url.startsWith("https://")) ? url : "";
    },

    formatDate(iso) {
        if (!iso) return "";
        const [year, month, day] = String(iso).slice(0, 10).split("-").map(Number);
        if (!year || !month || !day) return String(iso);
        return `${day} ${this.MONTHS[month - 1]} ${year}`;
    },

    number(value) {
        return Number(value || 0).toLocaleString("en-IN");
    },

    money(value) {
        return Number(value || 0).toLocaleString("en-IN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        });
    },

    esc(value) {
        return String(value === undefined || value === null ? "" : value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    },
};