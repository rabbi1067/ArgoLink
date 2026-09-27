window.Pages = window.Pages || {};

window.Pages.invoices = {
    container: null,
    invoices: [],
    role: "BUYER",
    page: 0,
    size: 20,
    pageData: null,

    init(container) {
        this.container = container;
        const user = window.Api && Api.getUser ? Api.getUser() : null;
        this.role = user && user.role ? user.role : "BUYER";
        this.invoices = [];
        this.page = 0;
        this.pageData = null;
        this.bind();
        this.applyAdminChrome();
        this.load();
    },

    isAdmin() {
        return this.role === "ADMIN" || this.role === "SUPER_ADMIN";
    },

    /** Admins see every invoice on the platform, not just their own. */
    applyAdminChrome() {
        const admin = this.isAdmin();
        this.container.querySelectorAll("[data-admin-only]").forEach((el) => {
            el.hidden = !admin;
        });
        const context = this.container.querySelector("#invoiceContext");
        if (context) {
            context.textContent = admin
                ? "Every invoice on the platform, newest first"
                : "Invoices for your orders, newest first";
        }
    },

    bind() {
        const list = this.container.querySelector("#invoiceItems");
        if (list) {
            list.addEventListener("click", (e) => {
                const del = e.target.closest("[data-invoice-delete]");
                if (del) {
                    e.stopPropagation();
                    this.deleteInvoice(del.dataset.invoiceDelete);
                    return;
                }
                const row = e.target.closest("[data-invoice-row]");
                if (!row) return;
                const inv = this.invoices.find((i) => i.id === row.dataset.id);
                if (inv) this.showInvoice(inv);
            });
        }

        const prev = this.container.querySelector("#invoicesPrevPage");
        if (prev) {
            prev.addEventListener("click", () => {
                if (this.page > 0) {
                    this.page -= 1;
                    this.load();
                }
            });
        }
        const next = this.container.querySelector("#invoicesNextPage");
        if (next) {
            next.addEventListener("click", () => {
                if (this.pageData && this.pageData.hasNext) {
                    this.page += 1;
                    this.load();
                }
            });
        }

        const print = this.container.querySelector("#invoiceDownloadBtn");
        if (print) print.addEventListener("click", () => this.print());

        const printInner = this.container.querySelector("#printInvoiceBtn");
        if (printInner) printInner.addEventListener("click", () => this.print());
    },

    async load() {
        const list = this.container.querySelector("#invoiceItems");
        const count = this.container.querySelector("#invoiceCount");
        const dlBtn = this.container.querySelector("#invoiceDownloadBtn");
        const info = this.container.querySelector("#invoicesPageInfo");
        const prev = this.container.querySelector("#invoicesPrevPage");
        const next = this.container.querySelector("#invoicesNextPage");
        if (list) list.innerHTML = '<div class="empty-state">Loading invoices…</div>';

        try {
            if (this.isAdmin()) {
                const res = await Api.get(`/invoices/admin/all?page=${this.page}&size=${this.size}`);
                this.pageData = res.data;
                this.invoices = (res.data && res.data.content) || [];
                if (info) {
                    const from = this.invoices.length ? this.page * this.size + 1 : 0;
                    const to = this.page * this.size + this.invoices.length;
                    info.textContent = `Showing ${from}–${to} of ${this.pageData.totalElements} · page ${this.pageData.page + 1} of ${Math.max(this.pageData.totalPages, 1)}`;
                }
                if (prev) prev.disabled = !this.pageData.hasPrevious;
                if (next) next.disabled = !this.pageData.hasNext;
            } else {
                const res = await Api.get("/invoices/my");
                this.pageData = null;
                this.invoices = res.data || [];
            }

            if (count) count.textContent = String(this.isAdmin() && this.pageData ? this.pageData.totalElements : this.invoices.length);
            if (dlBtn) dlBtn.disabled = this.invoices.length === 0;
            this.render(this.invoices);
        } catch (error) {
            if (list) list.innerHTML = `<div class="empty-state">Could not load invoices${error && error.message ? ": " + this.esc(error.message) : ""}</div>`;
        }
    },

    async deleteInvoice(invoiceId) {
        if (!invoiceId) return;
        if (!window.confirm("Delete this invoice permanently? This cannot be undone.")) return;
        try {
            await Api.del(`/invoices/admin/${invoiceId}`);
            if (window.Toast) Toast.success("Invoice deleted");
            this.invoices = this.invoices.filter((i) => i.id !== invoiceId);
            const detail = this.container.querySelector("#invoiceDetail");
            if (detail) detail.classList.add("hidden");
            this.load();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not delete the invoice");
        }
    },

    render(invoices) {
        const list = this.container.querySelector("#invoiceItems");
        if (!list) return;
        if (!invoices.length) {
            list.innerHTML = this.isAdmin()
                ? '<div class="empty-state">No invoices on the platform yet.</div>'
                : '<div class="empty-state">No invoices yet. Complete an order to generate one.</div>';
            return;
        }
        const tpl = this.container.querySelector("#invoiceRowTpl");
        list.innerHTML = invoices
            .map((inv) => {
                const node = tpl ? tpl.content.firstElementChild.cloneNode(true) : document.createElement("div");
                node.setAttribute("data-invoice-row", "");
                node.dataset.id = inv.id;
                node.querySelector(".invoice-number").textContent = this.esc(inv.invoiceNumber || inv.id || "Invoice");
                node.querySelector(".invoice-meta").textContent = `${inv.cropName || "Produce"} · ${inv.buyerName || ""} ↔ ${inv.farmerName || ""} · ${this.date(inv.createdAt)}`;
                node.querySelector(".invoice-total").textContent = `৳${this.money(inv.totalBt)}`;
                const status = node.querySelector(".invoice-status");
                status.textContent = inv.paymentStatus || "UNPAID";
                status.classList.add("badge-" + this.statusClass(inv.paymentStatus));
                const del = node.querySelector("[data-invoice-delete]");
                if (del) {
                    del.dataset.invoiceDelete = inv.id;
                    del.hidden = !this.isAdmin();
                }
                return node.outerHTML;
            })
            .join("");
    },

    showInvoice(inv) {
        const detail = this.container.querySelector("#invoiceDetail");
        const numberEl = this.container.querySelector("#detailInvoiceNumber");
        const body = this.container.querySelector("#detailInvoiceBody");
        if (!detail || !body) return;

        if (numberEl) numberEl.textContent = inv.invoiceNumber || inv.id;
        body.innerHTML = this.detailHtml(inv);
        detail.classList.remove("hidden");

        const area = this.container.querySelector("#printArea");
        if (area) area.innerHTML = this.printHtml(inv);
    },

    detailHtml(inv) {
        return `
            <div class="flex flex-wrap gap-6 mb-4">
                <div class="min-w-0">
                    <p class="text-muted mb-0" style="font-size: 0.75rem;">BILL TO</p>
                    <p class="font-semibold mb-0">${this.esc(inv.buyerName || "—")}</p>
                    <p class="text-muted mb-0" style="font-size: 0.8rem;">${this.esc(inv.deliveryAddress || "")}</p>
                </div>
                <div class="min-w-0">
                    <p class="text-muted mb-0" style="font-size: 0.75rem;">BILL FROM (FARMER)</p>
                    <p class="font-semibold mb-0">${this.esc(inv.farmerName || "—")}</p>
                </div>
                <div class="min-w-0 ml-auto text-right">
                    <p class="text-muted mb-0" style="font-size: 0.75rem;">STATUS</p>
                    <span class="badge badge-${this.statusClass(inv.paymentStatus)}">${this.esc(inv.paymentStatus || "UNPAID")}</span>
                    <p class="text-muted mb-0 mt-2" style="font-size: 0.75rem;">Due by ${this.date(inv.dueDate)}</p>
                </div>
            </div>

            <div class="table-scroll">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Item</th>
                            <th class="text-right">Qty</th>
                            <th class="text-right">Unit Price</th>
                            <th class="text-right">Amount</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>${this.esc(inv.cropName || "Produce")}</td>
                            <td class="text-right">${this.number(inv.quantity)}</td>
                            <td class="text-right">৳${this.money(inv.unitPriceBt)}</td>
                            <td class="text-right font-semibold">৳${this.money(inv.subtotalBt)}</td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <div class="flex justify-end mt-4">
                <div style="min-width: 16rem;">
                    ${this.summaryLine("Subtotal", inv.subtotalBt)}
                    ${this.summaryLine("Delivery charge", inv.deliveryChargeBt)}
                    ${this.summaryLine("Tax", inv.taxBt)}
                    ${this.summaryLine("Discount", inv.discountBt, true)}
                    <div class="flex items-center justify-between py-1" style="border-top: 2px solid var(--color-border);">
                        <span class="font-semibold">Total (BDT)</span>
                        <span class="font-semibold">৳${this.money(inv.totalBt)}</span>
                    </div>
                </div>
            </div>

            <p class="text-muted mb-0 mt-4" style="font-size: 0.75rem;">
                Recalculated from order #${this.esc(inv.orderId || "")} at ${this.time(inv.updatedAt)}.
                Advertisement: ${this.esc((inv.qrCodeUrl ? "QR-verified" : "AgroLink verified"))} invoice — no physical document required.
            </p>`;
    },

    summaryLine(label, value, negate) {
        const amount = this.money(value);
        return `
            <div class="flex items-center justify-between py-1">
                <span class="text-muted">${this.esc(label)}</span>
                <span>${negate ? "−" : ""}${amount}</span>
            </div>`;
    },

    printHtml(inv) {
        return `
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 720px; margin: 0 auto;">
                <div style="border-bottom: 2px solid #111; padding-bottom: 12px; margin-bottom: 16px; display: flex; justify-content: space-between; align-items: flex-end;">
                    <div>
                        <h1 style="font-size: 22px; margin: 0;">AgroLink</h1>
                        <p style="margin: 2px 0 0; font-size: 12px; color: #555;">B2B Agro-Supply Chain Platform</p>
                    </div>
                    <div style="text-align: right;">
                        <p style="margin: 0; font-size: 14px;"><strong>${this.esc(inv.invoiceNumber || inv.id)}</strong></p>
                        <p style="margin: 2px 0 0; font-size: 12px; color: #555;">INVOICE</p>
                    </div>
                </div>

                <div style="display: flex; justify-content: space-between; gap: 24px; margin-bottom: 20px;">
                    <div>
                        <p style="margin: 0; font-size: 11px; text-transform: uppercase; color: #777;">Bill To</p>
                        <p style="margin: 2px 0 0;"><strong>${this.esc(inv.buyerName || "")}</strong></p>
                        <p style="margin: 0; font-size: 12px;">${this.esc(inv.deliveryAddress || "")}</p>
                    </div>
                    <div>
                        <p style="margin: 0; font-size: 11px; text-transform: uppercase; color: #777;">Bill From / Farmer</p>
                        <p style="margin: 2px 0 0;"><strong>${this.esc(inv.farmerName || "")}</strong></p>
                    </div>
                    <div style="text-align: right;">
                        <p style="margin: 0; font-size: 11px; text-transform: uppercase; color: #777;">Status / Due</p>
                        <p style="margin: 2px 0 0;"><strong>${this.esc(inv.paymentStatus || "UNPAID")}</strong></p>
                        <p style="margin: 0; font-size: 12px;">Due ${this.date(inv.dueDate)}</p>
                    </div>
                </div>

                <table style="width: 100%; border-collapse: collapse; margin-bottom: 20px; font-size: 13px;">
                    <thead>
                        <tr style="border-bottom: 1px solid #111; text-align: left;">
                            <th style="padding: 6px 4px;">Item</th>
                            <th style="padding: 6px 4px; text-align: right;">Qty</th>
                            <th style="padding: 6px 4px; text-align: right;">Unit Price</th>
                            <th style="padding: 6px 4px; text-align: right;">Amount</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td style="padding: 8px 4px;">${this.esc(inv.cropName || "Produce")}</td>
                            <td style="padding: 8px 4px; text-align: right;">${this.number(inv.quantity)}</td>
                            <td style="padding: 8px 4px; text-align: right;">৳${this.money(inv.unitPriceBt)}</td>
                            <td style="padding: 8px 4px; text-align: right;">৳${this.money(inv.subtotalBt)}</td>
                        </tr>
                    </tbody>
                </table>

                <div style="margin-left: auto; width: 260px; font-size: 13px;">
                    ${this.printLine("Subtotal", inv.subtotalBt)}
                    ${this.printLine("Delivery charge", inv.deliveryChargeBt)}
                    ${this.printLine("Tax", inv.taxBt)}
                    ${this.printLine("Discount", inv.discountBt)}
                    <div style="display: flex; justify-content: space-between; border-top: 1px solid #111; padding-top: 6px; margin-top: 6px;">
                        <strong>Total (BDT)</strong>
                        <strong>৳${this.money(inv.totalBt)}</strong>
                    </div>
                </div>

                <div style="margin-top: 28px; padding-top: 12px; border-top: 1px dashed #999; font-size: 11px; color: #777;">
                    Invoice #<span style="font-family: monospace;">${this.esc(inv.invoiceNumber || inv.id)}</span> · Order #<span style="font-family: monospace;">${this.esc(inv.orderId || "")}</span> ·
                    Generated ${this.date(inv.createdAt)} · Updated ${this.time(inv.updatedAt)} ·
                    ${this.esc(inv.qrCodeUrl ? "QR-verifiable" : "AgroLink verified")} digital invoice — ${this.esc(inv.verificationSummary || "valid against the order in the marketplace")}.
                </div>
            </div>`;
    },

    printLine(label, value) {
        return `
            <div style="display: flex; justify-content: space-between; padding: 1px 0;">
                <span>${this.esc(label)}</span>
                <span>${this.money(value)}</span>
            </div>`;
    },

    print() {
        const area = this.container.querySelector("#printArea");
        if (area) window.print();
    },

    statusClass(status) {
        return status === "PAID" ? "success" : status === "VOID" ? "error" : "info";
    },

    money(value) {
        return Number(value || 0).toLocaleString("en-IN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        });
    },

    number(value) {
        return Number(value || 0).toLocaleString("en-IN");
    },

    date(iso) {
        return iso
            ? new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })
            : "—";
    },

    time(iso) {
        return iso
            ? new Date(iso).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })
            : "—";
    },

    esc(v) {
        return String(v)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    },
};