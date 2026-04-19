package com.ethan.chatapp.history.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.chatapp.history.entity.ChatMessage;
import com.ethan.chatapp.history.entity.ChatSession;
import com.ethan.chatapp.history.mapper.ChatMessageMapper;
import com.ethan.chatapp.history.mapper.ChatSessionMapper;
import com.ethan.chatapp.history.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    @Override
    public ChatSession createSession(String title) {
        ChatSession session = new ChatSession();
        // 如果标题为空，默认给个新对话
        session.setTitle(title == null || title.trim().isEmpty() ? "新对话" : title);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public List<ChatSession> getAllSessions() {
        // 按照 update_time 倒序排列，最新聊过的在最上面
        return sessionMapper.selectList(
                new QueryWrapper<ChatSession>().orderByDesc("update_time")
        );
    }

    @Override
    public List<ChatMessage> getMessagesBySessionId(String sessionId) {
        // 按照 create_time 正序排列，还原对话流
        return messageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByAsc("create_time")
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // 开启事务，保证两条消息同生共死
    public void saveMessagePair(String sessionId, String userContent, String assistantContent, String reasoningContent) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 保存用户的提问
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userContent);
        userMsg.setCreateTime(now);
        messageMapper.insert(userMsg);

        // 2. 保存 AI 的回答（稍晚一毫秒，确保排序绝对正确）
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(assistantContent);
        aiMsg.setReasoningContent(reasoningContent);
        aiMsg.setCreateTime(now.plusNanos(1000)); 
        messageMapper.insert(aiMsg);

        // 3. 更新会话的最后活跃时间，并根据用户的第一句话自动重命名会话
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdateTime(now);
            // 如果是“新对话”，用用户的第一句话作为标题（截取前15个字）
            if ("新对话".equals(session.getTitle()) && userContent != null) {
                String newTitle = userContent.length() > 15 ? userContent.substring(0, 15) + "..." : userContent;
                session.setTitle(newTitle);
            }
            sessionMapper.updateById(session);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId) {
        // 先删消息明细，再删会话主表
        messageMapper.delete(new QueryWrapper<ChatMessage>().eq("session_id", sessionId));
        sessionMapper.deleteById(sessionId);
    }

    @Override
    public Page<ChatSession> searchSessions(String keyword, int current, int size) {
        Page<ChatSession> page = new Page<>(current, size);
        QueryWrapper<ChatSession> queryWrapper = new QueryWrapper<>();

        // 如果输入了搜索词，就走数据库的 LIKE 模糊查询
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.like("title", keyword);
        }
        // 依然按照最后更新时间倒序
        queryWrapper.orderByDesc("update_time");

        return sessionMapper.selectPage(page, queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessagesFrom(String sessionId, String messageId) {
        // 1. 先查出消息，获取它的创建时间
        ChatMessage targetMsg = messageMapper.selectById(messageId);

        // 2. 确保消息存在，且确实属于当前会话防越权
        if (targetMsg != null && targetMsg.getSessionId().equals(sessionId)) {
            // 3. 删除该会话下，创建时间 >= 该消息创建时间的所有记录
            messageMapper.delete(new QueryWrapper<ChatMessage>()
                    .eq("session_id", sessionId)
                    .ge("create_time", targetMsg.getCreateTime()));
        }
    }
}