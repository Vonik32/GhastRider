package net.astra.ghastrider.config;

/**
 * Настройки физики полёта.
 */
public record FlightSettings(
        double baseSpeed,
        double acceleration,
        double maxVelocity,
        double verticalLookFactor,
        boolean disableGravityWhenRidden,
        boolean hoverWhenSneaking,
        double jumpBoost,
        float rotatePitchFactor
) {
}
