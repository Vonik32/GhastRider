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
        UUID playerId = player.getUniqueId();
        UUID ghastId = ghast.getUniqueId();
        // Если у этого ghast уже была пара (например, после prune не очищенная) — заменяем атомарно.
        UUID prevPlayer = ghastToPlayer.put(ghastId, playerId);
        if (prevPlayer != null && !prevPlayer.equals(playerId)) {
            playerToGhast.remove(prevPlayer, ghastId);
        }
        UUID prevGhast = playerToGhast.put(playerId, ghastId);
        if (prevGhast != null && !prevGhast.equals(ghastId)) {
            ghastToPlayer.remove(prevGhast, playerId);
        }

        // Managed Гаст всегда с aware=false; пилот работает через ванильную упряжку (body slot).
        ghast.setTarget(null);

        return true;
    }

    /**
     * Принудительно ссадить игрока (логаут, команда, ошибка).
     */
    public void dismount(Player player) {
        UUID playerId = player.getUniqueId();
        UUID ghastId = playerToGhast.remove(playerId);
        if (ghastId == null) {
            // На случай если в map нет, но физически сидит — всё равно очистим vehicle.
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof HappyGhast g) {
                g.removePassenger(player);
                // Удаляем только если запись действительно указывает на этого игрока.
                ghastToPlayer.remove(g.getUniqueId(), playerId);
                resetGhast(g);
            }
            return;
        }
        // Удаляем строго свою пару: ghastId -> playerId.
        ghastToPlayer.remove(ghastId, playerId);

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
        UUID ghastId = ghast.getUniqueId();
        UUID playerId = ghastToPlayer.remove(ghastId);
        if (playerId != null) {
            playerToGhast.remove(playerId, ghastId);
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
        UUID playerId = player.getUniqueId();
        UUID expectedGhast = playerToGhast.remove(playerId);
        if (expectedGhast != null) {
            ghastToPlayer.remove(expectedGhast, playerId);
        }
        if (vehicle instanceof HappyGhast g) {
            // Удалим только если запись действительно указывает на этого игрока,
            // чтобы не выбить чужую валидную пару.
            ghastToPlayer.remove(g.getUniqueId(), playerId);
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

    /**
     * Возвращает иммутабельный снимок текущих пар player -> ghast. Снимок защищает
     * вызывающую сторону от ConcurrentModification, если она вызывает методы,
     * мутирующие внутренние карты (notifyDismounted/dismount) во время итерации.
     */
    public Set<Map.Entry<UUID, UUID>> entries() {
        return Set.copyOf(playerToGhast.entrySet());
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
            UUID playerId = e.getKey();
            UUID ghastId = e.getValue();
            Player p = Bukkit.getPlayer(playerId);
            Entity g = Bukkit.getEntity(ghastId);
            if (p == null || !p.isOnline() || !(g instanceof HappyGhast ghast) || ghast.isDead()) {
                it.remove();
                // Удаляем строго парную запись, чтобы не выбить новую валидную пару,
                // если ghast был переиспользован между тиками.
                ghastToPlayer.remove(ghastId, playerId);
                if (g instanceof HappyGhast ghast2 && !ghast2.isDead()) {
                    resetGhast(ghast2);
                }
                removed++;
            }
        }
        // Дополнительно: отлавливаем orphan-записи в обратной карте
        // (ghastId без зеркальной пары в playerToGhast).
        Iterator<Map.Entry<UUID, UUID>> rit = ghastToPlayer.entrySet().iterator();
        while (rit.hasNext()) {
            Map.Entry<UUID, UUID> e = rit.next();
            UUID ghastId = e.getKey();
            UUID playerId = e.getValue();
            UUID mirror = playerToGhast.get(playerId);
            if (mirror == null || !mirror.equals(ghastId)) {
                rit.remove();
            }
        }
        return removed;
    }

    /**
     * Полная очистка состояния (используется при выгрузке плагина).
     */
    public void clearAll() {
        playerToGhast.clear();
        ghastToPlayer.clear();
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
