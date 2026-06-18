package com.ethan.chatapp.history.dto;

import lombok.Data;

@Data
public class UsageOverviewDto {
    private long totalTokens;
    private long totalSessions;
    private long totalMessages;
    private int activeDays;
    private int currentStreak;
    private String mostUsedModel;
    private double mostUsedModelPercentage;
}
