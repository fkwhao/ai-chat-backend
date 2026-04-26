# AI Chat Backend (Spring Boot)

一个基于 Spring Boot 的聊天后端，提供：
- 聊天流式转发（兼容 OpenAI 风格 SSE）
- 会话与消息历史管理（SQLite）
- 轻量级本地 RAG（文档入库、检索、自动注入上下文）

仓库地址：`https://github.com/fkwhao/ai-chat-backend`

## 技术栈
- Java 17
- Spring Boot 3.3.4
- Spring Web + WebFlux
- MyBatis-Plus 3.5.7
- SQLite (`sqlite-jdbc`)
- Spring AI OpenAI Starter
- Maven

## 核心能力
- 聊天网关：`/api/v1/chat/completions`
  - 通过请求头透传目标模型接口地址和 API Key
  - 以 SSE 流式返回模型响应
  - 支持 `stream_options.include_usage` 返回 token 用量
- 历史会话：`/api/v1/history/**`
  - 创建会话、分页查询会话、读取消息、删除会话
  - 支持”从某条消息开始截断历史”
  - Token 统计持久化（只增不减）
- RAG：`/api/v1/rag/**`
  - 文档入库（分块 + 简单 TF/IDF 向量化）
  - 检索 topK 相关分块
  - 在聊天请求前自动注入 system context（可配置开关）

## 快速启动

### 1. 环境要求
- JDK 17+
- Maven 3.9+

### 2. 启动项目
```bash
mvn spring-boot:run
```

默认端口：`8090`

### 3. 数据库
项目默认使用当前目录下的 `chat_history.db`（SQLite）。

首次启动会自动执行：`src/main/resources/schema.sql`

## 配置说明
主要配置在 `src/main/resources/application.yml`：

```yaml
server:
  port: 8090

spring:
  datasource:
    url: jdbc:sqlite:chat_history.db

rag:
  enabled: true
  top-k: 3
  max-chunks-scan: 2000
```

- `rag.enabled`：是否启用聊天前的 RAG 上下文注入
- `rag.top-k`：注入上下文时检索条数
- `rag.max-chunks-scan`：RAG 检索时扫描分块上限

## 主要接口

### Chat
- `POST /api/v1/chat/completions`

请求头：
- `X-User-Api-Key`: 目标模型 API Key
- `X-Target-Api-Url`: 目标模型聊天接口 URL

示例：
```bash
curl -N -X POST "http://localhost:8090/api/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "X-User-Api-Key: sk-xxxx" \
  -H "X-Target-Api-Url: https://api.openai.com/v1/chat/completions" \
  -d '{
    "model": "gpt-4o-mini",
    "stream": true,
    "messages": [
      {"role": "user", "content": "你好，介绍一下你自己"}
    ]
  }'
```

### History
- `POST /api/v1/history/session` 创建会话
- `GET /api/v1/history/sessions/page` 分页查询会话
- `GET /api/v1/history/session/{sessionId}` 获取会话信息（含 totalTokens）
- `GET /api/v1/history/session/{sessionId}/messages` 查询会话消息
- `POST /api/v1/history/session/{sessionId}/message-pair` 保存一轮问答（含 token 累加）
- `DELETE /api/v1/history/session/{sessionId}` 删除会话
- `DELETE /api/v1/history/session/{sessionId}/messages/from/{messageId}` 截断历史

### RAG
- `POST /api/v1/rag/documents` 文档入库
- `GET /api/v1/rag/search?query=...&topK=3` 检索
- `GET /api/v1/rag/documents` 文档列表
- `DELETE /api/v1/rag/documents/{documentId}` 删除文档

## 项目结构
```text
src/main/java/com/ethan/chatapp/
├─ core/      # 全局配置
├─ history/   # 会话与消息历史
├─ llm/       # 聊天网关
└─ rag/       # 检索增强生成
```

## 注意事项
- 请勿把真实密钥写入代码仓库。
- `chat_history.db` 建议加入 `.gitignore`（本地数据文件）。
- 当前 RAG 为轻量实现，适合中小规模知识库。若规模扩大，建议替换为向量数据库方案。
- Token 统计字段 `total_tokens` 需在数据库中添加：`ALTER TABLE chat_session ADD COLUMN total_tokens INTEGER DEFAULT 0;`
