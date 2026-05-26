package net.astra.ghastrider.config;

/**
 * Иммутабельная конфигурация одного тира упряжки.
 * Все значения — из config.yml (см. секцию harness.<tier>).
 */
public record HarnessConfig(
        HarnessTier tier,
        String itemId,
        boolean regenerationEnabled,
        int regenerationAmplifier,
        int regenerationDurationTicks,
        boolean fireResistance,
        double flyingSpeedMultiplier,
        double movementSpeedMultiplier,
        double flightSpeedBonus
) {
    public boolean hasInfiniteRegeneration() {
        return regenerationDurationTicks < 0;
    }
}
