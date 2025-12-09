package net.himeki.serverchan;

/**
 * Interface for platform-specific message broadcasting
 */
public interface MessageBroadcaster {
    /**
     * Broadcast a message to all players on the server
     * @param message The message to broadcast
     */
    void broadcastMessage(String message);

    /**
     * Check if the broadcaster is ready to send messages
     * @return true if messages can be broadcast, false otherwise
     */
    boolean isReady();
}