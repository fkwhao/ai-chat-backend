package com.ethan.chatapp.history.dto;

import lombok.Data;

@Data
public class SessionTokenDto {
    private String sessionId;
    private String title;
    private Integer totalTokens;
    private String updateTime;
}
