package io.shortly.dashboard.util;

import io.shortly.dashboard.exception.InvalidTargetUrlException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.InvalidUrlException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder.ParserType;

/**
 * Checks, and lightly normalises, the target URL of a new short link, parsed the way browsers parse
 * it.
 */
public final class UrlUtils {

    private static final int MAX_LENGTH = 2048;
    private static final Pattern DIGITS_AND_DOTS = Pattern.compile("[0-9.]+");

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private UrlUtils() {}

    /** The URL to store, or an {@link InvalidTargetUrlException} saying why it cannot be one. */
    public static String normaliseTarget(@Nullable String raw, String shortBaseUrl) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidTargetUrlException("target URL is empty");
        }
        rejectControlCharacters(raw);
        UriComponents url = parse(raw).build();
        if (!"http".equals(url.getScheme()) && !"https".equals(url.getScheme())) {
            throw new InvalidTargetUrlException("only http and https URLs are allowed");
        }
        if (url.getUserInfo() != null) {
            throw new InvalidTargetUrlException("credentials in the URL are not allowed");
        }
        String host = url.getHost();
        if (host == null) {
            throw new InvalidTargetUrlException("target URL has no host");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            throw new InvalidTargetUrlException("a host name must not end with a dot");
        }
        String shortHost = hostOf(shortBaseUrl);
        if (host.equals(shortHost) || host.endsWith("." + shortHost)) {
            throw new InvalidTargetUrlException("cannot shorten our own domain");
        }
        if (host.equals("localhost") || host.endsWith(".localhost") || isIpAddress(host)) {
            throw new InvalidTargetUrlException(
                    "only public host names are allowed, not localhost or IP addresses");
        }
        return stored(raw.strip(), "http".equals(url.getScheme()) ? "http" : "https", url, host);
    }

    private static void rejectControlCharacters(String raw) {
        if (raw.codePoints().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            throw new InvalidTargetUrlException("target URL contains a control character");
        }
    }

    private static UriComponentsBuilder parse(String raw) {
        try {
            return UriComponentsBuilder.fromUriString(raw, ParserType.WHAT_WG);
        } catch (InvalidUrlException e) {
            throw new InvalidTargetUrlException("target URL is not a valid URL", e);
        }
    }

    private static String hostOf(String shortBaseUrl) {
        String host = URI.create(shortBaseUrl).getHost();
        if (host == null) {
            throw new IllegalStateException("shortly.short-base-url has no host: " + shortBaseUrl);
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private static boolean isIpAddress(String host) {
        return host.startsWith("[") || DIGITS_AND_DOTS.matcher(host).matches();
    }

    /**
     * The URL to store: scheme, host and a non-default port rebuilt from the parse, everything
     * after the authority as the caller wrote it, except for characters a {@code Location} header
     * cannot carry, which are percent-encoded.
     */
    private static String stored(String raw, String scheme, UriComponents url, String host) {
        int start = raw.indexOf(':') + 1;
        while (start < raw.length() && (raw.charAt(start) == '/' || raw.charAt(start) == '\\')) {
            start++;
        }
        int end = start;
        while (end < raw.length() && "/\\?#".indexOf(raw.charAt(end)) < 0) {
            end++;
        }
        StringBuilder stored = new StringBuilder(scheme).append("://").append(host);
        if (url.getPort() != -1) {
            stored.append(':').append(url.getPort());
        }
        raw.substring(end).codePoints().forEach(c -> append(stored, c));
        if (stored.length() > MAX_LENGTH) {
            throw new InvalidTargetUrlException(
                    "target URL is longer than " + MAX_LENGTH + " characters");
        }
        return stored.toString();
    }

    private static void append(StringBuilder out, int c) {
        if (c > 0x7E || " \"<>`{}|^".indexOf(c) >= 0) {
            for (byte b : new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8)) {
                out.append('%').append(HEX.toHexDigits(b));
            }
        } else {
            out.append((char) c);
        }
    }
}
