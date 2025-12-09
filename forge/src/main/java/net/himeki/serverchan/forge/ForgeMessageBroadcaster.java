package net.himeki.serverchan.forge;

import net.himeki.serverchan.MessageBroadcaster;
import net.minecraft.network.chat.Component;
#if MC_VER < MC_1_19
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.ChatType;
import java.util.UUID;
#endif
import net.minecraft.server.MinecraftServer;

/**
 * Forge implementation of MessageBroadcaster
 */
public class ForgeMessageBroadcaster implements MessageBroadcaster {
    private MinecraftServer server;

    public ForgeMessageBroadcaster(MinecraftServer server) {
        this.server = server;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void broadcastMessage(String message) {
        if (server != null) {
            // Ensure execution on the main server thread for thread safety
            server.execute(() -> {
                if (server.getPlayerList() != null) {
                    #if MC_VER >= MC_1_19
                    server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
                    #else
                    server.getPlayerList().broadcastMessage(new TextComponent(message), ChatType.SYSTEM, UUID.randomUUID());
                    #endif
                }
            });
        }
    }

    @Override
    public boolean isReady() {
        return server != null && server.getPlayerList() != null;
    }
}