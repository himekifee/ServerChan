package net.himeki.serverchan.neoforge;

import net.himeki.serverchan.CommandExecutor;
import net.himeki.serverchan.neoforge.command.ServerChanCommandSource;
import net.minecraft.server.MinecraftServer;

/**
 * NeoForge implementation of CommandExecutor
 */
public class NeoforgeCommandExecutor implements CommandExecutor {
    private final ServerChanCommandSource commandSource;

    public NeoforgeCommandExecutor(MinecraftServer server) {
        this.commandSource = new ServerChanCommandSource(server);
    }

    @Override
    public String executeCommand(String command, int permissionLevel) {
        return commandSource.runCommand(command, permissionLevel);
    }

    @Override
    public boolean isReady() {
        return commandSource != null;
    }
}