const Toast = (() => {
    const DEFAULT_DURATION = 4000;
    const SUCCESS = "success";
    const ERROR = "error";
    const INFO = "info";

    let container = null;

    const ensureContainer = () => {
        if (container && document.body.contains(container)) return container;
        container = document.createElement("div");
        container.className = "toast-container";
        container.setAttribute("aria-live", "polite");
        document.body.appendChild(container);
        return container;
    };

    const remove = (toast) => {
        toast.classList.remove("toast-visible");
        toast.addEventListener("transitionend", () => toast.remove(), { once: true });
        setTimeout(() => toast.remove(), 400);
    };

    const show = (message, type = INFO, duration = DEFAULT_DURATION) => {
        const toast = document.createElement("div");
        toast.className = `toast toast-${type}`;
        toast.setAttribute("role", type === ERROR ? "alert" : "status");

        const icon = document.createElement("span");
        icon.className = "toast-icon";

        const text = document.createElement("span");
        text.className = "toast-message";
        text.textContent = message;

        const close = document.createElement("button");
        close.className = "toast-close";
        close.type = "button";
        close.setAttribute("aria-label", "Dismiss");
        close.textContent = "×";
        close.addEventListener("click", () => remove(toast));

        const timer = document.createElement("span");
        timer.className = "toast-timer";
        requestAnimationFrame(() => {
            timer.style.width = "0%";
        });

        toast.appendChild(icon);
        toast.appendChild(text);
        toast.appendChild(close);
        toast.appendChild(timer);
        ensureContainer().appendChild(toast);

        requestAnimationFrame(() => toast.classList.add("toast-visible"));
        setTimeout(() => remove(toast), duration);

        return toast;
    };

    const success = (message, duration) => show(message, SUCCESS, duration || DEFAULT_DURATION);
    const error = (message, duration) => show(message, ERROR, duration || DEFAULT_DURATION);
    const info = (message, duration) => show(message, INFO, duration || DEFAULT_DURATION);

    const fromResponse = (payload, fallback = "Something went wrong") => {
        const message = payload && payload.message ? payload.message : fallback;
        show(message, payload && payload.success ? SUCCESS : ERROR);
    };

    return { show, success, error, info, fromResponse, SUCCESS, ERROR, INFO };
})();

window.Toast = Toast;