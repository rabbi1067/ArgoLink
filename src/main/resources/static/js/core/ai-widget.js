(function () {
    "use strict";

    var WIDGET_ID = "ai-chat-widget";
    var FAB_ID = "ai-fab-btn";
    var HISTORY_KEY = "agrolink_ai_history";
    var ENDPOINT_KEY = "agrolink_ai_endpoint";
    var DEFAULT_ENDPOINT = "/assistant/chat";
    var MAX_LEN = 1000;
    var UI_DELAY = 260;

    var state = { open: false, busy: false };

    function getEndpoint() {
        try {
            return sessionStorage.getItem(ENDPOINT_KEY) || DEFAULT_ENDPOINT;
        } catch (e) {
            return DEFAULT_ENDPOINT;
        }
    }

    function injectStyles() {
        if (document.getElementById("ai-widget-styles")) return;
        var style = document.createElement("style");
        style.id = "ai-widget-styles";
        style.textContent = [
            "#" + FAB_ID + "{position:fixed;bottom:24px;right:24px;z-index:12000;width:56px;height:56px;border:none;border-radius:50%;cursor:pointer;display:flex;align-items:center;justify-content:center;font-size:26px;line-height:1;color:#fff;background:linear-gradient(135deg,#10b981,#06b6d4);box-shadow:0 4px 18px rgba(16,185,129,.55),0 0 40px rgba(6,182,212,.35);transition:transform .3s ease,box-shadow .3s ease}",
            "#" + FAB_ID + ":hover{transform:scale(1.1) rotate(6deg);box-shadow:0 8px 30px rgba(16,185,129,.75),0 0 60px rgba(6,182,212,.5)}",
            "#" + FAB_ID + ".ai-powered::after{content:'';position:absolute;inset:0;border-radius:50%;box-shadow:0 0 0 0 rgba(16,185,129,.5);animation:ai-pulse-ring 2s infinite}",
            "@keyframes ai-pulse-ring{0%{box-shadow:0 0 0 0 rgba(16,185,129,.5)}70%{box-shadow:0 0 0 14px rgba(16,185,129,0)}100%{box-shadow:0 0 0 0 rgba(16,185,129,0)}}",
            "#" + WIDGET_ID + "{position:fixed;bottom:92px;right:24px;z-index:11999;width:380px;height:520px;max-width:90vw;display:flex;flex-direction:column;overflow:hidden;border-radius:16px;background:rgba(15,23,42,.88);backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);border:1px solid rgba(255,255,255,.12);box-shadow:0 20px 50px rgba(0,0,0,.6);transform:translateY(20px) scale(.92);opacity:0;pointer-events:none;transition:transform .3s cubic-bezier(.34,1.56,.64,1),opacity .3s ease}",
            "#" + WIDGET_ID + ".ai-open{transform:translateY(0) scale(1);opacity:1;pointer-events:auto}",
            "#ai-widget-header{display:flex;align-items:center;gap:10px;padding:12px 14px;background:rgba(255,255,255,.08);border-bottom:1px solid rgba(255,255,255,.08);flex-shrink:0}",
            "#ai-widget-avatar{width:36px;height:36px;border-radius:50%;background:linear-gradient(135deg,#10b981,#06b6d4);display:flex;align-items:center;justify-content:center;font-size:18px;position:relative;flex-shrink:0}",
            "#ai-widget-avatar::after{content:'';position:absolute;bottom:1px;right:1px;width:9px;height:9px;border-radius:50%;background:#22c55e;border:2px solid #0f172a}",
            "#ai-widget-title{flex:1;color:#fff;font-weight:600;font-size:14px;line-height:1.25}",
            "#ai-widget-title small{display:block;font-weight:400;color:rgba(255,255,255,.55);font-size:11px}",
            ".ai-btn{background:rgba(255,255,255,.1);border:none;color:#fff;width:28px;height:28px;border-radius:8px;cursor:pointer;font-size:14px;line-height:1;flex-shrink:0}",
            ".ai-btn:hover{background:rgba(255,255,255,.22)}",
            "#ai-quick-chips{display:flex;gap:8px;padding:10px 12px;overflow-x:auto;flex-shrink:0;border-bottom:1px solid rgba(255,255,255,.06)}",
            "#ai-quick-chips::-webkit-scrollbar{display:none}",
            ".ai-chip{flex-shrink:0;border:1px solid rgba(16,185,129,.4);background:rgba(16,185,129,.12);color:#34d399;border-radius:999px;padding:6px 12px;font-size:12px;cursor:pointer;white-space:nowrap;transition:background .2s ease,color .2s ease}",
            ".ai-chip:hover{background:rgba(16,185,129,.3);color:#fff}",
            "#ai-chat-body{flex:1;overflow-y:auto;padding:14px;display:flex;flex-direction:column;gap:10px}",
            ".ai-msg{max-width:80%;width:fit-content;padding:10px 14px;border-radius:14px;font-size:14px;line-height:1.4;white-space:pre-wrap;word-wrap:break-word;word-break:break-word;overflow-wrap:anywhere}",
            ".ai-msg.ai-user{align-self:flex-end;margin-left:auto;background:#059669;color:#fff;border-bottom-right-radius:2px;min-width:0}",
            ".ai-msg.ai-bot{align-self:flex-start;margin-right:auto;background:#1e293b;color:#f1f5f9;border:1px solid rgba(255,255,255,.1);border-bottom-left-radius:2px;min-width:0}",
            ".ai-msg.ai-error{align-self:flex-start;background:rgba(239,68,68,.12);color:#fca5a5;border:1px solid rgba(239,68,68,.3);border-bottom-left-radius:4px}",
            ".ai-typing{display:inline-flex;gap:4px;align-items:center}",
            ".ai-typing span{width:6px;height:6px;border-radius:50%;background:#34d399;animation:ai-blink 1.2s infinite ease-in-out}",
            ".ai-typing span:nth-child(2){animation-delay:.2s}",
            ".ai-typing span:nth-child(3){animation-delay:.4s}",
            "@keyframes ai-blink{0%,80%,100%{opacity:.3;transform:scale(.8)}40%{opacity:1;transform:scale(1)}}",
            "#ai-input-bar{display:flex;align-items:flex-end;gap:8px;padding:10px 12px;border-top:1px solid rgba(255,255,255,.08);flex-shrink:0}",
            "#ai-text-input{flex:1;background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.12);border-radius:10px;padding:9px 12px;color:#fff;font-size:13px;outline:none;resize:none;max-height:96px}",
            "#ai-text-input::placeholder{color:rgba(255,255,255,.4)}",
            "#ai-send-btn{background:linear-gradient(135deg,#10b981,#06b6d4);border:none;color:#fff;width:38px;height:38px;border-radius:10px;cursor:pointer;font-size:16px;flex-shrink:0;line-height:1}",
            "#ai-send-btn:hover{filter:brightness(1.15)}",
            "#ai-send-btn:disabled{opacity:.5;cursor:not-allowed}",
            ".ai-banner{padding:8px 12px;font-size:11px;color:rgba(255,255,255,.6);background:rgba(16,185,129,.12);border-bottom:1px solid rgba(255,255,255,.06);flex-shrink:0;line-height:1.5}",
            ".ai-disclaimer{padding:12px;font-size:11px;color:rgba(255,255,255,.45);border-top:1px solid rgba(255,255,255,.08);flex-shrink:0;line-height:1.5}"
        ].join("");
        document.head.appendChild(style);
    }

    function buildWidget() {
        if (document.getElementById(FAB_ID)) return;

        var fab = document.createElement("button");
        fab.id = FAB_ID;
        fab.type = "button";
        fab.setAttribute("aria-label", "Open AgroLink AI Assistant");
        fab.title = "AgroLink AI Assist";
        fab.textContent = "🤖";
        fab.classList.add("ai-powered");
        fab.addEventListener("click", function () { toggle(); });
        document.body.appendChild(fab);

        var w = document.createElement("div");
        w.id = WIDGET_ID;
        w.setAttribute("role", "dialog");
        w.setAttribute("aria-hidden", "true");

        w.innerHTML =
            '<div id="ai-widget-header">' +
            '<span id="ai-widget-avatar">🤖</span>' +
            '<div id="ai-widget-title">AgroLink AI Assist<small>Powered by Google Gemini</small></div>' +
            '<button type="button" class="ai-btn" id="ai-min-btn" title="Minimize" aria-label="Minimize">&#8211;</button>' +
            '<button type="button" class="ai-btn" id="ai-close-btn" title="Close" aria-label="Close">&#10005;</button>' +
            '</div>' +
            `<div class="ai-banner">${window.t ? t("ai.banner") : "AI answers about orders, produce and platform usage. It can make mistakes, so verify important details."}</div>` +
            '<div id="ai-quick-chips">' +
            `<button type="button" class="ai-chip" data-q="How do I place an offer?">&#128161; ${window.t ? t("ai.chipOffer") : "How to place an offer?"}</button>` +
            `<button type="button" class="ai-chip" data-q="How do I confirm an order?">&#128230; ${window.t ? t("ai.chipConfirm") : "How to confirm an order?"}</button>` +
            `<button type="button" class="ai-chip" data-q="Where do I find the weather forecast?">&#127788; ${window.t ? t("ai.chipWeather") : "Weather forecast?"}</button>` +
            `<button type="button" class="ai-chip" data-q="How do I download an invoice?">&#128220; ${window.t ? t("ai.chipInvoice") : "Download invoice?"}</button>` +
            '</div>' +
            '<div id="ai-chat-body"></div>' +
            '<div id="ai-input-bar">' +
            `<textarea id="ai-text-input" rows="1" placeholder="${window.t ? t("ai.placeholder") : "Ask about orders, produce or platform usage&hellip;"}"></textarea>` +
            `<button type="button" id="ai-send-btn" title="${window.t ? t("ai.send") : "Send"}" aria-label="${window.t ? t("ai.send") : "Send"}">&#10148;</button>` +
            '</div>' +
            `<div class="ai-disclaimer">${window.t ? t("ai.disclaimer") : "AI-generated answers &mdash; verify before acting. Not a substitute for professional agronomic, financial or legal advice."}</div>`;

        document.body.appendChild(w);

        var restored = [];
        try {
            restored = JSON.parse(sessionStorage.getItem(HISTORY_KEY) || "[]");
        } catch (e) {
            restored = [];
        }
        if (restored.length === 0) {
            state.history = [];
            addMessage("bot", window.t ? t("ai.hello") : "Hello! I can help with orders, produce, weather location and platform usage. Choose a quick question below or type your own.");
        } else {
            state.history = restored;
            restored.forEach(function (m) {
                if (m && m.text) addMessage(m.role === "user" ? "user" : "bot", m.text, true);
            });
        }
        bindWidget();
    }

    function persist() {
        try {
            sessionStorage.setItem(HISTORY_KEY, JSON.stringify(state.history || []));
        } catch (e) { /* ignore */ }
    }

    // skipHistory = true when re-rendering messages that are already stored in state.history
    function addMessage(role, text, skipHistory) {
        var body = document.getElementById("ai-chat-body");
        if (!body) return;
        var div = document.createElement("div");
        div.className = "ai-msg " + (role === "user" ? "ai-user" : (role === "error" ? "ai-error" : "ai-bot"));
        div.textContent = text;
        body.appendChild(div);
        body.scrollTop = body.scrollHeight;
        if (role !== "error" && !skipHistory) {
            state.history = state.history || [];
            state.history.push({ role: role === "user" ? "user" : "bot", text: text });
            persist();
        }
        return div;
    }

    function showTyping() {
        var body = document.getElementById("ai-chat-body");
        if (!body) return null;
        var t = document.createElement("div");
        t.className = "ai-msg ai-bot";
        t.innerHTML = '<span class="ai-typing"><span></span><span></span><span></span></span>';
        body.appendChild(t);
        body.scrollTop = body.scrollHeight;
        return t;
    }

    function apiCall(payload) {
        if (window.Api && typeof window.Api.post === "function") {
            return window.Api.post(getEndpoint(), payload);
        }
        var token = null;
        try { token = localStorage.getItem("agrolink_token") || ""; } catch (e) { token = ""; }
        var headers = { "Content-Type": "application/json" };
        if (token) headers["Authorization"] = "Bearer " + token;
        return fetch("/api/v1" + getEndpoint(), {
            method: "POST",
            headers: headers,
            body: JSON.stringify(payload)
        }).then(function (res) {
            return res.json().then(function (data) {
                return { ok: res.ok, data: data.data };
            });
        });
    }

    function send(text) {
        text = (text || "").trim();
        if (!text || state.busy) return;
        if (text.length > MAX_LEN) {
            addMessage("error", "Message is too long (max " + MAX_LEN + " characters).");
            return;
        }
        state.busy = true;

        var sendBtn = document.getElementById("ai-send-btn");
        if (sendBtn) sendBtn.disabled = true;

        // Last 8 turns give Gemini the conversation context (taken before the new message is added).
        var history = (state.history || []).slice(-8).map(function (m) {
            return { role: m.role === "user" ? "user" : "model", text: String(m.text || "").slice(0, MAX_LEN) };
        });

        addMessage("user", text);
        var typing = showTyping();

        apiCall({ message: text, history: history })
            .then(function (res) {
                if (typing) typing.remove();
                var reply = res && res.data && res.data.reply;
                if (reply) {
                    addMessage("bot", reply);
                } else {
                    addMessage("error", "The assistant could not produce an answer. Please try again.");
                }
            })
            .catch(function (err) {
                if (typing) typing.remove();
                var msg = err && err.message && err.message !== "Network error" ? err.message : null;
                addMessage("error", msg || "Could not reach the assistant service. Please try again later.");
            })
            .then(function () {
                state.busy = false;
                if (sendBtn) sendBtn.disabled = false;
            });
    }

    function toggle(forceOpen) {
        var w = document.getElementById(WIDGET_ID);
        if (!w) return;
        state.open = typeof forceOpen === "boolean" ? forceOpen : !state.open;
        w.classList.toggle("ai-open", state.open);
        w.setAttribute("aria-hidden", state.open ? "false" : "true");
        var input = document.getElementById("ai-text-input");
        if (state.open && input) setTimeout(function () { input.focus(); }, UI_DELAY);
    }

    function bindWidget() {
        var chips = Array.prototype.slice.call(document.querySelectorAll(".ai-chip"));
        chips.forEach(function (chip) {
            chip.addEventListener("click", function () {
                var q = chip.getAttribute("data-q");
                if (q) send(q);
            });
        });

        var sendBtn = document.getElementById("ai-send-btn");
        var input = document.getElementById("ai-text-input");

        function onSend() {
            send(input ? input.value : "");
            if (input) input.value = "";
        }

        if (sendBtn) sendBtn.addEventListener("click", onSend);
        if (input) {
            input.addEventListener("keydown", function (e) {
                if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    onSend();
                }
            });
        }

        var minBtn = document.getElementById("ai-min-btn");
        var closeBtn = document.getElementById("ai-close-btn");
        if (minBtn) minBtn.addEventListener("click", function () { toggle(false); });
        if (closeBtn) closeBtn.addEventListener("click", function () { toggle(false); });
    }

    function init() {
        injectStyles();
        buildWidget();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();