package net.astra.ghastrider.data;

import net.astra.ghastrider.config.HarnessTier;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Обёртка для чтения/записи данных о упряжке в PDC сущности Ghast.
 * Все операции синхронные и работают только в основном тике.
 */
public final class GhastData {

    private static final byte MANAGED_FLAG = (byte) 1;

    private final PdcKeys keys;
    private final int currentDataVersion;

    public GhastData(PdcKeys keys, int currentDataVersion) {
        this.keys = keys;
        this.currentDataVersion = currentDataVersion;
    }

    public boolean isManaged(HappyGhast ghast) {
        PersistentDataContainer pdc = ghast.getPersistentDataContainer();
        Byte flag = pdc.get(keys.managed, PersistentDataType.BYTE);
        return flag != null && flag == MANAGED_FLAG;
    }

    @Nullable
    public UUID getOwner(HappyGhast ghast) {
        String raw = ghast.getPersistentDataContainer().get(keys.ownerUuid, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isOwner(HappyGhast ghast, Player player) {
        UUID ownerId = getOwner(ghast);
        return ownerId != null && ownerId.equals(player.getUniqueId());
    }

    @Nullable
    public String getOwnerName(HappyGhast ghast) {
        return ghast.getPersistentDataContainer().get(keys.ownerName, PersistentDataType.STRING);
    }

    @Nullable
    public HarnessTier getTier(HappyGhast ghast) {
        String raw = ghast.getPersistentDataContainer().get(keys.harnessTier, PersistentDataType.STRING);
        return HarnessTier.fromName(raw);
    }

    @Nullable
    public String getHarnessItemId(HappyGhast ghast) {
        return ghast.getPersistentDataContainer().get(keys.harnessItemId, PersistentDataType.STRING);
    }

    public int getDataVersion(HappyGhast ghast) {
        Integer v = ghast.getPersistentDataContainer().get(keys.dataVersion, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public void apply(HappyGhast ghast, UUID owner, String ownerName, HarnessTier tier, String itemId) {
        PersistentDataContainer pdc = ghast.getPersistentDataContainer();
        pdc.set(keys.managed, PersistentDataType.BYTE, MANAGED_FLAG);
        pdc.set(keys.ownerUuid, PersistentDataType.STRING, owner.toString());
        if (ownerName != null) {
            pdc.set(keys.ownerName, PersistentDataType.STRING, ownerName);
        }
        pdc.set(keys.harnessTier, PersistentDataType.STRING, tier.name());
        pdc.set(keys.harnessItemId, PersistentDataType.STRING, itemId);
        pdc.set(keys.dataVersion, PersistentDataType.INTEGER, currentDataVersion);
    }

    public void updateTier(HappyGhast ghast, HarnessTier tier, String itemId) {
        PersistentDataContainer pdc = ghast.getPersistentDataContainer();
        pdc.set(keys.harnessTier, PersistentDataType.STRING, tier.name());
        pdc.set(keys.harnessItemId, PersistentDataType.STRING, itemId);
    }

    public void clear(HappyGhast ghast) {
        PersistentDataContainer pdc = ghast.getPersistentDataContainer();
        pdc.remove(keys.managed);
        pdc.remove(keys.ownerUuid);
        pdc.remove(keys.ownerName);
        pdc.remove(keys.harnessTier);
        pdc.remove(keys.harnessItemId);
        pdc.remove(keys.dataVersion);
    }
}
