package com.ethan.chatapp.llm.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ChatRequestDto {
    private String model;
    private List<Message> messages;
    private boolean stream;
    private Map<String, Object> streamOptions;

    @Data
    public static class Message {
        private String role;
        private Object content;
    }
}
