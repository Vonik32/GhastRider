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

        String verb = amount == 1 ? "Выдана" : "Выдано";
        messageUtil.send(sender, "give-success",
                MessageUtil.placeholder("verb", verb),
                MessageUtil.placeholder("amount", String.valueOf(amount)),
                MessageUtil.placeholder("item", getFormattedItemName(itemId, amount)),
                MessageUtil.placeholder("player", target.getName()));
        return true;
    }

    private String getFormattedItemName(String itemId, int amount) {
        int lastDigit = amount % 10;
        int lastTwoDigits = amount % 100;
        boolean isOne = (lastDigit == 1 && lastTwoDigits != 11);
        boolean isFew = (lastDigit >= 2 && lastDigit <= 4 && (lastTwoDigits < 10 || lastTwoDigits >= 20));

        switch (itemId) {
            case "base_harness":
                String baseWord = isOne ? "базовая упряжка" : (isFew ? "базовые упряжки" : "базовых упряжек");
                return "<gradient:#c0c0c0:#ffffff><b>" + baseWord + "</b></gradient>";
            case "iron_harness":
                String ironWord = isOne ? "железная упряжка" : (isFew ? "железные упряжки" : "железных упряжек");
                return "<gradient:#cfd8dc:#90a4ae><b>" + ironWord + "</b></gradient>";
            case "gold_harness":
                String goldWord = isOne ? "золотая упряжка" : (isFew ? "золотые упряжки" : "золотых упряжек");
                return "<gradient:#ffe066:#ffb300><b>" + goldWord + "</b></gradient>";
            case "diamond_harness":
                String diamondWord = isOne ? "алмазная упряжка" : (isFew ? "алмазные упряжки" : "алмазных упряжек");
                return "<gradient:#5cdcff:#a0f0ff><b>" + diamondWord + "</b></gradient>";
            case "netherite_harness":
                String netheriteWord = isOne ? "незеритовая упряжка" : (isFew ? "незеритовые упряжки" : "незеритовых упряжек");
                return "<gradient:#5d4037:#ff6e40><b>" + netheriteWord + "</b></gradient>";
            case "essence_speed_1":
                String speed1 = isOne ? "эссенция скорости I" : (isFew ? "эссенции скорости I" : "эссенций скорости I");
                return "<#7ee8ff><b>" + speed1 + "</b>";
            case "essence_speed_2":
                String speed2 = isOne ? "эссенция скорости II" : (isFew ? "эссенции скорости II" : "эссенций скорости II");
                return "<#00bcd4><b>" + speed2 + "</b>";
            case "essence_regen_1":
                String regen1 = isOne ? "эссенция регенерации I" : (isFew ? "эссенции регенерации I" : "эссенций регенерации I");
                return "<#ff8aa3><b>" + regen1 + "</b>";
            case "essence_regen_2":
                String regen2 = isOne ? "эссенция регенерации II" : (isFew ? "эссенции регенерации II" : "эссенций регенерации II");
                return "<#e91e63><b>" + regen2 + "</b>";
            case "essence_fire_1":
                String fire1 = isOne ? "эссенция пламени I" : (isFew ? "эссенции пламени I" : "эссенций пламени I");
                return "<#ff7043><b>" + fire1 + "</b>";
            default:
                return itemId;
        }
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
