# Ethan's AI Chat Backend (Spring Boot)

本项目是基于 Spring Boot 架构的 AI 对话系统后端接口实现。作为“Ethan's Space”生态系统的一部分，它为前端 Electron 应用提供稳定、高效的会话管理和 LLM 接口转发功能。

## 🚀 技术栈
* **核心框架**：Spring Boot 3.x
* **持久化层**：MyBatis-Plus
* **数据库**：SQLite (嵌入式数据库，免配置，即开即用)
* **API 风格**：RESTful API
* **其他**：Lombok, Maven

## ✨ 核心特性
1. **轻量化部署**：采用 SQLite 作为数据库，无需安装复杂的 MySQL，项目克隆后即可直接运行，非常适合私密聊天与小规模部署。
2. **异步会话管理**：实现了对话历史的分页查询、会话创建与删除，支持多轮对话上下文逻辑。
3. **安全性设计**：
    * 数据库文件（.db）本地化存储并排除在版本控制外。
    * 采用 DTO 模式进行数据传输，实现内部实体与外部接口的解耦。
4. **自动初始化**：内置 `schema.sql` 脚本，首次启动时会自动完成数据库表结构的构建。

## 📂 项目结构
```text
src/main/java/com/ethan/chatapp/
├── core/config/     # 全局配置（MyBatis-Plus 等）
├── history/         # 聊天历史模块（Controller, Service, Mapper, Entity, DTO）
└── llm/             # 大模型网关模块（接口转发逻辑）
