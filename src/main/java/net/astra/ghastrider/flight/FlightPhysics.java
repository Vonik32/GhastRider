package net.astra.ghastrider.flight;

import org.bukkit.util.Vector;

/**
 * Чистая математика полёта: расчёт нового вектора скорости на основе
 * взгляда игрока, текущей скорости и параметров плавности.
 *
 * Без зависимостей от Bukkit-сущностей — легко тестируется юнит-тестами.
 */
public final class FlightPhysics {

    private FlightPhysics() {
    }

    /**
     * Вычислить новую скорость для гаста.
     *
     * @param lookDirection нормализованный (или близкий к нему) вектор направления взгляда игрока.
     * @param currentVelocity текущая скорость гаста.
     * @param baseSpeed базовая скорость полёта (блоков/тик) до множителя.
     * @param tierMultiplier множитель тира упряжки (например 2.0 = +100%).
     * @param acceleration коэффициент сглаживания в диапазоне (0..1].
     * @param maxVelocity максимальная длина результирующего вектора.
     * @param verticalLookFactor множитель Y-составляющей (для регулировки крутизны набора высоты).
     * @return новая скорость, готовая к {@code ghast.setVelocity(...)}.
     */
    public static Vector compute(Vector lookDirection,
                                 Vector currentVelocity,
                                 double baseSpeed,
                                 double tierMultiplier,
                                 double acceleration,
                                 double maxVelocity,
                                 double verticalLookFactor) {
        double speed = baseSpeed * Math.max(0.0, tierMultiplier);
        Vector look = lookDirection.clone();
        if (look.lengthSquared() == 0) {
            // Без направления — затухание.
            return currentVelocity.clone().multiply(1.0 - clamp01(acceleration));
        }
        Vector target = look.normalize();
        target.setY(target.getY() * verticalLookFactor);
        target.multiply(speed);

        double a = clamp01(acceleration);
        Vector newVel = currentVelocity.clone()
                .multiply(1.0 - a)
                .add(target.multiply(a));

        double max = Math.max(0.05, maxVelocity);
        if (newVel.lengthSquared() > max * max) {
            newVel.normalize().multiply(max);
        }
        return newVel;
    }

    /**
     * Сместить вертикальную составляющую вверх на jumpBoost (в пределах maxVelocity).
     */
    public static Vector applyJump(Vector velocity, double jumpBoost, double maxVelocity) {
        Vector v = velocity.clone();
        v.setY(Math.min(maxVelocity, v.getY() + jumpBoost));
        return v;
    }

    /**
     * Зафиксировать «зависание»: убираем вертикальную составляющую.
     */
    public static Vector applyHover(Vector velocity) {
        Vector v = velocity.clone();
        v.setY(0);
        return v;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
