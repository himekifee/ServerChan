package net.himeki.serverchan.config;

/**
 * Base configuration class for ServerChan.
 */
public class ServerChanConfigBase {
    // Default prompt constants - use these across all config implementations for consistency
    public static final String DEFAULT_INTENTION_CHECKING_PROMPT =
        "You are an AI assistant for a Minecraft server. Analyze the message content and decide if a response is needed.\n\n" +
        "Must respond:\n" +
        "- Direct commands starting with ':' or ':'\n" +
        "- Explicitly mentioning my name (ServerChan)\n" +
        "- Direct questions to me\n" +
        "- Requests to execute server commands\n\n" +
        "Can respond:\n" +
        "- Players needing help or confused\n" +
        "- Important achievements or milestones worth celebrating\n" +
        "- Players needing comfort\n" +
        "- When my response makes the conversation more interesting\n\n" +
        "Do not respond:\n" +
        "- Normal conversations between players\n" +
        "- Routine game activities (mining/building/trading)\n" +
        "- Discussions unrelated to me\n" +
        "- Repetitive or meaningless messages\n" +
        "- Normal join/leave events\n\n" +
        "Default principle: When in doubt, do not respond.";

    public static final String DEFAULT_RESPONSE_GENERATION_PROMPT =
        "You are an AI assistant for a Minecraft server, named ServerChan.\n\n" +
        "Role settings:\n" +
        "- Helpful and friendly assistant\n" +
        "- Knowledgeable about Minecraft mechanics\n" +
        "- Respectful to all players\n\n" +
        "Extra commands:\n" +
        "- 'serverchan reload' reloads config\n" +
        "- 'serverchan reset' clears memory\n" +
        "- 'serverchan disable' pauses message processing\n" +
        "- 'serverchan enable' resumes message processing\n\n" +
        "Response requirements:\n" +
        "- Output the response directly, without 'ServerChan:' prefix\n" +
        "- Keep responses natural and engaging\n" +
        "- Avoid list format unless necessary\n" +
        "- Use varied tone and vivid scenarios";

    public String locale = "en";

    public String openaiApiKey = "";

    public String intentionCheckingSystemMessage = DEFAULT_INTENTION_CHECKING_PROMPT;

    public String responseGenerationSystemMessage = DEFAULT_RESPONSE_GENERATION_PROMPT;

    public String model = "gpt-5.1";

    public String intentionCheckerModel = "qwen3-235b-a22b-2507";

    public boolean useIntentionChecker = true;

    public double responseProbabilityThreshold = 0.5;

    public int intentionCheckerContextLength = 20;

    public String intentionCheckerApiKey = "";

    public String intentionCheckerBaseUrl = "";

    public boolean useFastPathIntentionChecker = false;

    public String openaiBaseUrl = "https://api.openai.com/v1";

    public double temperature = 1.0;

    public int contextSize = 20;

    public String timeZone = "UTC";

    public String botColor = "b";

    public boolean enableGameEvents = true;

    public boolean enableJoinLeaveEvents = true;

    public boolean enableDeathEvents = true;

    public boolean inheritCmdSourcePermission = true;

    public boolean enableDebugFileLogging = false;

    public boolean disableDevEasterEgg = false;

    public boolean enabled = true;
}
