package com.ethan.chatapp.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RagSearchResultDto {
    private String documentId;
    private String documentTitle;
    private Integer chunkIndex;
    private String chunkContent;
    private Double score;
}
