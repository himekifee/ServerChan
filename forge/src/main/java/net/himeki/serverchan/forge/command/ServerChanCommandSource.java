package net.himeki.serverchan.forge.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
#if MC_VER < MC_1_19
import net.minecraft.network.chat.TextComponent;
import java.util.UUID;
#endif
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import net.himeki.serverchan.ServerChanCore;

public class ServerChanCommandSource implements CommandSource {
    #if MC_VER >= MC_1_19
    private static final Component ServerChanText = Component.literal("ServerChan");
    #else
    private static final Component ServerChanText = new TextComponent("ServerChan");
    #endif
    private final StringBuffer buffer = new StringBuffer();
    private final MinecraftServer server;

    public ServerChanCommandSource(MinecraftServer server) {
        this.server = server;
    }

    public void prepareForCommand() {
        this.buffer.setLength(0);
    }

    public String getCommandResponse() {
        return this.buffer.toString();
    }

    public CommandSourceStack createCommandSourceStack() {
        return createCommandSourceStack(4);
    }

    public CommandSourceStack createCommandSourceStack(int permissionLevel) {
        ServerLevel overWorld = this.server.overworld();
        return new CommandSourceStack(this, Vec3.ZERO, Vec2.ZERO, overWorld, permissionLevel, "ServerChan", ServerChanText, this.server, null);
    }

    public String runCommand(String cmd) {
        return runCommand(cmd, 4);
    }

    public String runCommand(String cmd, int permissionLevel) {
        this.prepareForCommand();
        CompletableFuture<Void> future = new CompletableFuture<>();

        server.execute(() -> {
            try {
                #if MC_VER >= MC_1_19
                this.performPrefixedCommandCompat(cmd, permissionLevel);
                #else
                server.getCommands().performCommand(this.createCommandSourceStack(permissionLevel), cmd);
                #endif
            } catch (Exception e) {
                ServerChanCore.LOGGER.error("Failed to execute command: {}", cmd, e);
            } finally {
                future.complete(null);
            }
        });

        future.join(); // Block until command execution completes
        return this.getCommandResponse();
    }

    #if MC_VER >= MC_1_19
    /**
     * 1.20.x changed Commands#performPrefixedCommand to return void instead of int.
     * Use reflection so the call works across all 1.19+ command APIs.
     */
    private void performPrefixedCommandCompat(String cmd, int permissionLevel) throws Exception {
        CommandSourceStack sourceStack = this.createCommandSourceStack(permissionLevel);
        Object commands = this.server.getCommands();

        try {
            Method method = commands.getClass()
                    .getMethod("performPrefixedCommand", CommandSourceStack.class, String.class);
            method.invoke(commands, sourceStack, cmd);
        } catch (NoSuchMethodException e) {
            String rawCmd = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            this.server.getCommands().getDispatcher().execute(rawCmd, sourceStack);
        }
    }
    #endif

    #if MC_VER >= MC_1_19
    @Override
    public void sendSystemMessage(Component message) {
        this.buffer.append(message.getString());
    }
    #else
    @Override
    public void sendMessage(Component message, UUID senderUUID) {
        this.buffer.append(message.getString());
    }
    #endif

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }
}
