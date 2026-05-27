package net.astra.ghastrider.listener;

import net.astra.ghastrider.item.ItemManager;
import net.astra.ghastrider.recipe.Ingredient;
import net.astra.ghastrider.recipe.RecipeDefinition;
import net.astra.ghastrider.recipe.RecipeRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * Доп. слой защиты от подделки кастомных ингредиентов.
 * ExactChoice уже сравнивает по isSimilar(), но этот листенер дополнительно
 * валидирует PDC-маркер на каждом custom-ингредиенте, чтобы исключить любые
 * редкие баги форков ядра и теневые конфликты с другими плагинами.
 */
public final class RecipeGuardListener implements Listener {

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final RecipeRegistry recipeRegistry;

    public RecipeGuardListener(JavaPlugin plugin, ItemManager itemManager, RecipeRegistry recipeRegistry) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.recipeRegistry = recipeRegistry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPrepare(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof ShapedRecipe shaped)) {
            return;
        }
        NamespacedKey key = shaped.getKey();
        String expectedNamespace = plugin.getName().toLowerCase(java.util.Locale.ROOT);
        if (!expectedNamespace.equals(key.getNamespace())) {
            return;
        }
        String idPart = key.getKey();
        if (!idPart.startsWith("recipe_")) {
            return;
        }
        String defId = idPart.substring("recipe_".length());

        RecipeDefinition def = findDef(defId);
        if (def == null) {
            return;
        }

        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (!validateMatrix(def, shaped, matrix)) {
            inv.setResult(null);
        }
    }

    private RecipeDefinition findDef(String id) {
        for (RecipeDefinition d : recipeRegistry.all()) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Проверяет, что для каждой клетки матрицы, соответствующей custom-ингредиенту,
     * предмет действительно содержит наш PDC-маркер.
     */
    private boolean validateMatrix(RecipeDefinition def, ShapedRecipe recipe, ItemStack[] matrix) {
        // Размеры матрицы: для верстака игрока — 2x2, для верстака — 3x3.
        int matrixSize = matrix.length;
        int matrixSide = (matrixSize == 4) ? 2 : 3;

        List<String> shape = def.shape();
        int recipeRows = shape.size();
        int recipeCols = shape.get(0).length();

        // Сдвигаем по матрице: пытаемся найти любое размещение, при котором custom-ингредиенты совпадают.
        // Если есть хотя бы одно валидное размещение — пропускаем; иначе блокируем.
        for (int rowOff = 0; rowOff <= matrixSide - recipeRows; rowOff++) {
            for (int colOff = 0; colOff <= matrixSide - recipeCols; colOff++) {
                if (matchesPlacement(def, shape, matrix, matrixSide, rowOff, colOff)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesPlacement(RecipeDefinition def, List<String> shape, ItemStack[] matrix,
                                     int matrixSide, int rowOff, int colOff) {
        Map<Character, Ingredient> key = def.key();

        // Сначала проверяем, что вне shape клетки матрицы пусты.
        for (int r = 0; r < matrixSide; r++) {
            for (int c = 0; c < matrixSide; c++) {
                ItemStack cell = matrix[r * matrixSide + c];
                int sr = r - rowOff;
                int sc = c - colOff;
                boolean inShape = sr >= 0 && sr < shape.size() && sc >= 0 && sc < shape.get(sr).length();
                if (!inShape) {
                    if (cell != null && !cell.getType().isAir()) {
                        return false;
                    }
                    continue;
                }
                char ch = shape.get(sr).charAt(sc);
                if (ch == ' ') {
                    if (cell != null && !cell.getType().isAir()) {
                        return false;
                    }
                    continue;
                }
                Ingredient ing = key.get(ch);
                if (ing == null) {
                    return false;
                }
                if (cell == null || cell.getType().isAir()) {
                    return false;
                }
                if (ing instanceof Ingredient.Custom custom) {
                    if (!itemManager.matches(cell, custom.customItemId())) {
                        return false;
                    }
                }
                // Vanilla — ExactChoice/MaterialChoice уже отвалидировал тип материала.
            }
        }
        return true;
    }
}
