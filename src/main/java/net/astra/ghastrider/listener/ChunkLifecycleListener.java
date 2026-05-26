package net.astra.ghastrider.listener;

import org.bukkit.event.world.EntitiesLoadEvent;
import net.astra.ghastrider.data.GhastData;
import net.astra.ghastrider.manager.GhastBuffService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Восстанавливает «летучие» состояния managed-Гаста после загрузки чанков:
 * - перевыдаёт баффы;
 * - выключает AI;
 * - выставляет glowing.
 *
 * PDC уже сохранён движком — сами данные не пересоздаём.
 */
public final class ChunkLifecycleListener implements Listener {

    private final GhastData ghastData;
    private final GhastBuffService buffService;

    public ChunkLifecycleListener(GhastData ghastData, GhastBuffService buffService) {
        this.ghastData = ghastData;
        this.buffService = buffService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof HappyGhast ghast)) {
                continue;
            }
            if (!ghastData.isManaged(ghast)) {
                continue;
            }
            buffService.reapply(ghast);
        }
    }
}
