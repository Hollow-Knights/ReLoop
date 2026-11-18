package br.com.fiap.reloop.support;

import java.util.concurrent.atomic.AtomicLong;

/** Synthetic test data computed locally; never imported from real people's records. */
public final class TestDocuments {
    private static final AtomicLong SEQUENCE = new AtomicLong(700000000);
    private TestDocuments() { }

    public static String next() {
        return fromBase("%09d".formatted(SEQUENCE.incrementAndGet()));
    }

    public static String fromBase(String base) {
        if (!base.matches("[0-9]{9}")) throw new IllegalArgumentException("Test CPF base must contain nine digits.");
        String first = base + digit(base);
        return first + digit(first);
    }

    private static int digit(String value) {
        int sum = 0;
        for (int i = 0; i < value.length(); i++) sum += (value.charAt(i) - '0') * (value.length() + 1 - i);
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    public static String formatted(String value) {
        return value.substring(0, 3) + "." + value.substring(3, 6) + "." + value.substring(6, 9) + "-" + value.substring(9);
    }
}
