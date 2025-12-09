package net.himeki.serverchan.config;

import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurationStore;

import net.himeki.serverchan.ServerChanCore;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Platform-independent configuration loader using ConfigLib.
 * This class provides a unified way to load, save, and update configurations
 * across all platforms (Fabric, Forge, NeoForge, Spigot).
 */
public class ConfigLoader {
    private static final String CONFIG_FILE_NAME = "serverchan.yml";
    private static ServerChanYamlConfig currentConfig;
    private static Path configPath;
    private static YamlConfigurationStore<ServerChanYamlConfig> configStore;

    /**
     * Initialize the configuration loader with a config directory.
     *
     * @param configDir The directory where the config file should be stored
     * @return The loaded configuration converted to ServerChanConfigBase
     */
    public static ServerChanConfigBase initialize(Path configDir) {
        configPath = configDir.resolve(CONFIG_FILE_NAME);

        // Create config properties with custom settings
        YamlConfigurationProperties properties = YamlConfigurationProperties.newBuilder()
                .header("ServerChan Configuration\n" +
                        "AI-powered chat assistant for Minecraft servers\n\n" +
                        "For more information, visit: https://github.com/himekifee/ServerChan")
                .build();

        configStore = new YamlConfigurationStore<>(ServerChanYamlConfig.class, properties);

        // Load or create config
        currentConfig = loadOrCreate();

        return currentConfig.toBase();
    }

    /**
     * Load the configuration, creating a default one if it doesn't exist.
     */
    private static ServerChanYamlConfig loadOrCreate() {
        try {
            // Ensure parent directory exists
            if (configPath.getParent() != null) {
                Files.createDirectories(configPath.getParent());
            }

            if (Files.exists(configPath)) {
                return configStore.load(configPath);
            } else {
                // Create default config
                ServerChanYamlConfig defaultConfig = new ServerChanYamlConfig();
                configStore.save(defaultConfig, configPath);
                return defaultConfig;
            }
        } catch (Exception e) {
            ServerChanCore.LOGGER.error("Failed to load config, using defaults", e);
            return new ServerChanYamlConfig();
        }
    }

    /**
     * Reload the configuration from disk.
     *
     * @return The reloaded configuration converted to ServerChanConfigBase
     */
    public static ServerChanConfigBase reload() {
        if (configStore == null || configPath == null) {
            throw new IllegalStateException("ConfigLoader not initialized");
        }

        try {
            currentConfig = configStore.load(configPath);
        } catch (Exception e) {
            ServerChanCore.LOGGER.error("Failed to reload config", e);
        }

        return currentConfig.toBase();
    }

    /**
     * Save the current configuration to disk.
     */
    public static void save() {
        if (configStore == null || configPath == null || currentConfig == null) {
            throw new IllegalStateException("ConfigLoader not initialized");
        }

        try {
            configStore.save(currentConfig, configPath);
        } catch (Exception e) {
            ServerChanCore.LOGGER.error("Failed to save config", e);
        }
    }

    /**
     * Update the configuration (load + save to add new fields).
     *
     * @return The updated configuration converted to ServerChanConfigBase
     */
    public static ServerChanConfigBase update() {
        if (configStore == null || configPath == null) {
            throw new IllegalStateException("ConfigLoader not initialized");
        }

        try {
            currentConfig = configStore.update(configPath);
        } catch (Exception e) {
            ServerChanCore.LOGGER.error("Failed to update config, regenerating defaults", e);
            currentConfig = loadOrCreate();
        }

        return currentConfig.toBase();
    }

    /**
     * Get the current configuration.
     */
    public static ServerChanYamlConfig getConfig() {
        return currentConfig;
    }

    /**
     * Persist the enabled/disabled state to configuration.
     *
     * @param enabled Whether ServerChan should be enabled
     */
    public static void updateEnabledState(boolean enabled) {
        if (configStore == null || configPath == null) {
            ServerChanCore.LOGGER.warn("ConfigLoader not initialized; skipping enabled state persistence");
            return;
        }

        if (currentConfig == null) {
            currentConfig = loadOrCreate();
        }

        currentConfig.enabled = enabled;
        try {
            configStore.save(currentConfig, configPath);
        } catch (Exception e) {
            ServerChanCore.LOGGER.error("Failed to save enabled state", e);
        }
    }

    /**
     * Get the current configuration as base config.
     */
    public static ServerChanConfigBase getBaseConfig() {
        return currentConfig != null ? currentConfig.toBase() : new ServerChanConfigBase();
    }
}
