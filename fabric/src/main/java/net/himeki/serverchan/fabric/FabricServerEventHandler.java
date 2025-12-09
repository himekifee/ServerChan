package net.himeki.serverchan.fabric;

#if MC_VER >= MC_1_19
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
#endif
import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.i18n.I18n;
import net.himeki.serverchan.util.PermissionUtil;

// Version-specific imports
#if MC_VER >= MC_1_19_2
import net.minecraft.network.chat.contents.TranslatableContents;
#elif MC_VER >= MC_1_19
import net.minecraft.text.TranslatableText;
#endif

public class FabricServerEventHandler {
    static {
        // Load Minecraft translations once on initialization
        I18n.loadMinecraftTranslations();
    }

    public static void register() {
        #if MC_VER >= MC_1_19
        // Chat message event registration (version-specific)
        #if MC_VER >= MC_1_19_2
            // 1.19.2+ uses signed messages
            ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
                String playerName = sender.getName().getString();
                #if MC_VER >= MC_1_20_1
                    String chatMessage = message.signedBody().content();
                #else
                    String chatMessage = message.signedContent();
                #endif

                int permissionLevel = getPlayerPermissionLevel(sender);
                ServerChanCore.onChatMessage(playerName, chatMessage, permissionLevel);
            });
        #else
            // 1.19.0/1.19.1 chat system
            ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
                String playerName = sender.getName().getString();
                String chatMessage = message.getString();
                int permissionLevel = getPlayerPermissionLevel(sender);
                ServerChanCore.onChatMessage(playerName, chatMessage, permissionLevel);
            });
        #endif

        // Game message event
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            #if MC_VER >= MC_1_19_2
                if (message.getContents() instanceof TranslatableContents translatableContent) {
                    String key = translatableContent.getKey();
                    Object[] args = translatableContent.getArgs();
                    String translatedMessage = I18n.getMinecraftTranslation(key, args);
                    ServerChanCore.onGameEvent(key, translatedMessage);
                }
            #else
                if (message instanceof TranslatableText translatableText) {
                    String key = translatableText.getKey();
                    Object[] args = translatableText.getArgs();
                    String translatedMessage = I18n.getMinecraftTranslation(key, args);
                    ServerChanCore.onGameEvent(key, translatedMessage);
                }
            #endif
        });
        #else
        // ServerMessageEvents API not available in 1.18 and earlier
        // Chat and game message events are not supported on this version
        #endif
    }

    #if MC_VER >= MC_1_19
    private static int getPlayerPermissionLevel(
        #if MC_VER >= MC_1_19_2
            net.minecraft.server.level.ServerPlayer player
        #else
            net.minecraft.server.network.ServerPlayerEntity player
        #endif
    ) {
        #if MC_VER >= MC_1_21_6
            net.minecraft.server.MinecraftServer server = player.createCommandSourceStack().getServer();
        #else
            net.minecraft.server.MinecraftServer server = player.server;
        #endif
        return PermissionUtil.getPermissionLevel(server, player.getGameProfile());
    }
    #endif
}
