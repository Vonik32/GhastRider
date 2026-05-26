package net.astra.ghastrider.item;

import net.astra.ghastrider.data.PdcKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Строит ItemStack из {@link CustomItem} и штампует PDC-маркер.
 * PDC: ghastrider:custom_item = id, ghastrider:item_version = version.
 */
public final class ItemFactory {

    public static final int CURRENT_ITEM_VERSION = 1;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PdcKeys keys;

    public ItemFactory(PdcKeys keys) {
        this.keys = keys;
    }

    public ItemStack build(CustomItem item, int amount) {
        ItemStack stack = new ItemStack(item.material(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            // Не у всех материалов есть мета (например, AIR), но для наших — должна быть.
            return stack;
        }

        // Display name (MiniMessage).
        if (item.displayName() != null && !item.displayName().isBlank()) {
            Component name = MM.deserialize(item.displayName())
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(name);
        }

        // Lore (MiniMessage).
        List<String> rawLore = item.lore();
        if (rawLore != null && !rawLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(rawLore.size());
            for (String line : rawLore) {
                lore.add(MM.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }

        // Custom model data.
        if (item.customModelData() > 0) {
            meta.setCustomModelData(item.customModelData());
        }

        // Glow (фейковая зачарованность + скрытие).
        if (item.glow()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }

        // PDC anti-counterfeit marker.
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.customItem, PersistentDataType.STRING, item.id());
        pdc.set(keys.itemVersion, PersistentDataType.INTEGER, CURRENT_ITEM_VERSION);

        stack.setItemMeta(meta);
        return stack;
    }
}
