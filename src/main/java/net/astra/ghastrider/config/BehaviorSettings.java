package net.astra.ghastrider.config;

/**
 * Поведенческие настройки (AI, glowing, повторное применение).
 */
public record BehaviorSettings(
        boolean setAwareFalse,
        int reapplyAwareIntervalTicks,
        boolean ownerOnlyGlowing,
        int ownerOnlyGlowingIntervalTicks
) {
}
