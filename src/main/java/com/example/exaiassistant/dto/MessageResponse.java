package com.example.exaiassistant.dto;

import java.time.LocalDateTime;

public class MessageResponse {
    private Long id;
    private String role;
    private String content;
    private LocalDateTime createdAt;

    public MessageResponse(Long id, String role, String content, LocalDateTime createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
