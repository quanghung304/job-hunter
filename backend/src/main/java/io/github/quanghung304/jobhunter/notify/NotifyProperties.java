package io.github.quanghung304.jobhunter.notify;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.notify.*} (config-spec §2). The bot token and chat id come from the environment
 * ({@code TELEGRAM_BOT_TOKEN}, {@code TELEGRAM_CHAT_ID}); without them nothing is sent.
 */
@ConfigurationProperties("app.notify")
public record NotifyProperties(
        @DefaultValue("0 30 8 * * *") String digestCron,
        @DefaultValue Telegram telegram) {

    /**
     * @param apiBaseUrl Bot API root; overridden by tests (WireMock)
     */
    public record Telegram(
            @DefaultValue("false") boolean enabled,
            String botToken,
            String chatId,
            @DefaultValue("https://api.telegram.org") URI apiBaseUrl) {

        /** Enabled and both secrets present. */
        public boolean usable() {
            return enabled && botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
        }
    }
}
