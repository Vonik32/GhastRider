package net.astra.ghastrider;

import net.astra.ghastrider.command.GhastRiderCommand;
import net.astra.ghastrider.command.subcommand.GiveSubcommand;
import net.astra.ghastrider.command.subcommand.InfoSubcommand;
import net.astra.ghastrider.command.subcommand.ReloadSubcommand;
import net.astra.ghastrider.command.subcommand.UnmountSubcommand;
import net.astra.ghastrider.config.ConfigManager;
import net.astra.ghastrider.data.GhastData;
import net.astra.ghastrider.data.PdcKeys;
import net.astra.ghastrider.flight.FlightTask;
import net.astra.ghastrider.flight.ProtocolGlowingTask;
import net.astra.ghastrider.item.ItemManager;
import net.astra.ghastrider.listener.ChunkLifecycleListener;
import net.astra.ghastrider.listener.GhastAIListener;
import net.astra.ghastrider.listener.GhastDeathListener;
import net.astra.ghastrider.listener.GhastProtectionListener;
import net.astra.ghastrider.listener.HarnessInteractListener;
import net.astra.ghastrider.listener.PlayerLifecycleListener;
import net.astra.ghastrider.listener.RecipeGuardListener;
import net.astra.ghastrider.manager.GhastBuffService;
import net.astra.ghastrider.manager.HarnessManager;
import net.astra.ghastrider.manager.RideController;
import net.astra.ghastrider.recipe.RecipeManager;
import net.astra.ghastrider.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class GhastRiderPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private PdcKeys pdcKeys;
    private GhastData ghastData;
    private MessageUtil messageUtil;
    private ItemManager itemManager;
    private RecipeManager recipeManager;
    private GhastBuffService buffService;
    private RideController rideController;
    private HarnessManager harnessManager;
    private FlightTask flightTask;
    private ProtocolGlowingTask glowingTask;

    @Override
    public void onEnable() {
        // 1. Конфиги.
        configManager = new ConfigManager(this);
        configManager.load();

        // 2. PDC ключи.
        pdcKeys = new PdcKeys(this);
        ghastData = new GhastData(pdcKeys, configManager.getDataVersion());
        messageUtil = new MessageUtil(configManager);

        // 3. Standalone item-система.
        itemManager = new ItemManager(this, pdcKeys);
        itemManager.load();

        // 4. Рецепты (после items, чтобы прототипы для ExactChoice были доступны).
        recipeManager = new RecipeManager(this, itemManager);
        recipeManager.load();

        // 5. Менеджеры геймплея.
        buffService = new GhastBuffService(this, configManager, ghastData, pdcKeys);
        rideController = new RideController(this, configManager, ghastData, pdcKeys, messageUtil);
        rideController.registerLoadedGhasts();
        harnessManager = new HarnessManager(this, configManager, itemManager,
                ghastData, buffService, rideController, messageUtil);

        // 6. Слушатели.
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new HarnessInteractListener(configManager, itemManager,
                ghastData, harnessManager, rideController, messageUtil), this);
        pm.registerEvents(new GhastProtectionListener(configManager, ghastData, rideController), this);
        pm.registerEvents(new GhastAIListener(configManager, ghastData), this);
        pm.registerEvents(new GhastDeathListener(ghastData, harnessManager, rideController), this);
        pm.registerEvents(new ChunkLifecycleListener(ghastData, buffService, rideController), this);
        pm.registerEvents(new PlayerLifecycleListener(this, rideController), this);
        pm.registerEvents(new RecipeGuardListener(this, itemManager, recipeManager.getRegistry()), this);

        // 7. Тик полёта.
        flightTask = new FlightTask(this, configManager, rideController, ghastData);
        flightTask.start();

        // 8. Owner-only glowing (через ProtocolLib).
        glowingTask = new ProtocolGlowingTask(this, configManager, ghastData, rideController);
        glowingTask.start();

        // 9. Команда (создаём после всех зависимостей, чтобы reload получил glowing task).
        PluginCommand cmd = getCommand("ghastrider");
        if (cmd != null) {
            GhastRiderCommand executor = new GhastRiderCommand(messageUtil,
                    new ReloadSubcommand(configManager, itemManager, recipeManager, buffService,
                            glowingTask, messageUtil),
                    new InfoSubcommand(ghastData, messageUtil),
                    new UnmountSubcommand(rideController, messageUtil),
                    new GiveSubcommand(itemManager, messageUtil));
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("GhastRider включён (standalone mode).");
    }

    @Override
    public void onDisable() {
        // Порядок важен: сначала ссаживаем всех всадников (пока задачи ещё активны
        // и могут безопасно отрабатывать), затем останавливаем задачи и снимаем рецепты.
        if (rideController != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (rideController.isRiding(p)) {
                    try {
                        rideController.dismount(p);
                    } catch (Exception e) {
                        getLogger().warning("Ошибка при dismount игрока " + p.getName() + ": " + e.getMessage());
                    }
                }
            }
            // Полностью очищаем карты, чтобы не утекли UUID между reload-ами плагина
            // (Bukkit держит экземпляр Plugin в classloader-е до сборки мусора).
            rideController.clearAll();
        }
        if (flightTask != null) {
            try {
                flightTask.cancel();
            } catch (IllegalStateException ignored) {
                // Не запущен — ок.
            }
            flightTask = null;
        }
        if (glowingTask != null) {
            glowingTask.stop();
            glowingTask = null;
        }
        if (recipeManager != null) {
            recipeManager.unregisterAll();
        }
        getLogger().info("GhastRider выключён.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public RideController getRideController() {
        return rideController;
    }

    public HarnessManager getHarnessManager() {
        return harnessManager;
    }

    public GhastBuffService getBuffService() {
        return buffService;
    }
}
