package net.himeki.serverchan.neoforge;

import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.ci.CITestRunner;
import net.himeki.serverchan.neoforge.command.ServerChanCommand;
import net.himeki.serverchan.config.ConfigLoader;
import net.himeki.serverchan.config.ServerChanConfigBase;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * NeoForge entry point for ServerChan
 */
@Mod(ServerChanCore.MOD_ID)
public class NeoforgeMain {
    private static NeoforgeMessageBroadcaster messageBroadcaster;

    public NeoforgeMain(IEventBus modEventBus) {
        // Register mod setup event
        modEventBus.addListener(this::setup);

        // Register ourselves for server and game events
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new NeoforgeEventHandler());
    }

    private void setup(final FMLCommonSetupEvent event) {
        // Initialize config using ConfigLib
        Path configDir = FMLPaths.CONFIGDIR.get();
        ServerChanConfigBase config = ConfigLoader.initialize(configDir);

        // Initialize the core with the base config
        ServerChanCore.initialize(config);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (CITestRunner.isCITestMode()) {
            ServerChanCore.LOGGER.info("CI Test mode detected - initializing test environment");
            initializeCITestMode(event.getServer());
            return;
        }
        initializeNormalMode(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ServerChanCore.shutdown();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ServerChanCommand.register(event.getDispatcher());
    }

    private void initializeNormalMode(MinecraftServer server) {
        // Reload config to pick up any changes
        ServerChanCore.reloadConfig(ConfigLoader.reload());

        // Initialize message broadcaster
        messageBroadcaster = new NeoforgeMessageBroadcaster(server);
        ServerChanCore.setMessageBroadcaster(messageBroadcaster);

        // Initialize command executor
        NeoforgeCommandExecutor commandExecutor = new NeoforgeCommandExecutor(server);
        ServerChanCore.setCommandExecutor(commandExecutor);
    }

    private void initializeCITestMode(MinecraftServer server) {
        // Initialize message broadcaster and command executor first
        messageBroadcaster = new NeoforgeMessageBroadcaster(server);
        ServerChanCore.setMessageBroadcaster(messageBroadcaster);

        NeoforgeCommandExecutor commandExecutor = new NeoforgeCommandExecutor(server);
        ServerChanCore.setCommandExecutor(commandExecutor);

        // Schedule test to run after a short delay
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            NeoforgeCITestCallback callback = new NeoforgeCITestCallback(server);
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
