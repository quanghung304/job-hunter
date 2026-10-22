package io.github.quanghung304.jobhunter.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.common.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The one Telegram client of the app, or none: messages are sent only when
 * {@code app.notify.telegram.enabled=true} and the bot token and chat id are set (WARN otherwise).
 */
@Component
@EnableConfigurationProperties(NotifyProperties.class)
public class TelegramGateway {

    private static final Logger log = LoggerFactory.getLogger(TelegramGateway.class);

    private final TelegramClient client;

    public TelegramGateway(NotifyProperties properties, Sleeper sleeper, ObjectMapper json) {
        NotifyProperties.Telegram settings = properties.telegram();
        this.client = settings.usable() ? new TelegramClient(settings, sleeper, json) : null;
        if (settings.enabled() && !settings.usable()) {
            log.warn("app.notify.telegram.enabled=true but TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is missing;"
                    + " no Telegram messages will be sent");
        } else if (!settings.enabled()) {
            log.info("Telegram notifications are off (app.notify.telegram.enabled=false)");
        }
    }

    public boolean enabled() {
        return client != null;
    }

    /** @throws TelegramClient.TelegramException when sending fails; nothing happens when disabled */
    public void send(String html) {
        if (client != null) {
            client.sendMessage(html);
        }
    }
}
