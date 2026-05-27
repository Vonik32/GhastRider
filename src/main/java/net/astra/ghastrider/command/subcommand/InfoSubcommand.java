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
        
        String ownerName = ghastData.getOwnerName(ghast);
        if (ownerName == null && ownerId != null) {
            Player onlineOwner = Bukkit.getPlayer(ownerId);
            if (onlineOwner != null) {
                ownerName = onlineOwner.getName();
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(ownerId);
                ownerName = offline.getName() != null ? offline.getName() : "?";
            }
        }
        if (ownerName == null) {
            ownerName = "?";
        }
        
        boolean isActualOwner = ownerId != null && ownerId.equals(player.getUniqueId());
        String ownerColor = isActualOwner ? "<green>" : "<red>";
        String ownerFormatted = ownerColor + ownerName + (isActualOwner ? "</green>" : "</red>");
        
        String harnessUsed = tier == null ? "?" : tier.getDisplayName();
        
        messageUtil.send(player, "info-format",
                MessageUtil.placeholder("owner", ownerFormatted),
                MessageUtil.placeholder("tier", harnessUsed));
        return true;
    }
}
