package net.astra.ghastrider.command.subcommand;

import net.astra.ghastrider.config.ConfigManager;
import net.astra.ghastrider.flight.ProtocolGlowingTask;
import net.astra.ghastrider.item.ItemManager;
import net.astra.ghastrider.manager.GhastBuffService;
import net.astra.ghastrider.recipe.RecipeManager;
import net.astra.ghastrider.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HappyGhast;

public final class ReloadSubcommand implements Subcommand {

    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final RecipeManager recipeManager;
    private final GhastBuffService buffService;
    private final ProtocolGlowingTask glowingTask;
    private final MessageUtil messageUtil;

    public ReloadSubcommand(ConfigManager configManager,
                            ItemManager itemManager,
                            RecipeManager recipeManager,
                            GhastBuffService buffService,
                            ProtocolGlowingTask glowingTask,
                            MessageUtil messageUtil) {
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.recipeManager = recipeManager;
        this.buffService = buffService;
        this.glowingTask = glowingTask;
        this.messageUtil = messageUtil;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return "ghastrider.admin";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        configManager.reload();
        itemManager.reload();
        recipeManager.reload();
        // Перезапустим owner-индикатор с новыми настройками.
        if (glowingTask != null) {
            glowingTask.start();
        }
        // Перепроверим всех загруженных managed-Гастов.
        for (World world : Bukkit.getWorlds()) {
            for (HappyGhast ghast : world.getEntitiesByClass(HappyGhast.class)) {
                buffService.reapply(ghast);
            }
        }
        messageUtil.send(sender, "reload-success");
        return true;
    }
}
