package net.himeki.serverchan.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

/**
 * ConfigLib-based YAML configuration for ServerChan.
 * This configuration is platform-independent and works across all loaders
 * (Fabric, Forge, NeoForge, Spigot).
 */
@Configuration
public class ServerChanYamlConfig {

    @Comment({"", "Enable or disable ServerChan globally", "全局启用或禁用 ServerChan"})
    public boolean enabled = true;

    @Comment({"", "OpenAI API configuration", "OpenAI API 配置"})
    public OpenAIConfig openai = new OpenAIConfig();

    @Comment({"", "Intention Checker - determines when AI should respond", "意图检查器 - 决定AI何时应该响应"})
    public IntentionCheckerConfig intention = new IntentionCheckerConfig();

    @Comment({"", "Bot behavior and personality settings", "机器人行为和个性设置"})
    public BotConfig bot = new BotConfig();

    @Comment({"", "Game event monitoring settings", "游戏事件监控设置"})
    public EventsConfig events = new EventsConfig();

    @Comment({"", "Localization settings", "本地化设置"})
    public LocalizationConfig localization = new LocalizationConfig();

    @Comment({"", "Debug settings", "调试设置"})
    public DebugConfig debug = new DebugConfig();

    /**
     * OpenAI API configuration
     */
    @Configuration
    public static class OpenAIConfig {
        @Comment({"", "API key for OpenAI authentication", "OpenAI API密钥"})
        public String apiKey = "";

        @Comment({"", "Base URL for OpenAI API (can be changed for proxies or compatible services)", "OpenAI API基础URL (可用于代理或兼容服务)"})
        public String baseUrl = "https://api.openai.com";

        @Comment({"", "AI model to use for generating responses", "用于生成响应的AI模型"})
        public String model = "gpt-5.1";

        @Comment({"", "Temperature controls randomness (0=deterministic, 2=very random)", "温度控制随机性 (0=确定性, 2=非常随机)"})
        public double temperature = 1.0;

        @Comment({"", "System prompts for AI behavior", "系统提示词配置"})
        public PromptsConfig prompts = new PromptsConfig();

        @Configuration
        public static class PromptsConfig {
            @Comment({"", "System message that defines AI's behavior and response style", "定义AI行为和响应风格的系统消息"})
            public String responseGenerationSystemMessage = ServerChanConfigBase.DEFAULT_RESPONSE_GENERATION_PROMPT;
        }
    }

    /**
     * Intention Checker configuration for AI response decision-making
     */
    @Configuration
    public static class IntentionCheckerConfig {
        @Comment({"", "Enable intention checking to filter when AI should respond", "启用意图检查以过滤AI何时应该响应"})
        public boolean enabled = true;

        @Comment({"", "Use fast path for intention checking (skips some checks for speed)", "使用快速路径进行意图检查 (跳过部分检查以提高速度)"})
        public boolean useFastPath = false;

        @Comment({"", "Minimum probability threshold for AI to respond (0.0-1.0)", "AI响应的最小概率阈值 (0.0-1.0)"})
        public double responseProbabilityThreshold = 0.5;

        @Comment({"", "Number of recent messages to include in context for intention checking", "意图检查时包含的最近消息数量"})
        public int contextLength = 20;

        @Comment({"", "API key for intention checker (leave empty to use main OpenAI key)", "意图检查器的API密钥 (留空则使用主OpenAI密钥)"})
        public String apiKey = "";

        @Comment({"", "Base URL for intention checker API (leave empty to use main URL)", "意图检查器的API基础URL (留空则使用主URL)"})
        public String baseUrl = "";

        @Comment({"", "Model for intention checking (usually a faster/cheaper model)", "用于意图检查的模型 (通常使用更快/更便宜的模型)"})
        public String model = "gpt-4o-mini";

        @Comment({"", "Prompts for intention checking system", "意图检查系统的提示词"})
        public IntentionPromptsConfig prompts = new IntentionPromptsConfig();

        @Configuration
        public static class IntentionPromptsConfig {
            @Comment({"", "System message for intention checker to determine if AI should respond", "意图检查器的系统消息，用于确定AI是否应该响应"})
            public String systemMessage = ServerChanConfigBase.DEFAULT_INTENTION_CHECKING_PROMPT;
        }
    }

    /**
     * Bot behavior and personality settings
     */
    @Configuration
    public static class BotConfig {
        @Comment({"", "Color code for bot chat (without §). Examples: b=aqua, e=yellow, a=green, c=red", "机器人聊天颜色代码 (不含§)。例如: b=青色, e=黄色, a=绿色, c=红色"})
        public String color = "b";

        @Comment({"", "Timezone for time-related functions (e.g., UTC, America/New_York, Asia/Shanghai)", "时区设置 (例如: UTC, America/New_York, Asia/Shanghai)"})
        public String timeZone = "UTC";

        @Comment({"", "Number of messages to keep in conversation context", "保留在对话上下文中的消息数量"})
        public int contextSize = 20;

        @Comment({"", "Inherit permissions from command source when executing commands", "执行命令时继承命令源的权限"})
        public boolean inheritCmdSourcePermission = true;

        public boolean disableDevEasterEgg = false;
    }

    /**
     * Game event monitoring configuration
     */
    @Configuration
    public static class EventsConfig {
        @Comment({"", "Enable monitoring and responding to game events", "启用游戏事件监控和响应"})
        public boolean enabled = true;

        @Comment({"", "Monitor player join/leave events", "监控玩家加入/离开事件"})
        public boolean joinLeaveEvents = true;

        @Comment({"", "Monitor player death events", "监控玩家死亡事件"})
        public boolean deathEvents = true;

        @Comment({"", "Monitor player advancement/achievement events", "监控玩家进度/成就事件"})
        public boolean advancementEvents = false;

        @Comment({"", "Monitor and process chat messages", "监控和处理聊天消息"})
        public boolean chatEvents = true;
    }

    /**
     * Localization settings
     */
    @Configuration
    public static class LocalizationConfig {
        @Comment({"", "Language/locale code (e.g., en, ja, zh_CN)", "语言/地区代码 (例如: en, ja, zh_CN)"})
        public String locale = "en";

        @Comment({"", "Automatically detect system locale if locale is not set", "如果未设置语言，则自动检测系统语言"})
        public boolean autoDetect = true;
    }

    /**
     * Debug settings
     */
    @Configuration
    public static class DebugConfig {
        @Comment({"", "Enable debug file logging", "启用调试文件日志"})
        public boolean enableFileLogging = false;
    }

    /**
     * Convert this hierarchical config to the flat base config for backwards compatibility
     */
    public ServerChanConfigBase toBase() {
        ServerChanConfigBase base = new ServerChanConfigBase();

        base.enabled = enabled;

        // Localization
        base.locale = localization.locale;

        // OpenAI settings
        base.openaiApiKey = openai.apiKey;
        base.openaiBaseUrl = openai.baseUrl;
        base.model = openai.model;
        base.temperature = openai.temperature;
        base.responseGenerationSystemMessage = openai.prompts.responseGenerationSystemMessage;

        // Intention checker settings
        base.useIntentionChecker = intention.enabled;
        base.useFastPathIntentionChecker = intention.useFastPath;
        base.responseProbabilityThreshold = intention.responseProbabilityThreshold;
        base.intentionCheckerContextLength = intention.contextLength;
        base.intentionCheckerApiKey = intention.apiKey;
        base.intentionCheckerBaseUrl = intention.baseUrl;
        base.intentionCheckerModel = intention.model;
        base.intentionCheckingSystemMessage = intention.prompts.systemMessage;

        // Bot settings
        base.botColor = bot.color;
        base.timeZone = bot.timeZone;
        base.contextSize = bot.contextSize;
        base.inheritCmdSourcePermission = bot.inheritCmdSourcePermission;
        base.disableDevEasterEgg = bot.disableDevEasterEgg;

        // Game events
        base.enableGameEvents = events.enabled;
        base.enableJoinLeaveEvents = events.joinLeaveEvents;
        base.enableDeathEvents = events.deathEvents;

        // Debug
        base.enableDebugFileLogging = debug.enableFileLogging;

        return base;
    }
}
