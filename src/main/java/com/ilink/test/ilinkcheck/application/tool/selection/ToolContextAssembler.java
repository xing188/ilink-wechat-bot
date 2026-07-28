package com.ilink.test.ilinkcheck.application.tool.selection;

import com.ilink.test.ilinkcheck.application.tool.ToolMetadata;
import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ToolContextAssembler {

    private final ToolRegistry toolRegistry;

    public ToolContextAssembler(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public String buildPromptSection(List<String> selectedToolIds) {
        // 根据选中的工具ID，从注册表拿到工具元信息（名称、简介、调用规则）
        List<ToolMetadata> metadataList = toolRegistry.metadataFor(selectedToolIds);
        // 分支1：本轮没有任何可用工具
        if (metadataList.isEmpty()) {
            return """
                    No external tool is active for this turn.
                    Answer directly in Chinese unless a short clarification question is required.
                    """;
        }

        // 分支2：存在选中工具，构造工具提示文本
        StringBuilder sb = new StringBuilder("""
                Active tools for this turn:
                """);
        for (ToolMetadata metadata : metadataList) {
            sb.append("- ").append(metadata.id()).append(": ").append(metadata.summary()).append('\n');
            sb.append("  Rule: ").append(metadata.routingInstruction()).append('\n');
        }
        sb.append("""
                Use only the active tools listed above.
                Prefer a matching active tool instead of guessing.
                If required information is missing, ask a concise follow-up question first.
                """);
        return sb.toString();
    }
}
