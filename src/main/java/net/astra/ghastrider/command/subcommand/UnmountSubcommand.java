package net.astra.ghastrider.command.subcommand;

import net.astra.ghastrider.manager.RideController;
import net.astra.ghastrider.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class UnmountSubcommand implements Subcommand {

    private final RideController rideController;
    private final MessageUtil messageUtil;

    public UnmountSubcommand(RideController rideController, MessageUtil messageUtil) {
        this.rideController = rideController;
        this.messageUtil = messageUtil;
    }

    @Override
    public String name() {
        return "unmount";
    }

    @Override
    public String permission() {
        return "ghastrider.use";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            if (!sender.hasPermission("ghastrider.admin")) {
                messageUtil.send(sender, "no-permission");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("Игрок не найден: " + args[0]);
                return true;
            }
            rideController.dismount(target);
            messageUtil.send(sender, "dismount-success");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Укажи игрока: /ghastrider unmount <player>");
            return true;
        }
        rideController.dismount(player);
        messageUtil.send(player, "dismount-success");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ghastrider.admin") || args.length != 1) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            result.add(p.getName());
        }
        return result;
    }
}
