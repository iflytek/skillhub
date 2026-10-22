package com.iflytek.skillhub.domain.authoring;

/**
 * Runtime types a draft can bind for behavior validation. The registry of supported
 * adapters lives in the application layer; the domain only knows the stable identifiers.
 */
public enum AgentRuntimeType {

    /** Executes the skill's own scripts in an isolated working directory. */
    LOCAL_SCRIPT("local-script"),

    /** Sends prompts to an OpenAI-compatible chat completion endpoint with SKILL.md as context. */
    OPENAI_COMPATIBLE("openai-compatible");

    private final String identifier;

    AgentRuntimeType(String identifier) {
        this.identifier = identifier;
    }

    public String identifier() {
        return identifier;
    }

    public static AgentRuntimeType fromIdentifier(String identifier) {
        for (AgentRuntimeType type : values()) {
            if (type.identifier.equals(identifier)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown agent runtime type: " + identifier);
    }
}
