package net.astra.ghastrider.command.subcommand;

import net.astra.ghastrider.config.HarnessTier;
import net.astra.ghastrider.data.GhastData;
import net.astra.ghastrider.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class InfoSubcommand implements Subcommand {

    private final GhastData ghastData;
    private final MessageUtil messageUtil;

    public InfoSubcommand(GhastData ghastData, MessageUtil messageUtil) {
        this.ghastData = ghastData;
        this.messageUtil = messageUtil;
    }

    @Override
    public String name() {
        return "info";
    }

    @Override
    public String permission() {
        return "ghastrider.use";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        Entity target = player.getTargetEntity(8);
        if (!(target instanceof HappyGhast ghast)) {
            messageUtil.send(player, "info-no-target");
            return true;
        }
        if (!ghastData.isManaged(ghast)) {
            messageUtil.send(player, "info-not-managed");
            return true;
        }
        UUID ownerId = ghastData.getOwner(ghast);
        HarnessTier tier = ghastData.getTier(ghast);
        OfflinePlayer owner = ownerId == null ? null : Bukkit.getOfflinePlayer(ownerId);
        String ownerName = (owner == null || owner.getName() == null) ? "?" : owner.getName();
        String tierName = tier == null ? "?" : tier.name();
        messageUtil.send(player, "info-format",
                MessageUtil.placeholder("owner", ownerName),
                MessageUtil.placeholder("tier", tierName));
        return true;
    }
}
