package net.astra.ghastrider.recipe;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Загружает recipes.yml в список {@link RecipeDefinition}.
 */
public final class RecipeRegistry {

    private static final String FILE_NAME = "recipes.yml";

    private final JavaPlugin plugin;
    private final List<RecipeDefinition> definitions = new ArrayList<>();

    public RecipeRegistry(JavaPlugin plugin) {
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
        definitions.clear();
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.getLogger().warning(FILE_NAME + " не найден — рецепты не зарегистрированы.");
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("recipes");
        if (root == null) {
            plugin.getLogger().warning("Секция 'recipes' отсутствует в " + FILE_NAME);
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            String result = s.getString("result", "");
            int amount = Math.max(1, Math.min(64, s.getInt("amount", 1)));

            List<String> shape = s.getStringList("shape");
            if (shape.isEmpty() || shape.size() > 3) {
                plugin.getLogger().warning("recipes.yml: рецепт '" + id + "' имеет некорректный shape, пропущен.");
                continue;
            }
            int width = shape.get(0).length();
            if (width < 1 || width > 3) {
                plugin.getLogger().warning("recipes.yml: рецепт '" + id + "' — shape должен быть 1..3 символа в ширину.");
                continue;
            }
            boolean shapeOk = true;
            for (String row : shape) {
                if (row.length() != width) {
                    plugin.getLogger().warning("recipes.yml: рецепт '" + id + "' — строки shape разной длины.");
                    shapeOk = false;
                    break;
                }
            }
            if (!shapeOk) continue;

            ConfigurationSection ing = s.getConfigurationSection("ingredients");
            if (ing == null) {
                plugin.getLogger().warning("recipes.yml: рецепт '" + id + "' без секции ingredients, пропущен.");
                continue;
            }

            Map<Character, Ingredient> key = new HashMap<>();
            boolean ingOk = true;
            for (String charKey : ing.getKeys(false)) {
                if (charKey.length() != 1) {
                    plugin.getLogger().warning("recipes.yml: '" + id + "' ключ ingredient '" + charKey + "' должен быть 1 символ.");
                    ingOk = false;
                    break;
                }
                ConfigurationSection ingSec = ing.getConfigurationSection(charKey);
                if (ingSec == null) {
                    ingOk = false;
                    break;
                }
                String type = ingSec.getString("type", "vanilla").toLowerCase();
                Ingredient parsed;
                if ("vanilla".equals(type)) {
                    String matName = ingSec.getString("material", "");
                    Material mat;
                    try {
                        mat = Material.valueOf(matName.toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().log(Level.WARNING,
                                "recipes.yml: '" + id + "' material='" + matName + "' неизвестен.");
                        ingOk = false;
                        break;
                    }
                    parsed = new Ingredient.Vanilla(mat);
                } else if ("custom".equals(type)) {
                    String customId = ingSec.getString("item", "");
                    if (customId.isBlank()) {
                        plugin.getLogger().warning("recipes.yml: '" + id + "' custom ingredient без 'item'.");
                        ingOk = false;
                        break;
                    }
                    parsed = new Ingredient.Custom(customId);
                } else {
                    plugin.getLogger().warning("recipes.yml: '" + id + "' неизвестный type='" + type + "'.");
                    ingOk = false;
                    break;
                }
                key.put(charKey.charAt(0), parsed);
            }
            if (!ingOk) continue;

            try {
                definitions.add(new RecipeDefinition(id, result, amount, shape, key));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "recipes.yml: рецепт '" + id + "' отвергнут: " + ex.getMessage());
            }
        }

        plugin.getLogger().info("Загружено определений рецептов: " + definitions.size());
    }

    public List<RecipeDefinition> all() {
        return List.copyOf(definitions);
    }
}
