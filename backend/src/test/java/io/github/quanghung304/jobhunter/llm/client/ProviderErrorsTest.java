package io.github.quanghung304.jobhunter.llm.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProviderErrorsTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1} ms")
    @CsvSource(nullValues = "NULL", textBlock = """
            2m59.56s,                     179560
            7.66s,                        7660
            1h2m3s,                       3723000
            350ms,                        350
            17,                           17000
            1.5,                          1500
            2026-10-17T12:00:30Z,         30000
            2026-10-17T11:00:00Z,         0
            soon,                         NULL
            NULL,                         NULL
            """)
    void resetValues(String value, Long expectedMillis) {
        Duration reset = ProviderErrors.reset(value, CLOCK);

        assertThat(reset).isEqualTo(expectedMillis == null ? null : Duration.ofMillis(expectedMillis));
    }

    @Test
    void retryAfterSecondsOrHttpDate() {
        assertThat(ProviderErrors.retryAfter("12", CLOCK)).isEqualTo(Duration.ofSeconds(12));
        assertThat(ProviderErrors.retryAfter("Sat, 17 Oct 2026 12:01:00 GMT", CLOCK)).isEqualTo(Duration.ofMinutes(1));
        assertThat(ProviderErrors.retryAfter("later", CLOCK)).isNull();
        assertThat(ProviderErrors.retryAfter(" ", CLOCK)).isNull();
    }

    @ParameterizedTest(name = "[{index}] {0} {1} retry={2}s → {3}")
    @CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
            429 | on tokens per minute (TPM): Limit 12000          | 5    | RATE_LIMIT
            429 | on requests per day (RPD): Limit 1000            | 87   | QUOTA_EXHAUSTED
            429 | Rate limit exceeded: free-models-per-day         | NULL | QUOTA_EXHAUSTED
            429 | You exceeded your current quota                  | NULL | QUOTA_EXHAUSTED
            429 | Too many requests                                | 7200 | QUOTA_EXHAUSTED
            429 | Too many requests                                | NULL | RATE_LIMIT
            401 | Invalid API Key                                  | NULL | AUTH
            403 | forbidden                                        | NULL | AUTH
            404 | model_not_found                                  | NULL | MODEL_NOT_FOUND
            408 | timeout                                          | NULL | TIMEOUT
            500 | boom                                             | NULL | SERVER
            529 | overloaded                                       | NULL | SERVER
            400 | json_validate_failed                             | NULL | INVALID_OUTPUT
            400 | bad parameter                                    | NULL | BAD_REQUEST
            422 | unprocessable                                    | NULL | BAD_REQUEST
            """)
    void classify(int status, String body, Long retryAfterSeconds, LlmErrorKind expected) {
        Duration retryAfter = retryAfterSeconds == null ? null : Duration.ofSeconds(retryAfterSeconds);

        assertThat(ProviderErrors.classify(status, body, retryAfter)).isEqualTo(expected);
    }

    @Test
    void shortenKeepsOneLineOf2000Chars() {
        assertThat(ProviderErrors.shorten("a\n  b")).isEqualTo("a b");
        assertThat(ProviderErrors.shorten("x".repeat(3000))).hasSize(2000);
        assertThat(ProviderErrors.shorten(null)).isNull();
    }
}
