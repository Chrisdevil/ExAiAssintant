package com.example.exaiassistant.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int MAX_LENGTH = 512;

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private boolean ready = false;

    @Value("${embedding.model-path:classpath:embedding/model.onnx}")
    private Resource modelResource;

    @Value("${embedding.tokenizer-path:classpath:embedding/}")
    private Resource tokenizerResource;

    @PostConstruct
    public void init() throws Exception {
        env = OrtEnvironment.getEnvironment();
        session = env.createSession(modelResource.getContentAsByteArray(),
                new OrtSession.SessionOptions());
        Path tokPath = tokenizerResource.getFile().toPath();
        tokenizer = HuggingFaceTokenizer.newInstance(tokPath,
                Map.of("padding", "max_length", "maxLength", String.valueOf(MAX_LENGTH), "truncation", "longest_first"));
        ready = true;
        log.info("EmbeddingService loaded. inputs={}, outputs={}",
                session.getInputNames(), session.getOutputNames());
    }

    public boolean isReady() {
        return ready;
    }

    public float[] embed(String text) {
        if (!ready) return new float[0];

        Encoding encoding = tokenizer.encode(text);
        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        long[] tokenTypeIds = encoding.getTypeIds();

        int seqLen = inputIds.length;
        try (var inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), new long[]{1, seqLen});
             var maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), new long[]{1, seqLen});
             var typeIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), new long[]{1, seqLen})) {

            var inputNames = new java.util.ArrayList<>(session.getInputNames());
            var result = session.run(Map.of(
                    inputNames.get(0), inputIdsTensor,
                    inputNames.get(1), maskTensor,
                    inputNames.get(2), typeIdsTensor
            ));

            float[] embedding = meanPool((float[][][]) result.get(0).getValue(), attentionMask);
            result.close();
            return l2Normalize(embedding);
        } catch (OrtException e) {
            log.error("Embedding inference failed", e);
            return new float[0];
        }
    }

    private float[] meanPool(float[][][] lastHidden, long[] mask) {
        int seqLen = lastHidden[0].length;
        int dim = lastHidden[0][0].length;
        float[] pooled = new float[dim];
        float maskSum = 0;
        for (int i = 0; i < seqLen; i++) {
            float m = mask[i];
            if (m > 0) {
                maskSum += m;
                for (int j = 0; j < dim; j++) {
                    pooled[j] += lastHidden[0][i][j] * m;
                }
            }
        }
        if (maskSum > 0) {
            for (int j = 0; j < dim; j++) pooled[j] /= maskSum;
        }
        return pooled;
    }

    private float[] l2Normalize(float[] vec) {
        float sum = 0;
        for (float v : vec) sum += v * v;
        float norm = (float) Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < vec.length; i++) vec[i] /= norm;
        }
        return vec;
    }
}
