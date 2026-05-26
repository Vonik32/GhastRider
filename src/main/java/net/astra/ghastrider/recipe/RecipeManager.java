package net.astra.ghastrider.recipe;

import net.astra.ghastrider.item.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Регистрирует/снимает на сервере все рецепты, описанные в recipes.yml.
 * Кастомные ингредиенты помечаются {@link RecipeChoice.ExactChoice}, что
 * через {@link ItemStack#isSimilar(ItemStack)} включая ItemMeta+PDC
 * гарантированно отбрасывает ванильные подделки.
 */
public final class RecipeManager {

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final RecipeRegistry registry;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public RecipeManager(JavaPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.registry = new RecipeRegistry(plugin);
    }

    public void load() {
        registry.load();
        registerAll();
    }

    public void reload() {
        unregisterAll();
        registry.reload();
        registerAll();
    }

    public void registerAll() {
        int ok = 0;
        int fail = 0;
        for (RecipeDefinition def : registry.all()) {
            if (registerOne(def)) {
                ok++;
            } else {
                fail++;
            }
        }
        plugin.getLogger().info("Зарегистрировано рецептов: " + ok + (fail > 0 ? (" (отклонено: " + fail + ")") : ""));
    }

    public void unregisterAll() {
        for (NamespacedKey key : registeredKeys) {
            try {
                Bukkit.removeRecipe(key);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Не удалось снять рецепт " + key, t);
            }
        }
        registeredKeys.clear();
    }

    private boolean registerOne(RecipeDefinition def) {
        ItemStack result = itemManager.createStack(def.resultItemId(), def.resultAmount());
        if (result == null) {
            plugin.getLogger().warning("Рецепт '" + def.id()
                    + "' ссылается на неизвестный result-item '" + def.resultItemId() + "', пропущен.");
            return false;
        }

        NamespacedKey key = new NamespacedKey(plugin, "recipe_" + def.id());
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        // Shape: 1..3 строк по 1..3 символа.
        String[] rows = def.shape().toArray(new String[0]);
        recipe.shape(rows);

        // Только символы, реально встречающиеся в shape.
        for (Map.Entry<Character, Ingredient> e : def.key().entrySet()) {
            char ch = e.getKey();
            if (ch == ' ') continue; // пробел = пусто
            if (!shapeContains(rows, ch)) continue;
            Ingredient ing = e.getValue();
            RecipeChoice choice = toChoice(def.id(), ing);
            if (choice == null) {
                return false;
            }
            recipe.setIngredient(ch, choice);
        }

        try {
            Bukkit.addRecipe(recipe);
            registeredKeys.add(key);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Не удалось добавить рецепт '" + def.id() + "'", t);
            return false;
        }
    }

    private RecipeChoice toChoice(String recipeId, Ingredient ing) {
        if (ing instanceof Ingredient.Vanilla v) {
            return new RecipeChoice.MaterialChoice(v.material());
        }
        if (ing instanceof Ingredient.Custom c) {
            ItemStack proto = itemManager.prototype(c.customItemId());
            if (proto == null) {
                plugin.getLogger().warning("Рецепт '" + recipeId
                        + "' ссылается на неизвестный custom-item '" + c.customItemId() + "'.");
                return null;
            }
            return new RecipeChoice.ExactChoice(proto);
        }
        return null;
    }

    private static boolean shapeContains(String[] rows, char ch) {
        for (String r : rows) {
            if (r.indexOf(ch) >= 0) return true;
        }
        return false;
    }

    public List<NamespacedKey> getRegisteredKeys() {
        return List.copyOf(registeredKeys);
    }

    public RecipeRegistry getRegistry() {
        return registry;
    }
}
