package net.astra.ghastrider.listener;

import net.astra.ghastrider.config.ConfigManager;
import net.astra.ghastrider.data.GhastData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.HappyGhast;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Подавляет ванильный AI managed-Гаста: фаерболы запрещены, появляющиеся
 * Fireball'ы со shooter=managed гаста — отменяются на старте.
 */
public final class GhastAIListener implements Listener {

    private final ConfigManager configManager;
    private final GhastData ghastData;

    public GhastAIListener(ConfigManager configManager, GhastData ghastData) {
        this.configManager = configManager;
        this.ghastData = ghastData;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!configManager.getProtectionSettings().preventFireballs()) {
            return;
        }
        ProjectileSource src = event.getEntity().getShooter();
        if (src instanceof HappyGhast ghast && ghastData.isManaged(ghast)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (!configManager.getProtectionSettings().preventFireballs()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof Fireball fireball)) {
            return;
        }
        ProjectileSource src = fireball.getShooter();
        if (src instanceof HappyGhast ghast && ghastData.isManaged(ghast)) {
            event.setCancelled(true);
        }
    }
}
