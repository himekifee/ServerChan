package net.himeki.serverchan.neoforge;

import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.i18n.I18n;
import net.himeki.serverchan.util.PermissionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * NeoForge-specific event handlers for ServerChan
 * Note: NeoForge is only available for MC 1.20.2+
 */
public class NeoforgeEventHandler {
    static {
        // Load Minecraft translations once on initialization
        I18n.loadMinecraftTranslations();
    }

    /**
     * Handle chat messages
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String playerName = player.getName().getString();

        // Extract chat message - NeoForge uses different methods across versions
        #if MC_VER >= MC_1_20_4
            String chatMessage = event.getRawText();
        #else
            String chatMessage = event.getMessage().getString();
        #endif

        // Get permission level across API changes
        #if MC_VER >= MC_1_21_6
            MinecraftServer server = player.createCommandSourceStack().getServer();
        #else
            MinecraftServer server = player.server;
        #endif
        int permissionLevel = PermissionUtil.getPermissionLevel(server, player.getGameProfile());

        // Pass to the core handler
        ServerChanCore.onChatMessage(playerName, chatMessage, permissionLevel);
    }

    /**
     * Handle player join events
     */
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
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
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
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
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // Get the death message
            Component deathMessage = event.getSource().getLocalizedDeathMessage(serverPlayer);

            if (deathMessage.getContents() instanceof TranslatableContents translatableContent) {
                String key = translatableContent.getKey();
                Object[] args = translatableContent.getArgs();

                // Convert args to strings for translation
                String[] stringArgs = new String[args.length];
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Component comp) {
                        stringArgs[i] = comp.getString();
                    } else {
                        stringArgs[i] = args[i].toString();
                    }
                }

                String translatedMessage = I18n.getMinecraftTranslation(key, (Object[]) stringArgs);

                // Pass to the core handler
                ServerChanCore.onGameEvent(key, translatedMessage);
            }
        }
    }
}
