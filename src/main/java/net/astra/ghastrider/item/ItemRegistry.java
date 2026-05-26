package net.astra.ghastrider.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Парсит items.yml и хранит карту id -> {@link CustomItem}.
 */
public final class ItemRegistry {

    private static final String FILE_NAME = "items.yml";

    private final JavaPlugin plugin;
    private final Map<String, CustomItem> items = new LinkedHashMap<>();

    public ItemRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        reload();
    }

    public void reload() {
        items.clear();
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.getLogger().warning(FILE_NAME + " не найден — кастомных предметов не будет.");
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("items");
        if (root == null) {
            plugin.getLogger().warning("Секция 'items' отсутствует в " + FILE_NAME);
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            String materialName = s.getString("material", "");
            Material material;
            try {
                material = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "items.yml: предмет '" + id + "' имеет неизвестный material='" + materialName + "', пропущен.");
                continue;
            }

            int cmd = s.getInt("custom-model-data", 0);
            String displayName = s.getString("display-name", id);
            List<String> lore = s.getStringList("lore");
            boolean glow = s.getBoolean("glow", false);

            CustomItem item = new CustomItem(id, material, cmd, displayName, lore, glow);
            items.put(id, item);
        }

        plugin.getLogger().info("Загружено кастомных предметов: " + items.size());
    }

    public CustomItem get(String id) {
        return items.get(id);
    }

    public boolean contains(String id) {
        return items.containsKey(id);
    }

    public Map<String, CustomItem> all() {
        return Collections.unmodifiableMap(items);
    }
}
