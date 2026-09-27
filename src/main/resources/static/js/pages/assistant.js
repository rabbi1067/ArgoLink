window.Pages = window.Pages || {};

window.Pages.assistant = {
    container: null,

    init(container) {
        this.container = container;
        this.bind();
    },

    bind() {
        const form = this.container.querySelector("#assistantForm");
        const input = this.container.querySelector("#assistantInput");
        const disclaimer = this.container.querySelector("#assistantDisclaimer");
        if (disclaimer) disclaimer.textContent = window.t ? t("as.disclaimer") : "Rule-based guidance only. Not a substitute for verified agronomic expertise.";
        const resetBtn = this.container.querySelector("#assistantResetBtn");
        if (resetBtn) resetBtn.addEventListener("click", () => this.clear());

        if (form) {
            form.addEventListener("submit", (e) => {
                e.preventDefault();
                const text = (input && input.value ? input.value : "").trim();
                if (!text) return;
                this.ask(text);
                if (input) input.value = "";
            });
        }
    },

    async ask(text) {
        this.addBubble(text, "user");
        const wrap = this.container.querySelector("#assistantMessages");
        const tpl = this.container.querySelector("#assistantMsgTpl");

        const loadingHtml = (tpl ? (() => { const n = tpl.content.firstElementChild.cloneNode(true); n.querySelector(".assistant-text").textContent = window.t ? t("as.thinking") : "Thinking..."; return n; })() : null);
        const loadingNode = loadingHtml;
        if (wrap && loadingNode) wrap.appendChild(loadingNode);

        try {
            const res = await Api.post("/assistant/ask", { message: text });
            if (wrap && loadingNode) loadingNode.querySelector(".assistant-text").textContent = res.data && res.data.reply ? res.data.reply : "Sorry, I could not answer that.";
        } catch (e) {
            if (wrap && loadingNode) loadingNode.querySelector(".assistant-text").textContent = window.t ? t("as.unreachable") : "Could not reach the assistant. Please try again.";
        }
        if (wrap) wrap.scrollTop = wrap.scrollHeight;
    },

    addBubble(text, who) {
        const wrap = this.container.querySelector("#assistantMessages");
        const tpl = this.container.querySelector("#assistantMsgTpl");
        if (!wrap || !tpl) return;
        const node = tpl.content.firstElementChild.cloneNode(true);
        node.querySelector(".assistant-text").textContent = text;
        node.classList.add(who === "user" ? "bg-info-soft" : "bg-muted-soft");
        wrap.appendChild(node);
        wrap.scrollTop = wrap.scrollHeight;
    },

    clear() {
        const wrap = this.container.querySelector("#assistantMessages");
        if (wrap) wrap.innerHTML = `<div class="empty-state">${window.t ? t("as.emptyShort") : "Ask me about crops, orders, or platform usage."}</div>`;
    },
};
