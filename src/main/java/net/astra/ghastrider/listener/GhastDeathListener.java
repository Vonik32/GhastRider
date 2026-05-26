package net.astra.ghastrider.listener;

import net.astra.ghastrider.data.GhastData;
import net.astra.ghastrider.manager.HarnessManager;
import net.astra.ghastrider.manager.RideController;
import org.bukkit.entity.HappyGhast;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * При смерти managed-Гаста добавляет в drops кастомную упряжку (через ItemsAdder)
 * и очищает регистрацию в RideController. Ванильный лут не трогается.
 */
public final class GhastDeathListener implements Listener {

    private final GhastData ghastData;
    private final HarnessManager harnessManager;
    private final RideController rideController;

    public GhastDeathListener(GhastData ghastData, HarnessManager harnessManager, RideController rideController) {
        this.ghastData = ghastData;
        this.harnessManager = harnessManager;
        this.rideController = rideController;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof HappyGhast ghast)) {
            return;
        }
        if (!ghastData.isManaged(ghast)) {
            return;
        }
        ItemStack drop = harnessManager.buildDeathDrop(ghast);
        if (drop != null) {
            event.getDrops().add(drop);
        }
        rideController.dismountIfRiding(ghast);
        ghastData.clear(ghast);
    }
}
