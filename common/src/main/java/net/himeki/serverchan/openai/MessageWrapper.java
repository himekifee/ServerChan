package net.himeki.serverchan.openai;

import com.openai.models.chat.completions.*;
import java.util.List;

/**
 * Wrapper class for chat messages since openai-java doesn't have a common base class
 */
public class MessageWrapper {
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    private final Role role;
    private final String content;
    private final List<ChatCompletionMessageToolCall> toolCalls;
    private final String toolCallId;
    private final String functionName;

    private MessageWrapper(Role role, String content, List<ChatCompletionMessageToolCall> toolCalls, String toolCallId, String functionName) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
        this.functionName = functionName;
    }

    public static MessageWrapper system(String content) {
        return new MessageWrapper(Role.SYSTEM, content, null, null, null);
    }

    public static MessageWrapper user(String content) {
        return new MessageWrapper(Role.USER, content, null, null, null);
    }

    public static MessageWrapper assistant(String content) {
        return new MessageWrapper(Role.ASSISTANT, content, null, null, null);
    }

    public static MessageWrapper assistant(String content, List<ChatCompletionMessageToolCall> toolCalls) {
        return new MessageWrapper(Role.ASSISTANT, content, toolCalls, null, null);
    }

    public static MessageWrapper tool(String content, String toolCallId, String functionName) {
        return new MessageWrapper(Role.TOOL, content, null, toolCallId, functionName);
    }

    /**
     * Add this message to a ChatCompletionCreateParams.Builder
     */
    public void addToBuilder(ChatCompletionCreateParams.Builder builder) {
        switch (role) {
            case SYSTEM:
                builder.addSystemMessage(content);
                break;
            case USER:
                builder.addUserMessage(content);
                break;
            case ASSISTANT:
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    builder.addMessage(ChatCompletionAssistantMessageParam.builder()
                            .content(content)
                            .toolCalls(toolCalls)
                            .build());
                } else {
                    builder.addAssistantMessage(content);
                }
                break;
            case TOOL:
                builder.addMessage(ChatCompletionToolMessageParam.builder()
                        .content(content)
                        .toolCallId(toolCallId)
                        .build());
                break;
        }
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}