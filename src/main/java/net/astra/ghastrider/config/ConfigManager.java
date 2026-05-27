package net.astra.ghastrider.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Загружает и хранит все настройки плагина из config.yml.
 * Поддерживает горячую перезагрузку через {@link #reload()}.
 */
public final class ConfigManager {

    /** Текущая версия дефолтного config.yml в JAR. Меняйте при правках секций. */
    public static final int EXPECTED_DATA_VERSION = 4;

    private final JavaPlugin plugin;

    private final Map<HarnessTier, HarnessConfig> harnessByTier = new EnumMap<>(HarnessTier.class);
    private final Map<String, HarnessTier> tierByItemId = new HashMap<>();

    private FlightSettings flightSettings;
    private ProtectionSettings protectionSettings;
    private BehaviorSettings behaviorSettings;
    private volatile Map<String, String> messages = new HashMap<>();
    private int dataVersion = 1;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        migrateIfOutdated();
        plugin.saveDefaultConfig();
        reload();
    }

    /**
     * Если на диске лежит config.yml с устаревшей data-version,
     * делаем бэкап и удаляем файл, чтобы saveDefaultConfig() записал свежую версию из JAR.
     */
    private void migrateIfOutdated() {
        File dataFolder = plugin.getDataFolder();
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            return;
        }
        FileConfiguration existing = YamlConfiguration.loadConfiguration(configFile);
        int existingVersion = existing.getInt("data-version", 1);
        if (existingVersion >= EXPECTED_DATA_VERSION) {
            return;
        }
        File backup = new File(dataFolder, "config.yml.backup-v" + existingVersion);
        try {
            Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(configFile.toPath());
            plugin.getLogger().log(Level.INFO,
                    "config.yml мигрирован: v" + existingVersion + " -> v" + EXPECTED_DATA_VERSION
                            + " (резервная копия: " + backup.getName() + ")");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось мигрировать config.yml", e);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        harnessByTier.clear();
        tierByItemId.clear();

        ConfigurationSection harnessSection = cfg.getConfigurationSection("harness");
        if (harnessSection == null) {
            plugin.getLogger().warning("Секция 'harness' отсутствует в config.yml — упряжки не загружены.");
        } else {
            for (HarnessTier tier : HarnessTier.values()) {
                ConfigurationSection s = harnessSection.getConfigurationSection(tier.getConfigKey());
                if (s == null) {
                    plugin.getLogger().warning("Секция harness." + tier.getConfigKey() + " отсутствует. Тир пропущен.");
                    continue;
                }
                String itemId = s.getString("item-id", "");
                if (itemId == null || itemId.isBlank()) {
                    plugin.getLogger().warning("harness." + tier.getConfigKey() + ".item-id пуст. Тир пропущен.");
                    continue;
                }
                boolean regenEnabled = s.getBoolean("regeneration.enabled", true);
                int regenAmp = s.getInt("regeneration.amplifier", 0);
                int regenDur = s.getInt("regeneration.duration", -1);
                boolean fireRes = s.getBoolean("fire-resistance", false);
                double flySpeed = s.getDouble("flying-speed-multiplier", 1.0);
                double moveSpeed = s.getDouble("movement-speed-multiplier", 1.0);
                double flightBonus = s.getDouble("flight-speed-bonus", flySpeed);

                HarnessConfig hc = new HarnessConfig(
                        tier, itemId, regenEnabled, regenAmp, regenDur,
                        fireRes, flySpeed, moveSpeed, flightBonus);

                harnessByTier.put(tier, hc);
                tierByItemId.put(itemId.toLowerCase(Locale.ROOT), tier);
            }
        }

        flightSettings = new FlightSettings(
                cfg.getDouble("flight.base-speed", 0.6),
                clamp01(cfg.getDouble("flight.acceleration", 0.25)),
                Math.max(0.1, cfg.getDouble("flight.max-velocity", 3.0)),
                cfg.getDouble("flight.vertical-look-factor", 1.0),
                cfg.getBoolean("flight.disable-gravity-when-ridden", true),
                cfg.getBoolean("flight.hover-when-sneaking", true),
                cfg.getDouble("flight.jump-boost", 0.6),
                (float) cfg.getDouble("flight.rotate-pitch-factor", 0.3));

        protectionSettings = new ProtectionSettings(
                cfg.getBoolean("protection.deny-damage-from-others", true),
                cfg.getBoolean("protection.deny-owner-damage", true),
                cfg.getBoolean("protection.immune-to-lava", true),
                cfg.getBoolean("protection.immune-to-fire", true),
                cfg.getBoolean("protection.immune-to-drowning", false),
                cfg.getBoolean("protection.prevent-fireballs", true),
                cfg.getBoolean("protection.prevent-targeting", true),
                cfg.getBoolean("protection.prevent-mount-by-others", true),
                cfg.getBoolean("protection.remove-when-far-away", false),
                cfg.getBoolean("protection.persistent", true));

        behaviorSettings = new BehaviorSettings(
                cfg.getBoolean("behavior.set-aware-false", true),
                Math.max(1, cfg.getInt("behavior.reapply-aware-interval-ticks", 20)),
                cfg.getBoolean("behavior.owner-only-glowing", true),
                Math.max(1, cfg.getInt("behavior.owner-only-glowing-interval-ticks", 10)));

        Map<String, String> newMessages = new HashMap<>();
        ConfigurationSection msg = cfg.getConfigurationSection("messages");
        if (msg != null) {
            for (String key : msg.getKeys(false)) {
                newMessages.put(key, msg.getString(key, ""));
            }
        }
        messages = java.util.Collections.unmodifiableMap(newMessages);

        dataVersion = cfg.getInt("data-version", 1);

        plugin.getLogger().log(Level.INFO,
                "Загружено упряжек: " + harnessByTier.size() + " (data-version=" + dataVersion + ")");
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    @Nullable
    public HarnessConfig getHarness(HarnessTier tier) {
        return harnessByTier.get(tier);
    }

    @Nullable
    public HarnessTier getTierByItemId(@Nullable String itemId) {
        if (itemId == null) {
            return null;
        }
        return tierByItemId.get(itemId.toLowerCase(Locale.ROOT));
    }

    public FlightSettings getFlightSettings() {
        return flightSettings;
    }

    public ProtectionSettings getProtectionSettings() {
        return protectionSettings;
    }

    public BehaviorSettings getBehaviorSettings() {
        return behaviorSettings;
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "");
    }

    public String getPrefix() {
        return messages.getOrDefault("prefix", "");
    }

    public int getDataVersion() {
        return dataVersion;
    }
}
