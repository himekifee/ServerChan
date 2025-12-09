package net.himeki.serverchan.fabric;

import net.himeki.serverchan.MessageBroadcaster;
import net.minecraft.network.chat.Component;
#if MC_VER < MC_1_19
import net.minecraft.network.chat.TextComponent;
#if MC_VER >= MC_1_16
import net.minecraft.network.chat.ChatType;
import java.util.UUID;
#endif
#endif
import net.minecraft.server.MinecraftServer;

/**
 * Fabric implementation of MessageBroadcaster
 */
public class FabricMessageBroadcaster implements MessageBroadcaster {
    private MinecraftServer server;

    public FabricMessageBroadcaster(MinecraftServer server) {
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
                    #elif MC_VER >= MC_1_16
                    server.getPlayerList().broadcastMessage(new TextComponent(message), ChatType.SYSTEM, UUID.randomUUID());
                    #else
                    server.getPlayerList().broadcastMessage(new TextComponent(message));
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