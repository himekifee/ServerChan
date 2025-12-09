package net.himeki.serverchan.fabric;

import net.himeki.serverchan.CommandExecutor;
import net.himeki.serverchan.fabric.command.ServerChanCommandSource;
import net.minecraft.server.MinecraftServer;

/**
 * Fabric implementation of CommandExecutor
 */
public class FabricCommandExecutor implements CommandExecutor {
    private final ServerChanCommandSource commandSource;

    public FabricCommandExecutor(MinecraftServer server) {
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