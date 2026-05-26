package net.astra.ghastrider.command.subcommand;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Контракт подкоманды /ghastrider.
 */
public interface Subcommand {

    String name();

    String permission();

    boolean execute(CommandSender sender, String[] args);

    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
