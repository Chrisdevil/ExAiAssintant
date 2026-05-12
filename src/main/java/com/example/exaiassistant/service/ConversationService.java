package com.example.exaiassistant.service;

import com.example.exaiassistant.dto.ConversationDetailResponse;
import com.example.exaiassistant.dto.ConversationListResponse;
import com.example.exaiassistant.dto.MessageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final String dataDir;
    private final String claudeBin;
    private final String claudeProjectsDir;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MemoryService memoryService;
    private final ExecutorService memoryExtractExecutor;

    public ConversationService(@Value("${claude.data-dir:./data}") String dataDir,
                                @Value("${claude.bin:claude}") String claudeBin,
                                MemoryService memoryService,
                                ExecutorService memoryExtractExecutor) {
        this.dataDir = dataDir;
        this.claudeBin = claudeBin;
        this.memoryService = memoryService;
        this.memoryExtractExecutor = memoryExtractExecutor;
        String userHome = System.getProperty("user.home");
        this.claudeProjectsDir = userHome + "/.claude/projects";
    }

    public Map<String, String> createConversation() {
        String id = UUID.randomUUID().toString();
        File dir = new File(dataDir + "/sessions/" + id);
        dir.mkdirs();
        return Map.of("id", id, "title", "新对话");
    }

    public void injectMemories(String conversationId, String firstMessage) {
        log.info("Injecting memories for conversation {}, query: {}", conversationId, firstMessage);
        List<String> memories = memoryService.searchSimilar(firstMessage, 5);
        if (memories.isEmpty()) {
            log.info("No relevant memories found for: {}", firstMessage);
            return;
        }

        StringBuilder sb = new StringBuilder("# 历史相关记忆\n\n");
        sb.append("以下是从你过往对话中检索到的相关信息，请在回答时参考：\n\n");
        for (int i = 0; i < memories.size(); i++) {
            sb.append("- ").append(memories.get(i)).append("\n");
        }

        File claudeMd = new File(dataDir + "/sessions/" + conversationId + "/CLAUDE.md");
        try {
            Files.writeString(claudeMd.toPath(), sb.toString());
            log.info("Injected {} memories into {}", memories.size(), claudeMd);
        } catch (IOException e) {
            log.error("Failed to write CLAUDE.md for memory injection", e);
        }
    }

    /**
     * Async trigger: extract key memories from the conversation after response.
     */
    public void triggerMemoryExtract(String conversationId) {
        memoryExtractExecutor.submit(() -> {
            try {
                // Small delay to let Claude Code flush JSONL to disk
                Thread.sleep(500);
                JsonlData data = readJsonl(conversationId);
                if (data.messages.isEmpty()) {
                    log.warn("Memory extract: no messages found in JSONL for {}", conversationId);
                    return;
                }

                // Build conversation text for extraction
                StringBuilder convText = new StringBuilder();
                for (MessageResponse msg : data.messages) {
                    convText.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
                }

                // Use Claude to extract key memories
                String partialConv = convText.toString();
                if (partialConv.length() > 4000) {
                    partialConv = partialConv.substring(partialConv.length() - 4000);
                }

                String extractPrompt = "请从以下对话片段中提取1-3条关键信息，用于将来的记忆召回。" +
                        "每条信息用一句话概括（不超过40字），以JSON数组格式返回。\n" +
                        "只返回JSON数组，不要其他文字。\n\n" + partialConv;

                String sessionDir = dataDir + "/sessions/" + conversationId;

                ProcessBuilder pb = new ProcessBuilder(
                        claudeBin, "-p",
                        "--bare",
                        "--output-format", "json"
                );
                pb.directory(new File(sessionDir));
                Process p = pb.start();

                // Write prompt via stdin to avoid newline-in-argument issue on Windows
                p.getOutputStream().write(extractPrompt.getBytes());
                p.getOutputStream().close();

                String output = new String(p.getInputStream().readAllBytes());
                p.waitFor();
                p.destroy();

                log.info("Memory extract convText ({} chars, first 300): {}",
                        partialConv.length(),
                        partialConv.length() > 300 ? partialConv.substring(0, 300) : partialConv);
                log.info("Memory extract raw output (first 400): {}",
                        output.substring(0, Math.min(400, output.length())));

                // --output-format json wraps output in a result object
                JsonNode wrapper = objectMapper.readTree(output);
                String resultText = wrapper.path("result").asText("");

                if (resultText.isEmpty()) {
                    log.warn("Memory extract: empty result field");
                    return;
                }

                // Parse the extracted JSON array from result text
                String jsonStr = resultText.trim();
                if (jsonStr.startsWith("```")) {
                    jsonStr = jsonStr.replaceAll("```json?\\s*", "").replaceAll("```\\s*", "");
                }
                if (!jsonStr.startsWith("[")) {
                    log.warn("Memory extract: Claude did not return JSON array, got: {}",
                            resultText.length() > 200 ? resultText.substring(0, 200) : resultText);
                    return;
                }
                JsonNode arr = objectMapper.readTree(jsonStr);
                if (arr.isArray()) {
                    for (JsonNode item : arr) {
                        memoryService.saveMemory(item.asText(), conversationId);
                    }
                }
            } catch (Exception e) {
                log.error("Memory extraction failed for {}", conversationId, e);
            }
        });
    }

    public String getSessionDir(String conversationId) {
        return dataDir + "/sessions/" + conversationId;
    }

    public boolean isFirstMessage(String conversationId) {
        return !new File(jsonlPath(conversationId)).exists();
    }

    private String jsonlPath(String conversationId) {
        try {
            String sessionDir = dataDir + "/sessions/" + conversationId;
            String normalized = new File(sessionDir).getCanonicalPath();
            String projectName = normalized
                    .replace(":\\", "--")
                    .replace("\\", "-");
            return claudeProjectsDir + "/" + projectName + "/" + conversationId + ".jsonl";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<ConversationListResponse> listConversations() {
        File sessionsDir = new File(dataDir + "/sessions");
        if (!sessionsDir.exists()) return List.of();

        File[] dirs = sessionsDir.listFiles(File::isDirectory);
        if (dirs == null) return List.of();

        return Arrays.stream(dirs)
                .map(dir -> {
                    String id = dir.getName();
                    JsonlData data = readJsonl(id);
                    String title = data.title != null ? data.title : "新对话";
                    LocalDateTime createdAt = dirToTime(dir);
                    LocalDateTime updatedAt = data.updatedAt != null ? data.updatedAt : createdAt;
                    return new ConversationListResponse(id, title, createdAt, updatedAt, data.messageCount);
                })
                .sorted(Comparator.comparing(ConversationListResponse::getUpdatedAt).reversed())
                .toList();
    }

    public ConversationDetailResponse getConversation(String id) {
        JsonlData data = readJsonl(id);
        if (data.messages.isEmpty() && data.title == null) {
            File dir = new File(dataDir + "/sessions/" + id);
            if (!dir.exists()) throw new RuntimeException("会话不存在");
            return new ConversationDetailResponse(id, "新对话", List.of(),
                    dirToTime(dir), dirToTime(dir));
        }

        return new ConversationDetailResponse(id,
                data.title != null ? data.title : "新对话",
                data.messages,
                data.createdAt != null ? data.createdAt : LocalDateTime.now(),
                data.updatedAt != null ? data.updatedAt : LocalDateTime.now());
    }

    public void deleteConversation(String id) {
        String jsonlDir = jsonlPath(id).replace("/" + id + ".jsonl", "");
        deleteDir(new File(jsonlDir));
        File dir = new File(dataDir + "/sessions/" + id);
        if (dir.exists()) deleteDir(dir);
    }

    private JsonlData readJsonl(String id) {
        String path = jsonlPath(id);
        File jsonlFile = new File(path);
        if (!jsonlFile.exists()) {
            log.warn("readJsonl: file not found: {}", path);
            return new JsonlData();
        }

        JsonlData result = new JsonlData();
        result.createdAt = fileToTime(jsonlFile);
        result.updatedAt = fileToTime(jsonlFile);
        long msgSeq = 1;
        log.info("readJsonl: {}, size={} bytes", path, jsonlFile.length());

        try (BufferedReader reader = new BufferedReader(new FileReader(jsonlFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonNode node = objectMapper.readTree(line);
                    String type = node.path("type").asText();

                    if ("user".equals(type)) {
                        String content = node.path("message").path("content").asText("");
                        if (result.title == null && !content.isEmpty()) {
                            result.title = content.length() > 30
                                    ? content.substring(0, 30) + "..."
                                    : content;
                        }
                        result.messages.add(new MessageResponse(msgSeq++, "user", content,
                                parseTimestamp(node.path("timestamp").asText(null))));
                        result.messageCount++;

                    } else if ("assistant".equals(type)) {
                        JsonNode content = node.path("message").path("content");
                        StringBuilder text = new StringBuilder();
                        if (content.isArray()) {
                            for (JsonNode item : content) {
                                if ("text".equals(item.path("type").asText())) {
                                    text.append(item.path("text").asText(""));
                                }
                            }
                        }
                        if (!text.isEmpty()) {
                            result.messages.add(new MessageResponse(msgSeq++, "assistant",
                                    text.toString(),
                                    parseTimestamp(node.path("timestamp").asText(null))));
                            result.messageCount++;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skip unparsable JSONL line: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read JSONL for session {}", id, e);
        }
        return result;
    }

    private LocalDateTime parseTimestamp(String ts) {
        if (ts == null) return LocalDateTime.now();
        try {
            return Instant.parse(ts).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private LocalDateTime fileToTime(File f) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault());
    }

    private LocalDateTime dirToTime(File dir) {
        return fileToTime(dir);
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) deleteDir(f);
        dir.delete();
    }

    private static class JsonlData {
        String title;
        long messageCount;
        LocalDateTime createdAt;
        LocalDateTime updatedAt;
        List<MessageResponse> messages = new ArrayList<>();
    }
}
