package com.ethan.chatapp.history.dto;

import lombok.Data;

@Data
public class MessagePairDto {
    private String userContent;
    private String assistantContent;
    private String reasoningContent;
}