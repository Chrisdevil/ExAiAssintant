package com.example.exaiassistant.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ConversationListResponse {
    private String id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long messageCount;

    public ConversationListResponse(String id, String title, LocalDateTime createdAt,
                                     LocalDateTime updatedAt, long messageCount) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messageCount = messageCount;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getMessageCount() { return messageCount; }
}
