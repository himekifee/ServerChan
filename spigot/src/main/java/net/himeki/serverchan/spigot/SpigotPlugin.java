package net.himeki.serverchan.spigot;

import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.ci.CITestRunner;
import net.himeki.serverchan.config.ConfigLoader;
import net.himeki.serverchan.config.ServerChanConfigBase;
import net.himeki.serverchan.i18n.I18n;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Spigot/Bukkit plugin entry point for ServerChan
 */
public class SpigotPlugin extends JavaPlugin {
    private static SpigotPlugin instance;

    @Override
    public void onEnable() {
        instance = this;

        // Check if running in CI test mode
        if (CITestRunner.isCITestMode()) {
            getLogger().info("CI Test mode detected - initializing test environment");
            initializeCITestMode();
            return;
        }

        // Normal startup
        initializeNormalMode();
    }

    /**
     * Initialize normal plugin mode
     */
    private void initializeNormalMode() {
        // Initialize config using ConfigLib
        ServerChanConfigBase config = ConfigLoader.initialize(getDataFolder().toPath());

        // Initialize the core
        ServerChanCore.initialize(config);

        // Initialize message broadcaster
        SpigotMessageBroadcaster messageBroadcaster = new SpigotMessageBroadcaster();
        ServerChanCore.setMessageBroadcaster(messageBroadcaster);

        // Initialize command executor
        SpigotCommandExecutor commandExecutor = new SpigotCommandExecutor(this);
        ServerChanCore.setCommandExecutor(commandExecutor);

        // Register event listeners
        SpigotEventListener eventListener = new SpigotEventListener();
        getServer().getPluginManager().registerEvents(eventListener, this);

        // Register commands
        SpigotCommandHandler commandHandler = new SpigotCommandHandler();
        getCommand("serverchan").setExecutor(commandHandler);
        getCommand("serverchan").setTabCompleter(commandHandler);

        getLogger().info(I18n.get("serverchan.startup"));
    }

    /**
     * Initialize CI test mode
     */
    private void initializeCITestMode() {
        // Initialize message broadcaster and command executor first
        SpigotMessageBroadcaster messageBroadcaster = new SpigotMessageBroadcaster();
        ServerChanCore.setMessageBroadcaster(messageBroadcaster);

        SpigotCommandExecutor commandExecutor = new SpigotCommandExecutor(this);
        ServerChanCore.setCommandExecutor(commandExecutor);

        // Schedule test to run after server is fully started
        Bukkit.getScheduler().runTaskLater(this, () -> {
            SpigotCITestCallback callback = new SpigotCITestCallback(this);
            CITestRunner testRunner = new CITestRunner(callback);
            testRunner.runTest().thenAccept(passed -> {
                if (passed) {
                    getLogger().info("CI Test PASSED - Shutting down server");
                } else {
                    getLogger().severe("CI Test FAILED - Shutting down server with error");
                }

                // Write result and shutdown
                callback.writeTestResult(passed);
                callback.shutdownServer();
            });
        }, 100L); // 5 seconds after plugin enable (100 ticks)
    }

    @Override
    public void onDisable() {
        ServerChanCore.shutdown();
        getLogger().info("ServerChan has been disabled!");
    }

    /**
     * Reload the configuration
     */
    public static void reloadServerChanConfigBase() {
        ServerChanConfigBase newConfig = ConfigLoader.reload();
        ServerChanCore.reloadConfig(newConfig);
    }

    public static SpigotPlugin getInstance() {
        return instance;
    }
}
