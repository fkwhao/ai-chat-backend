package com.ethan.chatapp.history.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.chatapp.history.dto.*;
import com.ethan.chatapp.history.entity.ChatMessage;
import com.ethan.chatapp.history.entity.ChatSession;
import com.ethan.chatapp.history.mapper.ChatMessageMapper;
import com.ethan.chatapp.history.mapper.ChatSessionMapper;
import com.ethan.chatapp.history.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    @Override
    public ChatSession createSession(String title) {
        ChatSession session = new ChatSession();
        session.setTitle(title == null || title.trim().isEmpty() ? "新对话" : title);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        session.setTotalTokens(0);
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public ChatSession getSessionById(String sessionId) {
        return sessionMapper.selectById(sessionId);
    }

    @Override
    public List<ChatSession> getAllSessions() {
        return sessionMapper.selectList(
                new QueryWrapper<ChatSession>().orderByDesc("update_time")
        );
    }

    @Override
    public List<ChatMessage> getMessagesBySessionId(String sessionId) {
        return messageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByAsc("create_time")
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMessagePair(String sessionId, String userContent, String assistantContent, String reasoningContent) {
        saveMessagePair(sessionId, userContent, assistantContent, reasoningContent, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMessagePair(String sessionId, String userContent, String assistantContent, String reasoningContent, Integer tokens) {
        saveMessagePair(sessionId, userContent, assistantContent, reasoningContent, tokens, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMessagePair(String sessionId, String userContent, String assistantContent, String reasoningContent, Integer tokens, String model) {
        LocalDateTime now = LocalDateTime.now();

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userContent);
        userMsg.setModel(model);
        userMsg.setCreateTime(now);
        messageMapper.insert(userMsg);

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(assistantContent);
        aiMsg.setReasoningContent(reasoningContent);
        aiMsg.setModel(model);
        aiMsg.setCreateTime(now.plusNanos(1000));
        messageMapper.insert(aiMsg);

        ChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdateTime(now);
            if ("新对话".equals(session.getTitle()) && userContent != null) {
                String newTitle = userContent.length() > 15 ? userContent.substring(0, 15) + "..." : userContent;
                session.setTitle(newTitle);
            }
            if (tokens != null && tokens > 0) {
                int currentTokens = session.getTotalTokens() != null ? session.getTotalTokens() : 0;
                session.setTotalTokens(currentTokens + tokens);
            }
            if (model != null && !model.isEmpty()) {
                session.setModel(model);
            }
            sessionMapper.updateById(session);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId) {
        messageMapper.delete(new QueryWrapper<ChatMessage>().eq("session_id", sessionId));
        sessionMapper.deleteById(sessionId);
    }

    @Override
    public Page<ChatSession> searchSessions(String keyword, int current, int size) {
        Page<ChatSession> page = new Page<>(current, size);
        QueryWrapper<ChatSession> queryWrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.like("title", keyword);
        }
        queryWrapper.orderByDesc("update_time");
        return sessionMapper.selectPage(page, queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessagesFrom(String sessionId, String messageId) {
        ChatMessage targetMsg = messageMapper.selectById(messageId);
        if (targetMsg != null && targetMsg.getSessionId().equals(sessionId)) {
            messageMapper.delete(new QueryWrapper<ChatMessage>()
                    .eq("session_id", sessionId)
                    .ge("create_time", targetMsg.getCreateTime()));
        }
    }

    @Override
    public TokenStatsDto getTokenStats() {
        TokenStatsDto stats = new TokenStatsDto();
        List<ChatSession> sessions = sessionMapper.selectList(null);
        long totalTokens = sessions.stream().mapToLong(s -> s.getTotalTokens() != null ? s.getTotalTokens() : 0).sum();
        stats.setTotalTokens(totalTokens);
        stats.setTotalSessions(sessions.size());
        Long totalMessages = messageMapper.selectCount(null);
        stats.setTotalMessages(totalMessages != null ? totalMessages : 0);
        stats.setAvgTokensPerSession(sessions.size() > 0 ? (double) totalTokens / sessions.size() : 0.0);
        return stats;
    }

    @Override
    public List<SessionTokenDto> getTopSessionsByTokens(int limit) {
        List<ChatSession> sessions = sessionMapper.selectList(
                new QueryWrapper<ChatSession>().orderByDesc("total_tokens").last("LIMIT " + limit));
        List<SessionTokenDto> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (ChatSession session : sessions) {
            SessionTokenDto dto = new SessionTokenDto();
            dto.setSessionId(session.getId());
            dto.setTitle(session.getTitle());
            dto.setTotalTokens(session.getTotalTokens() != null ? session.getTotalTokens() : 0);
            if (session.getUpdateTime() != null) dto.setUpdateTime(session.getUpdateTime().format(formatter));
            result.add(dto);
        }
        return result;
    }

    // ══════════════════════════════════════
    // 新增：使用统计
    // ══════════════════════════════════════

    private LocalDateTime daysAgo(int days) {
        return LocalDateTime.now().minusDays(days).withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    @Override
    public UsageOverviewDto getUsageOverview(int days) {
        UsageOverviewDto dto = new UsageOverviewDto();
        LocalDateTime since = daysAgo(days);

        // 时间范围内的会话
        List<ChatSession> sessions = sessionMapper.selectList(
                new QueryWrapper<ChatSession>().ge("update_time", since));
        long totalTokens = sessions.stream().mapToLong(s -> s.getTotalTokens() != null ? s.getTotalTokens() : 0).sum();
        dto.setTotalTokens(totalTokens);
        dto.setTotalSessions(sessions.size());

        // 消息数
        Long totalMsgs = messageMapper.selectCount(
                new QueryWrapper<ChatMessage>().ge("create_time", since));
        dto.setTotalMessages(totalMsgs != null ? totalMsgs : 0);

        // 活跃天数 + 连续天数
        List<ChatMessage> recentMessages = messageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .ge("create_time", since)
                        .orderByAsc("create_time"));
        Set<LocalDate> activeDateSet = new LinkedHashSet<>();
        for (ChatMessage msg : recentMessages) {
            if (msg.getCreateTime() != null) {
                activeDateSet.add(msg.getCreateTime().toLocalDate());
            }
        }
        dto.setActiveDays(activeDateSet.size());

        // 连续天数（从今天往回数）
        int streak = 0;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            if (activeDateSet.contains(today.minusDays(i))) {
                streak++;
            } else if (i == 0) {
                // 今天没活跃，检查昨天
                continue;
            } else {
                break;
            }
        }
        // 修正：如果今天没活跃但从昨天开始有连续
        if (streak == 0 && activeDateSet.contains(today.minusDays(1))) {
            for (int i = 1; i < days; i++) {
                if (activeDateSet.contains(today.minusDays(i))) streak++;
                else break;
            }
        }
        dto.setCurrentStreak(streak);

        // 最常用模型
        Map<String, Long> modelTokens = sessions.stream()
                .filter(s -> s.getModel() != null && !s.getModel().isEmpty())
                .collect(Collectors.groupingBy(ChatSession::getModel,
                        Collectors.summingLong(s -> s.getTotalTokens() != null ? s.getTotalTokens() : 0)));
        if (!modelTokens.isEmpty()) {
            Map.Entry<String, Long> top = modelTokens.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).get();
            dto.setMostUsedModel(top.getKey());
            dto.setMostUsedModelPercentage(totalTokens > 0 ? (top.getValue() * 100.0 / totalTokens) : 0);
        }

        return dto;
    }

    @Override
    public List<ModelBreakdownDto> getModelBreakdown(int days) {
        LocalDateTime since = daysAgo(days);
        List<ChatSession> sessions = sessionMapper.selectList(
                new QueryWrapper<ChatSession>().ge("update_time", since));
        long totalTokens = sessions.stream().mapToLong(s -> s.getTotalTokens() != null ? s.getTotalTokens() : 0).sum();

        Map<String, Long> modelTokens = sessions.stream()
                .filter(s -> s.getModel() != null && !s.getModel().isEmpty())
                .collect(Collectors.groupingBy(ChatSession::getModel,
                        Collectors.summingLong(s -> s.getTotalTokens() != null ? s.getTotalTokens() : 0)));

        return modelTokens.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    ModelBreakdownDto dto = new ModelBreakdownDto();
                    dto.setModel(e.getKey());
                    dto.setTokens(e.getValue());
                    dto.setPercentage(totalTokens > 0 ? (e.getValue() * 100.0 / totalTokens) : 0);
                    return dto;
                }).collect(Collectors.toList());
    }

    @Override
    public List<DailyTrendDto> getDailyTrend(int days) {
        LocalDateTime since = daysAgo(days);
        List<ChatSession> sessions = sessionMapper.selectList(
                new QueryWrapper<ChatSession>().ge("update_time", since));

        // 按 (日期, 模型) 聚合 token（使用会话的 update_time 近似）
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<DailyTrendDto> result = new ArrayList<>();
        for (ChatSession s : sessions) {
            if (s.getUpdateTime() == null) continue;
            String date = s.getUpdateTime().format(dateFmt);
            String model = s.getModel() != null ? s.getModel() : "unknown";
            long tokens = s.getTotalTokens() != null ? s.getTotalTokens() : 0;
            DailyTrendDto dto = new DailyTrendDto();
            dto.setDate(date);
            dto.setModel(model);
            dto.setTokens(tokens);
            result.add(dto);
        }
        return result;
    }

    @Override
    public List<ActivityHeatmapDto> getActivityHeatmap(int days) {
        LocalDateTime since = daysAgo(days);
        List<ChatMessage> messages = messageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .ge("create_time", since)
                        .orderByAsc("create_time"));
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Integer> dateCount = new LinkedHashMap<>();
        for (ChatMessage msg : messages) {
            if (msg.getCreateTime() != null) {
                String date = msg.getCreateTime().format(dateFmt);
                dateCount.merge(date, 1, Integer::sum);
            }
        }
        return dateCount.entrySet().stream().map(e -> {
            ActivityHeatmapDto dto = new ActivityHeatmapDto();
            dto.setDate(e.getKey());
            dto.setCount(e.getValue());
            return dto;
        }).collect(Collectors.toList());
    }
}
