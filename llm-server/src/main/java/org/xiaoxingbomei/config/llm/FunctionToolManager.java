package org.xiaoxingbomei.config.llm;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xiaoxingbomei.config.tools.CoffeeTools;

import java.util.HashMap;
import java.util.Map;

/**
 * Function Calling 动态工具管理器
 * 
 * 实现功能：
 * - 支持天气查询、数据库查询等工具的运行时注册与调用
 * - 提供统一的工具管理接口，支持工具的动态加载和卸载
 * - 与系统提示词关联，实现工具的自动化调用
 * 
 * 核心特性：
 * 1. 运行时注册：支持工具在应用启动时自动注册
 * 2. 动态调用：根据toolId动态获取工具实例
 * 3. 扩展性强：新增工具仅需在此注册，无需修改调用逻辑
 * 
 * @author Intelligent Agent Platform
 * @version 1.0
 */
@Slf4j
@Component
public class FunctionToolManager
{
    
    @Autowired
    private CoffeeTools coffeeTools;
    
    // 工具注册表：toolId -> 工具实例
    private final Map<String, Object> toolRegistry = new HashMap<>();
    
    /**
     * 初始化工具注册表（运行时注册）
     * 
     * 在应用启动时自动注册所有可用工具
     * 支持的工具类型：
     * - 天气查询工具
     * - 数据库查询工具
     * - 咖啡订购工具
     * - 更多工具可在此扩展...
     */
    @PostConstruct
    public void initTools()
    {
        // 注册咖啡订购工具（示例工具）
        toolRegistry.put("coffee_tools", coffeeTools);
        
        // 未来可以在这里注册更多工具（扩展点）
        // toolRegistry.put("weather_tools", weatherTools);        // 天气查询
        // toolRegistry.put("database_tools", databaseTools);      // 数据库查询
        // toolRegistry.put("ecommerce_tools", ecommerceTools);    // 电商工具
        // toolRegistry.put("bank_service_tools", bankServiceTools); // 银行服务
        
        log.info("==============================================");
        log.info("   Function Calling 动态工具管理器初始化    ");
        log.info("==============================================");
        log.info("✅ 已注册 {} 个工具类", toolRegistry.size());
        toolRegistry.keySet().forEach(toolId -> 
            log.info("  📌 工具ID: {} -> 工具类: {}", toolId, toolRegistry.get(toolId).getClass().getSimpleName())
        );
        log.info("==============================================");
    }
    
    /**
     * 根据工具ID获取工具实例
     * @param toolId 工具ID
     * @return 工具实例，如果找不到返回null
     */
    public Object getToolById(String toolId)
    {
        if (toolId == null || toolId.trim().isEmpty())
        {
            return null;
        }
        
        Object tool = toolRegistry.get(toolId);
        if (tool == null)
        {
            log.warn("未找到工具ID: {}", toolId);
        } else
        {
            log.debug("获取到工具: {} -> {}", toolId, tool.getClass().getSimpleName());
        }
        
        return tool;
    }
    
    /**
     * 检查工具是否存在
     * @param toolId 工具ID
     * @return 是否存在
     */
    public boolean hasTools(String toolId) {
        return toolId != null && toolRegistry.containsKey(toolId);
    }
    
    /**
     * 获取所有可用的工具ID
     * @return 工具ID集合
     */
    public Map<String, String> getAllToolIds()
    {
        Map<String, String> result = new HashMap<>();
        toolRegistry.forEach((toolId, toolInstance) -> 
            result.put(toolId, toolInstance.getClass().getSimpleName())
        );
        return result;
    }
} 