package net.himeki.serverchan.openai;

import com.google.gson.*;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.*;
import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.i18n.I18n;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class OpenAIHandler {
    private static OpenAIClient openAI;
    private static CircularQueue<MessageWrapper> messageContext;
    private static volatile boolean reloadInProgress = false;
    private static volatile long lastResetTime = 0;
    private static final long RESET_COOLDOWN_MS = 5000; // 5 second cooldown between resets
    private static volatile Future<CompletionResult> currentRequest = null;
    private static final AtomicReference<Exception> lastRequestException = new AtomicReference<>(null);

    // A single-thread executor to ensure requests are handled one at a time
    // We pin the context class loader to the mod's loader so Forge's event transformer
    // can resolve shaded dependencies (Kotlin/OpenAI) without spamming CNF warnings.
    private static volatile ExecutorService completionExecutor = createSingleThreadExecutor("ServerChan-OpenAI");
    // A cached pool for outer async wrappers (CompletableFuture.*) so they don't use the common pool with the wrong CCL.
    private static volatile ExecutorService asyncExecutor = createCachedExecutor("ServerChan-Async");

    private static ThreadFactory modThreadFactory(String prefix) {
        ThreadFactory backing = Executors.defaultThreadFactory();
        return r -> {
            Thread t = backing.newThread(r);
            t.setName(prefix);
            t.setDaemon(true);
            t.setContextClassLoader(OpenAIHandler.class.getClassLoader());
            return t;
        };
    }

    private static ExecutorService createSingleThreadExecutor(String prefix) {
        return Executors.newSingleThreadExecutor(modThreadFactory(prefix));
    }

    private static ExecutorService createCachedExecutor(String prefix) {
        return Executors.newCachedThreadPool(modThreadFactory(prefix));
    }

    public static ExecutorService getAsyncExecutor() {
        return asyncExecutor;
    }

    /**
     * Initialize or reset everything at startup.
     */
    public static void initializeOpenAI() {
        // Set reload flag to signal ongoing operations to stop
        reloadInProgress = true;

        // Cancel any existing request
        if (currentRequest != null && !currentRequest.isDone()) {
            currentRequest.cancel(true);
            currentRequest = null;
        }

    // Clear any pending tasks in the executor to prevent race conditions
    completionExecutor.shutdownNow();
    try {
        // Wait for the old executor to shut down cleanly
        if (!completionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ServerChanCore.LOGGER.warn("Old OpenAI executor did not terminate in time during reload.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ServerChanCore.LOGGER.warn("Interrupted while waiting for executor termination");
    }
    // Recreate the executor
    completionExecutor = createSingleThreadExecutor("ServerChan-OpenAI");

    // Shut down async executor and recreate to avoid thread leaks across reloads
    // Skip if we're running on the async executor to avoid self-interruption during CI/startup.
    boolean onAsyncExecutorThread = Thread.currentThread().getName().startsWith("ServerChan-Async");
    if (!onAsyncExecutorThread) {
        asyncExecutor.shutdownNow();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ServerChanCore.LOGGER.warn("Old async executor did not terminate in time during reload.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ServerChanCore.LOGGER.warn("Interrupted while waiting for async executor termination");
        }
        asyncExecutor = createCachedExecutor("ServerChan-Async");
    } else {
        ServerChanCore.LOGGER.debug("Skipping async executor reset because initializeOpenAI is running on async executor thread");
    }

        resetClient();
        resetMessageContext();

        // Initialize IntentionChecker if enabled
        if (ServerChanCore.CONFIG.useIntentionChecker) {
            IntentionChecker.initialize();
        }

        // Clear the reload flag
        reloadInProgress = false;
    }

    /**
     * Clean up resources (e.g., on shutdown).
     */
    public static void shutdown() {
        completionExecutor.shutdownNow();
        asyncExecutor.shutdownNow();
    }

    /**
     * Resets the OpenAI client with cooldown protection.
     */
    public static synchronized void resetClient() {
        // Check if we recently reset to avoid cascading resets
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastResetTime < RESET_COOLDOWN_MS) {
            ServerChanCore.LOGGER.info("Skipping client reset - cooldown active ({}ms since last reset)",
                currentTime - lastResetTime);
            return;
        }

        // Build the client with custom settings
        openAI = OpenAIOkHttpClient.builder()
                .apiKey(ServerChanCore.CONFIG.openaiApiKey)
                .baseUrl(ServerChanCore.CONFIG.openaiBaseUrl)
                .timeout(Duration.ofSeconds(180))
                .build();

        lastResetTime = currentTime;

        ServerChanCore.LOGGER.info("OpenAI client has been reset with base URL: {}",
            ServerChanCore.CONFIG.openaiBaseUrl);
    }

    public static String getEventResponse(String sender, String input, int permissionLevel) {
        recordRequestException(null);
        // Check intention first if enabled (events are marked as game events)
        if (ServerChanCore.CONFIG.useIntentionChecker && ServerChanCore.CONFIG.useFastPathIntentionChecker) {
            // Fast path with early response triggering
            final CompletableFuture<String> responseFuture = new CompletableFuture<>();
            final boolean[] responseStarted = {false};

            IntentionChecker.IntentionResponse intention = IntentionChecker.checkIntentionWithCallback(
                sender, input, true, messageContext.getMessages(),
                earlyResponse -> {
                    // Callback triggered when probability > threshold
                    if (earlyResponse.shouldRespond && !responseStarted[0]) {
                        responseStarted[0] = true;
                        ServerChanCore.LOGGER.info(I18n.get("intention.fastpath.trigger.response"));
                        // Start response generation asynchronously
                        CompletableFuture.runAsync(() -> {
                            try {
                                String result = getResponse(sender, input, permissionLevel);
                                responseFuture.complete(result);
                            } catch (Exception e) {
                                responseFuture.completeExceptionally(e);
                            }
                        }, asyncExecutor);
                    }
                });

            if (intention.isError) {
                ServerChanCore.LOGGER.warn("IntentionChecker failed for event \"{}\" - falling back to model response (reason: {})",
                    input, intention.reason);
            } else if (!intention.shouldRespond) {
                ServerChanCore.LOGGER.info("[NO RESPONSE] Event: \"{}\" | Reason: {} (probability: {}%)",
                    input, intention.reason, (int)(intention.probability * 100));

                // Add the event to context even when not responding
                if (!reloadInProgress) {
                    messageContext.add(MessageWrapper.user(input));
                    messageContext.add(MessageWrapper.assistant("<|no_message_this_turn|>"));
                }

                return "<|no_message_this_turn|>";
            }

            // If early response was started, wait for it
            if (responseStarted[0]) {
                try {
                    return responseFuture.get(200, TimeUnit.SECONDS);
                } catch (Exception e) {
                    ServerChanCore.LOGGER.error("Error waiting for early response", e);
                    return I18n.get("handler.error.generic");
                }
            }

            // Fallback if no early response was triggered (shouldn't happen with fast path)
            return getResponse(sender, input, permissionLevel);

        } else if (ServerChanCore.CONFIG.useIntentionChecker) {
            // Normal path without fast path
            IntentionChecker.IntentionResponse intention = IntentionChecker.checkIntentionWithCallback(
                sender, input, true, messageContext.getMessages(), null);
            if (intention.isError) {
                ServerChanCore.LOGGER.warn("IntentionChecker failed for event \"{}\" - falling back to model response (reason: {})",
                    input, intention.reason);
            } else if (!intention.shouldRespond) {
                ServerChanCore.LOGGER.info("[NO RESPONSE] Event: \"{}\" | Reason: {} (probability: {}%)",
                    input, intention.reason, (int)(intention.probability * 100));

                // Add the event to context even when not responding
                if (!reloadInProgress) {
                    messageContext.add(MessageWrapper.user(input));
                    messageContext.add(MessageWrapper.assistant("<|no_message_this_turn|>"));
                }

                return "<|no_message_this_turn|>";
            }
        }
        return getResponse(sender, input, permissionLevel);
    }

    public static String getChatResponse(String sender, String input, int permissionLevel) {
        recordRequestException(null);
        // Check intention first if enabled (regular chat messages)
        if (ServerChanCore.CONFIG.useIntentionChecker && ServerChanCore.CONFIG.useFastPathIntentionChecker) {
            // Fast path with early response triggering
            final CompletableFuture<String> responseFuture = new CompletableFuture<>();
            final boolean[] responseStarted = {false};

            IntentionChecker.IntentionResponse intention = IntentionChecker.checkIntentionWithCallback(
                sender, input, false, messageContext.getMessages(),
                earlyResponse -> {
                    // Callback triggered when probability > threshold
                    if (earlyResponse.shouldRespond && !responseStarted[0]) {
                        responseStarted[0] = true;
                        ServerChanCore.LOGGER.info("[FAST PATH] Starting early response generation!");
                        // Start response generation asynchronously
                        CompletableFuture.runAsync(() -> {
                            try {
                                String result = getResponse(sender, input, permissionLevel);
                                responseFuture.complete(result);
                            } catch (Exception e) {
                                responseFuture.completeExceptionally(e);
                            }
                        }, asyncExecutor);
                    }
                });

            if (intention.isError) {
                ServerChanCore.LOGGER.warn("IntentionChecker failed for message \"{}\" - falling back to model response (reason: {})",
                    input, intention.reason);
            } else if (!intention.shouldRespond) {
                ServerChanCore.LOGGER.info("[NO RESPONSE] Message: \"{}\" | Reason: {} (probability: {}%)",
                    input, intention.reason, (int)(intention.probability * 100));

                // Add the message to context even when not responding
                if (!reloadInProgress) {
                    messageContext.add(MessageWrapper.user(input));
                    messageContext.add(MessageWrapper.assistant("<|no_message_this_turn|>"));
                }

                return "<|no_message_this_turn|>";
            }

            // If early response was started, wait for it
            if (responseStarted[0]) {
                try {
                    return responseFuture.get(200, TimeUnit.SECONDS);
                } catch (Exception e) {
                    ServerChanCore.LOGGER.error("Error waiting for early response", e);
                    return I18n.get("handler.error.generic");
                }
            }

            // Fallback if no early response was triggered (shouldn't happen with fast path)
            return getResponse(sender, input, permissionLevel);

        } else if (ServerChanCore.CONFIG.useIntentionChecker) {
            // Normal path without fast path
            IntentionChecker.IntentionResponse intention = IntentionChecker.checkIntentionWithCallback(
                sender, input, false, messageContext.getMessages(), null);
            if (intention.isError) {
                ServerChanCore.LOGGER.warn("IntentionChecker failed for message \"{}\" - falling back to model response (reason: {})",
                    input, intention.reason);
            } else if (!intention.shouldRespond) {
                ServerChanCore.LOGGER.info("[NO RESPONSE] Message: \"{}\" | Reason: {} (probability: {}%)",
                    input, intention.reason, (int)(intention.probability * 100));

                // Add the message to context even when not responding
                if (!reloadInProgress) {
                    messageContext.add(MessageWrapper.user(input));
                    messageContext.add(MessageWrapper.assistant("<|no_message_this_turn|>"));
                }

                return "<|no_message_this_turn|>";
            }
        }
        return getResponse(sender, input, permissionLevel);
    }

    /**
     * Main method to get a response from OpenAI.
     * Will queue requests in a single thread, each with a 90s timeout.
     */
    private static String getResponse(String sender, String input,
                                      int permissionLevel) {
        // Always use the response generation system message
        // (IntentionChecker handles the decision logic separately when enabled)
        String systemMessage = ServerChanCore.CONFIG.responseGenerationSystemMessage;

        // Append dev easter egg prompt if not disabled
        if (!ServerChanCore.CONFIG.disableDevEasterEgg) {
            systemMessage += "\n\n" + I18n.get("prompt.dev.easteregg");
        }

        // Cancel any existing request BEFORE adding new message to context
        if (currentRequest != null && !currentRequest.isDone()) {
            ServerChanCore.LOGGER.info("Cancelling previous OpenAI request to process new one");
            currentRequest.cancel(true);

            // Add a no_message token for the cancelled request to maintain context consistency
            // This prevents the bot from trying to respond to the previous message
            if (!reloadInProgress) {
                messageContext.add(MessageWrapper.assistant("<|no_message_this_turn|>"));
                ServerChanCore.LOGGER.info("Added no_message token for cancelled request");
            }
        }

        // Create a conversation list: system message + user input + all stored context
        ChatCompletionCreateParams.Builder paramsBuilder = ChatCompletionCreateParams.builder()
                .model(ChatModel.of(ServerChanCore.CONFIG.model))
                .temperature(ServerChanCore.CONFIG.temperature)
                .addSystemMessage(systemMessage);

        // Only add user message to context if not reloading
        if (!reloadInProgress) {
            messageContext.add(MessageWrapper.user(input));
        }

        // Add all context messages
        for (MessageWrapper msg : messageContext.getMessages()) {
            msg.addToBuilder(paramsBuilder);
        }

        // Define function tools
        addExecuteMinecraftCommandsTool(paramsBuilder);

        // Create a callable that does the heavy lifting
        Callable<CompletionResult> task = () -> {
            try {
                String response = processResponse(sender, paramsBuilder, permissionLevel);
                return new CompletionResult(response, null);
            } catch (Exception e) {
                return new CompletionResult(null, e);
            }
        };

        // Submit the task to the single-thread executor and store the future
        Future<CompletionResult> future = completionExecutor.submit(task);
        currentRequest = future;

        try {
            // Wait up to 200 seconds for the response (slightly more than HTTP timeout)
            CompletionResult result = future.get(200, TimeUnit.SECONDS);

            // If an exception occurred in the worker
            if (result.error != null) {
                recordRequestException(result.error);
                ServerChanCore.LOGGER.error("Error in completion task", result.error);
                return I18n.get("handler.error.completion");
            }

            // Possibly the model returned no content
            if (result.response == null) {
                return I18n.get("handler.response.empty");
            }

            return result.response;

        } catch (TimeoutException e) {
            // Timed out -> cancel this request
            future.cancel(true);
            recordRequestException(e);

            // Check if we can reset or if we're in cooldown
            long timeSinceLastReset = System.currentTimeMillis() - lastResetTime;
            if (timeSinceLastReset >= RESET_COOLDOWN_MS) {
                resetClient();
                return I18n.get("handler.error.timeout.reset");
            } else {
                ServerChanCore.LOGGER.warn("Request timed out but reset cooldown active ({}ms remaining)",
                    RESET_COOLDOWN_MS - timeSinceLastReset);
                return I18n.get("handler.error.timeout.recovering");
            }
        } catch (InterruptedException e) {
            // Current thread was interrupted while waiting
            ServerChanCore.LOGGER.error("Interrupted while waiting for completion", e);
            recordRequestException(e);
            return I18n.get("handler.error.interrupted");
        } catch (ExecutionException e) {
            // Something else went wrong
            ServerChanCore.LOGGER.error("Execution error in OpenAI task", e);
            recordRequestException(e);
            return I18n.get("handler.error.execution");
        } finally {
            // Clear the reference if this was the current request
            if (currentRequest == future) {
                currentRequest = null;
            }
        }
    }

    /**
     * The main logic that calls the OpenAI ChatCompletion (function calling etc.).
     * We directly modify the same 'messages' list, so the entire conversation
     * remains in one place.
     */
    private static String processResponse(String sender, ChatCompletionCreateParams.Builder paramsBuilder,
                                          int permissionLevel) {
        boolean functionCallExists = true;
        String finalResponse = null;
        boolean resetContextAfterThisRound = false;

        // 1) Call the model repeatedly until no more function calls.
        while (functionCallExists && !Thread.currentThread().isInterrupted()) {
            ServerChanCore.LOGGER.info(I18n.format("openai.request.starting", ServerChanCore.CONFIG.model));
            ChatCompletion chatCompletion = openAI.chat().completions().create(paramsBuilder.build());
            ChatCompletion.Choice choice = chatCompletion.choices().get(0);
            ChatCompletionMessage message = choice.message();

            // If the assistant made any function calls
            List<ChatCompletionMessageToolCall> toolCalls = message.toolCalls().orElse(Collections.emptyList());
            if (!toolCalls.isEmpty()) {
                // 1.1) Add the assistant's function-call message
                String content = message.content().orElse("Function calls made.");

                // Build the assistant message with tool calls
                MessageWrapper assistantMsg = MessageWrapper.assistant(content, toolCalls);

                assistantMsg.addToBuilder(paramsBuilder);
                // Only add to context if not reloading
                if (!reloadInProgress) {
                    messageContext.add(assistantMsg);
                }

                // 1.2) For each function call, execute it and store the result
                for (int i = 0; i < toolCalls.size(); i++) {
                    ChatCompletionMessageToolCall toolCall = toolCalls.get(i);

                    String toolCallId = "call_" + i; // Default fallback
                    String functionName = "ExecuteMinecraftCommands"; // Default to our only function
                    String functionArgsJson = "{}"; // Default empty JSON

                    // Extract function details from the API
                    if (toolCall.function().isPresent()) {
                        try {
                            ChatCompletionMessageFunctionToolCall functionToolCall = toolCall.function().get();
                            toolCallId = functionToolCall.id();

                            // Get the inner function details
                            ChatCompletionMessageFunctionToolCall.Function innerFunction = functionToolCall.function();
                            functionName = innerFunction.name();
                            functionArgsJson = innerFunction.arguments();

                            ServerChanCore.LOGGER.info("Executing function: {} with args: {}", functionName, functionArgsJson);
                        } catch (Exception e) {
                            ServerChanCore.LOGGER.error("Error extracting function details", e);
                        }
                    }

                    String result;
                    if ("ExecuteMinecraftCommands".equals(functionName)) {
                        List<String> commands = parseCommandsFromJson(functionArgsJson);
                        // Check permission based on config
                        boolean canExecute = !ServerChanCore.CONFIG.inheritCmdSourcePermission || permissionLevel >= 4;
                        result = canExecute
                                ? executeCommands(sender, commands, permissionLevel)
                                : I18n.get("handler.command.permission.denied");

                        // IMPORTANT: if the user commands contain "serverchan reset", set a flag
                        if (commands.stream().anyMatch(cmd -> {
                            String cleanCmd = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                            return cleanCmd.equalsIgnoreCase("serverchan reset") || cleanCmd.equalsIgnoreCase("serverchan clear");
                        })) {
                            resetContextAfterThisRound = true;
                        }
                    } else {
                        result = I18n.format("handler.function.unknown", functionName);
                    }

                    // Add the tool's result as a new message with the function name
                    MessageWrapper toolMsg = MessageWrapper.tool(result, toolCallId, functionName);
                    toolMsg.addToBuilder(paramsBuilder);
                    // Only add to context if not reloading
                    if (!reloadInProgress) {
                        messageContext.add(toolMsg);
                    }
                }
            } else {
                // No function calls => final text from this model
                functionCallExists = false;
                finalResponse = message.content().orElse("");
            }
        }

        // 2) Once we have the final text, add that to both 'messages' and 'messageContext'
        //    INCLUDING the no_message token so the model knows it already processed this message
        if (isStandaloneNoMessageResponse(finalResponse)) {
            finalResponse = "<|no_message_this_turn|>";
        }

        if (finalResponse != null && !finalResponse.isEmpty()) {
            // Add to persistent context so model knows it processed this
            if (!reloadInProgress) {
                messageContext.add(MessageWrapper.assistant(finalResponse));
            }

            // Optionally: save to file for debugging (only if enabled in config)
            if (ServerChanCore.CONFIG.enableDebugFileLogging) {
                try {
                    saveMessagesToFile(paramsBuilder);
                } catch (Exception e) {
                    ServerChanCore.LOGGER.error("Error saving messages", e);
                }
            }
        }

        if (resetContextAfterThisRound) {
            resetMessageContext();
        }

        return finalResponse;
    }


    /**
     * Save messages to a local JSON file for debugging.
     */
    private static void saveMessagesToFile(ChatCompletionCreateParams.Builder paramsBuilder) {
        String DEBUG_FILE_NAME = "openai_debug_generation.json";
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject debugInfo = new JsonObject();
        debugInfo.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        debugInfo.addProperty("phase", "response_generation");
        debugInfo.addProperty("model", ServerChanCore.CONFIG.model);
        debugInfo.addProperty("temperature", ServerChanCore.CONFIG.temperature);
        debugInfo.addProperty("context_size", ServerChanCore.CONFIG.contextSize);

        // Include the message context so the debug file actually reflects the conversation
        JsonArray messages = new JsonArray();
        for (MessageWrapper wrapper : messageContext.getMessages()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("role", wrapper.getRole().name().toLowerCase(Locale.ROOT));
            msg.addProperty("content", wrapper.getContent());
            messages.add(msg);
        }
        debugInfo.add("messages", messages);

        try (FileWriter writer = new FileWriter(DEBUG_FILE_NAME, false)) {
            writer.write(gson.toJson(debugInfo));
        } catch (IOException e) {
            ServerChanCore.LOGGER.error("Failed to save debug messages to file", e);
        }
    }

    /**
     * A small class to hold either a successful response or an error.
     */
    private static class CompletionResult {
        final String response;
        final Exception error;

        CompletionResult(String response, Exception error) {
            this.response = response;
            this.error = error;
        }
    }

    /**
     * Get the last exception encountered while processing a completion request.
     * Returns null if the last request succeeded.
     */
    public static Exception getLastRequestException() {
        return lastRequestException.get();
    }

    /**
     * Record the last exception encountered by any OpenAI request handler.
     */
    public static void recordRequestException(Exception exception) {
        lastRequestException.set(exception);
    }

    /**
     * Parse a JSON string in a way compatible with all Gson versions.
     * JsonParser.parseString() was added in Gson 2.8.6, but older Minecraft versions use older Gson.
     */
    @SuppressWarnings("deprecation")
    private static JsonElement parseJsonString(String json) {
        return new JsonParser().parse(json);
    }

    /**
     * Parse an array of commands from functionArgsJson.
     */
    private static List<String> parseCommandsFromJson(String functionArgsJson) {
        try {
            JsonElement element = parseJsonString(functionArgsJson);
            JsonObject rootObject = element.getAsJsonObject();
            JsonArray commandsArray = rootObject.getAsJsonArray("commands");
            List<String> commands = new ArrayList<>();
            for (JsonElement commandElement : commandsArray) {
                commands.add(commandElement.getAsString());
            }
            return commands;
        } catch (Exception e) {
            ServerChanCore.LOGGER.error("Failed to parse commands from JSON", e);
            return Collections.emptyList();
        }
    }

    /**
     * Execute a list of Minecraft commands as console.
     */
    private static String executeCommands(String sender, List<String> commands, int permissionLevel) {
        StringBuilder resultBuilder = new StringBuilder();
        for (String command : commands) {
            // Strip leading slash if present
            String cleanCommand = command.startsWith("/") ? command.substring(1) : command;

            try {
                // Use configured permission level or bypass (level 4) based on config
                int effectivePermissionLevel = ServerChanCore.CONFIG.inheritCmdSourcePermission
                    ? permissionLevel
                    : 4;
                String result = ServerChanCore.getCommandExecutor() != null ?
                        ServerChanCore.getCommandExecutor().executeCommand(cleanCommand, effectivePermissionLevel) :
                        "Command executor not initialized";
                
                // Track command for testing
                ServerChanCore.executedCommandsForTesting.add(cleanCommand);

                resultBuilder
                        .append(I18n.get("handler.command.log.command"))
                        .append(cleanCommand)
                        .append("\n")
                        .append(I18n.get("handler.command.log.result"))
                        .append(result)
                        .append("\n\n");

                // Broadcast the command execution message
                if (ServerChanCore.getMessageBroadcaster() != null) {
                    ServerChanCore.getMessageBroadcaster().broadcastMessage(
                            I18n.format("handler.command.broadcast", sender, cleanCommand)
                    );
                }
            } catch (Exception e) {
                resultBuilder
                        .append(I18n.format("handler.command.error", cleanCommand, e.getMessage()))
                        .append("\n\n");
            }
        }
        return resultBuilder.toString();
    }

    /**
     * Check if a response indicates the model chose not to reply this turn.
     */
    public static boolean isNoMessageResponse(String response) {
        if (response == null) {
            return false;
        }
        String normalized = response.toLowerCase(Locale.ROOT);
        return normalized.contains("<|no_message_this_turn|>") ||
               normalized.contains("<|no_msg_this_turn|>");
    }

    /**
     * Check for a standalone no-message token (ignoring whitespace/casing).
     */
    private static boolean isStandaloneNoMessageResponse(String response) {
        if (response == null) {
            return false;
        }
        String trimmed = response.trim();
        return "<|no_message_this_turn|>".equalsIgnoreCase(trimmed) ||
               "<|no_msg_this_turn|>".equalsIgnoreCase(trimmed);
    }


    /**
     * Reset the circular queue storing the chat context.
     */
    public static void resetMessageContext() {
        messageContext = new CircularQueue<>(ServerChanCore.CONFIG.contextSize);
    }

    /**
     * Adds the 'execute_minecraft_commands' function tool to the builder.
     */
    private static void addExecuteMinecraftCommandsTool(ChatCompletionCreateParams.Builder paramsBuilder) {
        // Use the class-based approach for function definition
        try {
            paramsBuilder.addTool(ExecuteMinecraftCommands.class);
        } catch (Throwable t) {
            ServerChanCore.LOGGER.error("Failed to register ExecuteMinecraftCommands tool for structured outputs", t);
        }
    }



    /**
     * A simple circular queue for message context
     */
    public static class CircularQueue<T> {
        private final int maxSize;
        private final LinkedList<T> queue;

        public CircularQueue(int size) {
            this.maxSize = size;
            this.queue = new LinkedList<>();
        }

        public synchronized void add(T message) {
            // If we're at capacity, remove the oldest
            if (queue.size() == maxSize) {
                queue.removeFirst();
            }
            queue.addLast(message);
        }

        public synchronized List<T> getMessages() {
            return new LinkedList<>(queue);
        }

        public synchronized void clear() {
            queue.clear();
        }
    }
}
