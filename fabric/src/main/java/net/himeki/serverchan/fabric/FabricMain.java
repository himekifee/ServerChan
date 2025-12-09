package net.himeki.serverchan.fabric;

import net.fabricmc.api.ModInitializer;
#if MC_VER >= MC_1_19
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
#else
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
#endif
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.ci.CITestRunner;
import net.himeki.serverchan.fabric.command.ServerChanCommand;
import net.himeki.serverchan.config.ConfigLoader;
import net.himeki.serverchan.config.ServerChanConfigBase;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fabric entry point for ServerChan
 */
public class FabricMain implements ModInitializer {
    private static FabricMessageBroadcaster messageBroadcaster;

    @Override
    public void onInitialize() {
        // Initialize config using ConfigLib
        Path configDir = FabricLoader.getInstance().getConfigDir();
        ServerChanConfigBase config = ConfigLoader.initialize(configDir);

        // Initialize the core with the base config
        ServerChanCore.initialize(config);

        // Register command
        #if MC_VER >= MC_1_19
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ServerChanCommand.register(dispatcher));
        #else
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                ServerChanCommand.register(dispatcher));
        #endif

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Check if running in CI test mode
            if (CITestRunner.isCITestMode()) {
                ServerChanCore.LOGGER.info("CI Test mode detected - initializing test environment");
                initializeCITestMode(server);
                return;
            }

            // Normal initialization
            initializeNormalMode(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerChanCore.shutdown();
        });
    }

    /**
     * Initialize normal mode
     */
    private void initializeNormalMode(MinecraftServer server) {
        // Reload config to pick up any changes
        ServerChanCore.reloadConfig(ConfigLoader.reload());

        // Initialize message broadcaster
        messageBroadcaster = new FabricMessageBroadcaster(server);
        ServerChanCore.setMessageBroadcaster(messageBroadcaster);

        // Register Fabric-specific event handlers
        FabricServerEventHandler.register();

        // Initialize command executor
        FabricCommandExecutor commandExecutor = new FabricCommandExecutor(server);
        ServerChanCore.setCommandExecutor(commandExecutor);
    }

    /**
     * Initialize CI test mode
     */
    private void initializeCITestMode(MinecraftServer server) {
        // Initialize message broadcaster and command executor first
        messageBroadcaster = new FabricMessageBroadcaster(server);
        ServerChanCore.setMessageBroadcaster(messageBroadcaster);

        FabricCommandExecutor commandExecutor = new FabricCommandExecutor(server);
        ServerChanCore.setCommandExecutor(commandExecutor);

        // Schedule test to run after a short delay
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            FabricCITestCallback callback = new FabricCITestCallback(server);
            CITestRunner testRunner = new CITestRunner(callback);
            testRunner.runTest().thenAccept(passed -> {
                if (passed) {
                    ServerChanCore.LOGGER.info("CI Test PASSED - Shutting down server");
                } else {
                    ServerChanCore.LOGGER.error("CI Test FAILED - Shutting down server with error");
                }

                // Write result and shutdown
                callback.writeTestResult(passed);
                callback.shutdownServer();
            });
            scheduler.shutdown();
        }, 5, TimeUnit.SECONDS);
    }

    /**
     * Reload the configuration
     */
    public static void reloadConfig() {
        ServerChanConfigBase newConfig = ConfigLoader.reload();
        ServerChanCore.reloadConfig(newConfig);
    }
}
