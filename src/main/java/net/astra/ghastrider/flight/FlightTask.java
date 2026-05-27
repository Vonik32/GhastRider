package net.astra.ghastrider.flight;

import net.astra.ghastrider.config.ConfigManager;
import net.astra.ghastrider.data.GhastData;
import net.astra.ghastrider.manager.RideController;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

/**
 * Тик managed-Гастов:
 *  - Никакой кастомной физики, никакого зануления velocity.
 *  - Просто синхронизация ride-entries и поддержание aware=false
 *    (managed-Гаст не должен сам летать/крутиться).
 *  - Инерция от райдера сохраняется естественно через ванильный physics tick.
 */
public final class FlightTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final RideController rideController;
    private final GhastData ghastData;

    public FlightTask(JavaPlugin plugin,
                      ConfigManager configManager,
                      RideController rideController,
                      GhastData ghastData) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rideController = rideController;
        this.ghastData = ghastData;
    }

    public void start() {
        runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public void run() {
        rideController.prune();

        // Синхронизация ride-entries. entries() возвращает иммутабельный снимок,
        // поэтому модификации карт внутри notifyDismounted/dismount безопасны.
        for (Map.Entry<UUID, UUID> entry : rideController.entries()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            Entity vehicle = Bukkit.getEntity(entry.getValue());
            if (!(vehicle instanceof HappyGhast ghast) || ghast.isDead() || !ghast.isValid()) {
                continue;
            }
            if (!ghast.getPassengers().contains(player)) {
                rideController.notifyDismounted(player, ghast);
            } else if (player.getWorld() != ghast.getWorld()) {
                rideController.dismount(player);
            }
        }

        // Поддержание состояния managed-Гастов: AI выключен, цели — нет.
        // Iterate over a snapshot to avoid CME, when world plugin events spawn entities.
        for (var world : Bukkit.getWorlds()) {
            for (HappyGhast ghast : world.getEntitiesByClass(HappyGhast.class)) {
                if (ghast == null || ghast.isDead() || !ghast.isValid()) {
                    continue;
                }
                if (!ghastData.isManaged(ghast)) {
                    continue;
                }
                if (ghast.isAware()) {
                    ghast.setAware(false);
                }
                if (ghast.getTarget() != null) {
                    ghast.setTarget(null);
                }
            }
        }
    }
}



