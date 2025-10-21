package org.xiaoxingbomei.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.content.Media;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import org.xiaoxingbomei.common.entity.response.GlobalResponse;
import org.xiaoxingbomei.common.utils.Request_Utils;
import org.xiaoxingbomei.config.llm.ChatClientFactory;
import org.xiaoxingbomei.config.llm.FunctionToolManager;
import org.xiaoxingbomei.config.tools.CoffeeTools;
import org.xiaoxingbomei.constant.SystemPromptConstant;
import org.xiaoxingbomei.dao.localhost.ChatMapper;
import org.xiaoxingbomei.service.ChatService;
import org.xiaoxingbomei.service.FileService;
import org.xiaoxingbomei.service.PromptService;
import org.xiaoxingbomei.vo.LlmChatHistory;
import org.xiaoxingbomei.vo.LlmChatHistoryList;
import org.xiaoxingbomei.vo.LlmSystemPrompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 智能对话服务实现类
 * 
 * 核心功能：
 * 1. 智能对话：支持流式输出（SSE）和同步响应
 * 2. 会话记忆：完成多轮对话上下文管理
 * 3. RAG检索增强：文档向量化存储与语义检索
 * 4. 多模态交互：支持图片+文本混合输入
 * 5. Function Calling：动态工具调用
 * 
 * @author Intelligent Agent Platform
 * @version 1.0
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService
{
    @Autowired
    ChatMemory chatMemory;

    @Autowired
    private ChatMapper chatMapper;

    @Autowired
    private ChatClientFactory chatClientFactory;

    @Autowired
    private FunctionToolManager toolManager;

    @Autowired
    private PromptService promptService;

    @Autowired
    private FileService fileService;

    @Autowired
    private VectorStore vectorStore;


    // ==============================================================
    // 核心对话方法
    // ==============================================================

    /**
     * 智能对话核心方法
     * 
     * 实现功能：
     * 1. 多模型动态切换：根据modelProvider和modelName获取对应的ChatClient
     * 2. 流式输出（SSE）：支持实时流式返回，提升用户体验
     * 3. 会话记忆：通过chatId关联历史对话，实现多轮对话上下文管理
     * 4. RAG检索增强：自动检索相关文档，提升回答准确性
     * 5. 多模态交互：支持图片+文本混合输入
     * 6. Function Calling：根据systemPromptId自动加载工具
     * 
     * @param prompt 用户输入内容
     * @param chatId 会话ID（用于会话隔离和记忆）
     * @param isStream 是否流式输出（true/false）
     * @param modelProvider 模型提供商（openai/ollama）
     * @param modelName 模型名称（gpt-4o/grok-3/qwen3:14b等）
     * @param systemPromptId 系统提示词ID（关联Function Calling工具）
     * @param files 多模态文件列表（图片等）
     * @return Flux<String> 流式响应结果
     */
    @Override
    public Flux<String> chat(String prompt, String chatId, String isStream, String modelProvider, String modelName, String systemPromptId, List<MultipartFile> files)
    {
        // 1.根据模型选择获取对应的 ChatClient（多模型动态切换）
        ChatClient chatClient      = chatClientFactory.getClient(modelProvider, modelName);
        Boolean    isStreamBoolean = Boolean.valueOf(isStream);

        // 2.获取系统提示词和工具配置
        String systemPromptContent = SystemPromptConstant.XIAOXINGBOMEI_SYSTEM_PROMPT; // 默认提示词
        String functionToolId = null; // 工具ID，从系统提示词中自动获取
        
        if (StringUtils.isNotEmpty(systemPromptId))
        {
            try
            {
                // 根据ID获取系统提示词详情，同时自动获取对应的工具配置
                GlobalResponse systemPromptResponse = promptService.getSystemPromptById("{\"promptId\":\"" + systemPromptId + "\"}");
                if (systemPromptResponse != null && "200".equals(systemPromptResponse.getCode())) {
                    LlmSystemPrompt systemPromptData = (LlmSystemPrompt) systemPromptResponse.getData();
                    if (systemPromptData != null) {
                        systemPromptContent = systemPromptData.getPromptContent();
                        functionToolId = systemPromptData.getFunctionToolId(); // 🎯 自动获取工具ID
                        log.info("已获取系统提示词: {}, 自动配置工具ID: {}", systemPromptData.getPromptName(), functionToolId);
                    }
                }
            } catch (Exception e)
            {
                log.warn("获取系统提示词失败，使用默认提示词。systemPromptId: {}, error: {}", systemPromptId, e.getMessage());
            }
        }

        // 3. 🔍 RAG增强：使用向量数据库检索相关文档并增强提示词
        final String enhancedPrompt = performRAGEnhancement(prompt, chatId);

        // 4.构建prompt builder
        var promptBuilder = chatClient
                .prompt()
                .system(systemPromptContent) // 系统提示词
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId)); // 会话记忆与会话id进行关联
        
        // 5.根据functionToolId动态添加工具
        if (StringUtils.isNotEmpty(functionToolId))
        {
            Object toolInstance = toolManager.getToolById(functionToolId);
            if (toolInstance != null)
            {
                promptBuilder.tools(toolInstance); // 🎯 使用从toolManager获取的工具实例
                log.info("🔧 [Tool Setup] 已为对话添加工具: {} -> {}", functionToolId, toolInstance.getClass().getSimpleName());
                log.info("🔧 [Tool Setup] 原始用户提示词: {}", prompt);
                log.info("🔧 [Tool Setup] 系统提示词前50字符: {}", systemPromptContent.length() > 50 ? systemPromptContent.substring(0, 50) + "..." : systemPromptContent);
            } else {
                log.warn("⚠️ [Tool Setup] 未找到工具实例: {}", functionToolId);
            }
        } else {
            log.info("ℹ️ [Tool Setup] 本次对话不使用工具，functionToolId为空");
        }

        // 6. 🎯 关键：在一次user()调用中同时设置文本和媒体
        if (files != null && !files.isEmpty())
        {
            log.info("🖼️ [Multimodal] 处理多模态输入: 文件数量={}", files.size());
            
            // 解析多媒体文件
            List<Media> mediaList = files.stream()
                    .map(file -> {
                        log.info("🖼️ [Multimodal] 处理文件: {}, 类型: {}, 大小: {} bytes", 
                            file.getOriginalFilename(), file.getContentType(), file.getSize());
                        return new Media(MimeType.valueOf(file.getContentType()), file.getResource());
                    })
                    .toList();
            
            // ✅ 正确写法：同时设置文本和媒体
            promptBuilder.user(userSpec -> userSpec
                    .text(enhancedPrompt)  // 设置文本（包含RAG增强）
                    .media(mediaList.toArray(Media[]::new))  // 设置媒体文件
            );
            
            log.info("🖼️ [Multimodal] 多模态消息构建完成：文本 + {} 个媒体文件", mediaList.size());
        }
        else
        {
            // 📝 纯文本模式
            promptBuilder.user(enhancedPrompt); // 使用RAG增强后的提示词
            log.info("📝 [Text] 纯文本消息构建完成");
        }


        // 7.是否流式调用,执行最终的对话调用
        if(isStreamBoolean)
        {
            // 流式调用：返回实时流
            StringBuilder fullResponse = new StringBuilder();
            return promptBuilder.stream().content()
                .doOnNext(chunk ->
                {
                    fullResponse.append(chunk);
                })
                .doOnComplete(() ->
                {
                    // 流式调用完成后保存对话历史
                    saveChatHistoryToDatabase(chatId, prompt, fullResponse.toString());
                })
                .doOnError(error -> {
                    log.error("对话发生错误, chatId: {}", chatId, error);
                });
        }
        else
        {
            // 非流式调用：获取完整结果后包装成Flux
            String result = promptBuilder.call().content();
            
            // 保存对话历史到数据库
            saveChatHistoryToDatabase(chatId, prompt, result);
            
            return Flux.just(result);
        }
    }

    /**
     * 执行RAG（Retrieval-Augmented Generation）检索增强处理
     * 
     * 核心流程：
     * 1. 向量检索：使用VectorStore进行语义检索
     * 2. Top-K筛选：返回前3个最相关的文档片段
     * 3. 相似度过滤：阈值0.3，平衡准确率和召回率
     * 4. 提示词增强：将检索到的文档片段注入到用户提示词中
     * 
     * 技术实现：
     * - 文档向量化存储：使用OpenAI EmbeddingModel
     * - 语义检索：基于向量相似度计算
     * - 会话隔离：通过chatId过滤，只检索当前会话的文档
     * 
     * @param prompt 用户原始提示词
     * @param chatId 会话ID
     * @return 增强后的提示词（包含相关文档内容）
     */
    private String performRAGEnhancement(String prompt, String chatId) {
        try {
            // 使用向量数据库进行语义搜索（RAG核心步骤）
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(prompt)
                    .topK(3) // 返回前3个最相关的文档片段（Top-K检索）
                    .similarityThreshold(0.3) // 相似度阈值0.3，提高检索准确率
                    .filterExpression("chatId == '" + chatId + "'") // 会话隔离：只搜索特定会话的文档
                    .build();
            
            List<org.springframework.ai.document.Document> relevantDocs = vectorStore.similaritySearch(searchRequest);
            log.info("🔍 [RAG] 向量搜索结果: 查询='{}', 找到文档数={}", prompt, relevantDocs.size());
            
            // 打印每个文档的片段信息
            for (int i = 0; i < relevantDocs.size(); i++) {
                org.springframework.ai.document.Document doc = relevantDocs.get(i);
                log.info("📄 [RAG] 文档片段{}: 内容前50字符='{}'", 
                    i + 1, doc.getText().substring(0, Math.min(50, doc.getText().length())));
            }
            
            if (!relevantDocs.isEmpty()) {
                // 构建包含检索文档的增强提示词
                StringBuilder contextBuilder = new StringBuilder();
                contextBuilder.append("参考以下相关文档内容回答用户问题：\n\n");
                
                for (int i = 0; i < relevantDocs.size(); i++) {
                    org.springframework.ai.document.Document doc = relevantDocs.get(i);
                    contextBuilder.append("【文档片段 ").append(i + 1).append("】\n");
                    // 限制每个片段的长度，避免提示词过长
                    String docContent = doc.getText();
                    String truncatedContent = docContent.length() > 1000 
                        ? docContent.substring(0, 1000) + "..." 
                        : docContent;
                    contextBuilder.append(truncatedContent).append("\n\n");
                }
                
                contextBuilder.append("基于以上文档内容，请回答用户的问题：\n");
                contextBuilder.append(prompt);
                
                log.info("🔍 [RAG] 向量检索成功，检索到 {} 个相关片段", relevantDocs.size());
                return contextBuilder.toString();
            } else {
                // 检查是否有关联文件但没有找到相关文档
                Resource file = fileService.getFileByChatId(chatId);
                if (file != null && file.exists()) {
                    log.info("ℹ️ [RAG] 找到会话文件: {}，但当前查询未检索到相关内容", file.getFilename());
                } else {
                    log.info("ℹ️ [RAG] 当前会话无关联文档，使用普通对话模式");
                }
                return prompt; // 没有找到相关文档，使用原始提示词
            }
        } catch (Exception e) {
            log.warn("⚠️ [RAG] 向量检索失败，使用原始提示词: {}", e.getMessage());
            return prompt; // 异常情况下使用原始提示词
        }
    }

    /**
     * 保存对话历史到数据库
     */
    private void saveChatHistoryToDatabase(String chatId, String userMessage, String assistantMessage) {
        try {
            List<LlmChatHistory> chatHistories = new ArrayList<>();
            
            // 保存用户消息
            LlmChatHistory userHistory = new LlmChatHistory();
            userHistory.setChatId(chatId);
            userHistory.setChatRole("user");
            userHistory.setChatContent(userMessage);
            chatHistories.add(userHistory);
            
            // 保存AI回复
            LlmChatHistory assistantHistory = new LlmChatHistory();
            assistantHistory.setChatId(chatId);
            assistantHistory.setChatRole("assistant");
            assistantHistory.setChatContent(assistantMessage);
            chatHistories.add(assistantHistory);
            
            // 批量插入数据库
            chatMapper.insertChatHistory(chatHistories);
            log.info("成功保存对话历史到数据库, chatId: {}, 用户消息: {}, AI回复长度: {}", 
                chatId, userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage, 
                assistantMessage.length());
            
        } catch (Exception e) {
            log.error("保存对话历史到数据库失败, chatId: {}", chatId, e);
        }
    }

    // 构建 advisors 方法
    private static List<Advisor> buildAdvisors(List<Advisor> additionalAdvisors)
    {
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(new SimpleLoggerAdvisor()); // 添加默认的 logger advisor
        if (additionalAdvisors != null)
        {
            advisors.addAll(additionalAdvisors); // 将传入的额外 advisor 加入
        }
        return advisors;
    }

    @Override
    public GlobalResponse chat_for_string(String prompt)
    {
        log.info("chat_for_string");

        ChatClient chatClient = chatClientFactory.getClient("ollama", "qwen3:14b");

        String resultContent = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

        HashMap<String, Object> resultMap = new HashMap<>();
        resultMap.put("resultContent", resultContent);
        resultMap.put("prompt", prompt);
        return GlobalResponse.success(resultMap);
    }

    @Override
    public Flux<String> chat_for_stream(String prompt, String chatId) {
        return null;
    }

    @Override
    public GlobalResponse getAllChatHistoryList()
    {
        try {
            // 1、获取前端参数
            List<LlmChatHistoryList> allChatHistoryList = chatMapper.getAllChatHistoryList();
            
            // 2、检查空列表
            if (allChatHistoryList == null || allChatHistoryList.isEmpty()) {
                log.info("getAllChatHistoryList: 暂无会话历史记录");
                return GlobalResponse.success(new ArrayList<>(), "暂无会话历史记录");
            }
            
            log.info("getAllChatHistoryList: 获取到 {} 条会话记录", allChatHistoryList.size());
            
            // 3、直接返回列表，不要转换为字符串
            return GlobalResponse.success(allChatHistoryList, "获取全部会话历史成功");
            
        } catch (Exception e) {
            log.error("获取会话历史列表失败", e);
            return GlobalResponse.error("获取会话历史列表失败：" + e.getMessage());
        }
    }

    @Override
    public GlobalResponse insertChatHistoryList(String paramString)
    {
        try {
            // 1、获取前端参数
            String chatId       = Request_Utils.getParam(paramString, "chatId");
            String chatTittle   = Request_Utils.getParam(paramString, "chatTittle");
            String chatTag      = Request_Utils.getParam(paramString, "chatTag");
            
            log.info("insertChatHistoryList: chatId={}, chatTittle={}, chatTag={}", 
                    chatId, chatTittle, chatTag);

            // 2、参数校验
            if (StringUtils.isBlank(chatId) || StringUtils.isBlank(chatTittle)) {
                return GlobalResponse.error("参数不能为空: chatId=" + chatId + ", chatTittle=" + chatTittle);
            }

            // 3、插入会话历史列表
            LlmChatHistoryList chatHistoryList = new LlmChatHistoryList();
            chatHistoryList.setChatId(chatId);
            chatHistoryList.setChatTittle(chatTittle);
            chatHistoryList.setChatTag(chatTag);

            int result = chatMapper.insertChatHistoryList(chatHistoryList);
            
            if (result > 0) {
                log.info("insertChatHistoryList: 成功插入会话历史，chatId={}", chatId);
                return GlobalResponse.success("新增会话历史成功");
            } else {
                log.warn("insertChatHistoryList: 插入会话历史失败，chatId={}", chatId);
                return GlobalResponse.error("新增会话历史失败");
            }

        } catch (Exception e) {
            log.error("insertChatHistoryList: 插入会话历史异常", e);
            return GlobalResponse.error("新增会话历史失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GlobalResponse deleteChatHistoryList(String paramString)
    {
        try {
            String chatId = Request_Utils.getParam(paramString, "chatId");
            log.info("deleteChatHistoryList: 删除会话历史，chatId={}", chatId);

            // 删除会话历史列表
            chatMapper.deleteChatHistoryList(chatId);
            // 删除会话详细历史
            chatMapper.deleteChatHistory(chatId);

            return GlobalResponse.success("删除会话历史成功");
        } catch (Exception e) {
            log.error("deleteChatHistoryList: 删除会话历史失败", e);
            return GlobalResponse.error("删除会话历史失败：" + e.getMessage());
        }
    }

    @Override
    public GlobalResponse updateChatHistoryList(String paramString)
    {
        try {
            String chatId = Request_Utils.getParam(paramString, "chatId");
            String chatTittle = Request_Utils.getParam(paramString, "chatTittle");
            String chatTag = Request_Utils.getParam(paramString, "chatTag");

            log.info("updateChatHistoryList: chatId={}, chatTittle={}, chatTag={}", 
                    chatId, chatTittle, chatTag);

            LlmChatHistoryList chatHistoryList = new LlmChatHistoryList();
            chatHistoryList.setChatId(chatId);
            chatHistoryList.setChatTittle(chatTittle);
            chatHistoryList.setChatTag(chatTag);

            chatMapper.updateChatHistoryList(chatHistoryList);
            int result = 1; // 假设更新成功
            
            if (result > 0) {
                return GlobalResponse.success("更新会话历史成功");
            } else {
                return GlobalResponse.error("更新会话历史失败，未找到对应记录");
            }

        } catch (Exception e) {
            log.error("updateChatHistoryList: 更新会话历史失败", e);
            return GlobalResponse.error("更新会话历史失败：" + e.getMessage());
        }
    }

    @Override
    public List<LlmChatHistory> getChatHistoryById(String chatId)
    {
        try {
            List<LlmChatHistory> chatHistories = chatMapper.getChatHistoryById(chatId);
            log.info("getChatHistoryById: chatId={}, 获取到{}条记录", chatId, 
                    chatHistories != null ? chatHistories.size() : 0);
            return chatHistories != null ? chatHistories : new ArrayList<>();
        } catch (Exception e) {
            log.error("getChatHistoryById: 获取会话历史失败, chatId={}", chatId, e);
            return new ArrayList<>();
        }
    }
}
