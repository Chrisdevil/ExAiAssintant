package com.example.exaiassistant.service;

import com.example.exaiassistant.mapper.MemoryMapper;
import com.example.exaiassistant.model.Memory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    private static final double SIMILARITY_THRESHOLD = 0.75;
    private static final double DUPLICATE_THRESHOLD = 0.92;

    private final MemoryMapper memoryMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MemoryService(MemoryMapper memoryMapper, EmbeddingService embeddingService) {
        this.memoryMapper = memoryMapper;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public void saveMemory(String content, String conversationId) {
        log.info("saveMemory called: {} (embedding ready={})", content, embeddingService.isReady());
        if (!embeddingService.isReady()) return;

        List<Memory> existing = memoryMapper.selectAll();
        log.info("Existing memories count: {}", existing.size());

        float[] newEmb = embeddingService.embed(content);
        if (newEmb.length == 0) {
            log.warn("Embedding returned empty for: {}", content);
            return;
        }

        for (Memory m : existing) {
            float[] emb = parseEmbedding(m.getEmbedding());
            if (emb.length > 0) {
                double sim = cosineSimilarity(newEmb, emb);
                if (sim > DUPLICATE_THRESHOLD) {
                    log.info("Skipping duplicate memory (sim={}): {}", sim, content);
                    return;
                }
            }
        }

        Memory mem = new Memory();
        mem.setContent(content);
        mem.setEmbedding(toJson(newEmb));
        mem.setConversationId(conversationId);
        mem.setCreatedAt(LocalDateTime.now());
        memoryMapper.insert(mem);
        log.info("Saved memory: {}", content);
    }

    public List<String> searchSimilar(String query, int topK) {
        if (!embeddingService.isReady()) {
            return keywordFallback(query, topK);
        }

        float[] queryEmb = embeddingService.embed(query);
        if (queryEmb.length == 0) return List.of();

        List<Memory> all = memoryMapper.selectAll();
        if (all.isEmpty()) return List.of();

        // Compute similarity with time decay
        PriorityQueue<ScoredMemory> heap = new PriorityQueue<>(
                Comparator.comparingDouble(ScoredMemory::score));
        long now = System.currentTimeMillis();

        for (Memory m : all) {
            float[] emb = parseEmbedding(m.getEmbedding());
            if (emb.length == 0) continue;
            double rawSim = cosineSimilarity(queryEmb, emb);
            // Time decay: 30-day half-life
            long ageMs = now - m.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            double decay = Math.exp(-0.0231 * ageMs / (24.0 * 3600 * 1000)); // ln(2)/30 days
            double score = rawSim * decay;

            if (score > SIMILARITY_THRESHOLD) {
                heap.offer(new ScoredMemory(m.getContent(), score));
                if (heap.size() > topK) heap.poll();
            }
        }

        List<String> result = new ArrayList<>();
        while (!heap.isEmpty()) result.add(heap.poll().content);
        Collections.reverse(result);
        return result;
    }

    private List<String> keywordFallback(String query, int topK) {
        List<Memory> all = memoryMapper.selectAll();
        return all.stream()
                .sorted(Comparator.comparing(Memory::getCreatedAt).reversed())
                .limit(topK)
                .map(Memory::getContent)
                .toList();
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private float[] parseEmbedding(String json) {
        try {
            double[] arr = objectMapper.readValue(json, double[].class);
            float[] result = new float[arr.length];
            for (int i = 0; i < arr.length; i++) result[i] = (float) arr[i];
            return result;
        } catch (JsonProcessingException e) {
            return new float[0];
        }
    }

    private String toJson(float[] emb) {
        try {
            return objectMapper.writeValueAsString(emb);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private record ScoredMemory(String content, double score) {}
}
