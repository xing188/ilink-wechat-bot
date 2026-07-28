package com.ilink.test.ilinkcheck.application.tool.selection;

import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import com.ilink.test.ilinkcheck.application.tool.ToolMetadata;
import com.ilink.test.ilinkcheck.application.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedToolSelectorTest {

    @Test
    void shouldSelectReminderToolForReminderLanguage() {
        ToolRegistry registry = new ToolRegistry(List.of(
                tool("reminder", List.of("提醒", "待办", "分钟后")),
                tool("weather", List.of("天气", "下雨"))
        ));

        RuleBasedToolSelector selector = new RuleBasedToolSelector(registry);

        ToolSelectionResult result = selector.trySelect("十分钟后提醒我开会").orElseThrow();

        assertThat(result.toolIds()).containsExactly("reminder");
        assertThat(result.strategy()).isEqualTo("rule");
    }

    @Test
    void shouldReturnEmptyWhenNoKeywordMatches() {
        ToolRegistry registry = new ToolRegistry(List.of(
                tool("reminder", List.of("提醒", "待办")),
                tool("weather", List.of("天气", "下雨"))
        ));

        RuleBasedToolSelector selector = new RuleBasedToolSelector(registry);

        assertThat(selector.trySelect("你好，最近怎么样")).isEmpty();
    }

    private ChatFunctionTool tool(String id, List<String> keywords) {
        return new ChatFunctionTool() {
            @Override
            public ToolMetadata metadata() {
                return new ToolMetadata(
                        id,
                        id + " summary",
                        keywords,
                        List.of(),
                        "rule"
                );
            }
        };
    }
}
