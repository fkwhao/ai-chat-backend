package com.ethan.chatapp.llm.controller;

import com.ethan.chatapp.llm.dto.ChatRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
@Slf4j
@CrossOrigin(origins = "*")
public class ChatGatewayController {

    private final WebClient webClient;

    public ChatGatewayController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestHeader("X-User-Api-Key") String userApiKey,
            @RequestHeader("X-Target-Api-Url") String targetApiUrl,
            // 💡 重点：我们依然使用 DTO 接收，为 RAG 留出操作空间
            @RequestBody ChatRequestDto request) {

        log.info("【LLM 调用】目标URL: {}", targetApiUrl);

        // ==========================================
        // 🚀 RAG 预留位 (下一步就在这里写代码)：
        // 1. 拦截 request.getMessages() 获取用户最新的提问。
        // 2. 去 SQLite 向量库检索相关文档。
        // 3. 将检索到的文档组装成 SystemMessage，塞回 request 中。
        // ==========================================

        return webClient.post()
                // 直接使用前端传来的精确 URL，绕过 Spring AI 的路径强绑定
                .uri(targetApiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userApiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .accept(MediaType.TEXT_EVENT_STREAM)
                // Spring Boot 会自动把 DTO 重新序列化为大模型需要的 JSON
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorResume(e -> {
                    log.error("【AI调用异常】: {}", e.getMessage());
                    String safeError = e.getMessage().replace("\"", "\\\"").replace("\n", " ");
                    return Flux.just(
                            "data: {\"choices\": [{\"delta\": {\"content\": \"\\n\\n**[网络拦截]**: " + safeError + "\"}}]}\n\n",
                            "data: [DONE]\n\n"
                    );
                });
    }
}