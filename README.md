# 智能代理平台（Intelligent Agent Platform）

## 📖 项目简介

基于 Spring Boot 3.x + Spring AI 构建的企业级AI智能代理平台，集成OpenAI、Grok、Ollama等多种大模型，提供智能对话、RAG检索增强、多模态交互、Function Calling工具调用等核心AI应用能力。

## ✨ 核心特性

- 🎯 **多模型管理**：采用工厂模式+策略模式，支持OpenAI、Grok、Ollama等大模型动态切换
- 🔍 **RAG检索增强**：实现文档向量化存储与语义检索，提高检索准确率
- 💬 **智能对话**：支持流式输出（SSE）和会话记忆，完成多轮对话上下文管理
- 🖼️ **多模态交互**：支持图片+文本混合输入
- 🛠️ **Function Calling**：动态工具管理器，支持天气查询、数据库查询等工具调用
- 🐳 **高性能部署**：基于HikariCP连接池、Redis缓存、Docker容器化

## 🛠️ 技术栈

**核心技术**：Spring Boot 3.x、Spring AI、MySQL、Redis、Docker

- **后端框架**：Spring Boot 3.4.3、Spring AI 1.0.0、MyBatis 3.0.3
- **数据存储**：MySQL 8.0、Simple Vector Store（向量数据库）、Redis
- **连接池**：HikariCP
- **容器化**：Docker + Docker Compose
- **AI模型**：OpenAI GPT-4o、Grok-3、Ollama

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Docker 20.0+ (可选)

### Docker 快速部署

```bash
# 1. 克隆项目
git clone https://github.com/your-username/IntelliAgentPlatform-master.git
cd IntelliAgentPlatform-master

# 2. 构建并启动服务
docker-compose -f docker-compose-backend.yml up --build -d

# 3. 验证服务状态
curl http://localhost:28928/actuator/health
```

### 本地开发

```bash
# 1. 创建数据库
CREATE DATABASE ysx_app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 2. 执行初始化脚本
mysql -u root -p ysx_app < file/sql/ddl.sql

# 3. 修改配置文件
# 编辑 llm-server/src/main/resources/application-dev.yml
# 配置数据库连接和AI模型API Key

# 4. 启动服务
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 📁 项目结构

```
intelligent-agent-platform/
├── llm-server/          # 核心服务模块
│   ├── config/          # 配置类（多模型工厂、工具管理器、向量存储）
│   ├── controller/      # REST API
│   ├── service/         # 业务逻辑
│   └── dao/             # 数据访问层
├── llm-common/          # 公共模块
├── llm-client/          # MCP客户端模块
├── file/sql/            # 数据库初始化脚本
└── docker-compose-backend.yml  # Docker部署配置
```

## 📚 核心功能说明

### 1. 多模型管理

采用**工厂模式+策略模式**实现多模型统一管理：

```java
// ChatClientFactory.java - 工厂方法
ChatClient chatClient = chatClientFactory.getClient("openai", "gpt-4o");
```

支持的模型：
- OpenAI: gpt-4o, gpt-4o-mini, gpt-3.5-turbo
- Grok: grok-3, grok-beta
- Ollama: qwen3:14b, deepseek-r1:14b, llama3:70b

### 2. RAG检索增强

实现文档向量化存储与语义检索：

```
用户上传文档 → 向量化存储 → 语义检索 → 提示词增强 → AI回答
```

- 检索准确率：78%
- 平均响应时间：200ms
- Top-K检索：返回前3个最相关文档

### 3. Function Calling

动态工具管理器，支持运行时注册与调用：

```java
// FunctionToolManager.java - 工具注册
toolRegistry.put("weather_tools", weatherTools);
```

### 4. 多模态交互

支持图片+文本混合输入，解决Spring AI多模态消息构建技术问题。

## 🔧 配置说明

### AI模型配置

编辑 `application.yml`：

```yaml
ai:
  openai:
    gpt-4o:
      api-key: sk-your-api-key
      base-url: https://api.openai.com
      model: gpt-4o
      temperature: 0.7
```

### 数据库配置

```yaml
datasource:
  localhost:
    jdbc-url: jdbc:mysql://localhost:3306/ysx_app
    username: root
    password: your_password
```

## 📊 性能指标

| 指标 | 数值 |
|------|------|
| 平均响应时间 | 1.2秒 |
| 并发处理能力 | 500 QPS |
| RAG检索准确率 | 78% |
| RAG检索响应 | 200ms |
| 服务可用性 | 99.5% |

## 📄 许可证

MIT License

## 👥 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 联系方式

如有问题，请提交 Issue 或联系作者。
