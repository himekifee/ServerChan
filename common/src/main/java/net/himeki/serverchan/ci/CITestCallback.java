package net.himeki.serverchan.ci;

/**
 * Platform-specific callback interface for CI testing.
 * Each platform (Fabric, Forge, NeoForge, Spigot) implements this interface
 * to provide platform-specific functionality needed during CI tests.
 */
public interface CITestCallback {

    /**
     * Log an info message
     */
    void logInfo(String message);

    /**
     * Log a warning message
     */
    void logWarning(String message);

    /**
     * Log an error/severe message
     */
    void logError(String message);

    /**
     * Check if the gamerule was changed successfully
     * @return true if announceAdvancements is false
     */
    boolean verifyGameruleChanged();

    /**
     * Write test result to a file
     * @param passed whether the test passed
     */
    void writeTestResult(boolean passed);

    /**
     * Shutdown the server
     */
    void shutdownServer();

    /**
     * Get the platform name (e.g., "Fabric", "Forge", "NeoForge", "Spigot")
     */
    String getPlatformName();
}
