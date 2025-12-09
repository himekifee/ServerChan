package net.himeki.serverchan.neoforge;

import net.himeki.serverchan.MessageBroadcaster;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * NeoForge implementation of MessageBroadcaster
 */
public class NeoforgeMessageBroadcaster implements MessageBroadcaster {
    private MinecraftServer server;

    public NeoforgeMessageBroadcaster(MinecraftServer server) {
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
                    server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
                }
            });
        }
    }

    @Override
    public boolean isReady() {
        return server != null && server.getPlayerList() != null;
    }
}