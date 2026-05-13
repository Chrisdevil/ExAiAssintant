package com.example.exaiassistant.controller;

import com.example.exaiassistant.dto.*;
import com.example.exaiassistant.service.ClaudeProcessService;
import com.example.exaiassistant.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ClaudeProcessService claudeProcessService;

    public ConversationController(ConversationService conversationService,
                                   ClaudeProcessService claudeProcessService) {
        this.conversationService = conversationService;
        this.claudeProcessService = claudeProcessService;
    }

    @PostMapping
    public Map<String, String> createConversation() {
        return conversationService.createConversation();
    }

    @GetMapping
    public List<ConversationListResponse> listConversations() {
        return conversationService.listConversations();
    }

    @GetMapping("/{id}")
    public ConversationDetailResponse getConversation(@PathVariable String id) {
        return conversationService.getConversation(id);
    }

    @DeleteMapping("/{id}")
    public void deleteConversation(@PathVariable String id) {
        conversationService.deleteConversation(id);
    }

    @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sendMessage(@PathVariable String id, @Valid @RequestBody MessageRequest request) {
        String sessionDir = conversationService.getSessionDir(id);

        // Re-inject latest memories on every message so context stays fresh
        conversationService.injectMemories(id, request.getContent());

        boolean isFirst = conversationService.isFirstMessage(id);
        Flux<String> stream = isFirst
                ? claudeProcessService.startSession(id, sessionDir, request.getContent())
                : claudeProcessService.resumeSession(id, sessionDir, request.getContent());

        return stream
                .concatWithValues("[DONE]")
                .doFinally(signal -> conversationService.triggerMemoryExtract(id));
    }
}
