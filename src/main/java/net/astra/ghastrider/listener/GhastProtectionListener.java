package net.astra.ghastrider.listener;

import net.astra.ghastrider.config.ConfigManager;
import net.astra.ghastrider.config.ProtectionSettings;
import net.astra.ghastrider.data.GhastData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Защита managed-Гаста: урон от чужих, стихии, нежелательное таргетирование, мount от чужих.
 */
public final class GhastProtectionListener implements Listener {

    private final ConfigManager configManager;
    private final GhastData ghastData;

    public GhastProtectionListener(ConfigManager configManager, GhastData ghastData) {
        this.configManager = configManager;
        this.ghastData = ghastData;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof HappyGhast ghast)) {
            return;
        }
        if (!ghastData.isManaged(ghast)) {
            return;
        }
        ProtectionSettings ps = configManager.getProtectionSettings();
        switch (event.getCause()) {
            case LAVA:
            case HOT_FLOOR:
                if (ps.immuneToLava()) event.setCancelled(true);
                break;
            case FIRE:
            case FIRE_TICK:
                if (ps.immuneToFire()) event.setCancelled(true);
                break;
            case DROWNING:
                if (ps.immuneToDrowning()) event.setCancelled(true);
                break;
            default:
                break;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof HappyGhast ghast)) {
            return;
        }
        if (!ghastData.isManaged(ghast)) {
            return;
        }
        ProtectionSettings ps = configManager.getProtectionSettings();
        Entity damager = resolveDamager(event.getDamager());

        if (damager instanceof Player p) {
            boolean isOwner = ghastData.isOwner(ghast, p);
            if (isOwner) {
                if (ps.denyOwnerDamage()) {
                    event.setCancelled(true);
                }
            } else {
                if (ps.denyDamageFromOthers()) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        if (ps.denyDamageFromOthers()) {
            event.setCancelled(true);
        }
    }

    private Entity resolveDamager(Entity raw) {
        if (raw instanceof Projectile projectile) {
            ProjectileSource src = projectile.getShooter();
            if (src instanceof Entity e) {
                return e;
            }
        }
        return raw;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof HappyGhast ghast)) {
            // Если кто-то таргетит нашего гаста.
            if (event.getTarget() instanceof HappyGhast targetGhast && ghastData.isManaged(targetGhast)
                    && configManager.getProtectionSettings().preventTargeting()) {
                event.setCancelled(true);
            }
            return;
        }
        if (!ghastData.isManaged(ghast)) {
            return;
        }
        if (configManager.getProtectionSettings().preventTargeting()) {
            event.setCancelled(true);
            ghast.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTargetLiving(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof HappyGhast ghast
                && ghastData.isManaged(ghast)
                && configManager.getProtectionSettings().preventTargeting()) {
            event.setCancelled(true);
            ghast.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!(event.getMount() instanceof HappyGhast ghast)) {
            return;
        }
        if (!ghastData.isManaged(ghast)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        boolean isOwner = ghastData.isOwner(ghast, player);
        if (!isOwner && !player.hasPermission("ghastrider.bypass.owner")
                && configManager.getProtectionSettings().preventMountByOthers()) {
            event.setCancelled(true);
        }
    }

    /** Заглушка для совместимости — для других LivingEntity-targets отдельной логики не нужно. */
    @SuppressWarnings("unused")
    private void noop(LivingEntity ignored) {
    }
}
