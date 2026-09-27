window.Pages = window.Pages || {};

window.Pages.messages = {
    container: null,
    convs: [],
    activeConvId: null,
    renderedIds: new Set(),
    me: null,
    chatState: null,

    init(container) {
        this.container = container;
        const user = window.Api && Api.getUser ? Api.getUser() : null;
        this.me = user || {};
        this.role = this.me.role || "BUYER";
        this.convs = [];
        this.activeConvId = null;
        this.renderedIds = new Set();
        this.page = 0;
        this.pageData = null;

        this.bind();
        this.applyAdminChrome();
        this.loadThreads();

        const pending = window.PendingConversation;
        if (pending) {
            window.PendingConversation = null;
            this.loadThreads().then(() => this.openThread(pending));
        }
    },

    isAdmin() {
        return this.role === "ADMIN" || this.role === "SUPER_ADMIN";
    },

    /** Admins moderate every thread on the platform, not only their own chats. */
    applyAdminChrome() {
        const admin = this.isAdmin();
        this.container.querySelectorAll("[data-admin-only]").forEach((el) => {
            el.hidden = !admin;
        });
        const title = this.container.querySelector("#convListTitle");
        if (title) {
            title.textContent = admin ? "All Conversations" : "Conversations";
        }
    },

    bind() {
        const list = this.container.querySelector("#convList");
        if (list) {
            list.addEventListener("click", (e) => {
                const del = e.target.closest("[data-conv-delete]");
                if (del) {
                    e.preventDefault();
                    e.stopPropagation();
                    this.deleteThread(del.dataset.convDelete);
                    return;
                }
                const item = e.target.closest("[data-conv]");
                if (!item) return;
                e.preventDefault();
                this.openThread(item.dataset.conv);
            });
        }

        const prev = this.container.querySelector("#convsPrevPage");
        if (prev) {
            prev.addEventListener("click", () => {
                if (this.page > 0) {
                    this.page -= 1;
                    this.loadThreads();
                }
            });
        }
        const next = this.container.querySelector("#convsNextPage");
        if (next) {
            next.addEventListener("click", () => {
                if (this.pageData && this.pageData.hasNext) {
                    this.page += 1;
                    this.loadThreads();
                }
            });
        }

        const form = this.container.querySelector("#chatForm");
        if (form) {
            form.addEventListener("submit", (e) => this.handleSend(e));
        }
    },

    async loadThreads() {
        const list = this.container.querySelector("#convList");
        const unread = this.container.querySelector("#convUnread");
        if (!list) return;

        try {
            if (this.isAdmin()) {
                await this.loadAdminThreads(list);
                return;
            }

            const res = await Api.get("/messages/conversations");
            this.convs = res.data || [];
            const totalUnread = this.convs.reduce((sum, c) => sum + (Number(c.unreadCount) || 0), 0);
            if (unread) unread.textContent = String(totalUnread);

            list.innerHTML = this.convs.length
                ? this.convs
                      .map(
                          (c) => `
                              <a href="#" class="conv-item d-block p-3" data-conv="${this.esc(c.id)}" style="border-bottom: 1px solid var(--color-border); text-decoration: none;">
                                  <div class="flex items-center justify-between gap-2">
                                      <p class="font-semibold mb-0 truncate">${this.esc(c.otherPartyName || "Conversation")}</p>
                                      ${c.unreadCount ? `<span class="badge badge-info">${Number(c.unreadCount) || 0}</span>` : ""}
                                  </div>
                                  <p class="text-muted mb-0 truncate" style="font-size: 0.75rem;">${this.esc(c.lastMessage || (c.subject ? c.subject + " (no messages yet)" : "No messages yet"))}</p>
                                  <small class="text-muted">${this.listTime(c.lastMessageAt)}</small>
                              </a>`
                      )
                      .join("")
                : '<div class="empty-state">No conversations yet. Open a thread from your orders or listings.</div>';
        } catch (error) {
            list.innerHTML = `<div class="empty-state">Could not load conversations${error && error.message ? ": " + this.esc(error.message) : ""}</div>`;
        }
    },

    async loadAdminThreads(list) {
        const res = await Api.get(`/messages/admin/conversations?page=${this.page}&size=20`);
        this.pageData = res.data;
        this.convs = (res.data && res.data.content) || [];
        const unread = this.container.querySelector("#convUnread");
        if (unread) unread.textContent = String(this.pageData.totalElements);

        const info = this.container.querySelector("#convsPageInfo");
        if (info) {
            const from = this.convs.length ? this.page * 20 + 1 : 0;
            const to = this.page * 20 + this.convs.length;
            info.textContent = `Showing ${from}–${to} of ${this.pageData.totalElements} · newest first`;
        }
        const prev = this.container.querySelector("#convsPrevPage");
        const next = this.container.querySelector("#convsNextPage");
        if (prev) prev.disabled = !this.pageData.hasPrevious;
        if (next) next.disabled = !this.pageData.hasNext;

        list.innerHTML = this.convs.length
            ? this.convs
                  .map(
                      (c) => `
                        <div class="conv-item d-block p-3" data-conv="${this.esc(c.id)}" style="border-bottom: 1px solid var(--color-border); cursor: pointer;">
                            <div class="flex items-center justify-between gap-2">
                                <p class="font-semibold mb-0 truncate">${this.esc(c.subject || "Conversation")}</p>
                                <div class="flex items-center gap-2">
                                    ${c.moderated ? '<span class="badge badge-warning">Moderated</span>' : ""}
                                    <button type="button" class="btn btn-ghost btn-sm text-danger" data-conv-delete="${this.esc(c.id)}" title="Delete conversation">Delete</button>
                                </div>
                            </div>
                            <p class="text-muted mb-0 truncate" style="font-size: 0.75rem;">
                                ${this.esc(c.buyerName || "Buyer")} &rarr; ${this.esc(c.farmerName || "Farmer")} · ${Number(c.messageCount) || 0} message(s)
                            </p>
                            <p class="text-muted mb-0 truncate" style="font-size: 0.75rem;">${this.esc(c.lastMessage || "No messages yet")}</p>
                            <small class="text-muted">${this.listTime(c.lastMessageAt)}</small>
                        </div>`
                  )
                  .join("")
            : '<div class="empty-state">No conversations on the platform yet.</div>';
    },

    async deleteThread(convId) {
        if (!convId) return;
        if (!window.confirm("Delete this conversation and all of its messages? This cannot be undone.")) return;
        try {
            await Api.del(`/messages/admin/conversations/${convId}`);
            if (window.Toast) Toast.success("Conversation deleted");
            if (this.activeConvId === convId) {
                this.activeConvId = null;
                const thread = this.container.querySelector("#convThread");
                if (thread) thread.innerHTML = '<div class="empty-state">Select a conversation to read it.</div>';
                const composer = this.container.querySelector("#convComposer");
                if (composer) composer.classList.add("hidden");
            }
            this.loadThreads();
        } catch (error) {
            if (window.Toast) Toast.fromResponse(error, "Could not delete the conversation");
        }
    },

    async openThread(convId) {
        const thread = this.container.querySelector("#convThread");
        const composer = this.container.querySelector("#convComposer");
        if (!thread) return;

        if (this.activeConvId && this.activeConvId !== convId) {
            window.ChatSocket && ChatSocket.unsubscribe(this.activeConvId);
        }
        this.activeConvId = convId;
        this.renderedIds = new Set();

        thread.innerHTML = '<div class="empty-state">Loading…</div>';

        try {
            const res = this.isAdmin()
                ? await Api.get(`/messages/admin/conversations/${convId}/messages`)
                : await Api.get(`/messages/conversations/${convId}/messages`);
            const msgs = res.data || [];
            const conv = (this.convs || []).find((c) => c.id === convId) || {};
            const heading = this.isAdmin()
                ? `${conv.buyerName || "Buyer"} &rarr; ${conv.farmerName || "Farmer"}`
                : this.esc(conv.otherPartyName || "Conversation");
            const route = this.isAdmin() ? "READ ONLY" : this.esc(conv.otherPartyRole || "");

            thread.innerHTML = `
                <div class="card-header flex items-center justify-between">
                    <div class="min-w-0">
                        <h2 class="card-title mb-0">${this.isAdmin() ? heading : heading}</h2>
                        <p class="text-muted mb-0" style="font-size: 0.75rem;">${this.esc(conv.subject || "")}</p>
                    </div>
                    <span class="badge badge-info" id="chatRoute">${route}</span>
                </div>
                <div id="threadMessages" class="p-4 d-flex flex-column gap-2" style="flex: 1; overflow-y: auto; max-height: 22rem;"></div>`;

            msgs.forEach((m) => this.renderMessage(m));
            if (composer && !this.isAdmin()) composer.classList.remove("hidden");
            this.scrollThread();
        } catch (error) {
            thread.innerHTML = `<div class="empty-state">Could not load thread.</div>`;
            return;
        }

        if (!this.isAdmin() && window.ChatSocket && ChatSocket.subscribe) {
            ChatSocket.subscribe(convId, (dto) => this.onSocketMessage(dto)).catch(() => {});
        }
        if (!this.isAdmin()) this.loadThreads();
    },

    onSocketMessage(dto) {
        if (dto && dto.conversationId === this.activeConvId) {
            this.renderMessage(dto);
        }
        this.refreshListBadge();
    },

    renderMessage(msg) {
        if (!msg || !msg.id || this.renderedIds.has(msg.id)) return;
        this.renderedIds.add(msg.id);

        const wrap = this.container.querySelector("#threadMessages");
        if (!wrap) return;

        const mine = this.me.userId && msg.senderId === this.me.userId;
        const row = document.createElement("div");
        row.className = "msg-row mb-2 d-flex " + (mine ? "justify-content-end" : "");
        // Bubble backgrounds are intentionally fixed colors regardless of theme,
        // so the text color must be fixed too — otherwise dark-mode's near-white
        // default text becomes invisible on the light "sent" bubble.
        const bubbleStyle = mine
            ? "background: var(--color-primary-soft, #e7ecff); border: 1px solid var(--color-border); color: #1e293b;"
            : "background: var(--color-surface); border: 1px solid var(--color-border); color: var(--color-text);";
        const timeStyle = mine ? "color: #475569;" : "";
        row.innerHTML = `
            <div class="msg-bubble p-2 rounded" style="max-width: 80%; ${bubbleStyle}">
                <p class="mb-0 msg-text" style="color: inherit;">${this.esc(msg.body || "")}</p>
                <small class="msg-time" style="${timeStyle || "color: var(--color-text-muted);"}">${this.threadTime(msg.createdAt)}</small>
            </div>`;
        wrap.appendChild(row);
        this.scrollThread();
    },

    scrollThread() {
        const wrap = this.container.querySelector("#threadMessages");
        if (!wrap) return;
        wrap.scrollTop = wrap.scrollHeight;
    },

    async handleSend(event) {
        event.preventDefault();
        const input = this.container.querySelector("#chatInput");
        if (!input || !this.activeConvId) return;
        const content = input.value.trim();
        if (!content) return;

        input.value = "";
        try {
            const res = await Api.post(`/messages/conversations/${this.activeConvId}/messages`, { content });
            if (res && res.data) {
                this.renderMessage(res.data);
                this.refreshListBadge();
            }
        } catch (error) {
            input.value = content;
            if (window.Toast) Toast.fromResponse(error, "Could not send message");
        }
    },

    refreshListBadge() {
        if (!this.activeConvId || this.isAdmin()) return;
        Api.get("/messages/conversations")
            .then((res) => {
                this.convs = res.data || [];
                const unread = this.container.querySelector("#convUnread");
                const totalUnread = this.convs.reduce((s, c) => s + (Number(c.unreadCount) || 0), 0);
                if (unread) unread.textContent = String(totalUnread);
                const list = this.container.querySelector("#convList");
                if (list) {
                    const active = this.convs.find((c) => c.id === this.activeConvId);
                    const item = list.querySelector(`[data-conv="${this.activeConvId}"]`);
                    if (item && active) {
                        const preview = item.querySelector("p.text-muted");
                        if (preview) preview.textContent = active.lastMessage || "";
                    }
                }
            })
            .catch(() => {});
    },

    listTime(iso) {
        if (!iso) return "";
        const d = new Date(iso);
        const today = new Date();
        if (d.toDateString() === today.toDateString()) {
            return d.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
        }
        return d.toLocaleDateString("en-IN", { day: "numeric", month: "short" });
    },

    threadTime(iso) {
        if (!iso) return "";
        const d = new Date(iso);
        const today = new Date();
        const time = d.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
        if (d.toDateString() === today.toDateString()) {
            return time;
        }
        return d.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) + " " + time;
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