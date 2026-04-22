package com.ethan.chatapp.rag.service.impl;

import com.ethan.chatapp.rag.dto.RagDocumentDto;
import com.ethan.chatapp.rag.dto.RagSearchResultDto;
import com.ethan.chatapp.rag.service.RagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagServiceImpl implements RagService {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 80;
    private static final int MAX_CHUNK_SCAN_DEFAULT = 2000;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]|[a-z0-9]{2,}");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rag.max-chunks-scan:" + MAX_CHUNK_SCAN_DEFAULT + "}")
    private int maxChunksScan;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String ingestDocument(String title, String source, String content, Integer chunkSize, Integer chunkOverlap) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content can not be empty");
        }

        int actualChunkSize = (chunkSize == null || chunkSize < 100) ? DEFAULT_CHUNK_SIZE : chunkSize;
        int actualOverlap = (chunkOverlap == null || chunkOverlap < 0) ? DEFAULT_CHUNK_OVERLAP : chunkOverlap;
        if (actualOverlap >= actualChunkSize) {
            actualOverlap = actualChunkSize / 2;
        }

        String documentId = UUID.randomUUID().toString();
        String safeTitle = (title == null || title.isBlank()) ? "Untitled Document" : title.trim();
        String safeSource = (source == null || source.isBlank()) ? null : source.trim();

        jdbcTemplate.update(
                "INSERT INTO rag_document(id, title, source, content, create_time) VALUES (?, ?, ?, ?, ?)",
                documentId, safeTitle, safeSource, content, LocalDateTime.now()
        );

        List<String> chunks = splitIntoChunks(content, actualChunkSize, actualOverlap);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            Map<String, Integer> tokenTf = buildTokenTf(chunk);
            if (tokenTf.isEmpty()) {
                continue;
            }
            int tokenCount = tokenTf.values().stream().mapToInt(Integer::intValue).sum();
            String vectorJson = toJson(tokenTf);
            jdbcTemplate.update(
                    "INSERT INTO rag_chunk(id, document_id, chunk_index, content, token_vector_json, token_count, create_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), documentId, i, chunk, vectorJson, tokenCount, LocalDateTime.now()
            );

            Set<String> uniqueTokens = tokenTf.keySet();
            for (String token : uniqueTokens) {
                jdbcTemplate.update(
                        "INSERT INTO rag_token_stats(token, doc_freq) VALUES (?, 1) " +
                                "ON CONFLICT(token) DO UPDATE SET doc_freq = doc_freq + 1",
                        token
                );
            }
        }
        return documentId;
    }

    @Override
    public List<RagSearchResultDto> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        int actualTopK = topK <= 0 ? 3 : Math.min(topK, 10);

        Map<String, Integer> queryTf = buildTokenTf(query);
        if (queryTf.isEmpty()) {
            return Collections.emptyList();
        }

        long totalChunks = Optional.ofNullable(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM rag_chunk", Long.class)).orElse(0L);
        if (totalChunks == 0) {
            return Collections.emptyList();
        }

        Map<String, Integer> queryDf = loadTokenDf(queryTf.keySet());
        Map<String, Double> queryWeighted = new HashMap<>();
        for (Map.Entry<String, Integer> e : queryTf.entrySet()) {
            String token = e.getKey();
            int tf = e.getValue();
            int df = queryDf.getOrDefault(token, 0);
            double idf = idf(totalChunks, df);
            queryWeighted.put(token, tf * idf);
        }
        double queryNorm = l2Norm(queryWeighted.values());
        if (queryNorm == 0D) {
            return Collections.emptyList();
        }

        List<ChunkRow> candidates = jdbcTemplate.query(
                "SELECT c.document_id, d.title, c.chunk_index, c.content, c.token_vector_json " +
                        "FROM rag_chunk c JOIN rag_document d ON c.document_id = d.id " +
                        "ORDER BY c.create_time DESC LIMIT ?",
                (rs, rowNum) -> mapChunkRow(rs),
                maxChunksScan
        );

        PriorityQueue<ScoredChunk> heap = new PriorityQueue<>(Comparator.comparingDouble(ScoredChunk::score));
        for (ChunkRow row : candidates) {
            Map<String, Integer> chunkTf = fromJson(row.tokenVectorJson());
            if (chunkTf.isEmpty()) {
                continue;
            }

            double dot = 0D;
            double chunkNormSq = 0D;
            for (Map.Entry<String, Integer> queryToken : queryTf.entrySet()) {
                String token = queryToken.getKey();
                int chunkTokenTf = chunkTf.getOrDefault(token, 0);
                int df = queryDf.getOrDefault(token, 0);
                double idf = idf(totalChunks, df);
                double chunkWeight = chunkTokenTf * idf;
                chunkNormSq += chunkWeight * chunkWeight;
                dot += queryWeighted.get(token) * chunkWeight;
            }
            double chunkNorm = Math.sqrt(chunkNormSq);
            if (chunkNorm == 0D) {
                continue;
            }
            double score = dot / (queryNorm * chunkNorm);
            if (score <= 0D) {
                continue;
            }
            ScoredChunk scored = new ScoredChunk(row, score);
            if (heap.size() < actualTopK) {
                heap.offer(scored);
            } else if (heap.peek() != null && score > heap.peek().score()) {
                heap.poll();
                heap.offer(scored);
            }
        }

        List<ScoredChunk> sorted = new ArrayList<>(heap);
        sorted.sort((a, b) -> Double.compare(b.score(), a.score()));
        return sorted.stream()
                .map(sc -> new RagSearchResultDto(
                        sc.row().documentId(),
                        sc.row().documentTitle(),
                        sc.row().chunkIndex(),
                        sc.row().content(),
                        sc.score()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public String buildContext(String query, int topK) {
        List<RagSearchResultDto> hits = search(query, topK);
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Use the following context when it is relevant. ");
        sb.append("If context conflicts with user intent, ask for clarification.\n\n");
        for (int i = 0; i < hits.size(); i++) {
            RagSearchResultDto hit = hits.get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append("title=").append(hit.getDocumentTitle());
            sb.append(", score=").append(String.format(Locale.US, "%.4f", hit.getScore())).append("\n");
            sb.append(hit.getChunkContent()).append("\n\n");
        }
        return sb.toString();
    }

    @Override
    public List<RagDocumentDto> listDocuments() {
        return jdbcTemplate.query(
                "SELECT d.id, d.title, d.source, d.create_time, COUNT(c.id) AS chunk_count " +
                        "FROM rag_document d LEFT JOIN rag_chunk c ON d.id = c.document_id " +
                        "GROUP BY d.id, d.title, d.source, d.create_time " +
                        "ORDER BY d.create_time DESC",
                (rs, rowNum) -> new RagDocumentDto(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("source"),
                        rs.getLong("chunk_count"),
                        rs.getString("create_time")
                )
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId can not be empty");
        }

        List<String> vectors = jdbcTemplate.query(
                "SELECT token_vector_json FROM rag_chunk WHERE document_id = ?",
                (rs, rowNum) -> rs.getString("token_vector_json"),
                documentId
        );

        Map<String, Integer> tokenReduce = new HashMap<>();
        for (String vector : vectors) {
            Map<String, Integer> tf = fromJson(vector);
            for (String token : tf.keySet()) {
                tokenReduce.put(token, tokenReduce.getOrDefault(token, 0) + 1);
            }
        }

        jdbcTemplate.update("DELETE FROM rag_chunk WHERE document_id = ?", documentId);
        jdbcTemplate.update("DELETE FROM rag_document WHERE id = ?", documentId);

        for (Map.Entry<String, Integer> e : tokenReduce.entrySet()) {
            jdbcTemplate.update(
                    "UPDATE rag_token_stats SET doc_freq = doc_freq - ? WHERE token = ?",
                    e.getValue(), e.getKey()
            );
        }
        jdbcTemplate.update("DELETE FROM rag_token_stats WHERE doc_freq <= 0");
    }

    private List<String> splitIntoChunks(String content, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int step = Math.max(1, chunkSize - overlap);
        while (start < content.length()) {
            int end = Math.min(content.length(), start + chunkSize);
            String chunk = content.substring(start, end).trim();
            if (!chunk.isBlank()) {
                result.add(chunk);
            }
            if (end == content.length()) {
                break;
            }
            start += step;
        }
        return result;
    }

    private Map<String, Integer> buildTokenTf(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        Map<String, Integer> tf = new HashMap<>();
        while (matcher.find()) {
            String token = matcher.group();
            tf.put(token, tf.getOrDefault(token, 0) + 1);
        }
        return tf;
    }

    private Map<String, Integer> loadTokenDf(Set<String> tokens) {
        if (tokens.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object> params = new ArrayList<>(tokens);
        String placeholders = String.join(",", Collections.nCopies(tokens.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT token, doc_freq FROM rag_token_stats WHERE token IN (" + placeholders + ")",
                params.toArray()
        );
        Map<String, Integer> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object token = row.get("token");
            Object docFreq = row.get("doc_freq");
            if (token != null && docFreq instanceof Number) {
                map.put(token.toString(), ((Number) docFreq).intValue());
            }
        }
        return map;
    }

    private double idf(long totalDocs, int docFreq) {
        return Math.log((totalDocs + 1.0) / (docFreq + 1.0)) + 1.0;
    }

    private double l2Norm(Collection<Double> values) {
        double sum = 0D;
        for (Double value : values) {
            if (value == null) {
                continue;
            }
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private String toJson(Map<String, Integer> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize token vector", e);
        }
    }

    private Map<String, Integer> fromJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Collections.emptyMap();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("failed to deserialize token vector: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private ChunkRow mapChunkRow(ResultSet rs) throws SQLException {
        return new ChunkRow(
                rs.getString("document_id"),
                rs.getString("title"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("token_vector_json")
        );
    }

    private record ChunkRow(
            String documentId,
            String documentTitle,
            Integer chunkIndex,
            String content,
            String tokenVectorJson
    ) {
    }

    private record ScoredChunk(ChunkRow row, double score) {
    }
}
