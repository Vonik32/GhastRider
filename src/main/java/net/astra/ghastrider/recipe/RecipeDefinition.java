package net.astra.ghastrider.recipe;

import java.util.List;
import java.util.Map;

/**
 * Описание одного рецепта из recipes.yml.
 */
public record RecipeDefinition(
        String id,
        String resultItemId,
        int resultAmount,
        List<String> shape,
        Map<Character, Ingredient> key
) {
    public RecipeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("RecipeDefinition.id must not be blank");
        }
        if (resultItemId == null || resultItemId.isBlank()) {
            throw new IllegalArgumentException("RecipeDefinition.resultItemId must not be blank");
        }
        if (shape == null || shape.isEmpty() || shape.size() > 3) {
            throw new IllegalArgumentException("Shape must contain 1..3 rows");
        }
        if (key == null) {
            throw new IllegalArgumentException("Key map must not be null");
        }
        // Defensive copies.
        shape = List.copyOf(shape);
        key = Map.copyOf(key);
    }
}
