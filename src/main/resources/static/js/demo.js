/**
 * Public demo logins (Super Admin / Admin / Buyer / Farmer).
 *
 * Self-contained on purpose: it only fills the existing login form and
 * submits it, so landing.js is never touched. Delete this file plus its
 * one <script> line in index.html and the whole demo package
 * (com.agrolink.app.demo), and the main project is exactly what it was.
 */
(function () {
    "use strict";

    var DEMOS = [
        { label: "Super Admin", email: "demo-superadmin@agrolink.demo", password: "Demo1234!" },
        { label: "Admin", email: "demo-admin@agrolink.demo", password: "Demo1234!" },
        { label: "Buyer", email: "demo-buyer@agrolink.demo", password: "Demo1234!" },
        { label: "Farmer", email: "demo-farmer@agrolink.demo", password: "Demo1234!" },
    ];

    var CSS = ".demo-login{margin-top:1rem;padding-top:1rem;border-top:1px dashed var(--color-border)}"
        + ".demo-title{margin:0 0 .5rem;font-size:.75rem;font-weight:700;text-transform:uppercase;"
        + "letter-spacing:.06em;color:var(--color-text-muted);text-align:center}"
        + ".demo-buttons{display:grid;grid-template-columns:1fr 1fr;gap:.5rem}"
        + ".demo-btn{padding:.5rem .25rem;font-size:.8rem;font-weight:700;border:1px solid var(--color-border);"
        + "border-radius:.5rem;background:transparent;color:var(--color-text);cursor:pointer;"
        + "transition:border-color .15s,color .15s,background-color .15s}"
        + ".demo-btn:hover{border-color:var(--color-brand-600);color:var(--color-brand-600)}";

    document.addEventListener("DOMContentLoaded", function () {
        var form = document.getElementById("loginForm");
        if (!form || form.dataset.demoReady) return;
        form.dataset.demoReady = "true";

        var style = document.createElement("style");
        style.textContent = CSS;
        document.head.appendChild(style);

        var wrap = document.createElement("div");
        wrap.className = "demo-login";
        var title = document.createElement("p");
        title.className = "demo-title";
        title.textContent = "Try a demo account (view only)";
        wrap.appendChild(title);

        var box = document.createElement("div");
        box.className = "demo-buttons";
        DEMOS.forEach(function (demo) {
            var button = document.createElement("button");
            button.type = "button";
            button.className = "demo-btn";
            button.textContent = demo.label;
            button.addEventListener("click", function () {
                var email = document.getElementById("loginEmail");
                var password = document.getElementById("loginPassword");
                if (email) email.value = demo.email;
                if (password) password.value = demo.password;
                if (form.requestSubmit) form.requestSubmit();
                else form.submit();
            });
            box.appendChild(button);
        });
        wrap.appendChild(box);
        form.appendChild(wrap);
    });
})();
