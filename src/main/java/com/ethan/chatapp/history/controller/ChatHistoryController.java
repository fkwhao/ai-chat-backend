package com.ethan.chatapp.history.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.chatapp.history.dto.MessagePairDto;
import com.ethan.chatapp.history.entity.ChatMessage;
import com.ethan.chatapp.history.entity.ChatSession;
import com.ethan.chatapp.history.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/history")
@CrossOrigin(origins = "*") // 允许 Vue 前端跨域调用
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    // 1. 创建新会话
    @PostMapping("/session")
    public ChatSession createSession(@RequestParam(required = false) String title) {
        return chatHistoryService.createSession(title);
    }

    // 2. 获取所有历史会话列表 (用于渲染侧边栏)
    @GetMapping("/sessions/page")
    public Page<ChatSession> getSessionsPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size) { // 每次查询15条
        return chatHistoryService.searchSessions(keyword, current, size);
    }

    // 3. 获取某个会话的所有消息 (用于点击侧边栏后渲染聊天窗口)
    @GetMapping("/session/{sessionId}/messages")
    public List<ChatMessage> getSessionMessages(@PathVariable String sessionId) {
        return chatHistoryService.getMessagesBySessionId(sessionId);
    }

    // 4. 删除会话
    @DeleteMapping("/session/{sessionId}")
    public String deleteSession(@PathVariable String sessionId) {
        chatHistoryService.deleteSession(sessionId);
        return "success";
    }

    // 5. 保存一轮对话（用户问 + AI答）
    @PostMapping("/session/{sessionId}/message-pair")
    public String saveMessagePair(
            @PathVariable String sessionId,
            @RequestBody MessagePairDto dto) {
        chatHistoryService.saveMessagePair(
                sessionId,
                dto.getUserContent(),
                dto.getAssistantContent(),
                dto.getReasoningContent(),
                dto.getTokens()
        );
        return "success";
    }

    // 6. 获取会话信息（包括 totalTokens）
    @GetMapping("/session/{sessionId}")
    public ChatSession getSession(@PathVariable String sessionId) {
        return chatHistoryService.getSessionById(sessionId);
    }

    // 6. 截断历史：删除某条消息及其之后的所有消息 (用于重新编辑)
    @DeleteMapping("/session/{sessionId}/messages/from/{messageId}")
    public String deleteMessagesFrom(@PathVariable String sessionId, @PathVariable String messageId) {
        chatHistoryService.deleteMessagesFrom(sessionId, messageId);
        return "success";
    }

}