package net.astra.ghastrider.command.subcommand;

import net.astra.ghastrider.item.ItemManager;
import net.astra.ghastrider.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /ghastrider give <player> <item_id> [amount]
 * Выдаёт игроку кастомный предмет (с PDC-маркером) без необходимости крафта.
 */
public final class GiveSubcommand implements Subcommand {

    private final ItemManager itemManager;
    private final MessageUtil messageUtil;

    public GiveSubcommand(ItemManager itemManager, MessageUtil messageUtil) {
        this.itemManager = itemManager;
        this.messageUtil = messageUtil;
    }

    @Override
    public String name() {
        return "give";
    }

    @Override
    public String permission() {
        return "ghastrider.admin";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageUtil.send(sender, "give-usage");
            return true;
        }
        String playerName = args[0];
        String itemId = args[1].toLowerCase(Locale.ROOT);
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                messageUtil.send(sender, "give-usage");
                return true;
            }
            if (amount < 1) amount = 1;
            if (amount > 64) amount = 64;
        }

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            messageUtil.send(sender, "give-unknown-player");
            return true;
        }

        ItemStack stack = itemManager.createStack(itemId, amount);
        if (stack == null) {
            messageUtil.send(sender, "give-unknown-item",
                    MessageUtil.placeholder("item", itemId),
                    MessageUtil.placeholder("available", String.join(", ", itemManager.ids())));
            return true;
        }

        Map<Integer, ItemStack> overflow = new HashMap<>(target.getInventory().addItem(stack));
        if (!overflow.isEmpty()) {
            Location loc = target.getLocation();
            for (ItemStack remainder : overflow.values()) {
                if (loc.getWorld() != null) {
                    Item dropped = loc.getWorld().dropItemNaturally(loc, remainder);
                    dropped.setCanMobPickup(false);
                }
            }
        }

        messageUtil.send(sender, "give-success",
                MessageUtil.placeholder("amount", String.valueOf(amount)),
                MessageUtil.placeholder("item", itemId),
                MessageUtil.placeholder("player", target.getName()));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(p.getName());
                }
            }
            return result;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String id : itemManager.ids()) {
                if (id.startsWith(prefix)) {
                    result.add(id);
                }
            }
            return result;
        }
        if (args.length == 3) {
            return List.of("1", "8", "16", "32", "64");
        }
        return List.of();
    }
}
