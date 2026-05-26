package net.astra.ghastrider.listener;

import net.astra.ghastrider.manager.RideController;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Реакция на жизненный цикл игрока: выход, кик, dismount, смена мира.
 * При выходе верхом — принудительный dismount.
 */
public final class PlayerLifecycleListener implements Listener {

    private final RideController rideController;

    public PlayerLifecycleListener(RideController rideController) {
        this.rideController = rideController;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (rideController.isRiding(player)) {
            rideController.dismount(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        if (rideController.isRiding(player)) {
            rideController.dismount(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Entity vehicle = event.getDismounted();
        rideController.notifyDismounted(player, vehicle);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (rideController.isRiding(player)) {
            rideController.dismount(player);
        }
    }
}
