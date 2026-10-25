package io.shortly.dashboard.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.shortly.dashboard.exception.InvalidTargetUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlUtilsTest {

    private static final String SHORT_BASE_URL = "https://short.ly/";

    @ParameterizedTest
    @CsvSource({
        "HTTPS://Example.COM:443/Path?Q=1, https://example.com/Path?Q=1",
        "http://example.com:8080/A/b, http://example.com:8080/A/b",
        "'  https://example.com/a  ', https://example.com/a",
        "https://bücher.example/x, https://xn--bcher-kva.example/x",
        "https://www.google.com/search?q=c%2B%2B, https://www.google.com/search?q=c%2B%2B",
        "https://ja.wikipedia.org/wiki/東京, https://ja.wikipedia.org/wiki/%E6%9D%B1%E4%BA%AC",
        "https:\\\\example.com\\x, https://example.com\\x",
        "https://365.example/, https://365.example/",
        "https://notshort.ly/x, https://notshort.ly/x",
        "https://web.archive.org/web/2020/https://example.com/page, https://web.archive.org/web/2020/https://example.com/page",
        "https://Example.COM:443/a?a=1&&b=2, https://example.com/a?a=1&&b=2",
        "https://example.com/a?=x, https://example.com/a?=x",
        "https://example.com/a?, https://example.com/a?",
        "https://example.com/a#, https://example.com/a#",
        "https://example.com?, https://example.com?",
        "https://example.com/a/../b, https://example.com/a/../b",
        "https://example.com/a b, https://example.com/a%20b",
        "https://example.com/a|b^c`d{e}f\"g<h>i, https://example.com/a%7Cb%5Ec%60d%7Be%7Df%22g%3Ch%3Ei",
        "https://example.com/caf\u00e9?q=\u00fc, https://example.com/caf%C3%A9?q=%C3%BC",
        "https://example.com/100%25 sure, https://example.com/100%25%20sure",
        "https://evil.com\\@good.com/x, https://evil.com\\@good.com/x",
        "https://example.com:8443?x=1#f, https://example.com:8443?x=1#f",
    })
    void normalises(String raw, String stored) {
        assertThat(UrlUtils.normaliseTarget(raw, SHORT_BASE_URL)).hasToString(stored);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "not a url",
                "javascript:alert(1)",
                "ftp://x.com/",
                "https://",
                "http://exa mple.com/",
                "https://example.com/a\r\nSet-Cookie: x=1",
                "https://example.com/a\tb",
                "https://example.com/a\u007fb",
                "https://example.com/\u0000",
                "https://user:pw@example.com/",
                "https://paypal.com@evil.com/",
                "https://short.ly/abc",
                "https://APP.short.ly/abc",
                "https:\\\\short.ly\\abc",
                "https://localhost/",
                "https://foo.localhost/",
                "http://127.0.0.1/",
                "http://2130706433/",
                "http://127.1/",
                "http://0x7f.0.0.1/",
                "http://0177.0.0.1/",
                "http://169.254.169.254/",
                "http://93.184.216.34/",
                "http://[::1]/",
                "http://[::ffff:127.0.0.1]/",
                "http://[fd00::1]/",
                "http://localhost./admin",
                "https://short.ly./abc",
                "http://localhost。/",
            })
    void refuses(String raw) {
        assertThatThrownBy(() -> UrlUtils.normaliseTarget(raw, SHORT_BASE_URL))
                .isInstanceOf(InvalidTargetUrlException.class);
    }

    @Test
    void validatesTheHostBrowsersSeeBehindABackslash() {
        assertThatThrownBy(() -> normalise("https://short.ly\\@good.com/x"))
                .isInstanceOf(InvalidTargetUrlException.class)
                .hasMessageContaining("own domain");
    }

    @Test
    void refusesWhatIsTooLongOnceStored() {
        assertThatThrownBy(() -> normalise("https://e.com/" + "a".repeat(2048)))
                .hasMessageContaining("longer than 2048");
        assertThatThrownBy(() -> normalise("https://e.com/" + "東".repeat(1000)))
                .hasMessageContaining("longer than 2048");
    }

    private static void normalise(String raw) {
        UrlUtils.normaliseTarget(raw, SHORT_BASE_URL);
    }
}
