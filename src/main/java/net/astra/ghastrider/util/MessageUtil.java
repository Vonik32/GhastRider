package net.astra.ghastrider.util;

import net.astra.ghastrider.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Утилиты по работе с сообщениями (MiniMessage + префикс из конфига).
 */
public final class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ConfigManager configManager;

    public MessageUtil(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Component parse(String raw, TagResolver... resolvers) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return MM.deserialize(raw, resolvers);
    }

    public Component prefixed(String key, TagResolver... resolvers) {
        String prefix = configManager.getPrefix();
        String body = configManager.getMessage(key);
        return parse(prefix).append(parse(body, resolvers));
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        Component msg = prefixed(key, resolvers);
        to.sendMessage(msg);
    }

    public void sendActionBar(Player to, String key, TagResolver... resolvers) {
        to.sendActionBar(parse(configManager.getMessage(key), resolvers));
    }

    public static TagResolver placeholder(String key, String value) {
        return Placeholder.parsed(key, value == null ? "" : value);
    }
}
