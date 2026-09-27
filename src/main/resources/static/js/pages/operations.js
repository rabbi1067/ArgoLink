/* ------------------------------------------------------------------
 * operations.js - the Operations dashboard for platform admins.
 * ------------------------------------------------------------------ */
window.Pages = window.Pages || {};

window.Pages.operations = window.DashboardPage.create({
    prefix: "operations",
    name: "Operations",
    cardCount: 6,
    leadTableKey: "disputeQueue",
});
