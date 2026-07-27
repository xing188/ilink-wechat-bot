package com.ilink.test.ilinkcheck.application.tool.orchestration;

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

@Service
public class ToolMethodRegistry {

    private final ToolRegistry toolRegistry;
    private final Map<String, ToolMethodDefinition> definitionsByCallbackName;
    private final Map<String, List<ToolMethodDefinition>> definitionsByToolId;

    public ToolMethodRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;

        LinkedHashMap<String, ToolMethodDefinition> callbackMap = new LinkedHashMap<>();
        LinkedHashMap<String, List<ToolMethodDefinition>> toolMap = new LinkedHashMap<>();

        for (String toolId : toolRegistry.allToolIds()) {
            ChatFunctionTool toolBean = toolRegistry.toolById(toolId);
            if (toolBean == null) {
                continue;
            }

            ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(toolBean)
                    .build()
                    .getToolCallbacks();

            Map<String, Method> methodsByName = methodsByCallbackName(toolBean);
            List<ToolMethodDefinition> definitions = java.util.Arrays.stream(callbacks)
                    .map(callback -> toDefinition(toolId, callback, methodsByName.get(callback.getToolDefinition().name())))
                    .filter(Objects::nonNull)
                    .toList();

            for (ToolMethodDefinition definition : definitions) {
                ToolMethodDefinition previous = callbackMap.put(definition.callbackName(), definition);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate callback tool name: " + definition.callbackName());
                }
            }
            toolMap.put(toolId, List.copyOf(definitions));
        }

        this.definitionsByCallbackName = Collections.unmodifiableMap(callbackMap);
        this.definitionsByToolId = Collections.unmodifiableMap(toolMap);
    }

    public ToolMethodDefinition findByCallbackName(String callbackName) {
        return definitionsByCallbackName.get(callbackName);
    }

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

    public String buildPlannerCatalog(List<String> selectedToolIds) {
        List<ToolMethodDefinition> definitions = listForToolIds(selectedToolIds);
        if (definitions.isEmpty()) {
            return "No tool methods are active for this turn.";
        }

        StringBuilder sb = new StringBuilder();
        for (ToolMethodDefinition definition : definitions) {
            sb.append("- ").append(definition.callbackName())
                    .append(" | toolId=").append(definition.toolId())
                    .append(" | readOnly=").append(definition.readOnly())
                    .append('\n');
            if (definition.description() != null && !definition.description().isBlank()) {
                sb.append("  desc: ").append(definition.description().trim()).append('\n');
            }
            if (definition.inputSchema() != null && !definition.inputSchema().isBlank()) {
                sb.append("  inputSchema: ").append(definition.inputSchema().trim()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private ToolMethodDefinition toDefinition(String toolId, ToolCallback callback, Method method) {
        if (method == null) {
            return null;
        }

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

    private Map<String, Method> methodsByCallbackName(ChatFunctionTool toolBean) {
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
