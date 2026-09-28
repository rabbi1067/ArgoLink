/**
 * i18n.js - English / Bangla toggle for the whole project.
 *
 * Mirrors theme.js: language persists in localStorage, [data-lang-toggle]
 * buttons sit next to the day/night button, static text is tagged with
 * data-i18n (text), data-i18n-ph (placeholder), data-i18n-aria (aria-label),
 * data-i18n-title (title) or data-i18n-html (markup), and dynamic code uses
 * t("key") / window.t("key"). Re-applied on every dashboard navigation.
 */
const I18n = (() => {
    "use strict";

    const LANG_KEY = "agrolink_lang";
    const EN = "en";
    const BN = "bn";

    const DICT = { en: {}, bn: {} };

    const add = (key, en, bn) => {
        DICT.en[key] = en;
        DICT.bn[key] = bn;
    };

    /* ---------------------------------------------------------- navigation */

    add("nav.home", "Home", "হোম");
    add("nav.explore", "Explore Crops", "ফসল দেখুন");
    add("nav.about", "About", "সম্পর্কে");
    add("nav.contact", "Contact", "যোগাযোগ");
    add("nav.login", "Login / Sign Up", "লগইন / সাইন আপ");
    add("lang.toggle.toBn", "Switch to Bangla", "বাংলায় দেখুন");
    add("lang.toggle.toEn", "Switch to English", "ইংরেজিতে দেখুন");

    /* ------------------------------------------------------------------ hero */

    add("hero.eyebrow", "B2B Agro-Supply Chain Platform", "বিটুবি কৃষি সাপ্লাই চেইন প্ল্যাটফর্ম");
    add("hero.title1", "Farm to market,", "খামার থেকে বাজার,");
    add("hero.title2", "directly & transparently.", "সরাসরি ও স্বচ্ছভাবে।");
    add("hero.sub",
        "AgroLink connects verified farmers with professional buyers. Negotiate offers, lock in prices, and track every order through a secure digital supply chain.",
        "অ্যাগ্রোলিংক যাচাইকৃত কৃষকদের সাথে পেশাদার ক্রেতাদের যুক্ত করে। দর কষুন, দাম নিশ্চিত করুন, এবং নিরাপদ ডিজিটাল সাপ্লাই চেইনে প্রতিটি অর্ডার ট্র্যাক করুন।");
    add("hero.explore", "Explore Available Crops", "ফসল দেখুন");
    add("hero.sell", "Start Selling as a Farmer", "কৃষক হিসেবে বিক্রি শুরু করুন");
    add("hero.statFarmers", "Registered Farmers", "নিবন্ধিত কৃষক");
    add("hero.statBuyers", "Registered Buyers", "নিবন্ধিত ক্রেতা");
    add("hero.statListings", "Live Listings", "চলমান তালিকা");

    /* -------------------------------------------------------------- explorer */

    add("explore.eyebrow", "Live Marketplace", "লাইভ বাজার");
    add("explore.title", "Public Crop Explorer", "উন্মুক্ত ফসল তালিকা");
    add("explore.sub", "Browse fresh produce listed by verified farmers in real time.",
        "যাচাইকৃত কৃষকদের তাজা পণ্য সরাসরি দেখুন।");
    add("explore.searchPh", "Search crop name...", "ফসলের নাম লিখুন...");
    add("explore.searchLabel", "Search crops", "ফসল খুঁজুন");
    add("explore.categoryLabel", "Filter by category", "ক্যাটাগরি অনুযায়ী ছাঁকুন");
    add("explore.allCategories", "All categories", "সব ক্যাটাগরি");
    add("explore.refresh", "Refresh", "রিফ্রেশ");
    add("explore.refreshLabel", "Refresh produce listings", "পণ্য তালিকা রিফ্রেশ করুন");
    add("explore.loading", "Loading produce listings...", "পণ্য তালিকা লোড হচ্ছে...");
    add("explore.empty", "No produce listings match your filters.", "কোনো পণ্য মেলেনি।");
    add("explore.none", "No produce available right now. Please try again later.",
        "এখন কোনো পণ্য নেই। পরে আবার চেষ্টা করুন।");
    add("explore.seeMore", "See more", "আরো দেখুন");
    add("explore.loginToView", "Login to view all", "সব দেখতে লগইন করুন");

    /* ----------------------------------------------------------------- about */

    add("about.eyebrow", "About AgroLink", "অ্যাগ্রোলিংক সম্পর্কে");
    add("about.title", "A digital supply chain built for agriculture",
        "কৃষির জন্য তৈরি ডিজিটাল সাপ্লাই চেইন");
    add("about.sub", "We remove middlemen and guesswork, giving both sides of the trade visibility, control, and speed.",
        "মধ্যস্বত্বভোগী ও অনিশ্চয়তা দূর করে দুই পক্ষকেই দিচ্ছি স্বচ্ছতা, নিয়ন্ত্রণ ও গতি।");
    add("about.direct", "Direct Sourcing", "সরাসরি সংগ্রহ");
    add("about.directText", "Buy straight from verified farms - fresher stock, fewer intermediaries.",
        "যাচাইকৃত খামার থেকে সরাসরি কিনুন - তাজা পণ্য, কম মধ্যস্বত্বভোগী।");
    add("about.fair", "Fair Pricing", "ন্যায্য দাম");
    add("about.fairText", "Transparent listings and counter-offers let both sides agree on price.",
        "স্বচ্ছ তালিকা ও পাল্টা দরদামে দুই পক্ষ দামে সম্মত হয়।");
    add("about.secure", "Secure Orders", "নিরাপদ অর্ডার");
    add("about.secureText", "Offers are locked with atomic quantity reservations, preventing overselling.",
        "পরিমাণ সংরক্ষণে অফার লক থাকে - অতিরিক্ত বিক্রির সুযোগ নেই।");
    add("about.tracking", "End-to-End Tracking", "সম্পূর্ণ ট্র্যাকিং");
    add("about.trackingText", "Follow every order from confirmation to delivery within one dashboard.",
        "এক ড্যাশবোর্ডেই নিশ্চিতকরণ থেকে ডেলিভারি পর্যন্ত প্রতিটি অর্ডার দেখুন।");

    /* --------------------------------------------------------------- contact */

    add("contact.eyebrow", "Contact", "যোগাযোগ");
    add("contact.title", "Get in touch", "যোগাযোগ করুন");
    add("contact.sub", "Questions about selling, sourcing, or partnering with AgroLink? Reach out directly - we reply within one business day.",
        "বিক্রি, সংগ্রহ বা অংশীদারত্ব নিয়ে প্রশ্ন? সরাসরি লিখুন - এক কর্মদিবসে উত্তর দিই।");
    add("contact.phone", "Phone", "ফোন");
    add("contact.email", "Email", "ইমেইল");
    add("contact.hours", "Support hours", "সহায়তার সময়");
    add("contact.hoursValue", "Sat - Thu, 9am - 8pm", "শনি - বৃহস্পতি, সকাল ৯টা - রাত ৮টা");
    add("contact.cta", "Ready to join the supply chain?", "সাপ্লাই চেইনে যোগ দিতে প্রস্তুত?");
    add("contact.ctaSub", "Create a free account and start exploring listings or listing your harvest today.",
        "ফ্রি অ্যাকাউন্ট খুলে আজই তালিকা দেখুন বা ফসল বিক্রি শুরু করুন।");
    add("contact.start", "Get Started", "শুরু করুন");

    /* ---------------------------------------------------------------- footer */

    add("footer.tag", "A B2B agro-supply chain platform connecting farmers and buyers directly.",
        "কৃষক ও ক্রেতাকে সরাসরি যুক্ত করা বিটুবি কৃষি সাপ্লাই চেইন প্ল্যাটফর্ম।");
    add("footer.dev", "Developed by", "ডেভেলপার");
    add("footer.rights", "© 2026 AgroLink. All rights reserved.", "© ২০২৬ অ্যাগ্রোলিংক। সর্বস্বত্ব সংরক্ষিত।");

    /* ------------------------------------------------------------ auth modal */

    add("auth.title", "Welcome to AgroLink", "অ্যাগ্রোলিংকে স্বাগতম");
    add("auth.login", "Login", "লগইন");
    add("auth.signup", "Sign Up", "সাইন আপ");
    add("auth.email", "Email", "ইমেইল");
    add("auth.emailPh", "you@company.com", "you@company.com");
    add("auth.password", "Password", "পাসওয়ার্ড");
    add("auth.loginPasswordPh", "••••••••", "••••••••");
    add("auth.forgot", "Forgot password?", "পাসওয়ার্ড ভুলে গেছেন?");
    add("auth.newHere", "New to AgroLink?", "অ্যাগ্রোলিংকে নতুন?");
    add("auth.createAccount", "Create an account", "অ্যাকাউন্ট খুলুন");
    add("auth.haveAccount", "Already registered?", "ইতিমধ্যে নিবন্ধিত?");
    add("auth.backLogin", "Back to login", "লগইনে ফিরুন");
    add("auth.fullName", "Full name", "পুরো নাম");
    add("auth.fullNamePh", "Green Valley Farms", "সবুজ উপত্যকা খামার");
    add("auth.iAm", "I am a", "আমি একজন");
    add("auth.farmer", "Farmer", "কৃষক");
    add("auth.farmerDesc", "Sell your produce", "পণ্য বিক্রি করুন");
    add("auth.buyer", "Buyer", "ক্রেতা");
    add("auth.buyerDesc", "Source fresh stock", "তাজা পণ্য সংগ্রহ করুন");
    add("auth.passwordPh", "Minimum 8 characters", "কমপক্ষে ৮ অক্ষর");
    add("auth.phone", "Phone (optional)", "ফোন (ঐচ্ছিক)");
    add("auth.location", "Location", "ঠিকানা");
    add("auth.locationPh", "Dhaka, Bangladesh", "ঢাকা, বাংলাদেশ");
    add("auth.create", "Create Account", "অ্যাকাউন্ট তৈরি করুন");
    add("auth.showPassword", "Show password", "পাসওয়ার্ড দেখুন");
    add("auth.hidePassword", "Hide password", "পাসওয়ার্ড লুকান");
    add("auth.sendCode", "Send code", "কোড পাঠান");
    add("auth.codeSentTo", "We sent a 6-digit code to", "আমরা ৬ সংখ্যার কোড পাঠিয়েছি");
    add("auth.codeExpiry", ". It expires in 10 minutes.", " নম্বরে। ১০ মিনিটে মেয়াদ শেষ হবে।");
    add("auth.codeLabel", "6-digit code", "৬ সংখ্যার কোড");
    add("auth.continue", "Continue", "এগিয়ে যান");
    add("auth.noCode", "Didn't get it?", "কোড পাননি?");
    add("auth.resend", "Resend code", "আবার পাঠান");
    add("auth.newPassword", "New password", "নতুন পাসওয়ার্ড");
    add("auth.confirmPassword", "Confirm new password", "নতুন পাসওয়ার্ড নিশ্চিত করুন");
    add("auth.confirmPasswordPh", "Repeat the new password", "নতুন পাসওয়ার্ড আবার লিখুন");
    add("auth.reset", "Reset password", "পাসওয়ার্ড রিসেট করুন");

    /* ------------------------------------------------------------------ demo */

    add("demo.title", "Try a demo account (view only)", "ডেমো অ্যাকাউন্ট চেষ্টা করুন (শুধু দেখা)");

    /* ----------------------------------------------------------------- roles */

    add("role.superAdmin", "Super Admin", "সুপার অ্যাডমিন");
    add("role.admin", "Admin", "অ্যাডমিন");
    add("role.farmer", "Farmer", "কৃষক");
    add("role.buyer", "Buyer", "ক্রেতা");

    /* ----------------------------------------------------------------- shell */

    add("shell.searchPh", "Search produce, orders, users...", "পণ্য, অর্ডার, ব্যবহারকারী খুঁজুন...");
    add("shell.searchLabel", "Global search", "সার্বিক অনুসন্ধান");
    add("shell.theme", "Toggle day/night theme", "দিন/রাত থিম বদলান");
    add("shell.profile", "Profile Settings", "প্রোফাইল সেটিংস");
    add("shell.logout", "Logout", "লগআউট");
    add("shell.guest", "Guest", "অতিথি");
    add("shell.loading", "Loading...", "লোড হচ্ছে...");
    add("shell.menu", "Menu", "মেনু");
    add("shell.openMenu", "Open menu", "মেনু খুলুন");
    add("shell.openProfile", "Open profile menu", "প্রোফাইল মেনু খুলুন");
    add("shell.idleWarn", "No activity for a while - logging out in 1 minute. Click anywhere to stay signed in.",
        "অনেকক্ষণ কোনো কাজ হয়নি - ১ মিনিটে লগআউট হবে। থাকতে চাইলে কোথাও ক্লিক করুন।");
    add("shell.idleOut", "Logged out due to inactivity", "নিষ্ক্রিয়তার জন্য লগআউট হয়েছে");

    /* ----------------------------------------------------------------- routes */

    add("route.overview", "Overview", "সংক্ষেপ");
    add("route.operations", "Operations", "কার্যক্রম");
    add("route.control", "Control Center", "নিয়ন্ত্রণ কেন্দ্র");
    add("route.produce", "Produce Supply", "পণ্য সরবরাহ");
    add("route.orders", "Purchase Orders", "ক্রয় আদেশ");
    add("route.weather", "Weather", "আবহাওয়া");
    add("route.invoices", "Invoices", "চালান");
    add("route.messages", "Messages", "বার্তা");
    add("route.users", "User Management", "ব্যবহারকারী ব্যবস্থাপনা");
    add("route.analytics", "Analytics", "বিশ্লেষণ");
    add("route.settings", "Settings", "সেটিংস");

    /* ----------------------------------------------------------------- common */

    add("common.loading", "Loading...", "লোড হচ্ছে...");
    add("common.refresh", "Refresh", "রিফ্রেশ");
    add("common.retry", "Try again", "আবার চেষ্টা করুন");
    add("common.viewMore", "View more", "আরো দেখুন");
    add("common.noData", "Nothing to show yet.", "এখনো কিছু নেই।");
    add("common.close", "Close", "বন্ধ করুন");

    /* --------------------------------------------------------- landing dynamic */

    add("card.qty", "Qty", "পরিমাণ");    add("card.harvest", "Harvest", "সংগ্রহ");
    add("card.offer", "Make an Offer", "দর দিন");
    add("auth.wait", "Please wait...", "অপেক্ষা করুন...");
    add("auth.working", "Working...", "কাজ চলছে...");
    add("auth.welcome", "Welcome back!", "ফিরে আসায় স্বাগতম!");
    add("auth.created", "Account created! Please log in with your email and password.",
        "অ্যাকাউন্ট তৈরি! ইমেইল ও পাসওয়ার্ড দিয়ে লগইন করুন।");
    add("auth.codeSent", "Code sent to your email", "আপনার ইমেইলে কোড পাঠানো হয়েছে");
    add("auth.codeResent", "A fresh code is on its way", "নতুন কোড আসছে");
    add("auth.badCode", "Code must be 6 digits", "কোড ৬ সংখ্যার হতে হবে");
    add("auth.mismatch", "Passwords do not match", "পাসওয়ার্ড মেলেনি");
    add("auth.resetDone", "Password reset successful. Please log in.",
        "পাসওয়ার্ড রিসেট হয়েছে। লগইন করুন।");
    add("auth.failed", "Something went wrong", "কিছু ভুল হয়েছে");

    /* ------------------------------------------------------------ dashboards */

    add("page.exportCsv", "Export CSV", "CSV ডাউনলোড");
    add("page.refresh", "Refresh", "রিফ্রেশ");
    add("page.recentActivity", "Recent activity", "সাম্প্রতিক কার্যকলাপ");
    add("page.latest5", "Latest 5 shown, View more opens the full page.",
        "সর্বশেষ ৫টি দেখানো হচ্ছে, পুরো পেজে আরো দেখুন।");
    add("page.attention", "Needs your attention", "আপনার মনোযোগ দরকার");
    add("ov.eyebrow", "Overview", "সংক্ষেপ");
    add("ov.greeting", "Welcome back", "ফিরে স্বাগতম");
    add("ov.trends", "Trends", "প্রবণতা");
    add("ov.trendsSub", "Built from your own records, month by month.",
        "আপনার নিজের রেকর্ড থেকে, মাস অনুযায়ী।");
    add("ov.records", "Your records", "আপনার রেকর্ড");
    add("ov.welcomeBack", "Welcome back", "ফিরে স্বাগতম");
    add("ov.scopeBadge", "Whole platform", "পুরো প্ল্যাটফর্ম");
    add("ov.scopeTitle", "These totals cover every farmer, buyer and order, not just your own.",
        "এই হিসাব শুধু আপনার নয় - সব কৃষক, ক্রেতা ও অর্ডারের।");
    add("dv.noDataPeriod", "No data for this period yet.", "এই সময়ের কোনো তথ্য নেই।");
    add("dv.chartsDown", "Charts are unavailable right now.", "চার্ট এখন দেখা যাচ্ছে না।");
    add("dv.chartBroken", "This chart could not be drawn.", "এই চার্ট আঁকা যায়নি।");
    add("dv.noMetrics", "No metrics yet.", "এখনো কোনো পরিমাপ নেই।");
    add("dv.noActivity", "Nothing has happened yet. New orders will show up here.",
        "এখনো কিছু হয়নি। নতুন অর্ডার এখানে দেখা যাবে।");
    add("dv.emptyTable", "Nothing to show yet.", "এখনো কিছু নেই।");
    add("dv.viewMore", "View more", "আরো দেখুন");
    add("ct.eyebrow", "Control Center", "নিয়ন্ত্রণ কেন্দ্র");
    add("ct.title", "Platform control center", "প্ল্যাটফর্ম নিয়ন্ত্রণ কেন্দ্র");
    add("ct.sub", "Governance, access and money movement across every region.",
        "সব অঞ্চলে পরিচালনা, অ্যাক্সেস ও অর্থ প্রবাহ।");
    add("ct.trends", "Platform trends", "প্ল্যাটফর্ম প্রবণতা");
    add("ct.trendsSub", "Revenue, account growth and where the trade happens.",
        "আয়, অ্যাকাউন্ট বৃদ্ধি ও বাণিজ্য কোথায় হয়।");
    add("ct.tables", "Governance tables", "পরিচালনা তালিকা");
    add("op.eyebrow", "Operations", "কার্যক্রম");
    add("op.title", "Marketplace administration", "বাজার প্রশাসন");
    add("op.sub", "Supply, demand and money movement across the whole platform.",
        "পুরো প্ল্যাটফর্মে সরবরাহ, চাহিদা ও অর্থ প্রবাহ।");
    add("op.lead", "Dispute queue", "বিরোধের সারি");
    add("op.leadSub", "Trades waiting for an admin decision, newest first.",
        "অ্যাডমিন সিদ্ধান্তের অপেক্ষায় থাকা লেনদেন, নতুন আগে।");
    add("op.trends", "Trade flow", "বাণিজ্য প্রবাহ");
    add("op.trendsSub", "Gross value against money still held in escrow.",
        "মোট মূল্য বনাম এসক্রোতে আটকে থাকা অর্থ।");
    add("op.tables", "Listing management", "তালিকা ব্যবস্থাপনা");
    add("an.eyebrow", "Insights", "তথ্য");
    add("an.title", "Analytics", "বিশ্লেষণ");
    add("an.window", "Platform performance", "প্ল্যাটফর্মের কর্মক্ষমতা");
    add("an.windowLabel", "Reporting window", "রিপোর্টের সময়সীমা");
    add("an.m3", "Last 3 months", "গত ৩ মাস");
    add("an.m6", "Last 6 months", "গত ৬ মাস");
    add("an.m12", "Last 12 months", "গত ১২ মাস");
    add("an.m24", "Last 24 months", "গত ২৪ মাস");
    add("an.charts", "Charts", "চার্ট");
    add("an.chartsSub", "Every series is grouped by the month the record was created.",
        "প্রতিটি সিরিজ রেকর্ড তৈরির মাস অনুযায়ী।");
    add("an.deny", "Insights are available to administrators only.",
        "তথ্য শুধু অ্যাডমিনদের জন্য।");

    /* --------------------------------------------------------------- produce */

    add("pd.eyebrow", "Marketplace", "বাজার");
    add("pd.title", "Produce Supply", "পণ্য সরবরাহ");
    add("pd.add", "+ Add Supply", "+ সরবরাহ যোগ করুন");
    add("pd.searchPh", "Search crop, place or category...", "ফসল, স্থান বা ক্যাটাগরি খুঁজুন...");
    add("pd.searchLabel", "Search produce", "পণ্য খুঁজুন");
    add("pd.categoryLabel", "Filter category", "ক্যাটাগরি ছাঁকুন");
    add("pd.allCategories", "All categories", "সব ক্যাটাগরি");
    add("pd.statusLabel", "Filter status", "অবস্থা ছাঁকুন");
    add("pd.allStatuses", "All statuses", "সব অবস্থা");
    add("pd.divisionLabel", "Filter division", "বিভাগ ছাঁকুন");
    add("pd.allDivisions", "All divisions", "সব বিভাগ");
    add("pd.districtLabel", "Filter district", "জেলা ছাঁকুন");
    add("pd.allDistricts", "All districts", "সব জেলা");
    add("pd.sortLabel", "Sort by", "সাজান");    add("pd.newest", "Newest first", "নতুন আগে");
    add("pd.priceAsc", "Price: low to high", "দাম: কম থেকে বেশি");
    add("pd.priceDesc", "Price: high to low", "দাম: বেশি থেকে কম");
    add("pd.mostStock", "Most stock", "সবচেয়ে বেশি মজুদ");
    add("pd.minPrice", "Min price ৳", "সর্বনিম্ন দাম ৳");
    add("pd.minPriceLabel", "Minimum price", "সর্বনিম্ন দাম");
    add("pd.maxPrice", "Max price ৳", "সর্বোচ্চ দাম ৳");
    add("pd.maxPriceLabel", "Maximum price", "সর্বোচ্চ দাম");
    add("pd.reserved", "Reserved", "সংরক্ষিত");
    add("pd.clear", "Clear filters", "ফিল্টার মুছুন");
    add("pd.loading", "Loading produce listings...", "পণ্য তালিকা লোড হচ্ছে...");
    add("pd.suggestTitle", "AI Suggest", "এআই পরামর্শ");
    add("pd.suggestSub", "Picked from your orders and offers", "আপনার অর্ডার ও অফার থেকে বাছাই");
    add("pd.makeOffer", "Make an Offer", "দর দিন");
    add("pd.cropFallback", "Crop", "ফসল");
    add("pd.unnamed", "Unnamed", "নামহীন");
    add("pd.unknownFarmer", "Unknown farmer", "অজানা কৃষক");
    add("pd.flexible", "Flexible", "নমনীয়");
    add("pd.harvest", "harvest", "সংগ্রহ");
    add("pd.available", "available", "মজুদ");
    add("pd.reserved", "reserved", "সংরক্ষিত");
    add("pd.unnamedProduce", "Unnamed produce", "নামহীন পণ্য");
    add("pd.messageFarmer", "Message the farmer", "কৃষককে বার্তা দিন");
    add("pd.updateStock", "Update stock", "মজুদ আপডেট");
    add("pd.publish", "Publish", "প্রকাশ করুন");
    add("pd.archive", "Archive", "আর্কাইভ");
    add("pd.stockFail", "Could not update stock", "মজুদ আপডেট করা যায়নি");
    add("pd.sold", "SOLD", "বিক্রীত");
    add("pd.repurchase", "Repurchase", "পুনঃক্রয়");
    add("pd.selectCategory", "Select category", "ক্যাটাগরি বেছে নিন");
    add("pd.selectDistrict", "Select district", "জেলা বেছে নিন");
    add("pd.active", "Active", "সক্রিয়");
    add("pd.draft", "Draft", "খসড়া");
    add("pd.archived", "Archived", "আর্কাইভ");
    add("pd.farmers", "Farmers", "কৃষক");
    add("pd.liveStock", "Live stock value", "চলমান মজুদ মূল্য");
    add("pd.noMatch", "No listings match these filters.", "এই ফিল্টারে কোনো তালিকা মেলেনি।");
    add("pd.noListings", "No listings found.", "কোনো তালিকা পাওয়া যায়নি।");
    add("pd.prev", "Previous", "আগের");
    add("pd.next", "Next", "পরের");
    add("pf.addSupply", "Add Supply", "সরবরাহ যোগ করুন");
    add("pf.editSupply", "Edit Supply", "সরবরাহ সম্পাদনা");
    add("pf.photo", "Crop photo (JPEG, PNG or WebP)", "ফসলের ছবি (JPEG, PNG বা WebP)");
    add("pf.photoAlt", "Selected crop photo preview", "বাছাই করা ফসলের ছবির প্রিভিউ");
    add("pf.removePhoto", "Remove photo", "ছবি মুছুন");
    add("pf.cropName", "Crop name", "ফসলের নাম");
    add("pf.cropNamePh", "e.g. Naogaon Aman Rice", "যেমন: নওগাঁর আমন ধান");
    add("pf.category", "Category", "ক্যাটাগরি");
    add("pf.desc", "Description (optional)", "বিবরণ (ঐচ্ছিক)");
    add("pf.descPh", "Variety, quality, packaging...", "জাত, মান, প্যাকেজিং...");
    add("pf.qty", "Available quantity", "মজুদ পরিমাণ");
    add("pf.unit", "Unit", "একক");
    add("pf.price", "Price per unit (৳)", "একক প্রতি দাম (৳)");
    add("pf.district", "District", "জেলা");
    add("pf.location", "Area / upazila / village (optional)", "এলাকা / উপজেলা / গ্রাম (ঐচ্ছিক)");
    add("pf.locationPh", "e.g. Manda, Naogaon", "যেমন: মান্দা, নওগাঁ");
    add("pf.harvest", "Harvest date", "সংগ্রহের তারিখ");
    add("pf.submit", "Add Listing", "তালিকা যোগ করুন");
    add("pf.save", "Save Changes", "সংরক্ষণ করুন");
    add("pf.offerTitle", "Make an Offer", "দর দিন");
    add("pf.offerQty", "Offered quantity", "প্রস্তাবিত পরিমাণ");
    add("pf.offerPrice", "Offered price per unit (৳)", "প্রস্তাবিত একক দাম (৳)");
    add("pf.estTotal", "Estimated total:", "আনুমানিক মোট:");
    add("pf.submitOffer", "Submit Offer", "অফার পাঠান");
    add("pf.badQty", "Enter a valid quantity", "সঠিক পরিমাণ দিন");
    add("pf.stockUpdated", "Stock updated", "মজুদ আপডেট হয়েছে");
    add("pf.deleteAdmin", "Delete this listing permanently? The farmer will lose it. This cannot be undone.",
        "তালিকাটি স্থায়ীভাবে মুছবেন? কৃষক এটি হারাবে। ফেরত হবে না।");
    add("pf.deleteMine", "Delete this listing? This cannot be undone.", "তালিকাটি মুছবেন? ফেরত হবে না।");
    add("pf.deleted", "Listing deleted", "তালিকা মুছে ফেলা হয়েছে");
    add("pf.waitPhoto", "Please wait for the photo to finish uploading", "ছবি আপলোড শেষ হওয়া পর্যন্ত অপেক্ষা করুন");
    add("pf.cropCatRequired", "Crop name and category are required", "ফসলের নাম ও ক্যাটাগরি দিন");
    add("pf.districtRequired", "Please select a district", "জেলা বেছে নিন");
    add("pf.qtyPrice", "Quantity and price must be greater than zero", "পরিমাণ ও দাম শূন্যের বেশি হতে হবে");
    add("pf.updated", "Listing updated", "তালিকা আপডেট হয়েছে");
    add("pf.created", "Listing created (saved as draft - publish it to go live)",
        "তালিকা তৈরি (খসড়া হিসেবে সংরক্ষিত - লাইভ করতে প্রকাশ করুন)");
    add("pf.badImage", "Choose a JPEG, PNG or WebP image", "JPEG, PNG বা WebP ছবি বেছে নিন");
    add("pf.bigImage", "That photo is too large (max 15 MB)", "ছবিটি বড় (সর্বোচ্চ ১৫ MB)");
    add("pf.offerQtyPrice", "Enter valid quantity and price", "সঠিক পরিমাণ ও দাম দিন");
    add("pf.offerSent", "Offer submitted", "অফার পাঠানো হয়েছে");
    add("pf.inStock", "in stock", "মজুদ আছে");
    add("inv.deleteConfirm", "Delete this invoice permanently? This cannot be undone.",
        "চালানটি স্থায়ীভাবে মুছবেন? ফেরত হবে না।");
    add("msg.deleteConfirm", "Delete this conversation and all of its messages? This cannot be undone.",
        "কথোপকথন ও সব বার্তা মুছবেন? ফেরত হবে না।");

    /* ---------------------------------------------------------------- orders */

    add("od.eyebrow", "Fulfilment", "পূরণ");
    add("od.title", "Purchase Orders", "ক্রয় আদেশ");
    add("od.statusLabel", "Filter by status", "অবস্থা অনুযায়ী ছাঁকুন");
    add("od.allCommitments", "All commitments", "সব প্রতিশ্রুতি");
    add("od.commitments", "Order Commitments", "অর্ডার প্রতিশ্রুতি");
    add("od.commitmentsSub", "Every order you are bound to, from accepted offer through escrow to delivery.",
        "গৃহীত অফার থেকে এসক্রো হয়ে ডেলিভারি পর্যন্ত আপনার প্রতিটি বাধ্যতামূলক অর্ডার।");
    add("od.total", "Total orders", "মোট অর্ডার");
    add("od.allTime", "all time", "সর্বমোট");
    add("od.offersToAccept", "Offers to accept", "গ্রহণ করার অফার");
    add("od.needsDecision", "needs your decision", "আপনার সিদ্ধান্ত দরকার");
    add("od.awaitingPayment", "Awaiting payment", "পেমেন্ট বাকি");
    add("od.addressDue", "address + payment due", "ঠিকানা + পেমেন্ট বাকি");
    add("od.escrow", "In escrow", "এসক্রোতে");
    add("od.heldSafe", "funds held safely", "অর্থ নিরাপদে আছে");
    add("od.onRoad", "On the road", "পথে আছে");
    add("od.paidNotDelivered", "paid, not delivered", "পেমেন্ট হয়েছে, ডেলিভারি বাকি");
    add("od.delivered", "Delivered", "ডেলিভারি হয়েছে");
    add("od.released", "escrow released", "এসক্রো ছাড় হয়েছে");
    add("od.thOrder", "Order", "অর্ডার");
    add("od.thCrop", "Crop", "ফসল");
    add("od.thQty", "Qty", "পরিমাণ");
    add("od.thUnitPrice", "Unit Price", "একক দাম");
    add("od.thTotal", "Total", "মোট");
    add("od.thAddress", "Delivery Address", "ডেলিভারি ঠিকানা");
    add("od.thPayment", "Payment", "পেমেন্ট");
    add("od.thTracking", "Live Tracking", "লাইভ ট্র্যাকিং");
    add("od.thDate", "Date", "তারিখ");
    add("od.thStatus", "Status", "অবস্থা");
    add("od.thActions", "Actions", "কাজ");
    add("od.noCommitments", "No order commitments.", "কোনো অর্ডার প্রতিশ্রুতি নেই।");
    add("od.noMoreNegotiations", "No more negotiations on this page.", "এই পেজে আর দরাদরি নেই।");
    add("od.noOffers", "No offers to review yet.", "এখনো দেখার মতো অফার নেই।");
    add("od.acceptOffer", "Accept offer", "অফার গ্রহণ");
    add("od.payConfirm", "Pay & confirm", "পেমেন্ট ও নিশ্চিত");
    add("od.updatePos", "Update position", "অবস্থান দিন");
    add("od.accept", "Accept", "গ্রহণ");
    add("od.counter", "Counter", "পাল্টা");
    add("od.reject", "Reject", "বাতিল");
    add("od.withdraw", "Withdraw", "ফিরিয়ে নিন");
    add("od.remove", "Remove", "মুছুন");
    add("od.chat", "Chat", "চ্যাট");
    add("od.loadingOrders", "Loading orders...", "অর্ডার লোড হচ্ছে...");
    add("od.loadingOffers", "Loading offers...", "অফার লোড হচ্ছে...");
    add("od.negotiations", "Offer Negotiations", "অফার দরাদরি");
    add("od.negotiationsSub", "Six negotiations per page - accept, counter or reject before stock runs out.",
        "প্রতি পেজে ছয়টি দরাদরি - মজুদ শেষের আগেই গ্রহণ, পাল্টা বা বাতিল করুন।");
    add("od.acceptTitle", "Accept Offer & Place Order", "অফার গ্রহণ ও অর্ডার");
    add("od.deliveryFrom", "Delivery address (from buyer)", "ডেলিভারি ঠিকানা (ক্রেতার)");
    add("od.acceptNote", "The buyer confirms the final shipping address and pays when the order is created.",
        "ক্রেতা চূড়ান্ত ঠিকানা নিশ্চিত করে অর্ডার তৈরির সময় পেমেন্ট করে।");
    add("od.confirmOrder", "Confirm Order", "অর্ডার নিশ্চিত করুন");
    add("od.counterTitle", "Counter Offer", "পাল্টা অফার");
    add("od.sendCounter", "Send Counter Offer", "পাল্টা অফার পাঠান");
    add("od.payTitle", "Confirm Payment", "পেমেন্ট নিশ্চিত করুন");
    add("od.shipAddress", "Shipping address", "পাঠানোর ঠিকানা");
    add("od.shipAddressPh", "House, road, district", "বাসা, রাস্তা, জেলা");
    add("od.payMethod", "Payment method", "পেমেন্ট মাধ্যম");
    add("od.escrowNote", "The amount is held in escrow and released to the farmer on delivery.",
        "অর্থ এসক্রোতে থাকে, ডেলিভারিতে কৃষক পায়।");
    add("od.payNow", "Pay & Confirm", "পেমেন্ট ও নিশ্চিত");
    add("od.trackTitle", "Update Live Position", "লাইভ অবস্থান দিন");
    add("od.latitude", "Latitude", "অক্ষাংশ");
    add("od.longitude", "Longitude", "দ্রাঘিমাংশ");
    add("od.savePos", "Save Position", "অবস্থান সংরক্ষণ");
    add("od.offerAccepted", "Offer accepted - the buyer now confirms address & payment",
        "অফার গৃহীত - ক্রেতা এখন ঠিকানা ও পেমেন্ট নিশ্চিত করবে");
    add("od.cancelConfirm", "Cancel and remove this order? This cannot be undone.",
        "এই অর্ডার বাতিল ও মুছে ফেলবেন? ফেরত হবে না।");
    add("od.cancelled", "Order cancelled and removed", "অর্ডার বাতিল ও মুছে ফেলা হয়েছে");
    add("od.moveConfirm", "Move this order to the new status?", "অর্ডারটি নতুন অবস্থায় নেবেন?");    add("od.addressRequired", "Shipping address is required", "পাঠানোর ঠিকানা দিন");
    add("od.latLng", "Enter a valid latitude and longitude", "সঠিক অক্ষাংশ ও দ্রাঘিমাংশ দিন");
    add("od.posUpdated", "Live position updated", "লাইভ অবস্থান আপডেট হয়েছে");
    add("od.withdrawQ", "Withdraw this offer?", "এই অফার ফিরিয়ে নেবেন?");
    add("od.rejectQ", "Reject this offer?", "এই অফার বাতিল করবেন?");
    add("od.continueQ", "Continue?", "এগিয়ে যাবেন?");
    add("od.withdrawn", "Offer withdrawn", "অফার ফিরিয়ে নেওয়া হয়েছে");
    add("od.rejected", "Offer rejected", "অফার বাতিল করা হয়েছে");
    add("od.placed", "Order placed", "অর্ডার দেওয়া হয়েছে");
    add("od.offerQtyPrice", "Enter valid quantity and price", "সঠিক পরিমাণ ও দাম দিন");
    add("od.counterSent", "Counter offer sent", "পাল্টা অফার পাঠানো হয়েছে");

    /* -------------------------------------------------------------- messages */

    add("msg.eyebrow", "Messaging", "বার্তালাপ");
    add("msg.title", "Conversations", "কথোপকথন");
    add("msg.sub", "Chat with buyers & farmers about your orders.", "অর্ডার নিয়ে ক্রেতা ও কৃষকদের সাথে চ্যাট করুন।");
    add("msg.backOrders", "Back to Orders", "অর্ডারে ফিরুন");
    add("msg.adminView", "Admin moderation view.", "অ্যাডমিন পর্যবেক্ষণ।");
    add("msg.adminViewText", "Every conversation on the platform, newest first. Threads are read-only here and can be deleted permanently.",
        "প্ল্যাটফর্মের সব কথোপকথন, নতুন আগে। এখানে শুধু পড়া যায়, মুছে ফেলা স্থায়ী।");
    add("msg.threads", "Threads", "থ্রেড");
    add("msg.loading", "Loading conversations...", "কথোপকথন লোড হচ্ছে...");
    add("msg.open", "Select a conversation to view messages.", "বার্তা দেখতে একটি কথোপকথন বেছে নিন।");
    add("msg.typePh", "Type a message...", "বার্তা লিখুন...");
    add("msg.send", "Send", "পাঠান");
    add("msg.loadFail", "Could not load conversations", "কথোপকথন লোড করা যায়নি");
    add("msg.deleted", "Conversation deleted", "কথোপকথন মুছে ফেলা হয়েছে");
    add("msg.selectRead", "Select a conversation to read it.", "পড়তে একটি কথোপকথন বেছে নিন।");
    add("msg.loadingThread", "Loading.", "লোড হচ্ছে।");
    add("msg.threadFail", "Could not load thread.", "থ্রেড লোড করা যায়নি।");
    add("msg.noConv", "No conversations yet. Open a thread from your orders or listings.",
        "এখনো কথোপকথন নেই। অর্ডার বা তালিকা থেকে থ্রেড খুলুন।");
    add("msg.noConvAdmin", "No conversations on the platform yet.", "প্ল্যাটফর্মে এখনো কথোপকথন নেই।");
    add("msg.loadingList", "Loading.", "লোড হচ্ছে।");

    /* ----------------------------------------------------------------- users */

    add("usr.eyebrow", "Administration", "প্রশাসন");
    add("usr.title", "User Management", "ব্যবহারকারী ব্যবস্থাপনা");
    add("usr.deleteSelected", "Delete Selected", "নির্বাচিত মুছুন");
    add("usr.create", "+ Create User", "+ ব্যবহারকারী তৈরি");
    add("usr.total", "Total Users", "মোট ব্যবহারকারী");
    add("usr.admins", "Total Admins", "মোট অ্যাডমিন");
    add("usr.active", "Active", "সক্রিয়");
    add("usr.inactive", "Deactivated", "নিষ্ক্রিয়");
    add("usr.roleLabel", "Filter by role", "ভূমিকা অনুযায়ী ছাঁকুন");
    add("usr.allRoles", "All roles", "সব ভূমিকা");
    add("usr.farmers", "Farmers", "কৃষকরা");
    add("usr.buyers", "Buyers", "ক্রেতারা");
    add("usr.adminsOpt", "Admins", "অ্যাডমিনরা");
    add("usr.superAdmins", "Super Admins", "সুপার অ্যাডমিনরা");
    add("usr.searchPh", "Search by name or email...", "নাম বা ইমেইলে খুঁজুন...");
    add("usr.list", "Users", "ব্যবহারকারী");
    add("usr.loading", "Loading users...", "ব্যবহারকারী লোড হচ্ছে...");
    add("usr.prev", "Previous", "আগের");
    add("usr.next", "Next", "পরের");
    add("usr.createTitle", "Create User", "ব্যবহারকারী তৈরি");
    add("usr.editTitle", "Edit User", "ব্যবহারকারী সম্পাদনা");
    add("usr.fullName", "Full name", "পুরো নাম");
    add("usr.fullNamePh", "e.g. Ashok Kumar", "যেমন: অশোক কুমার");
    add("usr.email", "Email", "ইমেইল");
    add("usr.password", "Password", "পাসওয়ার্ড");
    add("usr.passwordPh", "Minimum 8 characters", "কমপক্ষে ৮ অক্ষর");
    add("usr.role", "Role", "ভূমিকা");
    add("usr.selectRole", "Select role", "ভূমিকা বেছে নিন");
    add("usr.phone", "Phone (optional)", "ফোন (ঐচ্ছিক)");
    add("usr.location", "Location (optional)", "ঠিকানা (ঐচ্ছিক)");
    add("usr.locationPh", "Dhaka, Bangladesh", "ঢাকা, বাংলাদেশ");
    add("usr.createBtn", "Create User", "ব্যবহারকারী তৈরি করুন");
    add("usr.saveBtn", "Save Changes", "সংরক্ষণ করুন");
    add("usr.confirmTitle", "Please confirm", "নিশ্চিত করুন");
    add("usr.confirmMsg", "Are you sure?", "আপনি কি নিশ্চিত?");
    add("usr.cancel", "Cancel", "বাতিল");
    add("usr.confirm", "Confirm", "নিশ্চিত");
    add("usr.deleted", "User deleted", "ব্যবহারকারী মুছে ফেলা হয়েছে");
    add("usr.nameShort", "Name must be at least 2 characters", "নাম কমপক্ষে ২ অক্ষরের হতে হবে");
    add("usr.updated", "User updated", "ব্যবহারকারী আপডেট হয়েছে");
    add("usr.fillRequired", "Fill all required fields", "সব আবশ্যক ঘর পূরণ করুন");
    add("usr.created", "User created", "ব্যবহারকারী তৈরি হয়েছে");
    add("usr.couldNotDelete", "Could not delete selected users", "নির্বাচিত ব্যবহারকারী মোছা যায়নি");    add("usr.thUser", "User", "ব্যবহারকারী");
    add("usr.thEmail", "Email", "ইমেইল");
    add("usr.thRole", "Role", "ভূমিকা");
    add("usr.thPhone", "Phone", "ফোন");
    add("usr.thLocation", "Location", "ঠিকানা");
    add("usr.thStatus", "Status", "অবস্থা");
    add("usr.thActions", "Actions", "কাজ");
    add("usr.activeBadge", "Active", "সক্রিয়");
    add("usr.inactiveBadge", "Inactive", "নিষ্ক্রিয়");
    add("usr.edit", "Edit", "সম্পাদনা");
    add("usr.activate", "Activate", "সক্রিয় করুন");
    add("usr.deactivate", "Deactivate", "নিষ্ক্রিয় করুন");
    add("usr.delete", "Delete", "মুছুন");
    add("usr.createAdminUser", "Create Admin / User", "অ্যাডমিন / ব্যবহারকারী তৈরি");
    add("usr.createAdminTitle", "Create Admin / User", "অ্যাডমিন / ব্যবহারকারী তৈরি");
    add("usr.adminOnly", "Admins only.", "শুধু অ্যাডমিনদের জন্য।");

    /* --------------------------------------------------------------- settings */

    add("set.eyebrow", "Account", "অ্যাকাউন্ট");
    add("set.title", "Settings", "সেটিংস");
    add("set.profile", "Profile", "প্রোফাইল");
    add("set.photoAlt", "Profile photo", "প্রোফাইল ছবি");
    add("set.changePhoto", "Change profile photo", "প্রোফাইল ছবি বদলান");
    add("set.photoHint", "JPEG, PNG or WebP - max 5 MB", "JPEG, PNG বা WebP - সর্বোচ্চ ৫ MB");
    add("set.name", "Name", "নাম");
    add("set.email", "Email", "ইমেইল");
    add("set.phone", "Phone", "ফোন");
    add("set.location", "Location", "ঠিকানা");
    add("set.save", "Save Profile", "প্রোফাইল সংরক্ষণ");
    add("set.changePw", "Change Password", "পাসওয়ার্ড বদলান");
    add("set.currentPw", "Current password", "বর্তমান পাসওয়ার্ড");
    add("set.newPw", "New password", "নতুন পাসওয়ার্ড");
    add("set.pwHint", "At least 8 characters including an uppercase letter, a lowercase letter and a number.",
        "কমপক্ষে ৮ অক্ষর, বড় হাতের, ছোট হাতের ও সংখ্যা সহ।");
    add("set.updatePw", "Update Password", "পাসওয়ার্ড আপডেট");
    add("set.photoUpdated", "Profile photo updated", "প্রোফাইল ছবি আপডেট হয়েছে");
    add("set.avatarType", "Only JPEG, PNG and WebP images are allowed",
        "শুধু JPEG, PNG ও WebP ছবি দেওয়া যাবে");
    add("set.avatarSize", "Avatar must be 5 MB or smaller", "ছবি ৫ MB-এর মধ্যে হতে হবে");
    add("set.nameShort", "Name must be at least 2 characters", "নাম কমপক্ষে ২ অক্ষরের হতে হবে");
    add("set.saved", "Profile updated", "প্রোফাইল আপডেট হয়েছে");
    add("set.curRequired", "Current password is required", "বর্তমান পাসওয়ার্ড দিন");
    add("set.newShort", "New password must be at least 8 characters",
        "নতুন পাসওয়ার্ড কমপক্ষে ৮ অক্ষরের হতে হবে");
    add("set.newWeak", "New password must include an uppercase letter, a lowercase letter and a number",
        "নতুন পাসওয়ার্ডে বড় হাতের, ছোট হাতের ও সংখ্যা থাকতে হবে");
    add("set.newSame", "New password must be different from the current one",
        "নতুন পাসওয়ার্ড বর্তমানটির চেয়ে আলাদা হতে হবে");
    add("set.pwChanged", "Password changed", "পাসওয়ার্ড বদলে গেছে");
    add("set.saving", "Saving...", "সংরক্ষণ হচ্ছে...");
    add("set.updating", "Updating...", "আপডেট হচ্ছে...");
    add("set.loadFail", "Could not load profile", "প্রোফাইল লোড করা যায়নি");
    add("set.uploadFail", "Could not upload photo", "ছবি আপলোড করা যায়নি");
    add("set.updateFail", "Could not update profile", "প্রোফাইল আপডেট করা যায়নি");
    add("set.pwFail", "Could not change password", "পাসওয়ার্ড বদলানো যায়নি");

    /* -------------------------------------------------------------- assistant */

    add("as.eyebrow", "AI Assistant", "এআই সহকারী");
    add("as.title", "AgroLink Assistant", "অ্যাগ্রোলিংক সহকারী");
    add("as.sub", "Rule-based guidance for produce, orders and platform usage.",
        "পণ্য, অর্ডার ও প্ল্যাটফর্ম ব্যবহারে নিয়মভিত্তিক সহায়তা।");
    add("as.newChat", "New Chat", "নতুন চ্যাট");
    add("as.empty", "Ask me about crops, orders, or how to use AgroLink.",
        "ফসল, অর্ডার বা অ্যাগ্রোলিংক ব্যবহার নিয়ে জিজ্ঞেস করুন।");
    add("as.askLabel", "Ask a question", "প্রশ্ন করুন");
    add("as.askPh", "e.g. How do I confirm an order?", "যেমন: অর্ডার নিশ্চিত করবো কীভাবে?");
    add("as.ask", "Ask", "জিজ্ঞেস করুন");
    add("ai.banner", "AI answers about orders, produce and platform usage. It can make mistakes, so verify important details.",
        "অর্ডার, পণ্য ও প্ল্যাটফর্ম নিয়ে এআই উত্তর দেয়। ভুল হতে পারে, গুরুত্বপূর্ণ বিষয় যাচাই করুন।");
    add("ai.chipOffer", "How to place an offer?", "অফার দেবো কীভাবে?");
    add("ai.chipConfirm", "How to confirm an order?", "অর্ডার নিশ্চিত করবো কীভাবে?");
    add("ai.chipWeather", "Weather forecast?", "আবহাওয়া পূর্বাভাস?");
    add("ai.chipInvoice", "Download invoice?", "চালান ডাউনলোড?");
    add("ai.placeholder", "Ask about orders, produce or platform usage...",
        "অর্ডার, পণ্য বা প্ল্যাটফর্ম নিয়ে জিজ্ঞেস করুন...");
    add("ai.send", "Send", "পাঠান");
    add("ai.disclaimer", "AI-generated answers — verify before acting. Not a substitute for professional agronomic, financial or legal advice.",
        "এআই-এর উত্তর — কাজের আগে যাচাই করুন। পেশাদার কৃষি, আর্থিক বা আইনি পরামর্শের বিকল্প নয়।");
    add("ai.hello", "Hello! I can help with orders, produce, weather location and platform usage. Choose a quick question below or type your own.",
        "হ্যালো! অর্ডার, পণ্য, আবহাওয়া ও প্ল্যাটফর্ম নিয়ে সাহায্য করতে পারি। নিচে প্রশ্ন বেছে নিন বা নিজে লিখুন।");
    add("as.disclaimer", "Rule-based guidance only. Not a substitute for verified agronomic expertise.",
        "শুধু নিয়মভিত্তিক সহায়তা। যাচাইকৃত কৃষি পরামর্শের বিকল্প নয়।");
    add("as.thinking", "Thinking...", "ভাবছি...");
    add("as.unreachable", "Could not reach the assistant. Please try again.",
        "সহকারীর সাথে যোগাযোগ করা যায়নি। আবার চেষ্টা করুন।");
    add("as.emptyShort", "Ask me about crops, orders, or platform usage.",
        "ফসল, অর্ডার বা প্ল্যাটফর্ম নিয়ে জিজ্ঞেস করুন।");

    /* --------------------------------------------------------------- invoices */

    add("inv.eyebrow", "Invoices", "চালান");
    add("inv.title", "Digital Invoices", "ডিজিটাল চালান");
    add("inv.download", "Print / Save PDF", "প্রিন্ট / PDF সংরক্ষণ");
    add("inv.adminTitle", "Admin view.", "অ্যাডমিন ভিউ।");
    add("inv.adminText", "You are seeing every invoice on the platform, newest first. Deleting an invoice is permanent.",
        "প্ল্যাটফর্মের সব চালান দেখছেন, নতুন আগে। মুছে ফেললে স্থায়ীভাবে যাবে।");
    add("inv.list", "Invoices", "চালান");
    add("inv.empty", "No invoices yet. Complete an order to generate one.",
        "এখনো চালান নেই। অর্ডার সম্পন্ন করলে তৈরি হবে।");
    add("inv.prev", "Prev", "আগের");
    add("inv.next", "Next", "পরের");
    add("inv.contextAll", "Every invoice on the platform, newest first",
        "প্ল্যাটফর্মের সব চালান, নতুন আগে");
    add("inv.contextMine", "Invoices for your orders, newest first",
        "আপনার অর্ডারের চালান, নতুন আগে");
    add("inv.loadFail", "Could not load invoices", "চালান লোড করা যায়নি");
    add("inv.loadingList", "Loading invoices.", "চালান লোড হচ্ছে।");
    add("inv.deleted", "Invoice deleted", "চালান মুছে ফেলা হয়েছে");

    /* ---------------------------------------------------------------- weather */

    add("wx.eyebrow", "Weather", "আবহাওয়া");
    add("wx.title", "District Weather Forecast", "জেলার আবহাওয়া পূর্বাভাস");
    add("wx.sub", "7-day forecast for your farming location", "আপনার খামারের ৭ দিনের পূর্বাভাস");
    add("wx.search", "Search district...", "জেলা খুঁজুন...");
    add("wx.searchLabel", "Search district", "জেলা খুঁজুন");
    add("wx.district", "District", "জেলা");
    add("wx.loadingDistricts", "Loading districts...", "জেলা লোড হচ্ছে...");
    add("wx.noDistricts", "No districts found", "কোনো জেলা মেলেনি");
    add("wx.outlook", "7-Day Outlook", "৭ দিনের পূর্বাভাস");
    add("wx.loading", "Loading forecast...", "পূর্বাভাস লোড হচ্ছে...");
    add("wx.noData", "No forecast data yet.", "এখনো পূর্বাভাস নেই।");
    add("wx.todayTitle", "Weather today", "আজকের আবহাওয়া");
    add("wx.fullForecast", "7-day forecast", "৭ দিনের পূর্বাভাস");
    add("wx.rain", "Rain", "বৃষ্টি");
    add("wx.wind", "Wind", "বাতাস");
    add("wx.feelsLike", "Feels like", "অনুভূত");
    add("wx.gusts", "Gusts", "ঝাপটা");
    add("wx.high", "High", "সর্বোচ্চ");
    add("wx.low", "Low", "সর্বনিম্ন");
    add("wx.today", "Today", "আজ");
    add("wx.updated", "Updated", "হালনাগাদ");
    add("wx.stale", "Live update failed - showing the last saved forecast.",
        "লাইভ আপডেট ব্যর্থ - শেষ সংরক্ষিত পূর্বাভাস দেখানো হচ্ছে।");
    add("wx.unavailable", "Weather is unavailable right now.", "আবহাওয়া এখন পাওয়া যাচ্ছে না।");
    add("wx.storm", "Storm Alert", "ঝড়ের সতর্কতা");
    add("wx.rainWarn", "Rain Warning", "বৃষ্টির সতর্কতা");
    add("wx.heat", "Heat Stress", "তাপপ্রবাহ");
    add("wx.harvest", "Good for Harvest", "সংগ্রহের জন্য ভালো");
    add("wx.lightRain", "Light Rain - Plan Ahead", "হালকা বৃষ্টি - পরিকল্পনা করুন");
    add("wx.humidity", "Humidity", "আর্দ্রতা");
    add("wx.pressure", "Pressure", "চাপ");
    add("wx.loadingWx", "Loading weather...", "আবহাওয়া লোড হচ্ছে...");
    add("wx.tryAgain", "Try again", "আবার চেষ্টা করুন");
    add("wx.unavailableBadge", "Unavailable", "পাওয়া যায়নি");
    add("wx.lastSaved", "Last saved data", "শেষ সংরক্ষিত তথ্য");
    add("wx.noDistrict", "No district found", "কোনো জেলা মেলেনি");

    const getLang = () => {
        try {
            return localStorage.getItem(LANG_KEY) === BN ? BN : EN;
        } catch (e) {
            return EN;
        }
    };

    const t = (key) => {
        const table = DICT[getLang()] || {};
        if (table[key] !== undefined) return table[key];
        if (DICT.en[key] !== undefined) return DICT.en[key];
        return key;
    };

    const renderToggles = (root) => {
        const scope = root || document;
        scope.querySelectorAll("[data-lang-toggle]").forEach((button) => {
            const next = getLang() === BN ? EN : BN;
            button.textContent = next === BN ? "বাংলা" : "EN";
            const label = next === BN ? t("lang.toggle.toBn") : t("lang.toggle.toEn");
            button.setAttribute("aria-label", label);
            button.setAttribute("title", label);
        });
    };

    const apply = (root) => {
        const scope = root && root.querySelectorAll ? root : document;
        scope.querySelectorAll("[data-i18n]").forEach((el) => {
            el.textContent = t(el.getAttribute("data-i18n"));
        });
        scope.querySelectorAll("[data-i18n-ph]").forEach((el) => {
            el.setAttribute("placeholder", t(el.getAttribute("data-i18n-ph")));
        });
        scope.querySelectorAll("[data-i18n-aria]").forEach((el) => {
            el.setAttribute("aria-label", t(el.getAttribute("data-i18n-aria")));
        });
        scope.querySelectorAll("[data-i18n-alt]").forEach((el) => {
            el.setAttribute("alt", t(el.getAttribute("data-i18n-alt")));
        });
        scope.querySelectorAll("[data-i18n-title]").forEach((el) => {
            el.setAttribute("title", t(el.getAttribute("data-i18n-title")));
        });
        scope.querySelectorAll("[data-i18n-html]").forEach((el) => {
            el.innerHTML = t(el.getAttribute("data-i18n-html"));
        });
        if (scope === document) {
            document.documentElement.setAttribute("lang", getLang() === BN ? "bn" : "en");
        }
        renderToggles(scope);
        document.dispatchEvent(new CustomEvent("langchange", { detail: { lang: getLang() } }));
    };

    const setLang = (lang) => {
        try {
            localStorage.setItem(LANG_KEY, lang === BN ? BN : EN);
        } catch (e) {
            /* private mode: session-only */
        }
        apply();
        return getLang();
    };

    const toggle = () => setLang(getLang() === BN ? EN : BN);

    /** Locale for dates/numbers: bn-BD in Bangla mode, en-IN otherwise. */
    const locale = () => (getLang() === BN ? "bn-BD" : "en-IN");

    const bindToggles = (root) => {
        const scope = root || document;
        scope.querySelectorAll("[data-lang-toggle]").forEach((button) => {
            if (button.dataset.langBound) return;
            button.dataset.langBound = "1";
            button.addEventListener("click", () => toggle());
        });
    };

    const init = () => {
        bindToggles();
        apply();
        document.addEventListener("agrolink:navigate", () => apply());
        return getLang();
    };

    return { getLang, setLang, toggle, t, apply, bindToggles, init, locale };
})();

window.I18n = I18n;
window.t = (key) => I18n.t(key);
