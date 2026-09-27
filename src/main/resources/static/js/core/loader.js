const Loader = (() => {
    let backdrop = null;

    const ensureBackdrop = () => {
        if (backdrop && document.body.contains(backdrop)) return backdrop;
        backdrop = document.createElement("div");
        backdrop.className = "loader-backdrop is-hidden";
        backdrop.setAttribute("aria-hidden", "true");

        const spinner = document.createElement("div");
        spinner.className = "loader-spinner";
        spinner.setAttribute("role", "status");

        const text = document.createElement("div");
        text.className = "loader-text";
        text.textContent = "Loading...";

        backdrop.appendChild(spinner);
        backdrop.appendChild(text);
        document.body.appendChild(backdrop);
        return backdrop;
    };

    const show = (message = "Loading...") => {
        const el = ensureBackdrop();
        const label = el.querySelector(".loader-text");
        if (label) label.textContent = message;
        el.setAttribute("aria-hidden", "false");
        requestAnimationFrame(() => el.classList.remove("is-hidden"));
    };

    const hide = () => {
        if (!backdrop) return;
        backdrop.classList.add("is-hidden");
        backdrop.setAttribute("aria-hidden", "true");
    };

    const skeletonLine = (widthClass = "") =>
        `<div class="skeleton skeleton-line ${widthClass}"></div>`;

    const skeletonCard = (hasBlock = true) => `
        <div class="skeleton-card">
            ${hasBlock ? '<div class="skeleton skeleton-block"></div>' : ""}
            <div style="margin-top: 1rem;">
                ${skeletonLine("w-75")}
                ${skeletonLine("w-50")}
                ${skeletonLine("w-25")}
            </div>
        </div>`;

    const skeletonCards = (count = 4, hasBlock = true) =>
        Array.from({ length: count }, () => skeletonCard(hasBlock)).join("");

    const skeletonTableRow = (columns = 4) => {
        const cells = Array.from({ length: columns }, () =>
            `<td><div class="skeleton skeleton-line" style="margin: 0; width: 70%;"></div></td>`
        ).join("");
        return `<tr>${cells}</tr>`;
    };

    const skeletonTable = (columns = 4, rows = 5) => {
        const heads = Array.from({ length: columns }, (_, i) =>
            `<th><div class="skeleton skeleton-line" style="margin: 0; width: 50%;"></div></th>`
        ).join("");
        const body = Array.from({ length: rows }, () => skeletonTableRow(columns)).join("");
        return `
            <table class="skeleton-table">
                <thead><tr>${heads}</tr></thead>
                <tbody>${body}</tbody>
            </table>`;
    };

    const render = (target, html) => {
        const el = typeof target === "string" ? document.querySelector(target) : target;
        if (!el) return;
        el.dataset.originalHtml = el.dataset.originalHtml !== undefined
            ? el.dataset.originalHtml
            : el.innerHTML;
        el.innerHTML = html;
    };

    const restore = (target) => {
        const el = typeof target === "string" ? document.querySelector(target) : target;
        if (!el || el.dataset.originalHtml === undefined) return;
        el.innerHTML = el.dataset.originalHtml;
        delete el.dataset.originalHtml;
    };

    return {
        show,
        hide,
        skeletonLine,
        skeletonCard,
        skeletonCards,
        skeletonTable,
        render,
        restore,
    };
})();

window.Loader = Loader;