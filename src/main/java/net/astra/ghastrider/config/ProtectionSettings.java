package net.astra.ghastrider.config;

/**
 * Настройки защиты managed-Гаста.
 */
public record ProtectionSettings(
        boolean denyDamageFromOthers,
        boolean denyOwnerDamage,
        boolean immuneToLava,
        boolean immuneToFire,
        boolean immuneToDrowning,
        boolean preventFireballs,
        boolean preventTargeting,
        boolean preventMountByOthers,
        boolean removeWhenFarAway,
        boolean persistent
) {
}
