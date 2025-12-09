package net.himeki.serverchan.forge;

import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.ci.CITestRunner;
import net.himeki.serverchan.config.ConfigLoader;
import net.himeki.serverchan.config.ServerChanConfigBase;
import net.himeki.serverchan.forge.command.ServerChanCommand;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
#if MC_VER >= MC_1_18
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
#elif MC_VER >= MC_1_17
import net.minecraftforge.fmlserverevents.FMLServerStartedEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppingEvent;
#else
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
#endif
#if MC_VER < MC_1_21_6
import net.minecraftforge.eventbus.api.IEventBus;
#endif
#if MC_VER >= MC_1_21_6
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
#else
import net.minecraftforge.eventbus.api.SubscribeEvent;
#endif
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Forge entry point for ServerChan
 */
@Mod(ServerChanCore.MOD_ID)
public class ForgeMain {
    private static ForgeMessageBroadcaster messageBroadcaster;

    public ForgeMain(FMLJavaModLoadingContext context) {
        initialize(context);
    }
    public ForgeMain() {
        this(FMLJavaModLoadingContext.get());
    }

    private void initialize(FMLJavaModLoadingContext context) {
        registerSetupListener(context);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private static void setup(final FMLCommonSetupEvent event) {
        // Initialize config using ConfigLib
        Path configDir = FMLPaths.CONFIGDIR.get();
        ServerChanConfigBase config = ConfigLoader.initialize(configDir);

        // Initialize the core with the base config
        ServerChanCore.initialize(config);
    }

    private static void initializeNormalMode(MinecraftServer server) {
        // Reload config to pick up any changes
        ServerChanCore.reloadConfig(ConfigLoader.reload());

        // Initialize message broadcaster
        messageBroadcaster = new ForgeMessageBroadcaster(server);
        ServerChanCore.setMessageBroadcaster(messageBroadcaster);

        // Initialize command executor
        ForgeCommandExecutor commandExecutor = new ForgeCommandExecutor(server);
        ServerChanCore.setCommandExecutor(commandExecutor);
    }

    private static void initializeCITestMode(MinecraftServer server) {
        // Initialize message broadcaster and command executor first
        messageBroadcaster = new ForgeMessageBroadcaster(server);
        ServerChanCore.setMessageBroadcaster(messageBroadcaster);

        ForgeCommandExecutor commandExecutor = new ForgeCommandExecutor(server);
        ServerChanCore.setCommandExecutor(commandExecutor);

        // Schedule test to run after a short delay
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            ForgeCITestCallback callback = new ForgeCITestCallback(server);
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

    private static void registerSetupListener(FMLJavaModLoadingContext context) {
#if MC_VER >= MC_1_21_6
        if (tryRegisterUsingModBusGroup(context)) {
            return;
        }
#endif
        registerLegacySetupListener(context);
    }

#if MC_VER >= MC_1_21_6
    @SuppressWarnings("unchecked")
    private static boolean tryRegisterUsingModBusGroup(FMLJavaModLoadingContext context) {
        try {
            Method getModBusGroup = FMLJavaModLoadingContext.class.getMethod("getModBusGroup");
            Object modBusGroup = getModBusGroup.invoke(context);
            if (modBusGroup == null) {
                return false;
            }

            Method getBusMethod = null;
            for (Method candidate : FMLCommonSetupEvent.class.getMethods()) {
                if (!candidate.getName().equals("getBus")) {
                    continue;
                }
                Class<?>[] params = candidate.getParameterTypes();
                if (params.length == 1 && params[0].isInstance(modBusGroup)) {
                    getBusMethod = candidate;
                    break;
                }
            }

            if (getBusMethod == null) {
                return false;
            }

            Object eventBus = getBusMethod.invoke(null, modBusGroup);
            if (eventBus == null) {
                return false;
            }

            Method addListener = eventBus.getClass().getMethod("addListener", Consumer.class);
            addListener.invoke(eventBus, (Consumer<FMLCommonSetupEvent>) ForgeMain::setup);
            return true;
        } catch (ReflectiveOperationException ex) {
            ServerChanCore.LOGGER.debug("Unable to access Forge mod bus group APIs", ex);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerLegacySetupListener(FMLJavaModLoadingContext context) {
        try {
            Method method = FMLJavaModLoadingContext.class.getMethod("getModEventBus");
            Object eventBus = method.invoke(context);
            if (eventBus == null) {
                throw new IllegalStateException("Forge mod event bus returned null");
            }

            Method addListener = eventBus.getClass().getMethod("addListener", Consumer.class);
            addListener.invoke(eventBus, (Consumer<FMLCommonSetupEvent>) ForgeMain::setup);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to register ServerChan Forge setup listener", ex);
        }
    }
#else
    private static void registerLegacySetupListener(FMLJavaModLoadingContext context) {
        IEventBus eventBus = context.getModEventBus();
        if (eventBus == null) {
            throw new IllegalStateException("Forge mod event bus returned null");
        }
        eventBus.addListener(ForgeMain::setup);
    }
#endif

    #if MC_VER >= MC_1_18
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (!event.getServer().isDedicatedServer()) {
            return;
        }
        if (CITestRunner.isCITestMode()) {
            ServerChanCore.LOGGER.info("CI Test mode detected - initializing test environment");
            initializeCITestMode(event.getServer());
            return;
        }
        initializeNormalMode(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (!event.getServer().isDedicatedServer()) {
            return;
        }
        ServerChanCore.shutdown();
    }
    #else
    @SubscribeEvent
    public void onServerStarted(FMLServerStartedEvent event) {
        if (!event.getServer().isDedicatedServer()) {
            return;
        }
        if (CITestRunner.isCITestMode()) {
            ServerChanCore.LOGGER.info("CI Test mode detected - initializing test environment");
            initializeCITestMode(event.getServer());
            return;
        }
        initializeNormalMode(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        if (!event.getServer().isDedicatedServer()) {
            return;
        }
        ServerChanCore.shutdown();
    }
    #endif

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ServerChanCommand.register(event.getDispatcher());
    }
}
