package com.ethan.chatapp.llm.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequestDto {
    private String model;
    private List<Message> messages;
    private boolean stream;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}