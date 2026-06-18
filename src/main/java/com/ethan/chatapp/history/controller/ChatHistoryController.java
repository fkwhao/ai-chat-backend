package com.ethan.chatapp.history.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.chatapp.history.dto.*;
import com.ethan.chatapp.history.entity.ChatMessage;
import com.ethan.chatapp.history.entity.ChatSession;
import com.ethan.chatapp.history.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/history")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    @PostMapping("/session")
    public ChatSession createSession(@RequestParam(required = false) String title) {
        return chatHistoryService.createSession(title);
    }

    @GetMapping("/sessions/page")
    public Page<ChatSession> getSessionsPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size) {
        return chatHistoryService.searchSessions(keyword, current, size);
    }

    @GetMapping("/session/{sessionId}/messages")
    public List<ChatMessage> getSessionMessages(@PathVariable String sessionId) {
        return chatHistoryService.getMessagesBySessionId(sessionId);
    }

    @DeleteMapping("/session/{sessionId}")
    public String deleteSession(@PathVariable String sessionId) {
        chatHistoryService.deleteSession(sessionId);
        return "success";
    }

    @PostMapping("/session/{sessionId}/message-pair")
    public String saveMessagePair(
            @PathVariable String sessionId,
            @RequestBody MessagePairDto dto) {
        chatHistoryService.saveMessagePair(
                sessionId,
                dto.getUserContent(),
                dto.getAssistantContent(),
                dto.getReasoningContent(),
                dto.getTokens(),
                dto.getModel()
        );
        return "success";
    }

    @GetMapping("/session/{sessionId}")
    public ChatSession getSession(@PathVariable String sessionId) {
        return chatHistoryService.getSessionById(sessionId);
    }

    @DeleteMapping("/session/{sessionId}/messages/from/{messageId}")
    public String deleteMessagesFrom(@PathVariable String sessionId, @PathVariable String messageId) {
        chatHistoryService.deleteMessagesFrom(sessionId, messageId);
        return "success";
    }

    @GetMapping("/token-stats")
    public TokenStatsDto getTokenStats() {
        return chatHistoryService.getTokenStats();
    }

    @GetMapping("/token-ranking")
    public List<SessionTokenDto> getTokenRanking(@RequestParam(defaultValue = "10") int limit) {
        return chatHistoryService.getTopSessionsByTokens(limit);
    }

    // ══════════════════════════════════════
    // 新增：使用统计
    // ══════════════════════════════════════

    @GetMapping("/usage-overview")
    public UsageOverviewDto getUsageOverview(@RequestParam(defaultValue = "7") int days) {
        return chatHistoryService.getUsageOverview(days);
    }

    @GetMapping("/model-breakdown")
    public List<ModelBreakdownDto> getModelBreakdown(@RequestParam(defaultValue = "7") int days) {
        return chatHistoryService.getModelBreakdown(days);
    }

    @GetMapping("/daily-trend")
    public List<DailyTrendDto> getDailyTrend(@RequestParam(defaultValue = "7") int days) {
        return chatHistoryService.getDailyTrend(days);
    }

    @GetMapping("/activity-heatmap")
    public List<ActivityHeatmapDto> getActivityHeatmap(@RequestParam(defaultValue = "30") int days) {
        return chatHistoryService.getActivityHeatmap(days);
    }

}
