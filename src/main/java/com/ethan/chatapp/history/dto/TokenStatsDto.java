package com.ethan.chatapp.history.dto;

import lombok.Data;

@Data
public class TokenStatsDto {
    private long totalTokens;
    private long totalSessions;
    private long totalMessages;
    private double avgTokensPerSession;
}
