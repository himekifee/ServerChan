package net.himeki.serverchan.neoforge.command;

import net.himeki.serverchan.ServerChanCore;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

import static net.minecraft.commands.Commands.literal;

public class ServerChanCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("serverchan")
                .then(literal("reload")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> {
                            String message = ServerChanCore.executeReload();
                            if (ServerChanCore.getMessageBroadcaster() != null) {
                                ServerChanCore.getMessageBroadcaster().broadcastMessage(message);
                            }
                            return 1;
                        })
                )
                .then(literal("reset")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> {
                            String message = ServerChanCore.executeReset();
                            if (ServerChanCore.getMessageBroadcaster() != null) {
                                ServerChanCore.getMessageBroadcaster().broadcastMessage(message);
                            }
                            return 1;
                        })
                )
                .then(literal("kill")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> {
                            String message = ServerChanCore.executeKill();
                            if (ServerChanCore.getMessageBroadcaster() != null) {
                                ServerChanCore.getMessageBroadcaster().broadcastMessage(message);
                            }
                            return 1;
                        })
                )
                .then(literal("enable")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> {
                            String message = ServerChanCore.executeEnable();
                            if (ServerChanCore.getMessageBroadcaster() != null) {
                                ServerChanCore.getMessageBroadcaster().broadcastMessage(message);
                            }
                            return 1;
                        })
                )
                .then(literal("disable")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> {
                            String message = ServerChanCore.executeDisable();
                            if (ServerChanCore.getMessageBroadcaster() != null) {
                                ServerChanCore.getMessageBroadcaster().broadcastMessage(message);
                            }
                            return 1;
                        })
                )
        );
    }
}
