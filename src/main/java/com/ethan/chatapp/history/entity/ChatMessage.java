package com.ethan.chatapp.history.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    private String sessionId;
    private String role; // "user" / "assistant"
    private String content;
    private String reasoningContent;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}