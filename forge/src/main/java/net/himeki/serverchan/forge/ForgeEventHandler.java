package net.himeki.serverchan.forge;

import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.i18n.I18n;
import net.minecraft.network.chat.Component;
#if MC_VER >= MC_1_19_2
import net.minecraft.network.chat.contents.TranslatableContents;
#endif
#if MC_VER < MC_1_19_2
import net.minecraft.network.chat.TranslatableComponent;
#endif
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
#if MC_VER >= MC_1_21_6
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
#else
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
#endif
import net.minecraftforge.fml.common.Mod;

/**
 * Forge-specific event handlers for ServerChan
 */
@Mod.EventBusSubscriber(modid = ServerChanCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEventHandler {
    static {
        // Load Minecraft translations once on initialization
        I18n.loadMinecraftTranslations();
    }

    /**
     * Handle chat messages
     */
#if MC_VER >= MC_1_21_6
    @SubscribeEvent(priority = Priority.NORMAL)
#else
    @SubscribeEvent(priority = EventPriority.NORMAL)
#endif
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String playerName = player.getName().getString();

        // Extract chat message based on version
        #if MC_VER >= MC_1_19_3
        String chatMessage = event.getRawText();
        #elif MC_VER >= MC_1_19_2
        String chatMessage = event.getMessage().getString();
        #else
        String chatMessage = event.getMessage();
        #endif

        int permissionLevel = getPermissionLevel(player);

        // Pass to the core handler
        ServerChanCore.onChatMessage(playerName, chatMessage, permissionLevel);
    }

    /**
     * Handle player join events
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            String playerName = serverPlayer.getName().getString();
            String translatedMessage = I18n.getMinecraftTranslation("multiplayer.player.joined", playerName);

            // Pass to the core handler
            ServerChanCore.onGameEvent("multiplayer.player.joined", translatedMessage);
        }
    }

    /**
     * Handle player leave events
     */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            String playerName = serverPlayer.getName().getString();
            String translatedMessage = I18n.getMinecraftTranslation("multiplayer.player.left", playerName);

            // Pass to the core handler
            ServerChanCore.onGameEvent("multiplayer.player.left", translatedMessage);
        }
    }

    /**
     * Handle death events
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        Component deathMessage = event.getSource().getLocalizedDeathMessage(serverPlayer);

        #if MC_VER >= MC_1_19_2
        if (deathMessage.getContents() instanceof TranslatableContents tc) {
            handleDeathMessage(tc.getKey(), tc.getArgs());
        }
        #else
        if (deathMessage instanceof TranslatableComponent tc) {
            handleDeathMessage(tc.getKey(), tc.getArgs());
        }
        #endif
    }

    private static void handleDeathMessage(String key, Object[] args) {
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = (args[i] instanceof Component comp) ? comp.getString() : args[i].toString();
        }
        String translatedMessage = I18n.getMinecraftTranslation(key, (Object[]) stringArgs);
        ServerChanCore.onGameEvent(key, translatedMessage);
    }

    private static int getPermissionLevel(ServerPlayer player) {
        #if MC_VER >= MC_1_21_6
            MinecraftServer server = player.createCommandSourceStack().getServer();
            net.minecraft.server.players.NameAndId nameAndId = new net.minecraft.server.players.NameAndId(player.getGameProfile());
            return server.getProfilePermissions(nameAndId);
        #else
            MinecraftServer server = player.server;
            return server.getProfilePermissions(player.getGameProfile());
        #endif
    }
}
