package net.astra.ghastrider.manager;

import net.astra.ghastrider.config.ConfigManager;
import net.astra.ghastrider.data.GhastData;
import net.astra.ghastrider.data.PdcKeys;
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
    private final PdcKeys keys;
    private final MessageUtil messageUtil;

    private final Map<UUID, UUID> playerToGhast = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> ghastToPlayer = new ConcurrentHashMap<>();
    private final Set<UUID> mountBypass = ConcurrentHashMap.newKeySet();
    private final Set<UUID> managedGhasts = ConcurrentHashMap.newKeySet();

    public RideController(JavaPlugin plugin, ConfigManager configManager, GhastData ghastData, PdcKeys keys, MessageUtil messageUtil) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.ghastData = ghastData;
        this.keys = keys;
        this.messageUtil = messageUtil;
    }

    public boolean mount(Player player, HappyGhast ghast) {
        if (!ghastData.isManaged(ghast)) {
            return false;
        }
        if (!player.getPassengers().isEmpty() || player.getVehicle() != null) {
            return false;
        }

        boolean isOwner = ghastData.isOwner(ghast, player) || player.hasPermission("ghastrider.bypass.owner");

        // Gather current real riders
        java.util.List<Player> realRiders = new java.util.ArrayList<>();
        for (Entity e : ghast.getPassengers()) {
            if (e instanceof Player p && !p.equals(player)) {
                realRiders.add(p);
            }
        }

        // Check if owner is already riding
        boolean ownerAlreadyRiding = false;
        UUID ownerId = ghastData.getOwner(ghast);
        if (ownerId != null) {
            for (Player p : realRiders) {
                if (p.getUniqueId().equals(ownerId)) {
                    ownerAlreadyRiding = true;
                    break;
                }
            }
        }

        if (isOwner) {
            if (realRiders.size() >= 4) {
                return false;
            }
            // Put owner at the very front
            realRiders.add(0, player);
        } else {
            // Non-owner seat capacity limit check:
            // If owner is riding, total real players can be 4.
            // If owner is not riding, we have a dummy at index 0, so max real passenger seats is 3.
            int maxCapacity = ownerAlreadyRiding ? 4 : 3;
            if (realRiders.size() >= maxCapacity) {
                return false;
            }
            realRiders.add(player);
        }

        rebuildPassengers(ghast, realRiders);
        return true;
    }

    public boolean mountPassenger(Player player, HappyGhast ghast) {
        // mount handles passenger mounting automatically based on owner UUID
        return mount(player, ghast);
    }

    private Entity spawnDummySeat(HappyGhast ghast) {
        return ghast.getWorld().spawn(ghast.getLocation(), org.bukkit.entity.ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setPersistent(false);
            as.getPersistentDataContainer().set(keys.managed, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 2);
        });
    }

    private boolean isDummySeat(Entity entity) {
        if (!(entity instanceof org.bukkit.entity.ArmorStand)) return false;
        Byte val = entity.getPersistentDataContainer()
            .get(keys.managed, org.bukkit.persistence.PersistentDataType.BYTE);
        return val != null && val == (byte) 2;
    }

    public void cleanUpDummies(HappyGhast ghast) {
        java.util.List<Entity> passengers = new java.util.ArrayList<>(ghast.getPassengers());
        boolean hasRealPlayer = false;
        for (Entity e : passengers) {
            if (e instanceof Player) {
                hasRealPlayer = true;
                break;
            }
        }
        if (!hasRealPlayer) {
            for (Entity e : passengers) {
                if (isDummySeat(e)) {
                    ghast.removePassenger(e);
                    e.remove();
                }
            }
        }
    }

    public void dismount(Player player) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof HappyGhast ghast)) {
            UUID ghastId = playerToGhast.remove(player.getUniqueId());
            if (ghastId != null) {
                ghastToPlayer.remove(ghastId, player.getUniqueId());
                HappyGhast g = findGhast(ghastId);
                if (g != null) {
                    notifyDismounted(player, g);
                }
            }
            return;
        }
        notifyDismounted(player, ghast);
    }

    public void dismountIfRiding(HappyGhast ghast) {
        UUID ghastId = ghast.getUniqueId();
        UUID playerId = ghastToPlayer.remove(ghastId);
        if (playerId != null) {
            playerToGhast.remove(playerId, ghastId);
        }
        for (Entity passenger : new java.util.HashSet<>(ghast.getPassengers())) {
            ghast.removePassenger(passenger);
            if (passenger instanceof Player p) {
                playerToGhast.remove(p.getUniqueId(), ghastId);
            }
            if (isDummySeat(passenger)) {
                passenger.remove();
            }
        }
        resetGhast(ghast);
    }

    public void notifyDismounted(Player player, Entity vehicle) {
        if (vehicle instanceof HappyGhast ghast) {
            java.util.List<Player> realRiders = new java.util.ArrayList<>();
            for (Entity e : ghast.getPassengers()) {
                if (e instanceof Player p && !p.equals(player)) {
                    realRiders.add(p);
                }
            }
            rebuildPassengers(ghast, realRiders);
        }
    }

    private void rebuildPassengers(HappyGhast ghast, java.util.List<Player> realRiders) {
        java.util.List<Entity> oldPassengers = new java.util.ArrayList<>(ghast.getPassengers());
        for (Entity e : oldPassengers) {
            ghast.removePassenger(e);
        }
        for (Entity e : oldPassengers) {
            if (isDummySeat(e)) {
                e.remove();
            }
        }

        // Clean up old players in playerToGhast for this ghast
        for (Entity e : oldPassengers) {
            if (e instanceof Player p) {
                playerToGhast.remove(p.getUniqueId(), ghast.getUniqueId());
            }
        }

        if (realRiders.isEmpty()) {
            UUID ghastId = ghast.getUniqueId();
            UUID driverId = ghastToPlayer.remove(ghastId);
            if (driverId != null) {
                playerToGhast.remove(driverId, ghastId);
            }
            resetGhast(ghast);
            return;
        }

        UUID ownerId = ghastData.getOwner(ghast);
        Player ownerRider = null;
        if (ownerId != null) {
            for (Player p : realRiders) {
                if (p.getUniqueId().equals(ownerId)) {
                    ownerRider = p;
                    break;
                }
            }
        }

        // Map all current real riders to this ghast
        for (Player p : realRiders) {
            playerToGhast.put(p.getUniqueId(), ghast.getUniqueId());
        }

        if (ownerRider != null) {
            realRiders.remove(ownerRider);
            realRiders.add(0, ownerRider);

            UUID ghastId = ghast.getUniqueId();
            UUID prevDriver = ghastToPlayer.put(ghastId, ownerRider.getUniqueId());
            if (prevDriver != null && !prevDriver.equals(ownerRider.getUniqueId())) {
                playerToGhast.remove(prevDriver, ghastId);
            }
            UUID prevGhast = playerToGhast.put(ownerRider.getUniqueId(), ghastId);
            if (prevGhast != null && !prevGhast.equals(ghastId)) {
                ghastToPlayer.remove(prevGhast, ownerRider.getUniqueId());
            }

            for (Player p : realRiders) {
                mountBypass.add(p.getUniqueId());
                try {
                    ghast.addPassenger(p);
                } finally {
                    mountBypass.remove(p.getUniqueId());
                }
            }

            if (configManager.getFlightSettings().disableGravityWhenRidden()) {
                ghast.setGravity(false);
            } else {
                ghast.setGravity(true);
            }
        } else {
            UUID ghastId = ghast.getUniqueId();
            UUID prevDriver = ghastToPlayer.remove(ghastId);
            if (prevDriver != null) {
                playerToGhast.remove(prevDriver, ghastId);
            }

            Entity dummy = spawnDummySeat(ghast);
            if (dummy != null) {
                ghast.addPassenger(dummy);
            }

            for (Player p : realRiders) {
                mountBypass.add(p.getUniqueId());
                try {
                    ghast.addPassenger(p);
                } finally {
                    mountBypass.remove(p.getUniqueId());
                }
            }

            resetGhast(ghast);
        }

        ghast.setTarget(null);
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

    public boolean isBypassingMount(UUID uuid) {
        return mountBypass.contains(uuid);
    }

    public void addManagedGhast(HappyGhast ghast) {
        managedGhasts.add(ghast.getUniqueId());
    }

    public void removeManagedGhast(HappyGhast ghast) {
        managedGhasts.remove(ghast.getUniqueId());
    }

    public java.util.List<HappyGhast> getLoadedManagedGhasts() {
        java.util.List<HappyGhast> list = new java.util.ArrayList<>();
        Iterator<UUID> it = managedGhasts.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof HappyGhast ghast && ghastData.isManaged(ghast) && !ghast.isDead() && ghast.isValid()) {
                list.add(ghast);
            } else {
                it.remove();
            }
        }
        return list;
    }

    public void registerLoadedGhasts() {
        for (var world : Bukkit.getWorlds()) {
            for (HappyGhast ghast : world.getEntitiesByClass(HappyGhast.class)) {
                if (ghast != null && !ghast.isDead() && ghast.isValid() && ghastData.isManaged(ghast)) {
                    managedGhasts.add(ghast.getUniqueId());
                }
            }
        }
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
