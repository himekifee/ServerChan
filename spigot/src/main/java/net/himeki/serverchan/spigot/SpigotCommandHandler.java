package net.himeki.serverchan.spigot;

import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.i18n.I18n;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Spigot command handler for ServerChan
 */
public class SpigotCommandHandler implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("serverchan")) {
            return false;
        }

        // Check if sender has permission (respects permission plugins like LuckPerms)
        if (!sender.hasPermission("serverchan.admin")) {
            sender.sendMessage(ChatColor.RED + I18n.get("command.no_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /serverchan <reload|reset|kill|enable|disable>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                SpigotPlugin.reloadServerChanConfigBase();
                String reloadMessage = ServerChanCore.executeReload();
                sender.sendMessage(ChatColor.GREEN + reloadMessage);
                break;

            case "reset":
                String resetMessage = ServerChanCore.executeReset();
                sender.sendMessage(ChatColor.GREEN + resetMessage);
                break;

            case "kill":
                String killMessage = ServerChanCore.executeKill();
                sender.sendMessage(ChatColor.GREEN + killMessage);
                break;

            case "enable":
                String enableMessage = ServerChanCore.executeEnable();
                sender.sendMessage(ChatColor.GREEN + enableMessage);
                break;

            case "disable":
                String disableMessage = ServerChanCore.executeDisable();
                sender.sendMessage(ChatColor.YELLOW + disableMessage);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sender.sendMessage(ChatColor.YELLOW + "Usage: /serverchan <reload|reset|kill|enable|disable>");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("serverchan") || !sender.hasPermission("serverchan.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("reload", "reset", "kill", "enable", "disable");
            List<String> completions = new ArrayList<>();

            String partialCommand = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(partialCommand)) {
                    completions.add(subCommand);
                }
            }

            return completions;
        }

        return new ArrayList<>();
    }
}
