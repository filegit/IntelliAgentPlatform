package org.xiaoxingbomei.config.llm;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 多模型管理工厂类
 * 
 * 采用工厂模式+策略模式实现多模型统一管理
 * 支持OpenAI、Grok、Ollama等多种大模型动态切换
 * 
 * 设计模式：
 * - 工厂模式：根据provider和modelName动态创建ChatClient
 * - 策略模式：封装不同模型的调用逻辑，统一接口调用
 * 
 * @author Intelligent Agent Platform
 * @version 1.0
 */
@Component
@Slf4j
public class ChatClientFactory
{

    @Resource(name = "openaiChatClientMap")
    private Map<String, ChatClient> openaiClients;

    @Resource(name = "ollamaChatClientMap")
    private Map<String, ChatClient> ollamaClients;

    /**
     * 获取ChatClient实例（工厂方法）
     * 
     * @param provider 模型提供商（openai/ollama）
     * @param name 模型名称（gpt-4o/grok-3/qwen3:14b等）
     * @return ChatClient实例
     * @throws IllegalArgumentException 不支持的provider时抛出
     */
    public ChatClient getClient(String provider, String name)
    {
        return switch (provider.toLowerCase())
        {
            case "openai" -> openaiClients.get(name);
            case "ollama" -> ollamaClients.get(name);
            default -> throw new IllegalArgumentException("不支持的 provider: " + provider);
        };
    }

    /**
     * 初始化后打印已加载的模型信息
     * 便于开发调试和生产监控
     */
    @PostConstruct
    public void logClientInfo()
    {
        log.info("==============================================");
        log.info("        多模型管理工厂 - 已加载模型         ");
        log.info("==============================================");
        log.info(String.format("| %-12.5s | %-17s |", "模型类型", "模型名称"));
        log.info("|-----------------|----------------------|");

        // 打印 OpenAI 模型信息（包含GPT-4o、Grok-3等）
        if (!openaiClients.isEmpty()) {
            openaiClients.forEach((name, client) ->
                    log.info(String.format("| %-15s | %-20s |", "OpenAI", name))
            );
        } else {
            log.warn("| %-15s | %-20s |", "OpenAI", "没有加载任何模型");
        }

        // 打印 Ollama 模型信息（本地部署模型）
        if (!ollamaClients.isEmpty()) {
            ollamaClients.forEach((name, client) ->
                    log.info(String.format("| %-15s | %-20s |", "Ollama", name))
            );
        } else {
            log.warn("| %-15s | %-20s |", "Ollama", "没有加载任何模型");
        }

        log.info("==============================================");
        log.info("✅ 多模型管理架构初始化完成，共加载 {} 个模型", 
                openaiClients.size() + ollamaClients.size());
    }


}
