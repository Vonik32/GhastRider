package net.astra.ghastrider.item;

/**
 * Канонические ID кастомных предметов плагина.
 * Эти строки соответствуют ключам в items.yml и записываются в PDC ItemStack'ов
 * под ключом ghastrider:custom_item.
 */
public final class CustomItemId {

    private CustomItemId() {}

    // Harnesses (5).
    public static final String BASE_HARNESS = "base_harness";
    public static final String IRON_HARNESS = "iron_harness";
    public static final String GOLD_HARNESS = "gold_harness";
    public static final String DIAMOND_HARNESS = "diamond_harness";
    public static final String NETHERITE_HARNESS = "netherite_harness";

    // Essences (5).
    public static final String ESSENCE_SPEED_1 = "essence_speed_1";
    public static final String ESSENCE_SPEED_2 = "essence_speed_2";
    public static final String ESSENCE_REGEN_1 = "essence_regen_1";
    public static final String ESSENCE_REGEN_2 = "essence_regen_2";
    public static final String ESSENCE_FIRE_1 = "essence_fire_1";
}
