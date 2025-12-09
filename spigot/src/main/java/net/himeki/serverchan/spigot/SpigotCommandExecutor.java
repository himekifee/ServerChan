package net.himeki.serverchan.spigot;

import net.himeki.serverchan.CommandExecutor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Spigot implementation of CommandExecutor using Bukkit API
 */
public class SpigotCommandExecutor implements CommandExecutor {
    private final JavaPlugin plugin;

    public SpigotCommandExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String executeCommand(String command, int permissionLevel) {
        if (!isReady()) {
            return "Server not ready";
        }

        // Execute command on main thread and capture output
        CompletableFuture<String> future = new CompletableFuture<>();

        // Create a new buffer for this specific execution to avoid concurrency issues
        // Each command execution gets its own isolated buffer
        final StringBuffer outputBuffer = new StringBuffer();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Create sender with isolated buffer for this execution
                CommandSender sender = new ServerChanCommandSender(outputBuffer, permissionLevel);

                // Execute the command
                boolean success = Bukkit.dispatchCommand(sender, command);

                // Get captured output
                String output = outputBuffer.toString().trim();

                if (!success && output.isEmpty()) {
                    output = "Command failed: " + command;
                }

                future.complete(output);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error executing command: " + command, e);
                future.complete("Error: " + e.getMessage());
            }
        });

        try {
            // Wait for command execution with timeout
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Command execution timed out: " + command, e);
            return "Command execution timed out";
        }
    }

    @Override
    public boolean isReady() {
        return plugin != null && plugin.isEnabled() && Bukkit.getServer() != null;
    }

    /**
     * Custom CommandSender implementation that provides better compatibility
     * by implementing ConsoleCommandSender interface
     */
    private static class ServerChanCommandSender implements ConsoleCommandSender {
        private final StringBuffer outputBuffer;
        private final int permissionLevel;
        private final ConsoleCommandSender consoleSender;

        public ServerChanCommandSender(StringBuffer buffer, int permissionLevel) {
            this.outputBuffer = buffer;
            this.permissionLevel = permissionLevel;
            this.consoleSender = Bukkit.getConsoleSender();
        }

        @Override
        public void sendMessage(String message) {
            if (message != null) {
                outputBuffer.append(message).append("\n");
            }
        }

        @Override
        public void sendMessage(String... messages) {
            for (String message : messages) {
                sendMessage(message);
            }
        }

        #if MC_VER >= MC_1_16
        @Override
        public void sendMessage(UUID sender, String message) {
            sendMessage(message);
        }

        @Override
        public void sendMessage(UUID sender, String... messages) {
            sendMessage(messages);
        }
        #endif

        @Override
        public org.bukkit.Server getServer() {
            return Bukkit.getServer();
        }

        @Override
        public String getName() {
            return "ServerChan";
        }

        @Override
        public boolean isPermissionSet(String name) {
            return true;
        }

        @Override
        public boolean isPermissionSet(Permission perm) {
            return true;
        }

        @Override
        public boolean hasPermission(String name) {
            // Grant permissions based on level (0-4, where 4 is op)
            return permissionLevel >= 2;
        }

        @Override
        public boolean hasPermission(Permission perm) {
            return permissionLevel >= 2;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return consoleSender.addAttachment(plugin, name, value);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            return consoleSender.addAttachment(plugin);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            return consoleSender.addAttachment(plugin, name, value, ticks);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return consoleSender.addAttachment(plugin, ticks);
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
            consoleSender.removeAttachment(attachment);
        }

        @Override
        public void recalculatePermissions() {
            consoleSender.recalculatePermissions();
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return consoleSender.getEffectivePermissions();
        }

        @Override
        public boolean isOp() {
            return permissionLevel >= 4;
        }

        @Override
        public void setOp(boolean value) {
            // No-op for safety
        }

        @Override
        public org.bukkit.command.CommandSender.Spigot spigot() {
            return consoleSender.spigot();
        }

        // ConsoleCommandSender specific methods

        @Override
        public boolean isConversing() {
            return consoleSender.isConversing();
        }

        @Override
        public void acceptConversationInput(String input) {
            consoleSender.acceptConversationInput(input);
        }

        @Override
        public boolean beginConversation(Conversation conversation) {
            return consoleSender.beginConversation(conversation);
        }

        @Override
        public void abandonConversation(Conversation conversation) {
            consoleSender.abandonConversation(conversation);
        }

        @Override
        public void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) {
            consoleSender.abandonConversation(conversation, details);
        }

        @Override
        public void sendRawMessage(String message) {
            sendMessage(message);
        }

        #if MC_VER >= MC_1_16
        @Override
        public void sendRawMessage(UUID sender, String message) {
            sendMessage(message);
        }
        #endif
    }
}