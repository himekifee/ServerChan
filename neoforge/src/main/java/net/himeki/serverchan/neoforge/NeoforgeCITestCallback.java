package net.himeki.serverchan.neoforge;

import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.ci.CITestCallback;
import net.minecraft.server.MinecraftServer;

import net.himeki.serverchan.neoforge.command.ServerChanCommandSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoForge implementation of CITestCallback
 */
public class NeoforgeCITestCallback implements CITestCallback {
    private final MinecraftServer server;
    private static final Pattern GAMERULE_VALUE_PATTERN = Pattern.compile("\\b(true|false)\\b", Pattern.CASE_INSENSITIVE);

    public NeoforgeCITestCallback(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void logInfo(String message) {
        ServerChanCore.LOGGER.info(message);
    }

    @Override
    public void logWarning(String message) {
        ServerChanCore.LOGGER.warn(message);
    }

    @Override
    public void logError(String message) {
        ServerChanCore.LOGGER.error(message);
    }

    /**
     * Query the current value of keepInventory gamerule
     * @return the current value ("true" or "false"), or null if unable to query
     */
    private String queryKeepInventoryValue() {
        if (server == null) {
            return null;
        }
        try {
            ServerChanCommandSource commandSource = new ServerChanCommandSource(server);
            String response = commandSource.runCommand("gamerule keepInventory");
            logInfo("Gamerule query response: " + response);

            Matcher matcher = GAMERULE_VALUE_PATTERN.matcher(response);
            if (matcher.find()) {
                return matcher.group(1).toLowerCase();
            }
            return null;
        } catch (Exception e) {
            logWarning("Error querying gamerule: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean verifyGameruleChanged() {
        if (server == null) {
            logWarning("Server is null, cannot verify gamerule");
            return false;
        }

        try {
            // Query the actual gamerule state
            String currentValue = queryKeepInventoryValue();
            logInfo("Current keepInventory value: " + currentValue);

            if (currentValue == null) {
                logWarning("Could not query keepInventory gamerule value");
                return false;
            }

            if ("true".equals(currentValue)) {
                logInfo("SUCCESS: keepInventory gamerule is set to true");
                return true;
            } else {
                logWarning("FAILED: keepInventory gamerule is still " + currentValue + ", expected true");
                return false;
            }
        } catch (Exception e) {
            logError("Error verifying gamerule state: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void writeTestResult(boolean passed) {
        try {
            Path configDir = Path.of("config", "serverchan");
            Files.createDirectories(configDir);
            Path resultFile = configDir.resolve("ci-test-result.txt");
            Files.writeString(resultFile, passed ? "PASSED" : "FAILED");
            logInfo("Test result written to: " + resultFile.toAbsolutePath());
        } catch (IOException e) {
            logError("Failed to write test result: " + e.getMessage());
        }
    }

    @Override
    public void shutdownServer() {
        if (server != null) {
            new net.himeki.serverchan.neoforge.command.ServerChanCommandSource(server).runCommand("stop");
        }
    }

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }
}
