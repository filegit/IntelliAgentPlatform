package org.xiaoxingbomei.config.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG检索增强模块 - 向量存储配置类
 * 
 * 实现功能：
 * - 文档向量化存储：将文档转换为向量并存储
 * - 语义检索：基于向量相似度进行文档检索
 * - 提高检索准确率：通过语义理解提升检索质量
 * 
 * 技术实现：
 * - 使用OpenAI EmbeddingModel进行文档向量化
 * - 采用SimpleVectorStore实现向量存储与检索
 * - 支持Top-K相似度检索
 * 
 * @author Intelligent Agent Platform
 * @version 1.0
 */
@Slf4j
@Configuration
public class VectorStoreConfig
{

    /**
     * 创建VectorStore Bean
     * 
     * 用于实现RAG（Retrieval-Augmented Generation）检索增强
     * 
     * @param embeddingModel OpenAI嵌入模型，用于文档向量化
     * @return VectorStore实例，支持向量存储与语义检索
     */
    @Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel) {
        log.info("==============================================");
        log.info("     RAG检索增强模块 - VectorStore初始化    ");
        log.info("==============================================");
        log.info("✅ 使用OpenAI EmbeddingModel创建VectorStore");
        log.info("✅ 支持文档向量化存储与语义检索");
        log.info("==============================================");
        return SimpleVectorStore.builder(embeddingModel).build();
    }
} 