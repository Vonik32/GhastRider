package net.astra.ghastrider.config;

import org.jetbrains.annotations.Nullable;

/**
 * Тиры упряжек ездового Гаста. Порядок enum соответствует возрастанию мощности.
 */
public enum HarnessTier {
    BASIC("basic", "<gradient:#c0c0c0:#ffffff><b>Обычная</b></gradient>"),
    IRON("iron", "<gradient:#cfd8dc:#90a4ae><b>Железная</b></gradient>"),
    GOLD("gold", "<gradient:#ffe066:#ffb300><b>Золотая</b></gradient>"),
    DIAMOND("diamond", "<gradient:#5cdcff:#a0f0ff><b>Алмазная</b></gradient>"),
    NETHERITE("netherite", "<gradient:#5d4037:#ff6e40><b>Незеритовая</b></gradient>");

    private final String configKey;
    private final String displayName;

    HarnessTier(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDisplayName() {
        return displayName;
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
