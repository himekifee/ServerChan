package net.himeki.serverchan.openai;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Function definition for executing Minecraft commands
 */
@JsonClassDescription("Execute a list of Minecraft server commands")
public class ExecuteMinecraftCommands {
    @JsonPropertyDescription("List of Minecraft commands to execute")
    public List<String> commands;

    // Default constructor required for deserialization
    public ExecuteMinecraftCommands() {}

    // Constructor for convenience
    public ExecuteMinecraftCommands(List<String> commands) {
        this.commands = commands;
    }
}