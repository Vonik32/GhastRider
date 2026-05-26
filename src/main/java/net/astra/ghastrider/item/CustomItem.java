package net.astra.ghastrider.item;

import org.bukkit.Material;

import java.util.List;

/**
 * Иммутабельная модель кастомного предмета, описанного в items.yml.
 * Использует MiniMessage-строки для имени и лора (парсятся в ItemFactory).
 */
public record CustomItem(
        String id,
        Material material,
        int customModelData,
        String displayName,
        List<String> lore,
        boolean glow
) {
    public CustomItem {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CustomItem.id must not be blank");
        }
        if (material == null) {
            throw new IllegalArgumentException("CustomItem.material must not be null");
        }
        // Defensive copy.
        lore = lore == null ? List.of() : List.copyOf(lore);
    }
}
