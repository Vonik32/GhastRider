package net.astra.ghastrider.command;

import net.astra.ghastrider.command.subcommand.Subcommand;
import net.astra.ghastrider.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Корневая команда /ghastrider. Делегирует в подкоманды.
 */
public final class GhastRiderCommand implements CommandExecutor, TabCompleter {

    private final Map<String, Subcommand> subcommands = new LinkedHashMap<>();
    private final MessageUtil messageUtil;

    public GhastRiderCommand(MessageUtil messageUtil, Subcommand... subs) {
        this.messageUtil = messageUtil;
        for (Subcommand s : subs) {
            subcommands.put(s.name().toLowerCase(Locale.ROOT), s);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/" + label + " <" + String.join("|", subcommands.keySet()) + ">");
            return true;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        Subcommand sub = subcommands.get(name);
        if (sub == null) {
            sender.sendMessage("§cНеизвестная подкоманда.");
            return true;
        }
        if (!sender.hasPermission(sub.permission())) {
            messageUtil.send(sender, "no-permission");
            return true;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        return sub.execute(sender, rest);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Subcommand> entry : subcommands.entrySet()) {
                if (!sender.hasPermission(entry.getValue().permission())) continue;
                if (entry.getKey().startsWith(prefix)) result.add(entry.getKey());
            }
            return result;
        }
        if (args.length >= 2) {
            Subcommand sub = subcommands.get(args[0].toLowerCase(Locale.ROOT));
            if (sub == null || !sender.hasPermission(sub.permission())) return List.of();
            String[] rest = Arrays.copyOfRange(args, 1, args.length);
            return sub.tabComplete(sender, rest);
        }
        return List.of();
    }
}
