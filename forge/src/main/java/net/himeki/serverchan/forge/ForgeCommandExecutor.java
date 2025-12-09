package net.himeki.serverchan.forge;

import net.himeki.serverchan.CommandExecutor;
import net.himeki.serverchan.forge.command.ServerChanCommandSource;
import net.minecraft.server.MinecraftServer;

/**
 * Forge implementation of CommandExecutor
 */
public class ForgeCommandExecutor implements CommandExecutor {
    private final ServerChanCommandSource commandSource;

    public ForgeCommandExecutor(MinecraftServer server) {
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