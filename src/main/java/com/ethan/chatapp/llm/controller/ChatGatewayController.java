package com.ethan.chatapp.llm.controller;

import com.ethan.chatapp.llm.dto.ChatRequestDto;
import com.ethan.chatapp.rag.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@Slf4j
@CrossOrigin(origins = "*")
public class ChatGatewayController {

    private final WebClient webClient;
    private final RagService ragService;

    @Value("${rag.enabled:true}")
    private boolean ragEnabled;

    @Value("${rag.top-k:3}")
    private int ragTopK;

    public ChatGatewayController(WebClient.Builder webClientBuilder, RagService ragService) {
        this.webClient = webClientBuilder.build();
        this.ragService = ragService;
    }

    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestHeader("X-User-Api-Key") String userApiKey,
            @RequestHeader("X-Target-Api-Url") String targetApiUrl,
            @RequestBody ChatRequestDto request) {

        if (ragEnabled) {
            attachRagContext(request);
        }

        // 启用 token usage 统计
        if (request.isStream()) {
            request.setStreamOptions(Map.of("include_usage", true));
        }

        log.info("Sending request to API: streamOptions={}", request.getStreamOptions());

        return webClient.post()
                .uri(targetApiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userApiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorResume(e -> {
                    log.error("AI request failed: {}", e.getMessage());
                    String safeError = e.getMessage().replace("\"", "\\\"").replace("\n", " ");
                    return Flux.just(
                            "data: {\"choices\": [{\"delta\": {\"content\": \"\\n\\n**[Network Error]**: " + safeError + "\"}}]}\n\n",
                            "data: [DONE]\n\n"
                    );
                });
    }

    private void attachRagContext(ChatRequestDto request) {
        String latestUserQuestion = extractLatestUserMessage(request);
        if (latestUserQuestion == null || latestUserQuestion.isBlank()) {
            return;
        }
        String ragContext = ragService.buildContext(latestUserQuestion, ragTopK);
        if (ragContext.isBlank()) {
            return;
        }

        ChatRequestDto.Message ragMessage = new ChatRequestDto.Message();
        ragMessage.setRole("system");
        ragMessage.setContent(ragContext);

        List<ChatRequestDto.Message> messages = request.getMessages() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getMessages());
        messages.add(0, ragMessage);
        request.setMessages(messages);
    }

    private String extractLatestUserMessage(ChatRequestDto request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return null;
        }
        List<ChatRequestDto.Message> messages = request.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatRequestDto.Message message = messages.get(i);
            if (message == null) {
                continue;
            }
            if ("user".equalsIgnoreCase(message.getRole())) {
                String extracted = extractTextFromContent(message.getContent());
                if (extracted != null && !extracted.isBlank()) {
                    return extracted;
                }
            }
        }
        return null;
    }

    private String extractTextFromContent(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String) {
            return (String) content;
        }
        if (!(content instanceof List<?>)) {
            return null;
        }

        List<?> parts = (List<?>) content;
        StringBuilder text = new StringBuilder();
        for (Object part : parts) {
            if (!(part instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> partMap = (Map<?, ?>) part;
            Object type = partMap.get("type");
            if (!"text".equals(String.valueOf(type))) {
                continue;
            }
            Object textPart = partMap.get("text");
            if (textPart == null) {
                continue;
            }
            String value = String.valueOf(textPart).trim();
            if (value.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(value);
        }
        return text.toString();
    }
}
