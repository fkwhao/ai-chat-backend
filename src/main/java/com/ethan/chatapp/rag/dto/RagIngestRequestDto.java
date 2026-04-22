package com.ethan.chatapp.rag.dto;

import lombok.Data;

@Data
public class RagIngestRequestDto {
    private String title;
    private String source;
    private String content;
    private Integer chunkSize;
    private Integer chunkOverlap;
}
