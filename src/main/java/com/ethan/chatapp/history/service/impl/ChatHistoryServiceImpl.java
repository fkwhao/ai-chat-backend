package com.ethan.chatapp.history.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.chatapp.history.dto.SessionTokenDto;
import com.ethan.chatapp.history.dto.TokenStatsDto;
import com.ethan.chatapp.history.entity.ChatMessage;
import com.ethan.chatapp.history.entity.ChatSession;
import com.ethan.chatapp.history.mapper.ChatMessageMapper;
import com.ethan.chatapp.history.mapper.ChatSessionMapper;
import com.ethan.chatapp.history.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
        LocalDateTime now = LocalDateTime.now();

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userContent);
        userMsg.setCreateTime(now);
        messageMapper.insert(userMsg);

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(assistantContent);
        aiMsg.setReasoningContent(reasoningContent);
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

        // 总 token 数
        List<ChatSession> sessions = sessionMapper.selectList(null);
        long totalTokens = sessions.stream()
                .mapToLong(s -> s.getTotalTokens() != null ? s.getTotalTokens() : 0)
                .sum();
        stats.setTotalTokens(totalTokens);

        // 总会话数
        stats.setTotalSessions(sessions.size());

        // 总消息数
        Long totalMessages = messageMapper.selectCount(null);
        stats.setTotalMessages(totalMessages != null ? totalMessages : 0);

        // 平均每会话 token
        if (sessions.size() > 0) {
            stats.setAvgTokensPerSession((double) totalTokens / sessions.size());
        } else {
            stats.setAvgTokensPerSession(0.0);
        }

        return stats;
    }

    @Override
    public List<SessionTokenDto> getTopSessionsByTokens(int limit) {
        List<ChatSession> sessions = sessionMapper.selectList(
                new QueryWrapper<ChatSession>()
                        .orderByDesc("total_tokens")
                        .last("LIMIT " + limit)
        );

        List<SessionTokenDto> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (ChatSession session : sessions) {
            SessionTokenDto dto = new SessionTokenDto();
            dto.setSessionId(session.getId());
            dto.setTitle(session.getTitle());
            dto.setTotalTokens(session.getTotalTokens() != null ? session.getTotalTokens() : 0);
            if (session.getUpdateTime() != null) {
                dto.setUpdateTime(session.getUpdateTime().format(formatter));
            }
            result.add(dto);
        }

        return result;
    }
}
