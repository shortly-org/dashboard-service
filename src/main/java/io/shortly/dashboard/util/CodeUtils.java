package io.shortly.dashboard.util;

import java.security.SecureRandom;

/** Random nine-character base62 short codes. */
public final class CodeUtils {

    public static final int LENGTH = 9;
    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodeUtils() {}

    public static String randomCode() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < code.length; i++) {
            code[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }
}
