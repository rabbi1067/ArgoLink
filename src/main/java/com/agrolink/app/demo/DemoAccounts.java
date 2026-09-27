package com.agrolink.app.demo;

import com.agrolink.app.model.Role;

import java.util.Map;

/**
 * The four public demo logins. They exist only here: no database rows, no
 * seed migration, nothing to clean up. Show them on the login page with
 * {@code static/js/demo.js}; delete that file and this package and the main
 * project is exactly what it was before.
 */
public final class DemoAccounts {

    public static final String PASSWORD = "Demo1234!";

    public record Entry(String email, Role role, String userId, String name) {
    }

    public static final Map<String, Entry> ALL = Map.of(
            "demo-superadmin@agrolink.demo",
            new Entry("demo-superadmin@agrolink.demo", Role.SUPER_ADMIN, "demo-superadmin", "Demo Super Admin"),
            "demo-admin@agrolink.demo",
            new Entry("demo-admin@agrolink.demo", Role.ADMIN, "demo-admin", "Demo Admin"),
            "demo-buyer@agrolink.demo",
            new Entry("demo-buyer@agrolink.demo", Role.BUYER, "demo-buyer", "Demo Buyer"),
            "demo-farmer@agrolink.demo",
            new Entry("demo-farmer@agrolink.demo", Role.FARMER, "demo-farmer", "Demo Farmer"));

    private DemoAccounts() {
    }

    public static boolean isDemoEmail(String email) {
        return email != null && ALL.containsKey(email.toLowerCase().trim());
    }

    public static Entry entryOf(String email) {
        if (email == null) {
            return null;
        }
        return ALL.get(email.toLowerCase().trim());
    }
}
