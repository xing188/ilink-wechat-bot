package com.ilink.test.ilinkcheck.application.tool.orchestration.meta;

import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import org.springframework.aop.support.AopUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具方法元数据注册中心
 * 职责：完成【业务层toolId】 ↔【SpringAI底层callbackName】双向映射桥接
 * 分层概念说明：
 * toolId：业务侧粗粒度工具标识，ToolSelector 基于toolId做权限、场景筛选
 * callbackName：细粒度可执行函数名称，对应单个@Tool方法、绑定ToolCallback，最终被ToolOrchestrator调用
 *
 * 核心能力：
 * 1. 容器启动时扫描所有 ChatFunctionTool 工具Bean，解析@Tool注解生成ToolCallback
 * 2. 提取自定义注解 @ToolExecutionPolicy，标记工具是否只读（用于并行/串编排策略）
 * 3. 维护两组只读缓存：按callbackName查询定义、按toolId批量查询关联方法
 * 4. 为ToolExecutionPlanner生成标准化工具目录Prompt，提供给LLM规划执行计划
 */
@Service
public class ToolMethodRegistry {

    // 上层粗粒度工具Bean注册中心，存放全部ChatFunctionTool实例
    private final ToolRegistry toolRegistry;
    // 缓存1：callbackName → 完整工具方法定义
    private final Map<String, ToolMethodDefinition> definitionsByCallbackName;
    // 缓存2：toolId → 该业务工具下所有可用方法定义列表
    private final Map<String, List<ToolMethodDefinition>> definitionsByToolId;

    /**
     * 容器构造阶段一次性完成所有工具解析与缓存构建
     * @param toolRegistry 粗粒度工具Bean仓库
     */
    public ToolMethodRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;

        LinkedHashMap<String, ToolMethodDefinition> callbackMap = new LinkedHashMap<>();
        LinkedHashMap<String, List<ToolMethodDefinition>> toolMap = new LinkedHashMap<>();

        // 遍历所有注册的业务工具ID
        for (String toolId : toolRegistry.allToolIds()) {
            ChatFunctionTool toolBean = toolRegistry.toolById(toolId);
            if (toolBean == null) {
                continue;
            }

            // SpringAI官方工具构建器：扫描Bean内@Tool方法，生成可执行ToolCallback
            ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(toolBean)
                    .build()
                    .getToolCallbacks();

            // 反射提取当前Bean所有带有@Tool注解的原始Method对象
            // 原因：SpringAI ToolCallback对外不暴露原始Method，无法读取自定义注解@ToolExecutionPolicy
            Map<String, Method> methodsByName = methodsByCallbackName(toolBean);

            // 将SpringAI回调对象 + 自定义注解信息 封装成本项目统一定义
            List<ToolMethodDefinition> definitions = java.util.Arrays.stream(callbacks)
                    .map(callback -> toDefinition(toolId, callback, methodsByName.get(callback.getToolDefinition().name())))
                    .filter(Objects::nonNull)
                    .toList();

            // 填充全局callbackName索引，检测重名冲突（同一个方法名不能重复注册）
            for (ToolMethodDefinition definition : definitions) {
                ToolMethodDefinition previous = callbackMap.put(definition.callbackName(), definition);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate callback tool name: " + definition.callbackName());
                }
            }
            // 绑定 toolId -> 一组方法
            toolMap.put(toolId, List.copyOf(definitions));
        }

        // 构建不可变只读Map，运行期禁止修改，线程安全
        this.definitionsByCallbackName = Collections.unmodifiableMap(callbackMap);
        this.definitionsByToolId = Collections.unmodifiableMap(toolMap);
    }

    /**
     * 根据callbackName查询完整工具定义
     * 调用方：ToolOrchestrator#invoke() 执行工具
     * @param callbackName callback 名称
     * @return 对应的运行时方法定义；不存在时返回 null
     */
    public ToolMethodDefinition findByCallbackName(String callbackName) {
        return definitionsByCallbackName.get(callbackName);
    }

    /**
     * 根据选中的业务toolId集合，批量获取所有关联的工具方法定义
     * 调用方：ToolExecutionPlanner 规划器
     * @param toolIds ToolSelector筛选出来本轮允许启用的业务工具ID
     * @return 去重后的全部可用方法定义
     */
    public List<ToolMethodDefinition> listForToolIds(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<ToolMethodDefinition> definitions = new LinkedHashSet<>();
        for (String toolId : toolIds) {
            definitions.addAll(definitionsByToolId.getOrDefault(toolId, List.of()));
        }
        return List.copyOf(definitions);
    }

    /**
     * 构建给规划器LLM使用的【工具目录文本】
     * 核心：把可用工具名称、只读标记、描述、入参Schema拼接为Prompt片段
     * LLM依靠这份目录，生成合法的ToolExecutionBatch执行计划
     * @param selectedToolIds 当前会话放行的业务工具ID
     * @return 可直接塞入SystemPrompt的工具清单文本
     */
    public String buildPlannerCatalog(List<String> selectedToolIds) {
        List<ToolMethodDefinition> definitions = listForToolIds(selectedToolIds);
        if (definitions.isEmpty()) {
            return "当前这一轮没有可用的工具方法。";
        }

        StringBuilder sb = new StringBuilder();
        for (ToolMethodDefinition definition : definitions) {
            sb.append("- ").append(definition.callbackName())
                    .append(" | 工具=").append(definition.toolId())
                    .append(" | 只读=").append(definition.readOnly())
                    .append('\n');
            if (definition.description() != null && !definition.description().isBlank()) {
                sb.append("  说明：").append(definition.description().trim()).append('\n');
            }
            if (definition.inputSchema() != null && !definition.inputSchema().isBlank()) {
                sb.append("  入参结构：").append(definition.inputSchema().trim()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /**
     * 把 Spring AI 的底层 callback 和本地补充信息合并成统一定义。
     *
     * @param toolId 业务层工具 id
     * @param callback Spring AI 构造出的可执行 callback
     * @param method callback 对应的原始 Java 方法
     * @return 归一化后的方法定义；method 为空时返回 null
     */
    private ToolMethodDefinition toDefinition(String toolId, ToolCallback callback, Method method) {
        if (method == null) {
            return null;
        }

        // 读取自定义执行策略注解；无注解默认视为【只读工具】，允许并行
        ToolExecutionPolicy policy = method.getAnnotation(ToolExecutionPolicy.class);
        boolean readOnly = policy == null || policy.readOnly();
        return new ToolMethodDefinition(
                toolId,
                callback.getToolDefinition().name(),
                callback,
                readOnly,
                callback.getToolDefinition().description(),
                callback.getToolDefinition().inputSchema()
        );
    }

    /**
     * 反射扫描目标Bean，读取原始 @Tool 方法。
     * 因为 Spring AI 的 ToolCallback 自身不携带本地注解信息，
     * 所以这里要把 ToolExecutionPolicy 这类注解重新拿出来。
     *
     * @param toolBean 当前工具 bean
     * @return callbackName 到原始 Java 方法的映射
     */
    private Map<String, Method> methodsByCallbackName(ChatFunctionTool toolBean) {
        // 剥离AOP代理对象，拿到真实Class，防止反射读取注解失败
        Class<?> toolClass = AopUtils.getTargetClass(toolBean);
        Class<?> actualClass = toolClass != null ? toolClass : toolBean.getClass();
        LinkedHashMap<String, Method> methods = new LinkedHashMap<>();
        for (Method method : actualClass.getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            methods.put(tool.name(), method);
        }
        return methods;
    }
}
