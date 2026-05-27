package net.astra.ghastrider.listener;

import net.astra.ghastrider.config.ConfigManager;
import net.astra.ghastrider.config.HarnessTier;
import net.astra.ghastrider.data.GhastData;
import net.astra.ghastrider.item.ItemManager;
import net.astra.ghastrider.manager.HarnessManager;
import net.astra.ghastrider.manager.RideController;
import net.astra.ghastrider.util.MessageUtil;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Главный слушатель интеракции игрока с Гастом.
 * Решает: надеть, снять, заменить или сесть верхом.
 */
public final class HarnessInteractListener implements Listener {

    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final GhastData ghastData;
    private final HarnessManager harnessManager;
    private final RideController rideController;
    private final MessageUtil messageUtil;

    public HarnessInteractListener(ConfigManager configManager,
                                   ItemManager itemManager,
                                   GhastData ghastData,
                                   HarnessManager harnessManager,
                                   RideController rideController,
                                   MessageUtil messageUtil) {
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.ghastData = ghastData;
        this.harnessManager = harnessManager;
        this.rideController = rideController;
        this.messageUtil = messageUtil;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof HappyGhast ghast)) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();

        // Защита от двойного срабатывания (рука + офф-рука).
        if (hand != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }
        if (!player.hasPermission("ghastrider.use")) {
            event.setCancelled(true);
            messageUtil.send(player, "no-permission");
            return;
        }

        PlayerInventory inv = player.getInventory();
        ItemStack mainHand = inv.getItemInMainHand();
        boolean handEmpty = mainHand == null || mainHand.getType().isAir();

        boolean managed = ghastData.isManaged(ghast);
        boolean owner = managed && ghastData.isOwner(ghast, player);
        boolean bypass = player.hasPermission("ghastrider.bypass.owner");

        // Не managed Гаст — попытка надеть упряжку.
        if (!managed) {
            if (handEmpty) {
                return;
            }
            HarnessTier tier = resolveTier(mainHand);
            if (tier == null) {
                return;
            }
            event.setCancelled(true);
            harnessManager.applyHarness(player, ghast, mainHand, hand);
            return;
        }

        // Managed Гаст: всё, что не от владельца — отменяется.
        if (!owner && !bypass) {
            if (handEmpty) {
                event.setCancelled(true);
                rideController.mount(player, ghast);
                return;
            }
            event.setCancelled(true);
            messageUtil.send(player, "not-owner");
            return;
        }

        // Sneaking + пустая рука = снятие упряжки. Только владелец (или admin bypass) может это сделать.
        // Дроп идёт в инвентарь игрока, излишки — на землю рядом.
        if (handEmpty && player.isSneaking()) {
            event.setCancelled(true);
            harnessManager.removeHarness(player, ghast, false);
            return;
        }

        // Пустая рука без шифта = посадка верхом.
        if (handEmpty) {
            event.setCancelled(true);
            rideController.mount(player, ghast);
            return;
        }

        // В руке другая упряжка — попытка заменить.
        HarnessTier tier = resolveTier(mainHand);
        if (tier != null) {
            event.setCancelled(true);
            harnessManager.swapHarness(player, ghast, mainHand, hand);
        }
    }

    private HarnessTier resolveTier(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        String id = itemManager.getCustomId(item);
        if (id == null) {
            return null;
        }
        return configManager.getTierByItemId(id);
    }
}
