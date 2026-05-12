package com.example.exaiassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;

@Service
public class ClaudeProcessService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProcessService.class);

    private final String claudeBin;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor;

    public ClaudeProcessService(@Value("${claude.bin:claude}") String claudeBin,
                                 ExecutorService embeddingExecutor) {
        this.claudeBin = claudeBin;
        this.executor = embeddingExecutor;
    }

    public Flux<String> startSession(String sessionId, String sessionDir, String prompt) {
        return run("--session-id", sessionId, sessionDir, prompt);
    }

    public Flux<String> resumeSession(String sessionId, String sessionDir, String prompt) {
        return run("--resume", sessionId, sessionDir, prompt);
    }

    private Flux<String> run(String sessionFlag, String sessionId, String sessionDir, String prompt) {
        return Flux.create(sink -> {
            executor.submit(() -> {
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                            claudeBin, "-p",
                            sessionFlag, sessionId,
                            "--output-format", "stream-json",
                            "--include-partial-messages",
                            "--verbose",
                            prompt
                    );
                    pb.directory(new File(sessionDir));
                    pb.redirectErrorStream(false);
                    Process process = pb.start();

                    ByteArrayOutputStream stderrCapture = new ByteArrayOutputStream();
                    executor.submit(() -> {
                        try {
                            process.getErrorStream().transferTo(stderrCapture);
                        } catch (IOException ignored) {}
                    });

                    readLines(process, sink);
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        String errMsg = stderrCapture.toString().trim();
                        log.error("Claude exited code={}, stderr: {}", exitCode, errMsg);
                        sink.next("[ERROR] " + (errMsg.isEmpty() ? "exit=" + exitCode : errMsg));
                    }
                    sink.complete();
                } catch (Exception e) {
                    log.error("Claude process failed", e);
                    sink.error(e);
                }
            });
        });
    }

    private void readLines(Process process, FluxSink<String> sink) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            boolean hasStreamingText = false;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                JsonNode node;
                try {
                    node = objectMapper.readTree(line);
                } catch (Exception e) {
                    continue;
                }
                String type = node.path("type").asText();

                // stream_event: text_delta — real streaming
                if ("stream_event".equals(type)) {
                    JsonNode event = node.path("event");
                    if ("content_block_delta".equals(event.path("type").asText())) {
                        JsonNode delta = event.path("delta");
                        if ("text_delta".equals(delta.path("type").asText())) {
                            String text = delta.path("text").asText(null);
                            if (text != null) {
                                hasStreamingText = true;
                                sink.next(text);
                            }
                        }
                    }
                }

                // result event: fallback when no streaming text was received
                if ("result".equals(type) && !hasStreamingText) {
                    String text = node.path("result").asText(null);
                    if (text != null && !text.isEmpty()) {
                        sink.next(text);
                    }
                }
            }
        }
    }
}
