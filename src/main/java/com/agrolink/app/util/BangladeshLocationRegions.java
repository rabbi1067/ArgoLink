package com.agrolink.app.util;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class BangladeshLocationRegions {

    private BangladeshLocationRegions() {
    }

    public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {
    }

    private record District(String name, String division, GeoPoint point) {
    }

    public static final List<String> DIVISIONS = List.of(
            "Dhaka", "Chattogram", "Rajshahi", "Khulna", "Barishal",
            "Sylhet", "Rangpur", "Mymensingh");

    private static District d(String name, String division, String lat, String lon) {
        return new District(name, division, new GeoPoint(new BigDecimal(lat), new BigDecimal(lon)));
    }

    private static final List<District> DISTRICTS = List.of(
            // Dhaka (13)
            d("Dhaka", "Dhaka", "23.8103", "90.4125"),
            d("Faridpur", "Dhaka", "23.6070", "89.8429"),
            d("Gazipur", "Dhaka", "24.0023", "90.4264"),
            d("Gopalganj", "Dhaka", "23.0050", "89.8266"),
            d("Kishoreganj", "Dhaka", "24.4449", "90.7766"),
            d("Madaripur", "Dhaka", "23.1641", "90.1896"),
            d("Manikganj", "Dhaka", "23.8617", "90.0003"),
            d("Munshiganj", "Dhaka", "23.5422", "90.5305"),
            d("Narayanganj", "Dhaka", "23.6238", "90.4995"),
            d("Narsingdi", "Dhaka", "23.9322", "90.7151"),
            d("Rajbari", "Dhaka", "23.7574", "89.6445"),
            d("Shariatpur", "Dhaka", "23.2423", "90.4348"),
            d("Tangail", "Dhaka", "24.2513", "89.9167"),
            // Chattogram (11)
            d("Bandarban", "Chattogram", "22.1953", "92.2184"),
            d("Brahmanbaria", "Chattogram", "23.9571", "91.1119"),
            d("Chandpur", "Chattogram", "23.2333", "90.6713"),
            d("Chattogram", "Chattogram", "22.3569", "91.7832"),
            d("Cox's Bazar", "Chattogram", "21.4272", "92.0058"),
            d("Cumilla", "Chattogram", "23.4607", "91.1809"),
            d("Feni", "Chattogram", "23.0159", "91.3976"),
            d("Khagrachhari", "Chattogram", "23.1193", "91.9847"),
            d("Lakshmipur", "Chattogram", "22.9425", "90.8412"),
            d("Noakhali", "Chattogram", "22.8696", "91.0995"),
            d("Rangamati", "Chattogram", "22.7324", "92.2985"),
            // Rajshahi (8)
            d("Bogura", "Rajshahi", "24.8465", "89.3773"),
            d("Chapai Nawabganj", "Rajshahi", "24.5965", "88.2775"),
            d("Joypurhat", "Rajshahi", "25.0968", "89.0227"),
            d("Naogaon", "Rajshahi", "24.7936", "88.9318"),
            d("Natore", "Rajshahi", "24.4206", "89.0000"),
            d("Pabna", "Rajshahi", "24.0064", "89.2372"),
            d("Rajshahi", "Rajshahi", "24.3745", "88.6042"),
            d("Sirajganj", "Rajshahi", "24.4534", "89.7006"),
            // Khulna (10)
            d("Bagerhat", "Khulna", "22.6602", "89.7895"),
            d("Chuadanga", "Khulna", "23.6401", "88.8418"),
            d("Jashore", "Khulna", "23.1667", "89.2081"),
            d("Jhenaidah", "Khulna", "23.5450", "89.1726"),
            d("Khulna", "Khulna", "22.8456", "89.5403"),
            d("Kushtia", "Khulna", "23.9013", "89.1205"),
            d("Magura", "Khulna", "23.4873", "89.4199"),
            d("Meherpur", "Khulna", "23.7622", "88.6318"),
            d("Narail", "Khulna", "23.1725", "89.5126"),
            d("Satkhira", "Khulna", "22.7185", "89.0705"),
            // Barishal (6)
            d("Barguna", "Barishal", "22.0953", "90.1121"),
            d("Barishal", "Barishal", "22.7010", "90.3535"),
            d("Bhola", "Barishal", "22.6859", "90.6482"),
            d("Jhalokati", "Barishal", "22.6406", "90.1987"),
            d("Patuakhali", "Barishal", "22.3596", "90.3298"),
            d("Pirojpur", "Barishal", "22.5841", "89.9720"),
            // Sylhet (4)
            d("Habiganj", "Sylhet", "24.3745", "91.4155"),
            d("Moulvibazar", "Sylhet", "24.4829", "91.7774"),
            d("Sunamganj", "Sylhet", "25.0658", "91.4073"),
            d("Sylhet", "Sylhet", "24.8949", "91.8687"),
            // Rangpur (8)
            d("Dinajpur", "Rangpur", "25.6217", "88.6354"),
            d("Gaibandha", "Rangpur", "25.3297", "89.5430"),
            d("Kurigram", "Rangpur", "25.8054", "89.6361"),
            d("Lalmonirhat", "Rangpur", "25.9923", "89.2847"),
            d("Nilphamari", "Rangpur", "25.9318", "88.8560"),
            d("Panchagarh", "Rangpur", "26.3411", "88.5542"),
            d("Rangpur", "Rangpur", "25.7439", "89.2752"),
            d("Thakurgaon", "Rangpur", "26.0337", "88.4616"),
            // Mymensingh (4)
            d("Jamalpur", "Mymensingh", "24.9375", "89.9378"),
            d("Mymensingh", "Mymensingh", "24.7471", "90.4203"),
            d("Netrokona", "Mymensingh", "24.8703", "90.7279"),
            d("Sherpur", "Mymensingh", "25.0204", "90.0152"));

    private static final Map<String, GeoPoint> COORDINATES = DISTRICTS.stream()
            .collect(Collectors.toUnmodifiableMap(District::name, District::point));

    private static final Map<String, String> DIVISION_OF_DISTRICT = DISTRICTS.stream()
            .collect(Collectors.toUnmodifiableMap(District::name, District::division));

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("jessore", "Jashore"),
            Map.entry("comilla", "Cumilla"),
            Map.entry("chittagong", "Chattogram"),
            Map.entry("barisal", "Barishal"),
            Map.entry("bogra", "Bogura"),
            Map.entry("chapainawabganj", "Chapai Nawabganj"),
            Map.entry("coxs bazar", "Cox's Bazar"),
            Map.entry("cox’s bazar", "Cox's Bazar"));

    private static final Map<String, String> BENGALI_NAMES = Map.ofEntries(
            // Dhaka division
            Map.entry("ঢাকা", "Dhaka"),
            Map.entry("ফরিদপুর", "Faridpur"),
            Map.entry("গাজীপুর", "Gazipur"),
            Map.entry("গোপালগঞ্জ", "Gopalganj"),
            Map.entry("কিশোরগঞ্জ", "Kishoreganj"),
            Map.entry("মাদারীপুর", "Madaripur"),
            Map.entry("মানিকগঞ্জ", "Manikganj"),
            Map.entry("মুন্সিগঞ্জ", "Munshiganj"),
            Map.entry("নারায়ণগঞ্জ", "Narayanganj"),
            Map.entry("নরসিংদী", "Narsingdi"),
            Map.entry("রাজবাড়ী", "Rajbari"),
            Map.entry("শরীয়তপুর", "Shariatpur"),
            Map.entry("টাঙ্গাইল", "Tangail"),
            // Chattogram division
            Map.entry("বান্দরবান", "Bandarban"),
            Map.entry("ব্রাহ্মণবাড়িয়া", "Brahmanbaria"),
            Map.entry("চাঁদপুর", "Chandpur"),
            Map.entry("চট্টগ্রাম", "Chattogram"),
            Map.entry("কক্সবাজার", "Cox's Bazar"),
            Map.entry("কুমিল্লা", "Cumilla"),
            Map.entry("ফেনী", "Feni"),
            Map.entry("খাগড়াছড়ি", "Khagrachhari"),
            Map.entry("লক্ষ্মীপুর", "Lakshmipur"),
            Map.entry("নোয়াখালী", "Noakhali"),
            Map.entry("রাঙ্গামাটি", "Rangamati"),
            // Rajshahi division
            Map.entry("বগুড়া", "Bogura"),
            Map.entry("চাঁপাইনবাবগঞ্জ", "Chapai Nawabganj"),
            Map.entry("জয়পুরহাট", "Joypurhat"),
            Map.entry("নওগাঁ", "Naogaon"),
            Map.entry("নাটোর", "Natore"),
            Map.entry("পাবনা", "Pabna"),
            Map.entry("রাজশাহী", "Rajshahi"),
            Map.entry("সিরাজগঞ্জ", "Sirajganj"),
            // Khulna division
            Map.entry("বাগেরহাট", "Bagerhat"),
            Map.entry("চুয়াডাঙ্গা", "Chuadanga"),
            Map.entry("যশোর", "Jashore"),
            Map.entry("ঝিনাইদহ", "Jhenaidah"),
            Map.entry("খুলনা", "Khulna"),
            Map.entry("কুষ্টিয়া", "Kushtia"),
            Map.entry("মাগুরা", "Magura"),
            Map.entry("মেহেরপুর", "Meherpur"),
            Map.entry("নড়াইল", "Narail"),
            Map.entry("সাতক্ষীরা", "Satkhira"),
            // Barishal division
            Map.entry("বরগুনা", "Barguna"),
            Map.entry("বরিশাল", "Barishal"),
            Map.entry("ভোলা", "Bhola"),
            Map.entry("ঝালকাঠি", "Jhalokati"),
            Map.entry("পটুয়াখালী", "Patuakhali"),
            Map.entry("পিরোজপুর", "Pirojpur"),
            // Sylhet division
            Map.entry("হবিগঞ্জ", "Habiganj"),
            Map.entry("মৌলভীবাজার", "Moulvibazar"),
            Map.entry("সুনামগঞ্জ", "Sunamganj"),
            Map.entry("সিলেট", "Sylhet"),
            // Rangpur division
            Map.entry("দিনাজপুর", "Dinajpur"),
            Map.entry("গাইবান্ধা", "Gaibandha"),
            Map.entry("কুড়িগ্রাম", "Kurigram"),
            Map.entry("লালমনিরহাট", "Lalmonirhat"),
            Map.entry("নীলফামারী", "Nilphamari"),
            Map.entry("পঞ্চগড়", "Panchagarh"),
            Map.entry("রংপুর", "Rangpur"),
            Map.entry("ঠাকুরগাঁও", "Thakurgaon"),
            // Mymensingh division
            Map.entry("জামালপুর", "Jamalpur"),
            Map.entry("ময়মনসিংহ", "Mymensingh"),
            Map.entry("নেত্রকোণা", "Netrokona"),
            Map.entry("শেরপুর", "Sherpur"));

    public static boolean isValidDistrict(String district) {
        return normalize(district).isPresent();
    }

    public static List<String> allDistricts() {
        return COORDINATES.keySet().stream().sorted().toList();
    }

    public static Optional<String> divisionOf(String district) {
        return normalize(district).map(DIVISION_OF_DISTRICT::get);
    }
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (COORDINATES.containsKey(trimmed)) {
            return Optional.of(trimmed);
        }
        Optional<String> ci = COORDINATES.keySet().stream()
                .filter(d -> d.equalsIgnoreCase(trimmed))
                .findFirst();
        if (ci.isPresent()) {
            return ci;
        }
        String englishAlias = ALIASES.get(trimmed.toLowerCase());
        if (englishAlias != null) {
            return Optional.of(englishAlias);
        }
        // Bangla script has no case, so this is an exact (not lower-cased) lookup.
        return Optional.ofNullable(BENGALI_NAMES.get(trimmed));
    }


    public static List<String> allSearchableNames() {
        return java.util.stream.Stream.of(COORDINATES.keySet(), ALIASES.keySet(), BENGALI_NAMES.keySet())
                .flatMap(java.util.Set::stream)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    public static Optional<GeoPoint> coordinatesOf(String district) {
        return normalize(district).map(COORDINATES::get);
    }

    /** Great-circle distance in kilometres between (latA, lonA) and (latB, lonB). */
    public static double distanceKm(double latA, double lonA, double latB, double lonB) {
        double dLat = Math.toRadians(latB - latA);
        double dLon = Math.toRadians(lonB - lonA);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(latA)) * Math.cos(Math.toRadians(latB))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Nearest known district centroid to a raw device lat/lon; empty if out of range (> 400 km). */
    public static Optional<String> nearestDistrict(double lat, double lon) {
        String best = null;
        double bestKm = Double.MAX_VALUE;
        for (var e : COORDINATES.entrySet()) {
            double km = distanceKm(lat, lon,
                    e.getValue().latitude().doubleValue(), e.getValue().longitude().doubleValue());
            if (km < bestKm) {
                bestKm = km;
                best = e.getKey();
            }
        }
        if (best == null || bestKm > 400D) return Optional.empty();
        return Optional.of(best);
    }
}