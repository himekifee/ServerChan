package net.himeki.serverchan;

/**
 * Interface for platform-specific command execution
 */
public interface CommandExecutor {
    /**
     * Execute a command on the server
     * @param command The command to execute (without leading slash)
     * @param permissionLevel The permission level to execute with (0-4)
     * @return The result of the command execution
     */
    String executeCommand(String command, int permissionLevel);

    /**
     * Check if the executor is ready to execute commands
     * @return true if commands can be executed, false otherwise
     */
    boolean isReady();
}