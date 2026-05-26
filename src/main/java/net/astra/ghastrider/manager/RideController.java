package net.astra.ghastrider.manager;

import net.astra.ghastrider.config.ConfigManager;
import net.astra.ghastrider.data.GhastData;
import net.astra.ghastrider.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Учёт активных всадников. Хранит сопоставление playerId -> ghastId.
 * Все изменения должны идти через этот контроллер, чтобы FlightTask видел
 * актуальный список и корректно отрабатывал spurious dismount-ы.
 */
public final class RideController {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final GhastData ghastData;
    private final MessageUtil messageUtil;

    private final Map<UUID, UUID> playerToGhast = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> ghastToPlayer = new ConcurrentHashMap<>();

    public RideController(JavaPlugin plugin, ConfigManager configManager, GhastData ghastData, MessageUtil messageUtil) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.ghastData = ghastData;
        this.messageUtil = messageUtil;
    }

    public boolean mount(Player player, HappyGhast ghast) {
        if (!ghastData.isManaged(ghast)) {
            return false;
        }
        if (!ghastData.isOwner(ghast, player) && !player.hasPermission("ghastrider.bypass.owner")) {
            messageUtil.send(player, "not-owner");
            return false;
        }
        if (!player.getPassengers().isEmpty() || player.getVehicle() != null) {
            return false;
        }

        boolean ok = ghast.addPassenger(player);
        if (!ok) {
            return false;
        }
        playerToGhast.put(player.getUniqueId(), ghast.getUniqueId());
        ghastToPlayer.put(ghast.getUniqueId(), player.getUniqueId());

        // Managed Гаст всегда с aware=false; пилот работает через ванильную упряжку (body slot).
        ghast.setTarget(null);

        return true;
    }

    /**
     * Принудительно ссадить игрока (логаут, команда, ошибка).
     */
    public void dismount(Player player) {
        UUID ghastId = playerToGhast.remove(player.getUniqueId());
        if (ghastId == null) {
            // На случай если в map нет, но физически сидит — всё равно очистим vehicle.
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof HappyGhast g) {
                g.removePassenger(player);
                ghastToPlayer.remove(g.getUniqueId());
                resetGhast(g);
            }
            return;
        }
        ghastToPlayer.remove(ghastId);

        HappyGhast ghast = findGhast(ghastId);
        if (ghast != null) {
            ghast.removePassenger(player);
            resetGhast(ghast);
        } else if (player.getVehicle() instanceof HappyGhast g) {
            g.removePassenger(player);
            resetGhast(g);
        }
    }

    /**
     * Если на гасте кто-то сидит — ссадить (используется при removeHarness).
     */
    public void dismountIfRiding(HappyGhast ghast) {
        UUID playerId = ghastToPlayer.remove(ghast.getUniqueId());
        if (playerId != null) {
            playerToGhast.remove(playerId);
        }
        for (Entity passenger : new HashSet<>(ghast.getPassengers())) {
            ghast.removePassenger(passenger);
        }
        resetGhast(ghast);
    }

    /**
     * Синхронизация с EntityDismountEvent — игрок слез сам.
     */
    public void notifyDismounted(Player player, Entity vehicle) {
        UUID expectedGhast = playerToGhast.remove(player.getUniqueId());
        if (expectedGhast != null) {
            ghastToPlayer.remove(expectedGhast);
        }
        if (vehicle instanceof HappyGhast g) {
            ghastToPlayer.remove(g.getUniqueId());
            resetGhast(g);
        }
    }

    private void resetGhast(HappyGhast ghast) {
        ghast.setGravity(true);
    }

    @Nullable
    private HappyGhast findGhast(UUID id) {
        Entity entity = Bukkit.getEntity(id);
        return (entity instanceof HappyGhast g) ? g : null;
    }

    public boolean isRiding(Player player) {
        return playerToGhast.containsKey(player.getUniqueId());
    }

    public boolean isBeingRidden(HappyGhast ghast) {
        return ghastToPlayer.containsKey(ghast.getUniqueId());
    }

    public Set<Map.Entry<UUID, UUID>> entries() {
        return playerToGhast.entrySet();
    }

    /**
     * Удалить пары, у которых сторона больше невалидна (отсутствует или мертва).
     * Вызывается из FlightTask. Возвращает количество удалённых записей.
     */
    public int prune() {
        int removed = 0;
        Iterator<Map.Entry<UUID, UUID>> it = playerToGhast.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Entity g = Bukkit.getEntity(e.getValue());
            if (p == null || !p.isOnline() || !(g instanceof HappyGhast ghast) || ghast.isDead()) {
                it.remove();
                ghastToPlayer.remove(e.getValue());
                if (g instanceof HappyGhast ghast2) {
                    resetGhast(ghast2);
                }
                removed++;
            }
        }
        return removed;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
