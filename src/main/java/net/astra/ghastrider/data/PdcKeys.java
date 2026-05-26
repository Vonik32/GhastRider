package net.astra.ghastrider.data;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Централизованные NamespacedKey, используемые в PDC и AttributeModifier'ах.
 */
public final class PdcKeys {

    public final NamespacedKey ownerUuid;
    public final NamespacedKey harnessTier;
    public final NamespacedKey harnessItemId;
    public final NamespacedKey managed;
    public final NamespacedKey dataVersion;

    public final NamespacedKey attrFlyingSpeed;
    public final NamespacedKey attrMovementSpeed;

    // Item PDC markers (anti-counterfeit).
    public final NamespacedKey customItem;
    public final NamespacedKey itemVersion;

    public PdcKeys(Plugin plugin) {
        this.ownerUuid = new NamespacedKey(plugin, "owner_uuid");
        this.harnessTier = new NamespacedKey(plugin, "harness_tier");
        this.harnessItemId = new NamespacedKey(plugin, "harness_item_id");
        this.managed = new NamespacedKey(plugin, "managed");
        this.dataVersion = new NamespacedKey(plugin, "data_version");
        this.attrFlyingSpeed = new NamespacedKey(plugin, "attr_flying_speed");
        this.attrMovementSpeed = new NamespacedKey(plugin, "attr_movement_speed");
        this.customItem = new NamespacedKey(plugin, "custom_item");
        this.itemVersion = new NamespacedKey(plugin, "item_version");
    }
}
