package net.himeki.serverchan.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.panayotis.lalein.Lalein;
import com.panayotis.lalein.JsonLalein;
import net.himeki.serverchan.ServerChanCore;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internationalization utility class using lalein library.
 * Manages translations for ServerChan mod and Minecraft game events.
 */
public class I18n {
    private static final String DEFAULT_LOCALE = "en_GB";
    private static final ConcurrentHashMap<String, Lalein> translations = new ConcurrentHashMap<>();
    private static String currentLocale = DEFAULT_LOCALE;

    // Minecraft event translations - dynamically loaded based on locale
    private static final String MC_TRANSLATION_FILE_PREFIX = "/assets/serverchan/lang/mc_";
    private static final String MC_TRANSLATION_FILE_SUFFIX = ".json";
    private static final String MC_TRANSLATION_FALLBACK = "zh_CN"; // Fallback to existing Chinese file
    private static Map<String, String> minecraftTranslations = new ConcurrentHashMap<>();
    private static String loadedMcTranslationLocale = null;

    /**
     * Initialize i18n with the default locale
     */
    public static void init() {
        init(DEFAULT_LOCALE);
    }

    /**
     * Initialize i18n with a specific locale
     * @param locale The locale to use (e.g., "en", "zh_CN")
     */
    public static void init(String locale) {
        setLocale(locale);
        ServerChanCore.LOGGER.info("I18n initialized with locale: {}", locale);
    }

    /**
     * Set the current locale
     * @param locale The locale to set
     */
    public static void setLocale(String locale) {
        currentLocale = normalizeLocale(locale);
        // Preload the translation for this locale
        getLalein(currentLocale);
    }

    /**
     * Get the current locale
     * @return The current locale string
     */
    public static String getCurrentLocale() {
        return currentLocale;
    }

    /**
     * Get or load a Lalein instance for the specified locale
     * @param locale The locale to load
     * @return Lalein instance for the locale
     */
    private static Lalein getLalein(String locale) {
        return translations.computeIfAbsent(locale, loc -> {
            try {
                String resourcePath = String.format("/assets/serverchan/lang/%s.json", loc);
                Lalein lalein = JsonLalein.fromResource(resourcePath);
                ServerChanCore.LOGGER.debug("Loaded translations for locale: {}", loc);
                return lalein;
            } catch (Exception e) {
                ServerChanCore.LOGGER.warn("Failed to load translations for locale: {}. Falling back to English.", loc, e);
                // Fall back to English if the locale file doesn't exist
                if (!loc.equals(DEFAULT_LOCALE)) {
                    try {
                        return JsonLalein.fromResource("/assets/serverchan/lang/en_GB.json");
                    } catch (Exception fallbackError) {
                        ServerChanCore.LOGGER.error("Failed to load fallback English translations", fallbackError);
                        throw new RuntimeException("Failed to load i18n resources", fallbackError);
                    }
                }
                throw new RuntimeException("Failed to load default i18n resources", e);
            }
        });
    }

    /**
     * Get the current Lalein instance
     * @return Current Lalein instance based on the current locale
     */
    private static Lalein getCurrentLalein() {
        return getLalein(currentLocale);
    }

    /**
     * Format a translation key with parameters
     * @param key The translation key (e.g., "intention.check.entering")
     * @param params Optional parameters to format into the string
     * @return The formatted translated string
     */
    public static String format(String key, Object... params) {
        try {
            Lalein lalein = getCurrentLalein();
            return lalein.format(key, params);
        } catch (Exception e) {
            ServerChanCore.LOGGER.error("Failed to format translation for key: {}", key, e);
            // Return the key itself as a fallback
            return String.format("[%s]", key);
        }
    }

    /**
     * Get a simple translation without parameters
     * @param key The translation key
     * @return The translated string
     */
    public static String get(String key) {
        return format(key);
    }

    /**
     * Check if a translation key exists
     * @param key The translation key to check
     * @return true if the key exists, false otherwise
     */
    public static boolean hasKey(String key) {
        try {
            Lalein lalein = getCurrentLalein();
            String result = lalein.format(key);
            // If lalein returns the key itself wrapped in brackets, it doesn't exist
            return !result.equals(String.format("[%s]", key));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reload translations (useful for config changes)
     */
    public static void reload() {
        translations.clear();
        init(currentLocale);
    }

    /**
     * Update locale from configuration
     * @param configLocale The locale string from config
     */
    public static void updateLocaleFromConfig(String configLocale) {
        if (configLocale != null && !configLocale.isEmpty()) {
            setLocale(normalizeLocale(configLocale));
        } else {
            setSystemLocale();
        }
    }

    /**
     * Normalize locale code to match supported file names.
     * Maps short codes (en, ja, zh) to full codes (en_GB, ja_JP, zh_CN).
     * @param locale The input locale string
     * @return The normalized locale string
     */
    private static String normalizeLocale(String locale) {
        if (locale == null) return DEFAULT_LOCALE;
        
        String normalized = locale.trim();
        
        // Map short locale codes to full codes
        switch (normalized.toLowerCase()) {
            case "en":
            case "en_us":
            case "en_gb":
                return "en_GB";
            case "ja":
            case "ja_jp":
                return "ja_JP";
            case "zh":
            case "zh_cn":
                return "zh_CN";
            case "zh_tw":
            case "zh_hk":
                return "zh_TW"; // Not yet supported, will fallback
            default:
                // If already a proper code or unknown, try as-is
                return normalized;
        }
    }

    /**
     * Set locale based on system locale
     */
    public static void setSystemLocale() {
        Locale systemLocale = Locale.getDefault();
        String language = systemLocale.getLanguage();
        String country = systemLocale.getCountry();
        String localeString;

        // Map system locale to supported locale codes
        if ("zh".equals(language)) {
            if ("TW".equals(country) || "HK".equals(country) || "MO".equals(country)) {
                localeString = "zh_TW"; // Traditional Chinese (not yet supported, will fallback)
            } else {
                localeString = "zh_CN"; // Simplified Chinese
            }
        } else if ("ja".equals(language)) {
            localeString = "ja_JP";
        } else {
            localeString = "en_GB"; // Default to English
        }

        setLocale(localeString);
    }

    /**
     * Load Minecraft event translations based on current locale.
     * Tries locale-specific file first, then falls back to default.
     */
    public static void loadMinecraftTranslations() {
        // Check if we need to reload (locale changed)
        if (loadedMcTranslationLocale != null && loadedMcTranslationLocale.equals(currentLocale)) {
            return; // Already loaded for this locale
        }

        String resourcePath = MC_TRANSLATION_FILE_PREFIX + currentLocale + MC_TRANSLATION_FILE_SUFFIX;

        if (!tryLoadMinecraftTranslations(resourcePath, currentLocale)) {
            String fallbackPath = MC_TRANSLATION_FILE_PREFIX + MC_TRANSLATION_FALLBACK + MC_TRANSLATION_FILE_SUFFIX;
            if (!tryLoadMinecraftTranslations(fallbackPath, MC_TRANSLATION_FALLBACK)) {
                ServerChanCore.LOGGER.warn("Failed to load any Minecraft translations");
                minecraftTranslations = new ConcurrentHashMap<>();
            }
        }
    }

    /**
     * Attempt to load Minecraft translations from a specific resource path.
     * @param resourcePath The resource path to load from
     * @param locale The locale identifier for logging
     * @return true if successfully loaded, false otherwise
     */
    private static boolean tryLoadMinecraftTranslations(String resourcePath, String locale) {
        try {
            java.io.InputStream stream = I18n.class.getResourceAsStream(resourcePath);
            if (stream == null) {
                ServerChanCore.LOGGER.debug("Minecraft translation file not found: {}", resourcePath);
                return false;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                minecraftTranslations = new Gson().fromJson(reader, type);
                loadedMcTranslationLocale = currentLocale;
                ServerChanCore.LOGGER.info("Loaded Minecraft event translations for locale: {}", locale);
                return true;
            }
        } catch (Exception e) {
            ServerChanCore.LOGGER.warn("Failed to load Minecraft translations from {}: {}",
                                      resourcePath, e.getMessage());
            return false;
        }
    }

    /**
     * Get translation for a Minecraft game event
     * @param key The translation key
     * @param args Format arguments
     * @return The translated string, or the key if translation not found
     */
    public static String getMinecraftTranslation(String key, Object... args) {
        if (minecraftTranslations == null || minecraftTranslations.isEmpty()) {
            loadMinecraftTranslations();
        }

        String translation = minecraftTranslations.getOrDefault(key, key);
        try {
            return args.length > 0 ? String.format(translation, args) : translation;
        } catch (Exception e) {
            ServerChanCore.LOGGER.debug("Failed to format Minecraft translation for key: {}", key);
            return key;
        }
    }

    /**
     * Check if a Minecraft translation key exists
     * @param key The translation key
     * @return true if the key exists in translations
     */
    public static boolean hasMinecraftTranslation(String key) {
        if (minecraftTranslations == null || minecraftTranslations.isEmpty()) {
            loadMinecraftTranslations();
        }
        return minecraftTranslations.containsKey(key);
    }
}