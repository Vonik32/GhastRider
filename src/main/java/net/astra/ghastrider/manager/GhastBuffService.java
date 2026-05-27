package net.astra.ghastrider.manager;

import net.astra.ghastrider.config.ConfigManager;
import net.astra.ghastrider.config.HarnessConfig;
import net.astra.ghastrider.config.HarnessTier;
import net.astra.ghastrider.config.ProtectionSettings;
import net.astra.ghastrider.data.GhastData;
import net.astra.ghastrider.data.PdcKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.HappyGhast;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Сервис, отвечающий за выдачу/снятие баффов и атрибутов managed-Гасту
 * на основе текущей упряжки. Все операции идемпотентны: перед добавлением
 * AttributeModifier'а всегда удаляется старый по нашему {@link NamespacedKey}.
 */
public final class GhastBuffService {

    private static final int INFINITE_DURATION = Integer.MAX_VALUE;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final GhastData ghastData;
    private final PdcKeys keys;

    public GhastBuffService(JavaPlugin plugin, ConfigManager configManager, GhastData ghastData, PdcKeys keys) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.ghastData = ghastData;
        this.keys = keys;
    }

    /**
     * Применить баффы согласно тиру упряжки. Перед применением гарантированно
     * вызывается {@link #clear(HappyGhast)}, чтобы исключить дублирование модификаторов.
     */
    public void apply(HappyGhast ghast, HarnessConfig hc) {
        clear(ghast);

        if (hc.regenerationEnabled()) {
            int duration = hc.hasInfiniteRegeneration() ? INFINITE_DURATION : hc.regenerationDurationTicks();
            ghast.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION,
                    duration,
                    hc.regenerationAmplifier(),
                    true,
                    false,
                    true));
        }

        if (hc.fireResistance()) {
            ghast.addPotionEffect(new PotionEffect(
                    PotionEffectType.FIRE_RESISTANCE,
                    INFINITE_DURATION,
                    0,
                    true,
                    false,
                    true));
        }

        applyAttributeMultiplier(ghast, Attribute.FLYING_SPEED, keys.attrFlyingSpeed, hc.flyingSpeedMultiplier());
        applyAttributeMultiplier(ghast, Attribute.MOVEMENT_SPEED, keys.attrMovementSpeed, hc.movementSpeedMultiplier());

        applyVisualAndProtection(ghast);
    }

    /**
     * Заново применить баффы Гасту по сохранённому в PDC тиру.
     * Используется в EntitiesLoadEvent / ItemsAdderLoadDataEvent.
     */
    public void reapply(HappyGhast ghast) {
        if (!ghastData.isManaged(ghast)) {
            return;
        }
        HarnessTier tier = ghastData.getTier(ghast);
        if (tier == null) {
            return;
        }
        HarnessConfig hc = configManager.getHarness(tier);
        if (hc == null) {
            return;
        }
        apply(ghast, hc);
    }

    /**
     * Полная очистка: эффекты, атрибуты, glowing.
     */
    public void clear(HappyGhast ghast) {
        ghast.removePotionEffect(PotionEffectType.REGENERATION);
        ghast.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);

        removeAttribute(ghast, Attribute.FLYING_SPEED, keys.attrFlyingSpeed);
        removeAttribute(ghast, Attribute.MOVEMENT_SPEED, keys.attrMovementSpeed);

        // Глобальный glowing намеренно не используется: подсветка
        // владельца реализована через owner-only частицы в OwnerIndicatorTask.
        if (ghast.isGlowing()) {
            ghast.setGlowing(false);
        }
    }

    private void applyAttributeMultiplier(HappyGhast ghast, Attribute attribute, NamespacedKey key, double scalar) {
        AttributeInstance instance = ghast.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = findModifier(instance, key);
        if (existing != null) {
            instance.removeModifier(existing);
        }
        AttributeModifier modifier = new AttributeModifier(
                key,
                scalar,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                EquipmentSlotGroup.ANY);
        instance.addModifier(modifier);
    }

    private void removeAttribute(HappyGhast ghast, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = ghast.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = findModifier(instance, key);
        if (existing != null) {
            instance.removeModifier(existing);
        }
    }

    private AttributeModifier findModifier(AttributeInstance instance, NamespacedKey key) {
        for (AttributeModifier mod : instance.getModifiers()) {
            if (key.equals(mod.getKey())) {
                return mod;
            }
        }
        return null;
    }

    private void applyVisualAndProtection(HappyGhast ghast) {
        // Полностью отключаем самостоятельное поведение HappyGhast:
        // нет автополёта, нет вращения, нет поиска целей. Управление только от райдера.
        ghast.setAware(false);
        ghast.setTarget(null);

        // Максимальное сопротивление толчкам — никто не может его сдвинуть.
        AttributeInstance kb = ghast.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.setBaseValue(1.0);
        }

        ProtectionSettings ps = configManager.getProtectionSettings();
        ghast.setRemoveWhenFarAway(ps.removeWhenFarAway());
        ghast.setPersistent(ps.persistent());
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
