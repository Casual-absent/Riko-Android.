package com.casualabsent.riko;

/** A single user or assistant message used for bounded conversation context. */
public final class ChatMessage {
    public final String role;
    public final String content;

    public ChatMessage(String role, String content) {
        if (!"user".equals(role) && !"assistant".equals(role)) {
            throw new IllegalArgumentException("Unsupported chat role: " + role);
        }
        this.role = role;
        this.content = content == null ? "" : content;
    }
}
