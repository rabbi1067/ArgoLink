window.Pages = window.Pages || {};

window.Pages.orders = {
    container: null,
    role: "BUYER",
    orders: [],
    offers: [],
    listingCache: {},
    isAdmin: false,

    ordersPage: 0,
    ordersPageSize: 5,
    ordersTotalPages: 0,
    ordersHasNext: false,
    ordersHasPrevious: false,

    offersPage: 0,
    offersPageSize: 6,
    offersTotalPages: 0,
    offersHasNext: false,
    offersHasPrevious: false,
    offersTotal: 0,
    orderStats: null,
    paymentCapabilities: null,

    TRACK_STAGES: ["PENDING", "OFFER_ACCEPTED", "PAID_CONFIRMED", "IN_TRANSIT", "DELIVERED"],

    /** Mirrors the server-side transition table in OrderServiceImpl. */
    TRANSITIONS: {
        PENDING: ["OFFER_ACCEPTED", "CANCELLED", "DISPUTED"],
        OFFER_ACCEPTED: ["PAYMENT_PENDING", "CANCELLED", "DISPUTED"],
        PAYMENT_PENDING: ["PAID_CONFIRMED", "CANCELLED", "DISPUTED"],
        PAID_CONFIRMED: ["PROCESSING", "IN_TRANSIT", "CANCELLED", "DISPUTED"],
        ESCROW_HELD: ["PROCESSING", "IN_TRANSIT", "CANCELLED", "DISPUTED"],
        PROCESSING: ["IN_TRANSIT", "CANCELLED", "DISPUTED"],
        CONFIRMED: ["IN_TRANSIT", "CANCELLED", "DISPUTED"],
        IN_TRANSIT: ["DELIVERED", "DISPUTED"],
        DELIVERED: ["DISPUTED"],
        CANCELLED: [],
        DISPUTED: [],
    },

    /**
     * Who may push an order forward, per side of the deal. Same rule as the server: once the
     * buyer's money is in escrow the order can only be disputed, never cancelled, and only the
     * farmer can advance the delivery stages. Admins keep the manual override.
     */
    SIDE_TRANSITIONS: {
        buyer: {
            PENDING: ["CANCELLED", "DISPUTED"],
            OFFER_ACCEPTED: ["CANCELLED", "DISPUTED"],
            PAYMENT_PENDING: ["CANCELLED", "DISPUTED"],
            PAID_CONFIRMED: ["DISPUTED"],
            ESCROW_HELD: ["DISPUTED"],
            PROCESSING: ["DISPUTED"],
            CONFIRMED: ["DISPUTED"],
            IN_TRANSIT: ["DISPUTED"],
            DELIVERED: ["DISPUTED"],
            CANCELLED: [],
            DISPUTED: [],
        },
        farmer: {
            PENDING: ["CANCELLED", "DISPUTED"],
            OFFER_ACCEPTED: ["CANCELLED", "DISPUTED"],
            PAYMENT_PENDING: ["CANCELLED", "DISPUTED"],
            PAID_CONFIRMED: ["PROCESSING", "IN_TRANSIT", "DELIVERED", "DISPUTED"],
            ESCROW_HELD: ["PROCESSING", "IN_TRANSIT", "DELIVERED", "DISPUTED"],
            PROCESSING: ["IN_TRANSIT", "DISPUTED"],
            CONFIRMED: ["IN_TRANSIT", "DELIVERED", "DISPUTED"],
            IN_TRANSIT: ["DELIVERED", "DISPUTED"],
            DELIVERED: ["DISPUTED"],
            CANCELLED: [],
            DISPUTED: [],
        },
    },

    /** Stages where the buyer's money is already secured, so cancelling is off the table. */
    POST_PAYMENT_STAGES: ["PAID_CONFIRMED", "ESCROW_HELD", "PROCESSING", "CONFIRMED", "IN_TRANSIT", "DELIVERED"],

    /** Settled orders live in Invoices, so they drop out of the commitments list. */
    SETTLED_STAGES: ["DELIVERED"],

    isPostPayment(status) {
        return this.POST_PAYMENT_STAGES.includes(status);
    },

    /** Status changes this user may perform on an order in its current stage. */
    allowedTransitions(order) {
        if (this.isAdmin) {
            return this.TRANSITIONS[order.orderStatus] || [];
        }
        const side = this.role === "FARMER" ? "farmer" : "buyer";
        const table = this.SIDE_TRANSITIONS[side] || {};
        return table[order.orderStatus] || [];
    },

    /** Statuses where the farmer (or an admin) can push the live driver position. */
    LIVE_TRACKING_STAGES: ["PAID_CONFIRMED", "ESCROW_HELD", "PROCESSING", "IN_TRANSIT", "DELIVERED"],

    /** Statuses where the buyer still owes money. */
    PAYABLE_STAGES: ["OFFER_ACCEPTED", "PAYMENT_PENDING"],

    init(container) {
        this.container = container;
        const user = window.Api && Api.getUser ? Api.getUser() : null;
        this.role = user && user.role ? user.role : "BUYER";
        this.isAdmin = this.role === "ADMIN" || this.role === "SUPER_ADMIN";
        this.orders = [];
        this.offers = [];
        this.listingCache = {};
        this.ordersPage = 0;
        this.offersPage = 0;
        this.ordersPageSize = this.isAdmin ? 20 : 5;

        this.bindControls();
        this.load();
    },

    async load() {
        try {
            await Promise.all([this.loadOrders(), this.loadOffers(), this.loadStats(), this.loadPaymentCapabilities()]);
        } catch (error) {
            this.renderOrders(this.orders);
            this.renderOffers(this.offers);
            if (window.Toast) Toast.fromResponse(error, "Could not load orders");
        }
    },

    async loadOrders() {
        const filter = this.container.querySelector("#orderStatusFilter");
        const status = filter ? filter.value : "";
        const as = this.role === "FARMER" ? "FARMER" : "BUYER";

        const path = this.isAdmin ? "/orders/admin/all" : "/orders/my/page";
        const params = { page: this.ordersPage, size: this.ordersPageSize };
        if (!this.isAdmin) params.as = as;
        if (status) params.status = status;

        const res = await Api.get(path, params);
        const pageData = res.data || {};
        this.orders = pageData.content || [];
        this.ordersPage = pageData.page || 0;
        this.ordersTotalPages = pageData.totalPages || 0;
        this.ordersHasNext = Boolean(pageData.hasNext);
        this.ordersHasPrevious = Boolean(pageData.hasPrevious);

        this.renderOrders(this.orders);
        this.renderOrdersPagination();
    },

    /** Six negotiations per page, newest first, straight from the server. */
    async loadOffers() {
        const as = this.role === "FARMER" ? "FARMER" : "BUYER";
        const res = await Api.get("/offers/my/page", {
            as: as,
            page: this.offersPage,
            size: this.offersPageSize,
        });
        const pageData = res.data || {};
        this.offers = pageData.content || [];
        this.offersPage = pageData.page || 0;
        this.offersTotalPages = pageData.totalPages || 0;
        this.offersHasNext = Boolean(pageData.hasNext);
        this.offersHasPrevious = Boolean(pageData.hasPrevious);
        this.offersTotal = pageData.totalElements || this.offers.length;

        await Promise.all(this.offers.map((offer) => this.ensureListing(offer.listingId)));
        this.renderOffers(this.offers);
        this.renderOffersPagination();
    },

    /** Headline counters so the summary strip describes the whole order book, not one page. */
    async loadStats() {
        try {
            const res = await Api.get("/orders/my/stats");
            this.orderStats = res.data || null;
            this.renderOrderStats();
        } catch (error) {
            this.orderStats = null;
            this.renderOrderStats();
        }
    },

    /** Which payment rails this deployment can actually use right now. */
    async loadPaymentCapabilities() {
        try {
            const res = await Api.get("/orders/payment/capabilities");
            this.paymentCapabilities = res.data || null;
        } catch (error) {
            this.paymentCapabilities = null;
        }
        this.renderPaymentCapabilities();
    },

    bindControls() {
        const refresh = this.container.querySelector("[data-orders-refresh]");
        if (refresh) refresh.addEventListener("click", () => this.load());

        const filter = this.container.querySelector("#orderStatusFilter");
        if (filter) filter.addEventListener("change", () => {
            this.ordersPage = 0;
            this.loadOrders();
        });

        const prevBtn = this.container.querySelector("#ordersPrevPage");
        if (prevBtn) prevBtn.addEventListener("click", () => {
            if (this.ordersPage > 0) {
                this.ordersPage -= 1;
                this.loadOrders();
            }
        });

        const nextBtn = this.container.querySelector("#ordersNextPage");
        if (nextBtn) nextBtn.addEventListener("click", () => {
            if (this.ordersHasNext) {
                this.ordersPage += 1;
                this.loadOrders();
            }
        });

        const offersPrev = this.container.querySelector("#offersPrevPage");
        if (offersPrev) offersPrev.addEventListener("click", () => {
            if (this.offersHasPrevious) {
                this.offersPage -= 1;
                this.loadOffers();
            }
        });

        const offersNext = this.container.querySelector("#offersNextPage");
        if (offersNext) offersNext.addEventListener("click", () => {
            if (this.offersHasNext) {
                this.offersPage += 1;
                this.loadOffers();
            }
        });

        const acceptClose = this.container.querySelector("[data-accept-modal-close]");
        const acceptModal = this.container.querySelector("#acceptModal");
        if (acceptClose) acceptClose.addEventListener("click", () => this.closeModal(acceptModal));

        const counterClose = this.container.querySelector("[data-counter-modal-close]");
        const counterModal = this.container.querySelector("#counterModal");
        if (counterClose) counterClose.addEventListener("click", () => this.closeModal(counterModal));

        const paymentClose = this.container.querySelector("[data-payment-modal-close]");
        const paymentModal = this.container.querySelector("#paymentModal");
        if (paymentClose) paymentClose.addEventListener("click", () => this.closeModal(paymentModal));

        const trackingClose = this.container.querySelector("[data-tracking-modal-close]");
        const trackingModal = this.container.querySelector("#trackingModal");
        if (trackingClose) trackingClose.addEventListener("click", () => this.closeModal(trackingModal));

        this.container.querySelectorAll(".modal-backdrop").forEach((backdrop) => {
            backdrop.addEventListener("click", (event) => {
                if (event.target === backdrop) this.closeModal(backdrop);
            });
        });

        const acceptForm = this.container.querySelector("#acceptForm");
        if (acceptForm) acceptForm.addEventListener("submit", (event) => this.handleAcceptSubmit(event));

        const counterForm = this.container.querySelector("#counterForm");
        if (counterForm) counterForm.addEventListener("submit", (event) => this.handleCounterSubmit(event));

        const paymentForm = this.container.querySelector("#paymentForm");
        if (paymentForm) paymentForm.addEventListener("submit", (event) => this.handlePaymentSubmit(event));

        const paymentMethod = this.container.querySelector("#paymentMethod");
        if (paymentMethod) paymentMethod.addEventListener("change", () => this.toggleTransactionField());

        this.container.querySelectorAll('input[name="paymentMethodChoice"]').forEach((radio) => {
            radio.addEventListener("change", () => this.syncPaymentMethod());
        });

        const trackingForm = this.container.querySelector("#trackingForm");
        if (trackingForm) trackingForm.addEventListener("submit", (event) => this.handleTrackingSubmit(event));

        const offersWrap = this.container.querySelector("#offerList");
        if (offersWrap) offersWrap.addEventListener("click", (event) => this.handleOfferAction(event));

        const ordersWrap = this.container.querySelector("#ordersTableWrap");
        if (ordersWrap) ordersWrap.addEventListener("click", (event) => this.handleOrderAction(event));

        this.state = this.state || {};
        this.state.escHandler = (event) => {
            if (event.key !== "Escape") return;
            const open = this.container.querySelector(".modal-backdrop.open");
            if (open) this.closeModal(open);
        };
        document.addEventListener("keydown", this.state.escHandler);
    },

    handleOrderAction(event) {
        const chat = event.target.closest("[data-order-chat]");
        if (chat) {
            const id = chat.dataset.id;
            Api.post("/messages/conversations", { orderId: id })
                .then((res) => {
                    window.PendingConversation = (res.data && res.data.id) || null;
                    window.location.hash = "messages";
                })
                .catch((error) => {
                    if (window.Toast) Toast.fromResponse(error, "Could not open chat");
                });
            return;
        }

        const accept = event.target.closest("[data-order-accept]");
        if (accept) {
            const id = accept.dataset.id;
            Api.post(`/orders/${id}/accept-offer`)
                .then(() => {
                    if (window.Toast) Toast.success("Offer accepted — the buyer now confirms address & payment");
                    this.loadOrders();
                })
                .catch((error) => {
                    if (window.Toast) Toast.fromResponse(error, "Could not accept the offer");
                });
            return;
        }

        const pay = event.target.closest("[data-order-pay]");
        if (pay) {
            this.openPaymentModal(pay.dataset.id);
            return;
        }

        const track = event.target.closest("[data-order-track]");
        if (track) {
            this.openTrackingModal(track.dataset.id);
            return;
        }

        const remove = event.target.closest("[data-order-remove]");
        if (remove) {
            const id = remove.dataset.id;
            if (!window.confirm(`Cancel and remove order #${id}? This cannot be undone.`)) return;
            Api.del(`/orders/admin/${id}`)
                .then(() => {
                    if (window.Toast) Toast.success("Order cancelled and removed");
                    this.loadOrders();
                })
                .catch((error) => {
                    if (window.Toast) Toast.fromResponse(error, "Could not remove the order");
                });
            return;
        }

        const button = event.target.closest("[data-order-action]");
        if (!button) return;

        const id = button.dataset.id;
        const status = button.dataset.status;
        if (!window.confirm(`Move order #${id} to ${status}?`)) return;

        const call = this.isAdmin
            ? Api.put(`/orders/admin/${id}/status`, { status })
            : Api.patch(`/orders/${id}/status`, { status });
        call
            .then(() => {
                if (window.Toast) Toast.success(`Order moved to ${status}`);
                this.loadOrders();
            })
            .catch((error) => {
                if (window.Toast) Toast.fromResponse(error, "Could not update order");
            });
    },

    orderById(orderId) {
        return this.orders.find((order) => order.id === orderId) || null;
    },

    openPaymentModal(orderId) {
        const order = this.orderById(orderId);
        if (!order) return;

        const modal = this.container.querySelector("#paymentModal");
        const idInput = this.container.querySelector("#paymentOrderId");
        const totalInput = this.container.querySelector("#paymentTotal");
        const summary = this.container.querySelector("#paymentModalSummary");
        const addressInput = this.container.querySelector("#paymentAddress");

        if (idInput) idInput.value = order.id;
        if (totalInput) totalInput.value = order.totalAmount || "0";
        if (summary) {
            summary.textContent = `${order.cropName || "Crop"} · ${this.number(order.agreedQuantity)} units · ৳${this.money(order.totalAmount)} due`;
        }
        if (addressInput) addressInput.value = order.deliveryAddress || "";
        this.clearPaymentSecrets();

        // Default to bKash when it is the live rail; otherwise leave the current method selected.
        const methodSelect = this.container.querySelector("#paymentMethod");
        const caps = this.paymentCapabilities;
        if (methodSelect && caps && caps.bkashConfigured) {
            methodSelect.value = "BKASH";
            const radio = this.container.querySelector('input[name="paymentMethodChoice"][value="BKASH"]');
            if (radio) radio.checked = true;
        }
        this.toggleTransactionField();

        if (modal) this.openModal(modal);
    },

    /** Radio pills drive the hidden select, so the rest of the code reads one source of truth. */
    syncPaymentMethod() {
        const select = this.container.querySelector("#paymentMethod");
        if (!select) return;
        const picked = this.container.querySelector('input[name="paymentMethodChoice"]:checked');
        if (picked) select.value = picked.value;
        this.toggleTransactionField();
    },

    /** Wallets are addressed by a local mobile number, cards by the number printed on them. */
    paymentMethodProfile(method) {
        const wallets = {
            BKASH: { label: "bKash number", pinLabel: "bKash PIN", numberPattern: /^01[3-9][0-9]{8}$/, numberHint: "Enter a valid bKash number, e.g. 01712345678", pinPattern: /^[0-9]{4,6}$/, pinHint: "Enter your 4-6 digit bKash PIN", pinPlaceholder: "4-6 digits" },
            NAGAD: { label: "Nagad number", pinLabel: "Nagad PIN", numberPattern: /^01[3-9][0-9]{8}$/, numberHint: "Enter a valid Nagad number, e.g. 01712345678", pinPattern: /^[0-9]{4,6}$/, pinHint: "Enter your 4-6 digit Nagad PIN", pinPlaceholder: "4-6 digits" },
            CARD: { label: "Card number", pinLabel: "Card CVV", numberPattern: /^[0-9]{13,19}$/, numberHint: "Enter a valid card number (13 to 19 digits)", pinPattern: /^[0-9]{3,6}$/, pinHint: "Enter the 3-6 digit CVV from your card", pinPlaceholder: "3-6 digits" },
            SSLCOMMERZ: { label: "Card number", pinLabel: "Card CVV", numberPattern: /^[0-9]{13,19}$/, numberHint: "Enter a valid card number (13 to 19 digits)", pinPattern: /^[0-9]{3,6}$/, pinHint: "Enter the 3-6 digit CVV from your card", pinPlaceholder: "3-6 digits" },
        };
        return wallets[method] || wallets.BKASH;
    },

    /**
     * Every rail without server credentials is paid in-app: the buyer types that rail's account
     * number and PIN and the order settles at once. bKash keeps its hosted checkout when the
     * server actually holds bKash credentials, because that is the only real-money path.
     */
    toggleTransactionField() {
        const methodSelect = this.container.querySelector("#paymentMethod");
        if (!methodSelect) return;
        const method = methodSelect.value;
        const bkashLive = method === "BKASH"
            && Boolean(this.paymentCapabilities && this.paymentCapabilities.bkashConfigured);
        const inApp = !bkashLive;

        const fields = this.container.querySelector("#paymentAccountFields");
        if (fields) fields.hidden = !inApp;

        if (inApp) {
            const profile = this.paymentMethodProfile(method);
            const numberLabel = this.container.querySelector("#paymentAccountNumberLabel");
            const pinLabel = this.container.querySelector("#paymentAccountPinLabel");
            const number = this.container.querySelector("#paymentAccountNumber");
            const pin = this.container.querySelector("#paymentAccountPin");
            if (numberLabel) numberLabel.textContent = profile.label;
            if (pinLabel) pinLabel.textContent = profile.pinLabel;
            if (number) {
                number.value = "";
                number.placeholder = method === "BKASH" || method === "NAGAD" ? "01XXXXXXXXX" : "4242 4242 4242 4242";
                number.required = true;
            }
            if (pin) {
                pin.placeholder = profile.pinPlaceholder;
                pin.required = true;
            }
        }

        const note = this.container.querySelector("#paymentRailNote");
        if (note) {
            note.textContent = bkashLive
                ? "bKash opens its own secure page where you enter your number and PIN."
                : "Demo payment: no real money is charged and the PIN is not stored.";
        }
    },

    /** The PIN only lives in the form while it is open; it is never kept around. */
    clearPaymentSecrets() {
        const pin = this.container.querySelector("#paymentAccountPin");
        if (pin) pin.value = "";
        const number = this.container.querySelector("#paymentAccountNumber");
        if (number) number.value = "";
    },

    async handlePaymentSubmit(event) {
        event.preventDefault();

        const orderId = this.value("#paymentOrderId");
        const shippingAddress = this.value("#paymentAddress").trim();
        const paymentMethod = this.value("#paymentMethod");

        if (!shippingAddress) {
            if (window.Toast) Toast.error("Shipping address is required");
            return;
        }

        const submit = this.container.querySelector("#paymentForm button[type=submit]");
        const busyLabel = (button) => {
            if (!button) return;
            button.disabled = true;
            button.dataset.label = button.textContent;
            button.textContent = "Paying…";
        };
        const releaseButton = (button) => {
            if (button) {
                button.disabled = false;
                button.textContent = button.dataset.label || "Pay & Confirm";
            }
        };

        // bKash has two rails: the hosted bKash page when the server holds credentials, and the
        // in-app number + PIN rail otherwise. Every other method is in-app only.
        const bkashLive = paymentMethod === "BKASH"
            && Boolean(this.paymentCapabilities && this.paymentCapabilities.bkashConfigured);
        if (bkashLive) {
            busyLabel(submit);
            try {
                const res = await Api.post(`/orders/${orderId}/payment/bkash`, { shippingAddress });
                const checkout = res.data || {};
                this.closeModal(this.container.querySelector("#paymentModal"));
                if (window.Toast) Toast.success("bKash checkout ready — enter your number and PIN");
                if (checkout.bkashUrl) {
                    window.open(checkout.bkashUrl, "_blank", "noopener");
                }
                this.loadOrders();
            } catch (error) {
                if (window.Toast) Toast.fromResponse(error, "Could not start the bKash payment");
            } finally {
                releaseButton(submit);
            }
            return;
        }

        const profile = this.paymentMethodProfile(paymentMethod);
        const number = this.value("#paymentAccountNumber").trim();
        const pin = this.value("#paymentAccountPin").trim();
        const digits = number.replace(/[\s-]/g, "");
        if (!profile.numberPattern.test(digits)) {
            if (window.Toast) Toast.error(profile.numberHint);
            return;
        }
        if (!profile.pinPattern.test(pin)) {
            if (window.Toast) Toast.error(profile.pinHint);
            return;
        }

        busyLabel(submit);
        try {
            await Api.post(`/orders/${orderId}/payment/simulate`, {
                paymentMethod,
                number: digits,
                pin,
                shippingAddress,
            });
            this.clearPaymentSecrets();
            this.closeModal(this.container.querySelector("#paymentModal"));
            if (window.Toast) Toast.success(`${profile.label.replace(" number", "")} payment done — the amount is held in escrow`);
            this.loadOrders();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not complete the payment");
        } finally {
            releaseButton(submit);
        }
    },

    openTrackingModal(orderId) {
        const order = this.orderById(orderId);
        if (!order) return;

        const modal = this.container.querySelector("#trackingModal");
        const idInput = this.container.querySelector("#trackingOrderId");
        const summary = this.container.querySelector("#trackingModalSummary");
        const latInput = this.container.querySelector("#trackingLatitude");
        const lngInput = this.container.querySelector("#trackingLongitude");

        if (idInput) idInput.value = order.id;
        if (summary) {
            summary.textContent = order.cropName
                ? `${order.cropName} · delivering to ${order.deliveryAddress || "—"}`
                : `Order #${order.id}`;
        }
        if (latInput) latInput.value = order.driverLatitude != null ? order.driverLatitude : "";
        if (lngInput) lngInput.value = order.driverLongitude != null ? order.driverLongitude : "";

        if (modal) this.openModal(modal);
    },

    async handleTrackingSubmit(event) {
        event.preventDefault();

        const orderId = this.value("#trackingOrderId");
        const latitude = Number(this.value("#trackingLatitude"));
        const longitude = Number(this.value("#trackingLongitude"));

        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
            if (window.Toast) Toast.error("Enter a valid latitude and longitude");
            return;
        }

        try {
            await Api.put(`/orders/${orderId}/tracking`, null, { latitude, longitude });
            if (window.Toast) Toast.success("Live position updated");
            this.closeModal(this.container.querySelector("#trackingModal"));
            this.loadOrders();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not update the position");
        }
    },

    handleOfferAction(event) {
        const button = event.target.closest("[data-offer-action]");
        if (!button) return;

        const id = button.dataset.id;
        const action = button.dataset.offerAction;

        if (action === "accept") {
            this.openAcceptModal(id);
            return;
        }

        if (action === "counter") {
            this.openCounterModal(id);
            return;
        }

        const labels = {
            withdraw: "Withdraw this offer?",
            reject: "Reject this offer?",
        };
        if (!window.confirm(labels[action] || "Continue?")) return;

        Api.post(`/offers/${id}/${action}`)
            .then(() => {
                if (window.Toast) Toast.success(`Offer ${action === "reject" ? "rejected" : "withdrawn"}`);
                this.loadOffers();
            })
            .catch((error) => {
                if (window.Toast) Toast.fromResponse(error, `Could not ${action} offer`);
            });
    },

    openAcceptModal(offerId) {
        const offer = this.offers.find((o) => o.id === offerId);
        if (!offer) return;

        const modal = this.container.querySelector("#acceptModal");
        const idInput = this.container.querySelector("#acceptOfferId");
        const summary = this.container.querySelector("#acceptModalSummary");
        const listing = this.listingCache[offer.listingId] || {};

        if (idInput) idInput.value = offer.id;
        if (summary) {
            summary.textContent = `${listing.cropName || "Crop"} · ${this.number(offer.offeredQuantity)} units @ ৳${this.money(offer.offeredPrice)}/unit`;
        }

        const addressDisplay = this.container.querySelector("#acceptDeliveryAddress");
        if (addressDisplay) addressDisplay.textContent = offer.deliveryAddress || "—";

        if (modal) this.openModal(modal);
    },

    openCounterModal(offerId) {
        const offer = this.offers.find((o) => o.id === offerId);
        if (!offer) return;

        const modal = this.container.querySelector("#counterModal");
        const idInput = this.container.querySelector("#counterOfferId");
        const summary = this.container.querySelector("#counterModalListing");
        const listing = this.listingCache[offer.listingId] || {};

        if (idInput) idInput.value = offer.id;
        if (summary) {
            summary.textContent = `${listing.cropName || "Crop"} · current offer ${this.number(offer.offeredQuantity)} units @ ৳${this.money(offer.offeredPrice)}/unit`;
        }

        const quantity = this.container.querySelector("#counterQuantity");
        const price = this.container.querySelector("#counterPrice");
        if (quantity) quantity.value = offer.offeredQuantity;
        if (price) price.value = offer.offeredPrice;

        if (modal) this.openModal(modal);
    },

    async handleAcceptSubmit(event) {
        const form = this.container.querySelector("#acceptForm");
        if (!form) return;
        event.preventDefault();

        const offerId = this.value("#acceptOfferId");

        try {
            const created = await Api.post("/orders", { offerId });
            const order = created && created.data ? created.data : null;
            const orderId = order ? order.id : null;

            // A farmer accepting the deal moves the order straight to OFFER_ACCEPTED, which is
            // what tells the buyer to confirm address + payment. If that call fails the order
            // stays PENDING and the "Accept offer" button on the order row retries it.
            if (this.role === "FARMER" && orderId) {
                try {
                    await Api.post(`/orders/${orderId}/accept-offer`);
                    if (window.Toast) Toast.success("Offer accepted — the buyer now confirms address & payment");
                } catch (acceptError) {
                    if (window.Toast) Toast.fromResponse(acceptError, "Order created, but the offer could not be accepted yet");
                }
            } else if (window.Toast) {
                Toast.success("Order placed");
            }

            this.closeModal(this.container.querySelector("#acceptModal"));
            this.load();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not place order");
        }
    },

    async handleCounterSubmit(event) {
        const form = this.container.querySelector("#counterForm");
        if (!form) return;
        event.preventDefault();

        const offerId = this.value("#counterOfferId");
        const body = {
            offeredQuantity: Number(this.value("#counterQuantity")),
            offeredPrice: Number(this.value("#counterPrice")),
        };

        if (!body.offeredQuantity || body.offeredQuantity <= 0 || !body.offeredPrice || body.offeredPrice <= 0) {
            if (window.Toast) Toast.error("Enter valid quantity and price");
            return;
        }

        try {
            await Api.post(`/offers/${offerId}/counter`, body);
            if (window.Toast) Toast.success("Counter offer sent");
            this.closeModal(this.container.querySelector("#counterModal"));
            this.loadOffers();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not send counter offer");
        }
    },

    /** Headline strip above the commitments table. */
    renderOrderStats() {
        const wrap = this.container.querySelector("#orderStatsStrip");
        if (!wrap) return;

        const stats = this.orderStats;
        if (!stats) {
            wrap.innerHTML = "";
            wrap.hidden = true;
            return;
        }
        wrap.hidden = false;

        const isFarmer = this.role === "FARMER";
        const cards = [
            {
                label: "Total orders",
                value: this.number(stats.total || 0),
                hint: "all time",
                tone: "brand",
            },
            {
                label: isFarmer ? "Offers to accept" : "Awaiting payment",
                value: this.number(stats.needsAction || 0),
                hint: isFarmer ? "needs your decision" : "address + payment due",
                tone: (stats.needsAction || 0) > 0 ? "warn" : "muted",
            },
            {
                label: "In escrow",
                value: this.number(stats.inEscrow || 0),
                hint: "funds held safely",
                tone: "good",
            },
            {
                label: "On the road",
                value: this.number(stats.inTransit || 0),
                hint: "paid, not delivered",
                tone: "info",
            },
            {
                label: "Delivered",
                value: this.number(stats.delivered || 0),
                hint: "escrow released",
                tone: "good",
            },
        ];

        wrap.innerHTML = cards
            .map(
                (card) => `
            <div class="stat-card od-stat od-stat-${this.esc(card.tone)}">
                <div class="stat-card-head">
                    <span class="stat-label">${this.esc(card.label)}</span>
                </div>
                <div class="stat-value">${this.esc(card.value)}</div>
                <span class="od-stat-hint">${this.esc(card.hint)}</span>
            </div>`
            )
            .join("");
    },

    /** Reflects which rails are usable so the buyer never picks a dead payment option. */
    renderPaymentCapabilities() {
        const note = this.container.querySelector("#paymentRailNote");
        const select = this.container.querySelector("#paymentMethod");
        if (!note || !select) return;

        const caps = this.paymentCapabilities;
        const bkashLive = Boolean(caps && caps.bkashConfigured);
        const sslLive = Boolean(caps && caps.sslCommerzConfigured);

        // Every rail works in-app, so no option is ever blocked. bKash only switches to the
        // hosted page when the server actually holds bKash credentials.
        ["BKASH", "NAGAD", "CARD", "SSLCOMMERZ"].forEach((method) => {
            const option = select.querySelector(`option[value="${method}"]`);
            if (option) option.disabled = false;
            const radio = this.container.querySelector(`input[name="paymentMethodChoice"][value="${method}"]`);
            const label = radio && radio.parentElement ? radio.parentElement.querySelector("span") : null;
            if (!radio || !label) return;
            radio.disabled = false;
            if (method === "BKASH") {
                label.textContent = bkashLive ? "bKash" : "bKash";
                label.title = bkashLive
                    ? "Opens the official bKash page, where you enter your number and PIN"
                    : "Enter your bKash number and PIN here to settle this order";
            } else if (method === "SSLCOMMERZ") {
                label.textContent = "SSLCommerz sandbox";
                label.title = sslLive
                    ? "Validated against the SSLCommerz sandbox"
                    : "Enter your card number and CVV here to settle this order";
            } else {
                const name = method === "NAGAD" ? "Nagad" : "Card";
                label.textContent = name;
                label.title = method === "CARD"
                    ? "Enter your card number and CVV here to settle this order"
                    : "Enter your Nagad number and PIN here to settle this order";
            }
        });

        this.toggleTransactionField();
    },

    renderOrders(orders) {
        const wrap = this.container.querySelector("#ordersTableWrap");
        const count = this.container.querySelector("#ordersCount");
        if (!wrap) return;

        // Settled orders are billed, so they belong to Invoices. The server already filters them
        // out; this keeps them off the page even against a cached or stale response.
        const commitments = orders.filter((order) => !this.SETTLED_STAGES.includes(order.orderStatus));

        if (count) count.textContent = String(commitments.length);
        if (!commitments.length) {
            wrap.innerHTML = '<div class="empty-state">No order commitments.</div>';
            return;
        }

        const rows = commitments
            .map((order) => {
                const allowed = this.allowedTransitions(order);
                // The pay / accept actions own their stages, so those never appear as raw
                // status buttons. Admins keep the manual override for everything else.
                const workflowButtons = allowed
                    .filter((next) => this.isAdmin || !["OFFER_ACCEPTED", "PAYMENT_PENDING"].includes(next))
                    .map(
                        (next) =>
                            `<button type="button" class="btn btn-sm btn-${next === "CANCELLED" ? "danger" : "primary"}" data-order-action data-id="${this.esc(order.id)}" data-status="${next}">${this.esc(next.replaceAll("_", " "))}</button>`
                    )
                    .join("");

                const acceptButton =
                    this.role === "FARMER" && order.orderStatus === "PENDING"
                        ? `<button type="button" class="btn btn-sm btn-primary" data-order-accept data-id="${this.esc(order.id)}">Accept offer</button>`
                        : "";
                const payButton =
                    this.role === "BUYER" && this.PAYABLE_STAGES.includes(order.orderStatus)
                        ? `<button type="button" class="btn btn-sm btn-primary" data-order-pay data-id="${this.esc(order.id)}">Pay &amp; confirm</button>`
                        : "";
                const trackButton =
                    (this.role === "FARMER" || this.isAdmin)
                        && this.LIVE_TRACKING_STAGES.includes(order.orderStatus)
                        && !this.SETTLED_STAGES.includes(order.orderStatus)
                        ? `<button type="button" class="btn btn-sm btn-secondary" data-order-track data-id="${this.esc(order.id)}">Update position</button>`
                        : "";
                const removeButton = this.isAdmin
                    ? `<button type="button" class="btn btn-sm btn-danger" data-order-remove data-id="${this.esc(order.id)}">Remove</button>`
                    : "";

                const actions = [acceptButton, payButton, trackButton, workflowButtons, removeButton]
                    .filter(Boolean)
                    .join("");
                const chatButton = this.isAdmin
                    ? ""
                    : `<button type="button" class="btn btn-sm btn-secondary" data-order-chat data-id="${this.esc(order.id)}">💬 Chat</button>`;
                const paymentCell = this.paymentCellHtml(order);
                const trackingCell = this.trackingCellHtml(order);

                return `
                    <tr>
                        <td class="font-mono" style="font-size: 0.8rem;">#${this.esc(order.id || "")}</td>
                        <td class="font-semibold">${this.esc(order.cropName || "—")}</td>
                        <td>${this.number(order.agreedQuantity)}</td>
                        <td>৳${this.money(order.agreedPricePerUnit)}</td>
                        <td class="font-semibold">৳${this.money(order.totalAmount)}</td>
                        <td style="max-width: 180px; white-space: normal;" title="${this.esc(order.deliveryAddress || "")}">${this.esc(order.deliveryAddress || "—")}</td>
                        <td>${paymentCell}</td>
                        <td>${trackingCell}</td>
                        <td>${this.date(order.createdAt)}</td>
                        <td>${this.statusBadge(order.orderStatus)}</td>
                        <td>
                            <div class="flex gap-2">${chatButton}${actions || '<span class="text-muted">—</span>'}
                            </div>
                        </td>
                    </tr>`;
            })
            .join("");

        wrap.innerHTML = `
            <div class="table-scroll">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Order</th>
                            <th>Crop</th>
                            <th>Qty</th>
                            <th>Unit Price</th>
                            <th>Total</th>
                            <th>Delivery Address</th>
                            <th>Payment</th>
                            <th>Live Tracking</th>
                            <th>Date</th>
                            <th>Status</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>`;
    },

    /** Payment badge plus the method, escrow state and gateway reference behind it. */
    paymentCellHtml(order) {
        const badge = this.statusBadge(order.paymentStatus);
        const method = order.paymentMethodLabel || order.paymentMethod;
        if (!method && !order.transactionId) {
            return badge;
        }
        const escrow = order.escrowStatusLabel || order.escrowStatus;
        const lines = [];
        if (method) lines.push(this.esc(method));
        if (escrow && escrow !== "Unpaid") lines.push(this.esc(escrow));
        if (order.transactionId) lines.push(`<span class="font-mono">#${this.esc(order.transactionId)}</span>`);
        if (order.paidAmount != null) lines.push(`৳${this.money(order.paidAmount)} paid`);
        if (order.paymentGateway && order.paymentVerified === false) {
            lines.push('<span class="text-muted" style="font-size: 0.75rem;">sandbox (unverified)</span>');
        }
        return `${badge}<div class="text-muted" style="font-size: 0.75rem; line-height: 1.35;">${lines.join("<br>")}</div>`;
    },

    /** Stepper plus the live driver coordinates once tracking is open. */
    trackingCellHtml(order) {
        const stepper = this.trackHtml(order.orderStatus);
        if (!order.trackingActive) {
            return stepper;
        }
        if (order.driverLatitude == null || order.driverLongitude == null) {
            return `${stepper}<div class="text-muted" style="font-size: 0.75rem;">awaiting position</div>`;
        }
        const coords = `${Number(order.driverLatitude).toFixed(4)}, ${Number(order.driverLongitude).toFixed(4)}`;
        const updated = order.trackingUpdatedAt ? this.date(order.trackingUpdatedAt) : "";
        return `${stepper}<div class="font-mono" style="font-size: 0.75rem;">${this.esc(coords)}</div>
                <div class="text-muted" style="font-size: 0.75rem;">${this.esc(updated)}</div>`;
    },

    /** Small horizontal stepper: highlights every stage up to the order's current status. */
    trackHtml(status) {
        if (status === "CANCELLED" || status === "DISPUTED") {
            return `<span class="badge badge-error" style="white-space: nowrap;">${this.esc(status)}</span>`;
        }
        const currentIndex = this.TRACK_STAGES.indexOf(status);
        const dots = this.TRACK_STAGES
            .map((stage, index) => {
                const reached = currentIndex >= 0 && index <= currentIndex;
                const color = reached ? "var(--color-success, #16a34a)" : "var(--color-border, #d1d5db)";
                const dot = `<span title="${this.esc(stage.replaceAll("_", " "))}" style="display:inline-block; width:10px; height:10px; border-radius:50%; background:${color};"></span>`;
                const connector = index < this.TRACK_STAGES.length - 1
                    ? `<span style="display:inline-block; width:16px; height:2px; background:${reached && currentIndex > index ? "var(--color-success, #16a34a)" : "var(--color-border, #d1d5db)"};"></span>`
                    : "";
                return dot + connector;
            })
            .join("");
        return `<div class="flex items-center" style="gap:2px;" title="${this.esc((status || "").replaceAll("_", " "))}">${dots}</div>`;
    },

    renderOrdersPagination() {
        const info = this.container.querySelector("#ordersPageInfo");
        const prevBtn = this.container.querySelector("#ordersPrevPage");
        const nextBtn = this.container.querySelector("#ordersNextPage");

        if (info) {
            const totalPages = Math.max(this.ordersTotalPages, 1);
            const label = this.ordersTotalPages > 1
                ? `Page ${this.ordersPage + 1} of ${totalPages} · ${this.orders.length} shown`
                : `${this.orders.length} order${this.orders.length === 1 ? "" : "s"}`;
            info.textContent = label;
        }
        if (prevBtn) prevBtn.disabled = !this.ordersHasPrevious;
        if (nextBtn) nextBtn.disabled = !this.ordersHasNext;
    },

    renderOffers(offers) {
        const wrap = this.container.querySelector("#offerList");
        const count = this.container.querySelector("#offersCount");
        if (!wrap) return;

        if (count) {
            count.textContent = this.offersTotalPages > 1
                ? `${this.offersTotal} total`
                : String(this.offersTotal);
        }
        if (!offers.length) {
            wrap.innerHTML = this.offersPage > 0
                ? '<div class="empty-state">No more negotiations on this page.</div>'
                : '<div class="empty-state">No offers to review yet.</div>';
            return;
        }

        const rows = offers
            .map((offer) => {
                const listing = this.listingCache[offer.listingId] || {};
                const cropName = listing.cropName || "—";
                const offered = this.number(offer.offeredQuantity);
                const price = this.money(offer.offeredPrice);
                const pending = offer.status === "OFFERED";

                let actions = '<span class="text-muted">—</span>';
                if (pending) {
                    if (this.role === "FARMER") {
                        actions = `
                            <button type="button" class="btn btn-sm btn-primary" data-offer-action="accept" data-id="${this.esc(offer.id)}">Accept</button>
                            <button type="button" class="btn btn-sm btn-secondary" data-offer-action="counter" data-id="${this.esc(offer.id)}">Counter</button>
                            <button type="button" class="btn btn-sm btn-danger" data-offer-action="reject" data-id="${this.esc(offer.id)}">Reject</button>`;
                    } else if (this.role === "BUYER") {
                        actions = `
                            <button type="button" class="btn btn-sm btn-danger" data-offer-action="withdraw" data-id="${this.esc(offer.id)}">Withdraw</button>`;
                    }
                }

                return `
                    <div class="od-offer">
                        <div class="od-offer-main">
                            <div class="od-offer-icon" aria-hidden="true">🌾</div>
                            <div class="min-w-0">
                                <p class="od-offer-title">${this.esc(cropName)}</p>
                                <p class="od-offer-meta">
                                    ${this.esc(offered)} units · <strong>৳${this.esc(price)}</strong>/unit
                                    · <span class="font-mono">#${this.esc(offer.id || "")}</span>
                                </p>
                            </div>
                        </div>
                        <div class="od-offer-side">
                            ${this.statusBadge(offer.status)}
                            <div class="flex gap-2">${actions}</div>
                        </div>
                    </div>`;
            })
            .join("");

        wrap.innerHTML = rows;
    },

    /** Six negotiations per page, with a real page indicator. */
    renderOffersPagination() {
        const info = this.container.querySelector("#offersPageInfo");
        const prevBtn = this.container.querySelector("#offersPrevPage");
        const nextBtn = this.container.querySelector("#offersNextPage");

        if (info) {
            const totalPages = Math.max(this.offersTotalPages, 1);
            const from = this.offersTotal === 0 ? 0 : this.offersPage * this.offersPageSize + 1;
            const to = Math.min(from + this.offers.length - 1, this.offersTotal);
            info.textContent = this.offersTotal === 0
                ? "No negotiations"
                : `Showing ${from}–${to} of ${this.offersTotal} · page ${this.offersPage + 1} of ${totalPages}`;
        }
        if (prevBtn) prevBtn.disabled = !this.offersHasPrevious;
        if (nextBtn) nextBtn.disabled = !this.offersHasNext;
    },

    async ensureListing(listingId) {
        if (!listingId || this.listingCache[listingId]) return;
        try {
            const res = await Api.get(`/produce/${listingId}`);
            this.listingCache[listingId] = res.data || {};
        } catch (error) {
            this.listingCache[listingId] = {};
        }
    },

    statusBadge(status) {
        const cls = {
            PENDING: "badge-info",
            OFFER_ACCEPTED: "badge-info",
            PAYMENT_PENDING: "badge-warning",
            PAID_CONFIRMED: "badge-success",
            ESCROW_HELD: "badge-success",
            PROCESSING: "badge-info",
            CONFIRMED: "badge-success",
            IN_TRANSIT: "badge-warning",
            DELIVERED: "badge-success",
            CANCELLED: "badge-error",
            DISPUTED: "badge-warning",
            OFFERED: "badge-info",
            ACCEPTED: "badge-success",
            REJECTED: "badge-error",
            EXPIRED: "badge-warning",
            PAID: "badge-success",
            UNPAID: "badge-warning",
            VOID: "badge-error",
        };
        return `<span class="badge ${cls[status] || "badge-info"}">${this.esc(status || "")}</span>`;
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

    number(value) {
        return Number(value || 0).toLocaleString("en-IN");
    },

    money(value) {
        return Number(value || 0).toLocaleString("en-IN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        });
    },

    date(iso) {
        return iso
            ? new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })
            : "—";
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