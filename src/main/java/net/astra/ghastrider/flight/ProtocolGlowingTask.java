package net.astra.ghastrider.flight;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import net.astra.ghastrider.config.BehaviorSettings;
import net.astra.ghastrider.config.ConfigManager;
import net.astra.ghastrider.data.GhastData;
import org.bukkit.Bukkit;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Использует ProtocolLib для отправки пакета EntityMetadata,
 * чтобы включить ванильный Glowing-эффект (spectral arrow)
 * только для владельца Счастливого Гаста.
 */
public final class ProtocolGlowingTask {

    // В Entity metadata: индекс 0 (Entity), тип Byte (0), флаг 0x40 = Glowing
    private static final int ENTITY_FLAGS_INDEX = 0;
    private static final byte GLOWING_FLAG = 0x40;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final GhastData ghastData;
    private final ProtocolManager protocolManager;

    private BukkitTask task;

    public ProtocolGlowingTask(JavaPlugin plugin, ConfigManager configManager, GhastData ghastData) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.ghastData = ghastData;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void start() {
        stop();
        BehaviorSettings bs = configManager.getBehaviorSettings();
        if (!bs.ownerOnlyGlowing()) {
            return;
        }
        long period = Math.max(1L, bs.ownerOnlyGlowingIntervalTicks());
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (var world : Bukkit.getWorlds()) {
            for (HappyGhast ghast : world.getEntitiesByClass(HappyGhast.class)) {
                if (!ghastData.isManaged(ghast)) {
                    continue;
                }
                UUID ownerId = ghastData.getOwner(ghast);
                if (ownerId == null) {
                    continue;
                }
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner == null || !owner.isOnline()) {
                    continue;
                }
                if (!owner.getWorld().equals(world)) {
                    continue;
                }

                sendGlowingPacket(owner, ghast);
            }
        }
    }

    private void sendGlowingPacket(Player owner, HappyGhast ghast) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, ghast.getEntityId());

            // 1.21.11+ использует WrappedDataValue
            List<WrappedDataValue> dataValues = new ArrayList<>();

            // Читаем текущие флаги (чтобы не затереть другие: on fire, sneaking и т.д., хотя для гаста это редко нужно)
            // Но проще просто взять текущие из Bukkit, добавить Glowing и отправить
            WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(ghast);
            byte currentFlags = 0;
            if (watcher.hasIndex(ENTITY_FLAGS_INDEX)) {
                Object flagObj = watcher.getObject(ENTITY_FLAGS_INDEX);
                if (flagObj instanceof Byte) {
                    currentFlags = (Byte) flagObj;
                }
            }

            currentFlags |= GLOWING_FLAG;

            dataValues.add(new WrappedDataValue(
                    ENTITY_FLAGS_INDEX,
                    WrappedDataWatcher.Registry.get(Byte.class),
                    currentFlags
            ));

            packet.getDataValueCollectionModifier().write(0, dataValues);

            protocolManager.sendServerPacket(owner, packet);
        } catch (Exception e) {
            // Игнорируем ошибку при отправке
        }
    }
}
