package net.himeki.serverchan.ci;

import com.openai.errors.RateLimitException;
import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.config.ServerChanConfigBase;
import net.himeki.serverchan.openai.OpenAIHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CI Test Runner for ServerChan
 * Platform-agnostic implementation that runs integration tests with AI API.
 * Detects CI environment and verifies AI can execute game commands.
 */
public class CITestRunner {
    public static final String GITHUB_ACTIONS_ENV_VAR = "GITHUB_ACTIONS";
    public static final String API_KEY_ENV_VAR = "SERVERCHAN_CI_API_KEY";
    public static final String CI_CONFIG_FILE = "serverchan-ci-test.toml";
    private static final int RATE_LIMIT_MAX_ATTEMPTS = 5;
    private static final long RATE_LIMIT_RETRY_DELAY_MS = 30_000L;

    private final CITestCallback callback;
    private final AtomicBoolean testPassed = new AtomicBoolean(false);
    private final AtomicReference<String> executedCommand = new AtomicReference<>(null);

    public CITestRunner(CITestCallback callback) {
        this.callback = callback;
    }

    /**
     * Check if running in CI test mode (API key available)
     */
    public static boolean isCITestMode() {
        String apiKey = System.getenv(API_KEY_ENV_VAR);
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Get API key from environment variable
     */
    public static String getAPIKeyFromEnv() {
        return System.getenv(API_KEY_ENV_VAR);
    }

    /**
     * Run the CI test
     * @return CompletableFuture that completes with true if test passed
     */
    public CompletableFuture<Boolean> runTest() {
        return CompletableFuture.supplyAsync(() -> {
            callback.logInfo("========================================");
            callback.logInfo("Starting ServerChan CI Test on " + callback.getPlatformName());
            callback.logInfo("========================================");

            try {
                // Step 1: Create and load CI test config
                ServerChanConfigBase config = createCITestConfig();
                if (config == null) {
                    callback.logError("Failed to create CI test config");
                    return false;
                }

                // Step 2: Initialize ServerChan with CI config
                ServerChanCore.initialize(config);

                // Step 3: Set up command tracking
                setupCommandTracking();

                // Wait for initialization to complete
                Thread.sleep(2000);

                // Step 4: Test intention checker by sending a message that should NOT trigger a response
                callback.logInfo("Testing intention checker - sending message that should be filtered...");
                String filterTestMessage = "hello everyone, just testing things out";

                // Call AI - this should return no_message_this_turn because intention checker should filter it
                String filterResponse = getChatResponseWithRateLimitRetry("CITestPlayer",
                    "[CI Test] <CITestPlayer>: " + filterTestMessage, 4, "intention check");

                callback.logInfo("Intention filter test response: " + filterResponse);

                boolean intentionFilterWorked = OpenAIHandler.isNoMessageResponse(filterResponse);

                if (intentionFilterWorked) {
                    callback.logInfo("Intention checker correctly filtered the message (no response)");
                } else {
                    callback.logWarning("Intention checker did not filter the message - got response: " + filterResponse);
                }

                // Small delay between tests
                Thread.sleep(1000);

                // Step 5: Send actual test message to AI asking to change a gamerule
                callback.logInfo("Sending command test message to AI...");
                String testMessage = ": Please execute the command to set gamerule keepInventory to true";

                // Call AI directly (synchronously for test) - using : prefix to ensure it triggers response
                String response = getChatResponseWithRateLimitRetry("CITestPlayer",
                    "[CI Test] <CITestPlayer>: " + testMessage, 4, "command execution");

                callback.logInfo("AI Response: " + response);

                // Step 6: Verify the command was executed
                boolean commandExecuted = verifyCommandExecution();

                // Step 7: Report results
                reportResults(response, commandExecuted, intentionFilterWorked);

                // Test passes only if both intention filtering and command execution work
                boolean passed = intentionFilterWorked && commandExecuted;
                testPassed.set(passed);
                return passed;

            } catch (Exception e) {
                callback.logError("CI Test failed with exception: " + e.getMessage());
                ServerChanCore.LOGGER.error("CI Test failed", e);
                return false;
            }
        }, OpenAIHandler.getAsyncExecutor());
    }

    /**
     * Create CI test configuration with API key from environment
     */
    private ServerChanConfigBase createCITestConfig() {
        ServerChanConfigBase config = new ServerChanConfigBase();

        // Cerebras API endpoint
        config.openaiBaseUrl = "https://api.cerebras.ai/v1";

        // Use a fast model for testing
        config.model = "gpt-oss-120b";
        config.intentionCheckerModel = "gpt-oss-120b";

        // Enable intention checker to test filtering functionality
        config.useIntentionChecker = true;
        config.useFastPathIntentionChecker = false; // Use normal path for simpler CI testing
        config.responseProbabilityThreshold = 0.5f; // Default threshold

        // Simple system prompt for CI testing
        config.responseGenerationSystemMessage =
            "You are a Minecraft server assistant in CI test mode.\n" +
            "When asked to change a game setting, you MUST use the ExecuteMinecraftCommands function to execute the appropriate command.\n" +
            "For example, if asked to set gamerule keepInventory to true, execute: /gamerule keepInventory true\n" +
            "Always respond briefly and execute commands when requested.\n" +
            "Do not ask for confirmation - just execute the command directly.";

        // Small context size for faster testing
        config.contextSize = 5;

        // Disable command permission inheritance for full access in test
        config.inheritCmdSourcePermission = false;

        // Bot settings
        config.botColor = "c"; // Red for visibility in logs

        // Disable game events for cleaner test output
        config.enableGameEvents = false;

        // Override API key from environment variable
        String envApiKey = getAPIKeyFromEnv();
        if (envApiKey != null && !envApiKey.isEmpty()) {
            config.openaiApiKey = envApiKey;
            callback.logInfo("Using API key from environment variable");
        } else {
            callback.logError("No API key configured! Set " + API_KEY_ENV_VAR + " environment variable");
            return null;
        }

        return config;
    }

    /**
     * Call OpenAI with retries when rate limited (429).
     */
    private String getChatResponseWithRateLimitRetry(String sender, String input, int permissionLevel, String requestLabel) {
        String response = null;
        for (int attempt = 1; attempt <= RATE_LIMIT_MAX_ATTEMPTS; attempt++) {
            response = OpenAIHandler.getChatResponse(sender, input, permissionLevel);
            Exception lastError = OpenAIHandler.getLastRequestException();

            if (!isRateLimitException(lastError)) {
                return response;
            }

            if (attempt == RATE_LIMIT_MAX_ATTEMPTS) {
                callback.logError("Encountered 429 during " + requestLabel + " after " + attempt + " attempts - giving up");
                break;
            }

            callback.logWarning("Received 429 during " + requestLabel + " (attempt " + attempt + "/" + RATE_LIMIT_MAX_ATTEMPTS + "), retrying in 30 seconds...");
            if (!sleepBeforeRetry()) {
                break;
            }
        }
        return response;
    }

    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(RATE_LIMIT_RETRY_DELAY_MS);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            callback.logError("Rate limit retry interrupted");
            return false;
        }
    }

    private boolean isRateLimitException(Exception exception) {
        Exception current = exception;
        while (current != null) {
            if (current instanceof RateLimitException) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause instanceof Exception ? (Exception) cause : null;
        }
        return false;
    }

    /**
     * Set up command tracking via the command executor
     */
    private void setupCommandTracking() {
        // Wrap the existing command executor to track commands
        final net.himeki.serverchan.CommandExecutor originalExecutor = ServerChanCore.getCommandExecutor();
        if (originalExecutor != null) {
            ServerChanCore.setCommandExecutor(new net.himeki.serverchan.CommandExecutor() {
                @Override
                public String executeCommand(String command, int permissionLevel) {
                    callback.logInfo("[CI Test] Command executed: " + command);
                    executedCommand.set(command);
                    return originalExecutor.executeCommand(command, permissionLevel);
                }

                @Override
                public boolean isReady() {
                    return originalExecutor.isReady();
                }
            });
        }

        // Wrap message broadcaster to log messages
        final net.himeki.serverchan.MessageBroadcaster originalBroadcaster = ServerChanCore.getMessageBroadcaster();
        if (originalBroadcaster != null) {
            ServerChanCore.setMessageBroadcaster(new net.himeki.serverchan.MessageBroadcaster() {
                @Override
                public void broadcastMessage(String message) {
                    callback.logInfo("[CI Test] Broadcast: " + message);
                    originalBroadcaster.broadcastMessage(message);
                }

                @Override
                public boolean isReady() {
                    return originalBroadcaster.isReady();
                }
            });
        }
    }

    /**
     * Verify that the expected command was executed
     */
    private boolean verifyCommandExecution() {
        String executed = executedCommand.get();

        if (executed == null) {
            callback.logWarning("No command was executed by AI");
            return false;
        }

        // Check if gamerule command was executed
        String lowerCommand = executed.toLowerCase();
        boolean isGameruleCommand = lowerCommand.contains("gamerule") &&
                                    lowerCommand.contains("keepinventory");

        if (isGameruleCommand) {
            callback.logInfo("Correct gamerule command was executed: " + executed);

            // Verify the actual game state changed (platform-specific)
            return callback.verifyGameruleChanged();
        } else {
            callback.logWarning("Expected gamerule command but got: " + executed);
            return false;
        }
    }

    /**
     * Report test results
     */
    private void reportResults(String aiResponse, boolean commandExecuted, boolean intentionFilterWorked) {
        boolean overallPassed = intentionFilterWorked && commandExecuted;

        callback.logInfo("========================================");
        callback.logInfo("CI Test Results - " + callback.getPlatformName());
        callback.logInfo("========================================");
        callback.logInfo("Test 1 - Intention Filter: " + (intentionFilterWorked ? "PASSED" : "FAILED"));
        callback.logInfo("  (Message without bot mention should be filtered)");
        callback.logInfo("Test 2 - Command Execution: " + (commandExecuted ? "PASSED" : "FAILED"));
        callback.logInfo("  AI Response Received: " + (aiResponse != null && !aiResponse.isEmpty() ? "YES" : "NO"));
        callback.logInfo("  Executed Command: " + executedCommand.get());
        callback.logInfo("----------------------------------------");
        callback.logInfo("Overall Result: " + (overallPassed ? "PASSED" : "FAILED"));
        if (overallPassed) {
            callback.logInfo("ci pass");
        }
        callback.logInfo("========================================");

        if (!intentionFilterWorked) {
            callback.logError("CI TEST FAILED - Intention checker did not filter unrelated message");
        }
        if (!commandExecuted) {
            callback.logError("CI TEST FAILED - AI did not execute the expected command");
        }
    }

    /**
     * Check if test passed
     */
    public boolean hasTestPassed() {
        return testPassed.get();
    }
}
