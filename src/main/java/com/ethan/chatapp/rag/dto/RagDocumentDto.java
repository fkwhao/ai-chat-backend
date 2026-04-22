package com.ethan.chatapp.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RagDocumentDto {
    private String id;
    private String title;
    private String source;
    private Long chunkCount;
    private String createTime;
}
