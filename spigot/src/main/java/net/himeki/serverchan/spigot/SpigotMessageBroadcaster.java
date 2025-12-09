package net.himeki.serverchan.spigot;

import net.himeki.serverchan.MessageBroadcaster;
import org.bukkit.Bukkit;

/**
 * Spigot implementation of MessageBroadcaster with async safety
 */
public class SpigotMessageBroadcaster implements MessageBroadcaster {

    @Override
    public void broadcastMessage(String message) {
        // Ensure we're on the main thread for Bukkit operations
        if (Bukkit.isPrimaryThread()) {
            // Already on main thread, broadcast directly
            Bukkit.broadcastMessage(message);
        } else {
            // On async thread, schedule on main thread
            SpigotPlugin plugin = SpigotPlugin.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcastMessage(message);
                });
            }
        }
    }

    @Override
    public boolean isReady() {
        // Check if plugin is loaded and server is ready
        SpigotPlugin plugin = SpigotPlugin.getInstance();
        return plugin != null && plugin.isEnabled() && Bukkit.getServer() != null;
    }
}