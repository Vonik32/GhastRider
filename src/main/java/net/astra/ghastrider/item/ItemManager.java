package net.astra.ghastrider.item;

import net.astra.ghastrider.data.PdcKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Фасад над регистром предметов и фабрикой. Отвечает за:
 *  - создание ItemStack по id;
 *  - проверку, является ли стак нашим кастомным предметом и каким именно;
 *  - перезагрузку items.yml.
 */
public final class ItemManager {

    private final ItemRegistry registry;
    private final ItemFactory factory;
    private final PdcKeys keys;

    public ItemManager(JavaPlugin plugin, PdcKeys keys) {
        this.keys = keys;
        this.registry = new ItemRegistry(plugin);
        this.factory = new ItemFactory(keys);
    }

    public void load() {
        registry.load();
    }

    public void reload() {
        registry.reload();
    }

    public ItemRegistry getRegistry() {
        return registry;
    }

    public Set<String> ids() {
        return registry.all().keySet();
    }

    /**
     * Создаёт ItemStack по id из items.yml. Возвращает null, если id не зарегистрирован.
     */
    @Nullable
    public ItemStack createStack(String id, int amount) {
        CustomItem ci = registry.get(id);
        if (ci == null) {
            return null;
        }
        return factory.build(ci, amount);
    }

    /**
     * Прототип для RecipeChoice.ExactChoice (всегда amount=1, всегда с актуальной мета).
     */
    @Nullable
    public ItemStack prototype(String id) {
        return createStack(id, 1);
    }

    /**
     * Возвращает id кастомного предмета или null, если стак не наш.
     */
    @Nullable
    public String getCustomId(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(keys.customItem, PersistentDataType.STRING);
    }

    public boolean isCustom(@Nullable ItemStack stack) {
        return getCustomId(stack) != null;
    }

    public boolean matches(@Nullable ItemStack stack, String expectedId) {
        return expectedId != null && expectedId.equals(getCustomId(stack));
    }
}
