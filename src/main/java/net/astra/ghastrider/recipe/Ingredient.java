package net.astra.ghastrider.recipe;

import org.bukkit.Material;

/**
 * Тип ингредиента в рецепте: либо ванильный материал, либо наш кастомный предмет.
 */
public sealed interface Ingredient permits Ingredient.Vanilla, Ingredient.Custom {

    record Vanilla(Material material) implements Ingredient {
        public Vanilla {
            if (material == null) {
                throw new IllegalArgumentException("Vanilla ingredient material must not be null");
            }
        }
    }

    record Custom(String customItemId) implements Ingredient {
        public Custom {
            if (customItemId == null || customItemId.isBlank()) {
                throw new IllegalArgumentException("Custom ingredient id must not be blank");
            }
        }
    }
}
