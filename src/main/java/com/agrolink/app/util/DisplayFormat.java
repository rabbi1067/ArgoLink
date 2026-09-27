package com.agrolink.app.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;


public final class DisplayFormat {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);
    private static final ThreadLocal<DecimalFormat> MONEY = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.00", SYMBOLS));
    private static final ThreadLocal<DecimalFormat> MONEY_WHOLE = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0", SYMBOLS));
    private static final ThreadLocal<DecimalFormat> COUNT = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0", SYMBOLS));

    private DisplayFormat() {
    }

    public static String money(BigDecimal value) {
        if (value == null) {
            return "৳ 0.00";
        }
        return "৳ " + MONEY.get().format(value.setScale(2, RoundingMode.HALF_UP));
    }

    public static String moneyShort(BigDecimal value) {
        if (value == null) {
            return "৳ 0";
        }
        return "৳ " + MONEY_WHOLE.get().format(value.setScale(0, RoundingMode.HALF_UP));
    }

    public static String moneyCompact(BigDecimal value) {
        if (value == null) {
            return "৳ 0";
        }
        double v = value.doubleValue();
        double abs = Math.abs(v);
        if (abs >= 10_000_000) {
            return "৳ " + trim(v / 10_000_000) + "Cr";
        }
        if (abs >= 100_000) {
            return "৳ " + trim(v / 100_000) + "L";
        }
        if (abs >= 1_000) {
            return "৳ " + trim(v / 1_000) + "K";
        }
        return "৳ " + MONEY_WHOLE.get().format(BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_UP));
    }

    public static String count(long value) {
        return COUNT.get().format(value);
    }

    public static String quantity(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return new DecimalFormat("#,##0.##", SYMBOLS).format(value);
    }

    public static String percent(double fraction) {
        if (Double.isNaN(fraction) || Double.isInfinite(fraction)) {
            return "0%";
        }
        return new DecimalFormat("0.#", SYMBOLS).format(fraction * 100) + "%";
    }

    private static String trim(double value) {
        String text = new DecimalFormat("0.#", SYMBOLS).format(value);
        return "-0".equals(text) ? "0" : text;
    }
}
