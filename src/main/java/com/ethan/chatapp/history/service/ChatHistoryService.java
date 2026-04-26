package com.ethan.chatapp.history.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.chatapp.history.dto.SessionTokenDto;
import com.ethan.chatapp.history.dto.TokenStatsDto;
import com.ethan.chatapp.history.entity.ChatMessage;
import com.ethan.chatapp.history.entity.ChatSession;

import java.util.List;

public interface ChatHistoryService {
    // 创建一个新会话
    ChatSession createSession(String title);

    // 获取会话信息
    ChatSession getSessionById(String sessionId);

    // 获取左侧侧边栏的所有历史会话（按最后更新时间倒序）
    List<ChatSession> getAllSessions();

    // 获取某个会话下的所有历史消息（按时间正序）
    List<ChatMessage> getMessagesBySessionId(String sessionId);

    // 保存一轮对话（用户的问题 + AI的回答），并更新会话的最后活跃时间
    void saveMessagePair(String sessionId, String userContent, String assistantContent, String reasoningContent);

    // 保存一轮对话并累加 token
    void saveMessagePair(String sessionId, String userContent, String assistantContent, String reasoningContent, Integer tokens);

    // 删除某个会话及其关联的所有消息
    void deleteSession(String sessionId);

    Page<ChatSession> searchSessions(String keyword, int current, int size);

    // 截断消息：删除某个会话中指定消息及其之后的所有记录
    void deleteMessagesFrom(String sessionId, String messageId);

    // 获取 token 统计信息
    TokenStatsDto getTokenStats();

    // 获取各会话 token 排行
    List<SessionTokenDto> getTopSessionsByTokens(int limit);
}