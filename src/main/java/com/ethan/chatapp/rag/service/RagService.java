package com.ethan.chatapp.rag.service;

import com.ethan.chatapp.rag.dto.RagSearchResultDto;
import com.ethan.chatapp.rag.dto.RagDocumentDto;

import java.util.List;

public interface RagService {

    String ingestDocument(String title, String source, String content, Integer chunkSize, Integer chunkOverlap);

    List<RagSearchResultDto> search(String query, int topK);

    String buildContext(String query, int topK);

    List<RagDocumentDto> listDocuments();

    void deleteDocument(String documentId);
}
