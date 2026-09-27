/* ------------------------------------------------------------------
 * overview.js - the Overview dashboard for farmers and buyers.
 *
 * Both roles share this page and this design; the backend returns
 * different figures for each, so no client-side role branching is
 * needed beyond hiding the admin-only district picker.
 * ------------------------------------------------------------------ */
window.Pages = window.Pages || {};

window.Pages.overview = window.DashboardPage.create({
    prefix: "overview",
    name: "Overview",
    cardCount: 4,
});
