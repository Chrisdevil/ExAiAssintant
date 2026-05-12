package com.example.exaiassistant.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ConversationDetailResponse {
    private String id;
    private String title;
    private List<MessageResponse> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ConversationDetailResponse(String id, String title, List<MessageResponse> messages,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.messages = messages;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public List<MessageResponse> getMessages() { return messages; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
