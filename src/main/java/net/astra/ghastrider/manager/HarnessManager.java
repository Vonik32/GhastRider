package net.astra.ghastrider.manager;

import net.astra.ghastrider.config.ConfigManager;
import net.astra.ghastrider.config.HarnessConfig;
import net.astra.ghastrider.config.HarnessTier;
import net.astra.ghastrider.data.GhastData;
import net.astra.ghastrider.item.ItemManager;
import net.astra.ghastrider.util.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Высокоуровневый менеджер операций над упряжкой:
 * надевание, снятие, замена. Содержит все проверки прав, PDC и инвентаря.
 */
public final class HarnessManager {

    public enum InteractionResult {
        APPLIED,
        REMOVED,
        SWAPPED,
        DENIED_NOT_OWNER,
        DENIED_NOT_CUSTOM_ITEM,
        DENIED_UNKNOWN_TIER,
        DENIED_ALREADY_HARNESSED,
        DENIED_NOT_MANAGED,
        ERROR
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final GhastData ghastData;
    private final GhastBuffService buffService;
    private final RideController rideController;
    private final MessageUtil messageUtil;

    private Material cachedVanillaHarness = null;
    private boolean resolvedHarness = false;

    public HarnessManager(JavaPlugin plugin,
                          ConfigManager configManager,
                          ItemManager itemManager,
                          GhastData ghastData,
                          GhastBuffService buffService,
                          RideController rideController,
                          MessageUtil messageUtil) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.ghastData = ghastData;
        this.buffService = buffService;
        this.rideController = rideController;
        this.messageUtil = messageUtil;
    }

    /**
     * Надеть новую упряжку на свободного Гаста.
     */
    public InteractionResult applyHarness(Player player, HappyGhast ghast, ItemStack itemInHand, EquipmentSlot hand) {
        if (ghastData.isManaged(ghast)) {
            messageUtil.send(player, "already-harnessed");
            return InteractionResult.DENIED_ALREADY_HARNESSED;
        }
        String itemId = itemManager.getCustomId(itemInHand);
        if (itemId == null) {
            return InteractionResult.DENIED_NOT_CUSTOM_ITEM;
        }
        HarnessTier tier = configManager.getTierByItemId(itemId);
        if (tier == null) {
            return InteractionResult.DENIED_NOT_CUSTOM_ITEM;
        }
        HarnessConfig hc = configManager.getHarness(tier);
        if (hc == null) {
            return InteractionResult.DENIED_UNKNOWN_TIER;
        }

        // Сначала пытаемся списать предмет — если игрок умудрился сменить руку
        // или предмет за тот же тик, операция отменяется без сайд-эффектов на гасте.
        if (!consumeOne(player, hand, itemId)) {
            return InteractionResult.DENIED_NOT_CUSTOM_ITEM;
        }

        ghastData.apply(ghast, player.getUniqueId(), player.getName(), tier, itemId);
        buffService.apply(ghast, hc);
        applyVanillaHarness(ghast);
        rideController.addManagedGhast(ghast);

        messageUtil.send(player, "harness-applied",
                MessageUtil.placeholder("tier", tier.name()));
        return InteractionResult.APPLIED;
    }

    /**
     * Снять упряжку с гаста и дропнуть её в мир (или вернуть игроку, если задано).
     */
    public InteractionResult removeHarness(Player player, HappyGhast ghast, boolean dropInWorld) {
        if (!ghastData.isManaged(ghast)) {
            return InteractionResult.DENIED_NOT_MANAGED;
        }
        if (!ghastData.isOwner(ghast, player) && !player.hasPermission("ghastrider.bypass.owner")) {
            messageUtil.send(player, "not-owner");
            return InteractionResult.DENIED_NOT_OWNER;
        }
        String itemId = ghastData.getHarnessItemId(ghast);

        rideController.dismountIfRiding(ghast);
        rideController.removeManagedGhast(ghast);

        buffService.clear(ghast);
        ghastData.clear(ghast);
        clearVanillaHarness(ghast);
        // Возвращаем Гасту способность летать самостоятельно.
        ghast.setAware(true);
        AttributeInstance kb = ghast.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            // Сбрасываем на ванильное значение по умолчанию вместо жёсткой нули,
            // чтобы корректно восстановить поведение моба при будущих обновлениях ядра.
            kb.setBaseValue(kb.getDefaultValue());
        }

        dropOrGive(player, ghast.getLocation(), itemId, dropInWorld);

        messageUtil.send(player, "harness-removed");
        return InteractionResult.REMOVED;
    }

    /**
     * Заменить упряжку на другую.
     */
    public InteractionResult swapHarness(Player player, HappyGhast ghast, ItemStack newItem, EquipmentSlot hand) {
        if (!ghastData.isManaged(ghast)) {
            return InteractionResult.DENIED_NOT_MANAGED;
        }
        if (!ghastData.isOwner(ghast, player) && !player.hasPermission("ghastrider.bypass.owner")) {
            messageUtil.send(player, "not-owner");
            return InteractionResult.DENIED_NOT_OWNER;
        }
        String newItemId = itemManager.getCustomId(newItem);
        if (newItemId == null) {
            return InteractionResult.DENIED_NOT_CUSTOM_ITEM;
        }
        HarnessTier newTier = configManager.getTierByItemId(newItemId);
        if (newTier == null) {
            return InteractionResult.DENIED_NOT_CUSTOM_ITEM;
        }
        HarnessConfig newHc = configManager.getHarness(newTier);
        if (newHc == null) {
            return InteractionResult.DENIED_UNKNOWN_TIER;
        }
        if (newTier == ghastData.getTier(ghast)) {
            messageUtil.send(player, "already-harnessed");
            return InteractionResult.DENIED_ALREADY_HARNESSED;
        }

        String oldItemId = ghastData.getHarnessItemId(ghast);

        // Сначала списываем новую упряжку — если предмет внезапно исчез из руки,
        // не трогаем уже надетую и не дублируем дроп.
        if (!consumeOne(player, hand, newItemId)) {
            return InteractionResult.DENIED_NOT_CUSTOM_ITEM;
        }

        ghastData.updateTier(ghast, newTier, newItemId);
        buffService.apply(ghast, newHc);
        dropOrGive(player, player.getLocation(), oldItemId, false);

        messageUtil.send(player, "harness-swapped",
                MessageUtil.placeholder("tier", newTier.name()));
        return InteractionResult.SWAPPED;
    }

    /**
     * Дроп упряжки на месте смерти гаста (вызывается из EntityDeathEvent).
     * Возвращает ItemStack или null, если предмет неизвестен.
     */
    @Nullable
    public ItemStack buildDeathDrop(HappyGhast ghast) {
        String itemId = ghastData.getHarnessItemId(ghast);
        if (itemId == null) {
            return null;
        }
        return itemManager.createStack(itemId, 1);
    }

    /**
     * Списать один экземпляр предмета из указанной руки, проверив, что это всё ещё
     * та же кастомная упряжка, которую мы намереваемся применить. В креативе предмет
     * не списывается, но операция считается успешной.
     *
     * @return true если предмет всё ещё валиден (или режим креатив) и можно
     *         продолжать применение; false если рука пуста / держит не то.
     */
    private boolean consumeOne(Player player, EquipmentSlot hand, String expectedItemId) {
        PlayerInventory inv = player.getInventory();
        ItemStack stack = (hand == EquipmentSlot.OFF_HAND) ? inv.getItemInOffHand() : inv.getItemInMainHand();
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        // Сверяем PDC: за тот же тик игрок мог поменять предмет в руке. Без проверки
        // мы рискуем списать чужой стак.
        String currentId = itemManager.getCustomId(stack);
        if (currentId == null || !currentId.equals(expectedItemId)) {
            return false;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        int amount = stack.getAmount();
        if (amount <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                inv.setItemInOffHand(null);
            } else {
                inv.setItemInMainHand(null);
            }
        } else {
            stack.setAmount(amount - 1);
        }
        return true;
    }

    private void dropOrGive(Player player, Location location, @Nullable String itemId, boolean preferDrop) {
        if (itemId == null) {
            return;
        }
        ItemStack stack = itemManager.createStack(itemId, 1);
        if (stack == null) {
            plugin.getLogger().warning("ItemManager не нашёл предмет '" + itemId + "' при дропе/возврате упряжки.");
            return;
        }
        if (preferDrop) {
            spawnDrop(location, stack);
            return;
        }
        Map<Integer, ItemStack> overflow = new HashMap<>(player.getInventory().addItem(stack));
        if (!overflow.isEmpty()) {
            for (ItemStack remainder : overflow.values()) {
                spawnDrop(player.getLocation(), remainder);
            }
        }
    }

    private void spawnDrop(Location location, ItemStack stack) {
        if (location.getWorld() == null) {
            return;
        }
        Item dropped = location.getWorld().dropItemNaturally(location, stack);
        dropped.setCanMobPickup(false);
    }

    /**
     * Экипирует ванильную упряжку в слот BODY HappyGhast,
     * чтобы работало ванильное пилотирование.
     */
    private void applyVanillaHarness(HappyGhast ghast) {
        try {
            EntityEquipment eq = ghast.getEquipment();
            if (eq == null) {
                return;
            }
            Material harnessMat = resolveVanillaHarnessMaterial();
            if (harnessMat == null) {
                return;
            }
            eq.setItem(EquipmentSlot.BODY, new ItemStack(harnessMat));
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось экипировать ванильную упряжку на HappyGhast: " + t.getMessage());
        }
    }

    private void clearVanillaHarness(HappyGhast ghast) {
        try {
            EntityEquipment eq = ghast.getEquipment();
            if (eq == null) {
                return;
            }
            eq.setItem(EquipmentSlot.BODY, null);
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private Material resolveVanillaHarnessMaterial() {
        if (resolvedHarness) {
            return cachedVanillaHarness;
        }
        // В 1.21.6+ ванильная упряжка — это цветные *_HARNESS материалы.
        // Берём первый доступный, чтобы не зависеть от конкретного имени.
        String[] candidates = {
                "WHITE_HARNESS",
                "LIGHT_GRAY_HARNESS",
                "GRAY_HARNESS",
                "BLACK_HARNESS",
                "BROWN_HARNESS",
                "RED_HARNESS",
                "ORANGE_HARNESS",
                "YELLOW_HARNESS",
                "LIME_HARNESS",
                "GREEN_HARNESS",
                "CYAN_HARNESS",
                "LIGHT_BLUE_HARNESS",
                "BLUE_HARNESS",
                "PURPLE_HARNESS",
                "MAGENTA_HARNESS",
                "PINK_HARNESS",
                "HARNESS"
        };
        for (String name : candidates) {
            try {
                cachedVanillaHarness = Material.valueOf(name);
                break;
            } catch (IllegalArgumentException ignored) {
            }
        }
        resolvedHarness = true;
        return cachedVanillaHarness;
    }
}
