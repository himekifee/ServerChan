package net.himeki.serverchan.openai;

import com.google.gson.*;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.RequestOptions;
import com.openai.core.http.StreamResponse;
import com.openai.errors.RateLimitException;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.i18n.I18n;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dedicated intention checker that uses a smaller model to determine
 * if the bot should respond to a message.
 */
public class IntentionChecker {
    private static OpenAIClient intentionOpenAI;
    private static final int API_TIMEOUT_SECONDS = 60; // Per-request timeout

    /**
     * Response structure for intention checking
     */
    public static class IntentionResponse {
        public final boolean shouldRespond;
        public String reason;  // Made mutable for async updates
        public final float probability;
        public final boolean isEarlyResponse;  // Indicates if this is an early response
        public final boolean isError; // Indicates the intention check failed

        public IntentionResponse(boolean shouldRespond, String reason, float probability) {
            this(shouldRespond, reason, probability, false, false);
        }

        public IntentionResponse(boolean shouldRespond, String reason, float probability, boolean isEarlyResponse) {
            this(shouldRespond, reason, probability, isEarlyResponse, false);
        }

        public IntentionResponse(boolean shouldRespond, String reason, float probability, boolean isEarlyResponse, boolean isError) {
            this.shouldRespond = shouldRespond;
            this.reason = reason;
            this.probability = probability;
            this.isEarlyResponse = isEarlyResponse;
            this.isError = isError;
        }
    }

    /**
     * Callback interface for early intention decisions
     */
    public interface EarlyDecisionCallback {
        void onEarlyDecision(IntentionResponse earlyResponse);
    }

    /**
     * Initialize the intention checker with its own client
     */
    public static void initialize() {
        // Use intention-specific API settings if provided, otherwise fall back to main settings
        String apiKey = !ServerChanCore.CONFIG.intentionCheckerApiKey.isEmpty()
            ? ServerChanCore.CONFIG.intentionCheckerApiKey
            : ServerChanCore.CONFIG.openaiApiKey;

        String baseUrl = !ServerChanCore.CONFIG.intentionCheckerBaseUrl.isEmpty()
            ? ServerChanCore.CONFIG.intentionCheckerBaseUrl
            : ServerChanCore.CONFIG.openaiBaseUrl;

        // Create a separate OpenAI client for intention checking
        // This allows us to use different settings/models
        intentionOpenAI = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(30)) // Shorter timeout for quick checks
                .build();

        ServerChanCore.LOGGER.info(I18n.format("intention.initialized",
            ServerChanCore.CONFIG.intentionCheckerModel, ServerChanCore.CONFIG.intentionCheckerContextLength, baseUrl));
    }

    /**
     * Check if the bot should respond to a given message with early callback support
     * @param sender The sender of the message
     * @param message The message content
     * @param isGameEvent Whether this is a game event (join/leave/death)
     * @param recentMessages Recent conversation context (can be null)
     * @param callback Optional callback for early decision notification
     * @return IntentionResponse containing decision and reasoning
     */
    public static IntentionResponse checkIntentionWithCallback(String sender, String message, boolean isGameEvent,
                                                               List<MessageWrapper> recentMessages,
                                                               EarlyDecisionCallback callback) {
        ServerChanCore.LOGGER.info("IntentionChecker: Checking intention for sender={}, messageLength={}, isGameEvent={}, fastPath={}",
            sender, message != null ? message.length() : 0, isGameEvent, ServerChanCore.CONFIG.useFastPathIntentionChecker);
        ServerChanCore.LOGGER.debug(I18n.format("intention.check.entering",
            sender, message != null ? message.length() : 0, isGameEvent, callback != null));

        try {
            // Use the intention checking system message from config and append format instruction
            // For fast path, output probability first to enable early extraction
            String formatInstruction = ServerChanCore.CONFIG.useFastPathIntentionChecker ?
                I18n.get("intention.prompt.format.fastpath") :
                I18n.get("intention.prompt.format.normal");
            String systemPrompt = ServerChanCore.CONFIG.intentionCheckingSystemMessage + formatInstruction;

            // Build context history if available
            JsonArray conversationHistory = new JsonArray();
            if (recentMessages != null && !recentMessages.isEmpty()) {
                // Get last N messages for context (or less if not available)
                int contextLength = ServerChanCore.CONFIG.intentionCheckerContextLength;
                int startIndex = Math.max(0, recentMessages.size() - contextLength);
                ServerChanCore.LOGGER.debug(I18n.format("intention.check.context.processing",
                    Math.min(contextLength, recentMessages.size()), recentMessages.size(), contextLength));
                for (int i = startIndex; i < recentMessages.size(); i++) {
                    MessageWrapper msg = recentMessages.get(i);
                    JsonObject msgObj = new JsonObject();

                    if (msg.getRole() == MessageWrapper.Role.USER) {
                        msgObj.addProperty("role", "user");
                        msgObj.addProperty("content", msg.getContent());
                    } else if (msg.getRole() == MessageWrapper.Role.ASSISTANT) {
                        msgObj.addProperty("role", "assistant");
                        msgObj.addProperty("content", msg.getContent());
                    }

                    conversationHistory.add(msgObj);
                }
            }

            // Build the user message with context
            JsonObject contextObject = new JsonObject();
            contextObject.addProperty("sender", sender);
            contextObject.addProperty("message", message);
            contextObject.addProperty("is_game_event", isGameEvent);
            contextObject.addProperty("bot_names", I18n.get("intention.prompt.bot.names"));
            contextObject.add("recent_conversation", conversationHistory);

            String userMessage = I18n.get("intention.prompt.analyze") + "\n" +
                                  contextObject.toString();

            // Create the chat completion request
            ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                    .model(ChatModel.of(ServerChanCore.CONFIG.intentionCheckerModel))
                    .addSystemMessage(systemPrompt)
                    .addUserMessage(userMessage)
                    .temperature(0.1) // Low temperature for consistent decision-making
                    .build();

            ServerChanCore.LOGGER.debug(I18n.format("intention.check.checking", sender, message));

            float probability;
            String reason;
            JsonObject responseJson;

            if (ServerChanCore.CONFIG.useFastPathIntentionChecker) {
                // Fast path: Use streaming to get probability early
                ServerChanCore.LOGGER.debug(I18n.get("intention.fastpath.starting"));
                IntentionResponse streamResult = checkIntentionStreaming(request, sender, message, isGameEvent, conversationHistory, callback);
                probability = streamResult.probability;
                reason = streamResult.reason;

                // Create response JSON for debug logging
                responseJson = new JsonObject();
                responseJson.addProperty("respond_probability", probability);
                responseJson.addProperty("reason", reason);
            } else {
                // Normal path: Get complete response
                ServerChanCore.LOGGER.info("IntentionChecker: Making API request to {} with model {} (timeout={}s)...",
                    ServerChanCore.CONFIG.intentionCheckerBaseUrl.isEmpty()
                        ? ServerChanCore.CONFIG.openaiBaseUrl
                        : ServerChanCore.CONFIG.intentionCheckerBaseUrl,
                    ServerChanCore.CONFIG.intentionCheckerModel,
                    API_TIMEOUT_SECONDS);
                long apiStartTime = System.currentTimeMillis();
                RequestOptions requestOptions = RequestOptions.builder()
                    .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                    .build();
                ChatCompletion chatCompletion = intentionOpenAI.chat().completions().create(request, requestOptions);
                long apiEndTime = System.currentTimeMillis();
                ServerChanCore.LOGGER.info("IntentionChecker: API request completed in {}ms", apiEndTime - apiStartTime);
                String responseContent = chatCompletion.choices().get(0).message().content().orElse("");

                // Log raw response for debugging
                ServerChanCore.LOGGER.debug(I18n.format("intention.check.raw.response", responseContent));

                // Parse the JSON response with robust handling
                responseJson = extractJsonFromResponse(responseContent);

                probability = responseJson.get("respond_probability").getAsFloat();
                reason = responseJson.get("reason").getAsString();
            }

            // Determine if we should respond based on threshold
            boolean shouldRespond = probability > ServerChanCore.CONFIG.responseProbabilityThreshold;

            // Save debug information (only if enabled in config)
            if (ServerChanCore.CONFIG.enableDebugFileLogging) {
                saveIntentionDebug(sender, message, isGameEvent, conversationHistory, responseJson);
            }

            // Log the decision
            if (shouldRespond) {
                ServerChanCore.LOGGER.info(I18n.format("intention.check.will.respond",
                    (int)(probability * 100), (int)(ServerChanCore.CONFIG.responseProbabilityThreshold * 100), reason));
            } else {
                ServerChanCore.LOGGER.debug(I18n.format("intention.check.will.not.respond",
                    (int)(probability * 100), (int)(ServerChanCore.CONFIG.responseProbabilityThreshold * 100), reason));
            }

            return new IntentionResponse(shouldRespond, reason, probability);

        } catch (Exception e) {
            if (isRateLimitException(e)) {
                OpenAIHandler.recordRequestException(e);
            }
            ServerChanCore.LOGGER.error("IntentionChecker: Error checking intention - type={}, message={}",
                e.getClass().getSimpleName(), e.getMessage());
            ServerChanCore.LOGGER.error(I18n.format("intention.check.error", sender, message), e);

            // For critical messages (starting with : or mentioning bot), respond despite error
            String lowerMessage = message.toLowerCase();
            String botNames = I18n.get("intention.prompt.bot.names");
            String[] botNameArray = botNames.split(",");
            String trimmedMessage = message.trim();
            boolean isCriticalMessage = trimmedMessage.startsWith(":") ||
                                       trimmedMessage.startsWith("：");

            // Check if message contains any bot name
            for (String botName : botNameArray) {
                if (lowerMessage.contains(botName.toLowerCase())) {
                    isCriticalMessage = true;
                    break;
                }
            }

            if (isCriticalMessage) {
                ServerChanCore.LOGGER.warn(I18n.format("intention.check.critical.detected", sender, message));
                return new IntentionResponse(true, I18n.get("intention.reason.error.critical"), 1.0f, false, true);
            }

            // On error, default to not responding to avoid spam
            return new IntentionResponse(false, I18n.get("intention.reason.error.default"), 0.0f, false, true);
        }
    }

    /**
     * Check intention using streaming to extract probability early
     * @param request The chat completion request
     * @param sender The sender of the message
     * @param message The message content
     * @param isGameEvent Whether this is a game event
     * @param conversationHistory The conversation history for debug
     * @param callback Optional callback for early decision notification
     * @return IntentionResponse with probability and reason
     */
    private static IntentionResponse checkIntentionStreaming(ChatCompletionCreateParams request,
                                                              String sender, String message,
                                                              boolean isGameEvent,
                                                              JsonArray conversationHistory,
                                                              EarlyDecisionCallback callback) {
        long startTime = System.currentTimeMillis();
        StringBuilder streamedContent = new StringBuilder();
        Float extractedProbability = null;
        String extractedReason = "";
        boolean foundProbabilityKey = false;
        StringBuilder probBuffer = new StringBuilder();
        long probabilityExtractedTime = 0;

        ServerChanCore.LOGGER.info("IntentionChecker: Starting streaming request to {} with model {} (timeout={}s)...",
            ServerChanCore.CONFIG.intentionCheckerBaseUrl.isEmpty()
                ? ServerChanCore.CONFIG.openaiBaseUrl
                : ServerChanCore.CONFIG.intentionCheckerBaseUrl,
            ServerChanCore.CONFIG.intentionCheckerModel,
            API_TIMEOUT_SECONDS);

        RequestOptions streamRequestOptions = RequestOptions.builder()
            .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
            .build();

        try (StreamResponse<ChatCompletionChunk> streamResponse =
                intentionOpenAI.chat().completions().createStreaming(request, streamRequestOptions)) {

            ServerChanCore.LOGGER.debug(I18n.get("intention.fastpath.stream.starting"));

            // Process the stream chunk by chunk in real-time
            for (ChatCompletionChunk chunk : (Iterable<ChatCompletionChunk>) streamResponse.stream()::iterator) {
                if (chunk.choices() != null && !chunk.choices().isEmpty()) {
                    ChatCompletionChunk.Choice choice = chunk.choices().get(0);
                    if (choice.delta() != null && choice.delta().content().isPresent()) {
                        String content = choice.delta().content().get();
                        streamedContent.append(content);

                        // Early probability extraction
                        String currentContent = streamedContent.toString();

                        // Look for the probability key
                        if (!foundProbabilityKey && currentContent.contains("\"respond_probability\"")) {
                            foundProbabilityKey = true;
                            // Find the colon after the key
                            int keyIndex = currentContent.indexOf("\"respond_probability\"");
                            int colonIndex = currentContent.indexOf(":", keyIndex);
                            if (colonIndex != -1) {
                                // Start collecting the value after the colon
                                String afterColon = currentContent.substring(colonIndex + 1);
                                probBuffer.append(afterColon);
                            }
                        } else if (foundProbabilityKey && extractedProbability == null) {
                            probBuffer.append(content);

                            // Check if we have a complete probability value
                            String probString = probBuffer.toString().trim();

                            // Look for the end of the number (comma or closing brace)
                            if (probString.contains(",") || probString.contains("}")) {
                                // Extract just the number
                                probString = probString.split("[,}]")[0].trim();
                                try {
                                    extractedProbability = Float.parseFloat(probString);
                                    probabilityExtractedTime = System.currentTimeMillis() - startTime;

                                    // Log early probability extraction
                                    boolean shouldRespond = extractedProbability > ServerChanCore.CONFIG.responseProbabilityThreshold;
                                    String operator = shouldRespond ? I18n.get("intention.operator.greater") : I18n.get("intention.operator.lessequal");
                                    String decision = shouldRespond ? I18n.get("intention.decision.will.trigger") : I18n.get("intention.decision.wont.respond");
                                    ServerChanCore.LOGGER.debug(I18n.format("intention.fastpath.probability.extracted",
                                        probabilityExtractedTime,
                                        (int)(extractedProbability * 100),
                                        operator,
                                        (int)(ServerChanCore.CONFIG.responseProbabilityThreshold * 100),
                                        decision));

                                    // Trigger callback immediately if should respond
                                    if (callback != null && shouldRespond) {
                                        ServerChanCore.LOGGER.info(I18n.get("intention.fastpath.trigger.early"));
                                        IntentionResponse earlyResponse = new IntentionResponse(true,
                                            I18n.get("intention.reason.fastpath.early"), extractedProbability, true);
                                        callback.onEarlyDecision(earlyResponse);
                                    }

                                    // Continue streaming to get the reason for logging
                                } catch (NumberFormatException e) {
                                    ServerChanCore.LOGGER.debug(I18n.format("intention.fastpath.probability.parse.failed", probString));
                                }
                            }
                        }
                    }
                }
            }

            // Parse the complete response
            String fullResponse = streamedContent.toString();
            long streamEndTime = System.currentTimeMillis();
            ServerChanCore.LOGGER.info("IntentionChecker: Streaming request completed in {}ms", streamEndTime - startTime);
            ServerChanCore.LOGGER.debug(I18n.format("intention.fastpath.complete.response", fullResponse));

            try {
                JsonObject responseJson = extractJsonFromResponse(fullResponse);
                if (extractedProbability == null) {
                    extractedProbability = responseJson.get("respond_probability").getAsFloat();
                    probabilityExtractedTime = System.currentTimeMillis() - startTime;
                }
                extractedReason = responseJson.has("reason") ? responseJson.get("reason").getAsString() : I18n.get("intention.reason.fastpath.default");

                boolean shouldRespond = extractedProbability > ServerChanCore.CONFIG.responseProbabilityThreshold;

                long totalElapsed = System.currentTimeMillis() - startTime;

                // Log completion with both early extraction time and total time
                String operator = shouldRespond ? I18n.get("intention.operator.greater") : I18n.get("intention.operator.lessequal");
                if (probabilityExtractedTime > 0 && probabilityExtractedTime < totalElapsed) {
                    ServerChanCore.LOGGER.debug(I18n.format("intention.fastpath.complete.both",
                        probabilityExtractedTime, totalElapsed,
                        (int)(extractedProbability * 100),
                        operator,
                        (int)(ServerChanCore.CONFIG.responseProbabilityThreshold * 100),
                        extractedReason));
                } else {
                    ServerChanCore.LOGGER.debug(I18n.format("intention.fastpath.complete.total",
                        totalElapsed, (int)(extractedProbability * 100),
                        operator,
                        (int)(ServerChanCore.CONFIG.responseProbabilityThreshold * 100),
                        extractedReason));
                }

                return new IntentionResponse(shouldRespond, extractedReason, extractedProbability);

            } catch (Exception e) {
                // If we at least got the probability, use it
                if (extractedProbability != null) {
                    boolean shouldRespond = extractedProbability > ServerChanCore.CONFIG.responseProbabilityThreshold;
                    long elapsed = System.currentTimeMillis() - startTime;
                    ServerChanCore.LOGGER.warn(I18n.format("intention.fastpath.partial.warning",
                        extractedProbability, elapsed));
                    return new IntentionResponse(shouldRespond, I18n.get("intention.reason.fastpath.partial"), extractedProbability);
                }
                throw e;
            }

        } catch (Exception e) {
            ServerChanCore.LOGGER.error("IntentionChecker: Streaming error - type={}, message={}",
                e.getClass().getSimpleName(), e.getMessage());
            ServerChanCore.LOGGER.error(I18n.format("intention.fastpath.error", sender, message), e);

            // Fall back to non-streaming check
            ServerChanCore.LOGGER.warn(I18n.get("intention.fastpath.fallback.warning"));
            ServerChanCore.LOGGER.info("IntentionChecker: Attempting fallback non-streaming request (timeout={}s)...", API_TIMEOUT_SECONDS);
            try {
                RequestOptions requestOptions = RequestOptions.builder()
                    .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                    .build();
                ChatCompletion chatCompletion = intentionOpenAI.chat().completions().create(request, requestOptions);
                ServerChanCore.LOGGER.info("IntentionChecker: Fallback request completed");
                String responseContent = chatCompletion.choices().get(0).message().content().orElse("");
                JsonObject responseJson = extractJsonFromResponse(responseContent);
                float probability = responseJson.get("respond_probability").getAsFloat();
                String reason = responseJson.get("reason").getAsString();
                boolean shouldRespond = probability > ServerChanCore.CONFIG.responseProbabilityThreshold;
                return new IntentionResponse(shouldRespond, reason, probability);
            } catch (Exception fallbackError) {
                ServerChanCore.LOGGER.error("IntentionChecker: Fallback also failed - type={}, message={}",
                    fallbackError.getClass().getSimpleName(), fallbackError.getMessage());
                ServerChanCore.LOGGER.error(I18n.get("intention.fastpath.fallback.error"), fallbackError);
                return new IntentionResponse(false, I18n.get("intention.reason.error.default"), 0.0f, false, true);
            }
        }
    }

    /**
     * Reset/reinitialize the intention checker client
     */
    public static void reset() {
        initialize();
    }

    /**
     * Extract JSON from a response that might contain extra text or markdown formatting
     */
    private static JsonObject extractJsonFromResponse(String responseContent) throws JsonSyntaxException {
        if (responseContent == null || responseContent.isEmpty()) {
            throw new JsonSyntaxException(I18n.get("intention.parse.empty"));
        }

        String trimmed = responseContent.trim();

        // Try to extract JSON from markdown code blocks
        if (trimmed.contains("```json")) {
            int startIdx = trimmed.indexOf("```json") + 7;
            int endIdx = trimmed.indexOf("```", startIdx);
            if (endIdx != -1) {
                trimmed = trimmed.substring(startIdx, endIdx).trim();
            }
        } else if (trimmed.contains("```")) {
            // Handle generic code blocks
            int startIdx = trimmed.indexOf("```") + 3;
            // Skip any language identifier
            int newlineIdx = trimmed.indexOf('\n', startIdx);
            if (newlineIdx != -1) {
                startIdx = newlineIdx + 1;
            }
            int endIdx = trimmed.indexOf("```", startIdx);
            if (endIdx != -1) {
                trimmed = trimmed.substring(startIdx, endIdx).trim();
            }
        }

        // Try to find JSON object boundaries
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
        }

        // Attempt to parse the JSON
        try {
            JsonObject responseJson = parseJsonString(trimmed).getAsJsonObject();

            // Validate required fields
            if (!responseJson.has("respond_probability") || !responseJson.has("reason")) {
                throw new JsonSyntaxException(I18n.get("intention.parse.missing.fields"));
            }

            return responseJson;
        } catch (JsonSyntaxException e) {
            // Log the problematic response for debugging
            ServerChanCore.LOGGER.error(I18n.format("intention.parse.failed", responseContent));

            // Try one more time with lenient parsing by removing common issues
            trimmed = trimmed.replaceAll("\\s+", " ")  // Normalize whitespace
                           .replaceAll(",\\s*}", "}")      // Remove trailing commas
                           .replaceAll(",\\s*]", "]");      // Remove trailing commas in arrays

            try {
                return parseJsonString(trimmed).getAsJsonObject();
            } catch (Exception e2) {
                // If all parsing attempts fail, create a default response
                ServerChanCore.LOGGER.error(I18n.get("intention.parse.attempts.failed"));
                JsonObject fallback = new JsonObject();
                fallback.addProperty("reason", I18n.get("intention.reason.parse.failed"));
                fallback.addProperty("respond_probability", 0.0f);
                return fallback;
            }
        }
    }

    private static boolean isRateLimitException(Exception exception) {
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
     * Save intention check debug information to a JSON file
     */
    private static void saveIntentionDebug(String sender, String message, boolean isGameEvent,
                                           JsonArray conversationHistory, JsonObject response) {
        String DEBUG_FILE_NAME = "openai_debug_intention.json";
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject debugInfo = new JsonObject();
        debugInfo.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        debugInfo.addProperty("phase", "intention_checking");
        debugInfo.addProperty("model", ServerChanCore.CONFIG.intentionCheckerModel);

        // Input data
        JsonObject input = new JsonObject();
        input.addProperty("sender", sender);
        input.addProperty("message", message);
        input.addProperty("is_game_event", isGameEvent);
        debugInfo.add("input", input);

        // Recent conversation context
        debugInfo.add("recent_conversation", conversationHistory);

        // Response data
        debugInfo.add("response", response);

        try (FileWriter writer = new FileWriter(DEBUG_FILE_NAME, false)) {
            writer.write(gson.toJson(debugInfo));
            ServerChanCore.LOGGER.debug(I18n.format("intention.debug.saved", DEBUG_FILE_NAME));
        } catch (IOException e) {
            ServerChanCore.LOGGER.error(I18n.format("intention.debug.save.failed", DEBUG_FILE_NAME, e.getMessage()), e);
        }
    }

    /**
     * Parse a JSON string in a way compatible with all Gson versions.
     * JsonParser.parseString() was added in Gson 2.8.6, but older Minecraft versions use older Gson.
     */
    @SuppressWarnings("deprecation")
    private static JsonElement parseJsonString(String json) {
        return new JsonParser().parse(json);
    }
}
