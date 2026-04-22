package com.ethan.chatapp.rag.controller;

import com.ethan.chatapp.rag.dto.RagIngestRequestDto;
import com.ethan.chatapp.rag.dto.RagDocumentDto;
import com.ethan.chatapp.rag.dto.RagSearchResultDto;
import com.ethan.chatapp.rag.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rag")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/documents")
    public Map<String, String> ingest(@RequestBody RagIngestRequestDto request) {
        String docId = ragService.ingestDocument(
                request.getTitle(),
                request.getSource(),
                request.getContent(),
                request.getChunkSize(),
                request.getChunkOverlap()
        );
        Map<String, String> response = new HashMap<>();
        response.put("documentId", docId);
        response.put("status", "success");
        return response;
    }

    @GetMapping("/search")
    public List<RagSearchResultDto> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK) {
        return ragService.search(query, topK);
    }

    @GetMapping("/documents")
    public List<RagDocumentDto> listDocuments() {
        return ragService.listDocuments();
    }

    @DeleteMapping("/documents/{documentId}")
    public Map<String, String> deleteDocument(@PathVariable String documentId) {
        ragService.deleteDocument(documentId);
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("documentId", documentId);
        return response;
    }
}
