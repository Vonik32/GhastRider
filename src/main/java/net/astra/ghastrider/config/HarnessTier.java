package net.astra.ghastrider.config;

import org.jetbrains.annotations.Nullable;

/**
 * Тиры упряжек ездового Гаста. Порядок enum соответствует возрастанию мощности.
 */
public enum HarnessTier {
    BASIC("basic"),
    IRON("iron"),
    GOLD("gold"),
    DIAMOND("diamond"),
    NETHERITE("netherite");

    private final String configKey;

    HarnessTier(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    /**
     * Безопасный парсинг по строковому имени enum (как сохранено в PDC).
     */
    @Nullable
    public static HarnessTier fromName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return HarnessTier.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
