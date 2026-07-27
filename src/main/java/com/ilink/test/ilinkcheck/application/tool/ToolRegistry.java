package com.ilink.test.ilinkcheck.application.tool;

import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ToolRegistry {

    private final List<ChatFunctionTool> tools;
    private final Map<String, ChatFunctionTool> toolsById;
    private final Map<String, ToolMetadata> metadataById;

    public ToolRegistry(List<ChatFunctionTool> tools) {
        this.tools = List.copyOf(tools);
        LinkedHashMap<String, ChatFunctionTool> byId = new LinkedHashMap<>();
        LinkedHashMap<String, ToolMetadata> metadataMap = new LinkedHashMap<>();
        for (ChatFunctionTool tool : this.tools) {
            ToolMetadata metadata = resolveMetadata(tool);
            ChatFunctionTool previous = byId.put(metadata.id(), tool);
            if (previous != null) {
                throw new IllegalStateException("Duplicate tool id: " + metadata.id());
            }
            metadataMap.put(metadata.id(), metadata);
        }
        this.toolsById = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
        this.metadataById = Collections.unmodifiableMap(new LinkedHashMap<>(metadataMap));
    }

    public Object[] toolObjects() {
        return tools.toArray(Object[]::new);
    }

    public Object[] toolObjects(Collection<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new Object[0];
        }

        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>(toolIds);
        return uniqueIds.stream()
                .map(toolsById::get)
                .filter(Objects::nonNull)
                .toArray(Object[]::new);
    }

    public List<ToolMetadata> allMetadata() {
        return List.copyOf(metadataById.values());
    }

    public List<ToolMetadata> metadataFor(Collection<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>(toolIds);
        return uniqueIds.stream()
                .map(metadataById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<String> allToolIds() {
        return List.copyOf(metadataById.keySet());
    }

    public boolean containsToolId(String toolId) {
        return toolsById.containsKey(toolId);
    }

    public ChatFunctionTool toolById(String toolId) {
        return toolsById.get(toolId);
    }

    private ToolMetadata resolveMetadata(ChatFunctionTool tool) {
        ToolMetadata declared = tool.metadata();
        if (declared != null && !"General-purpose tool".equals(declared.summary())) {
            return declared;
        }

        Class<?> toolClass = AopUtils.getTargetClass(tool);
        String simpleName = toolClass != null ? toolClass.getSimpleName() : tool.getClass().getSimpleName();

        return switch (simpleName) {
            case "CurrentTimeTool" -> new ToolMetadata(
                    "time",
                    "Get the current date or time for a timezone.",
                    List.of("几点", "现在几点", "当前时间", "日期", "今天几号", "时区"),
                    List.of("现在几点了", "今天几号", "纽约现在时间"),
                    "Use this tool for current date, current time, or timezone questions."
            );
            case "NewsQueryTool" -> new ToolMetadata(
                    "news",
                    "Query recent news, headlines, and trending updates.",
                    List.of("新闻", "热点", "资讯", "最新消息", "最近发生", "热搜"),
                    List.of("今天有什么新闻", "推送科技新闻", "最近热点"),
                    "Use this tool for recent news or trending events instead of answering from stale memory."
            );
            case "BookSearchTool" -> new ToolMetadata(
                    "book",
                    "Search books, recommend books, or look up details by ISBN.",
                    List.of("图书", "书籍", "小说", "作者", "isbn", "推荐几本书"),
                    List.of("推荐几本科幻小说", "查一下这本书", "9787536692930 是什么书"),
                    "Use this tool for searching, recommending, or looking up books."
            );
            case "ExpenseTool" -> new ToolMetadata(
                    "expense",
                    "Record expenses, list expenses, or calculate expense totals.",
                    List.of("记账", "支出", "花了", "消费记录", "账单", "报销"),
                    List.of("帮我记一笔支出", "查看消费记录", "这个月花了多少钱"),
                    "Use this tool for expense recording and expense statistics."
            );
            case "IpLocationTool" -> new ToolMetadata(
                    "iplocation",
                    "Look up the location and ISP information of an IP address.",
                    List.of("ip归属地", "ip地址", "ip位置", "哪个国家的ip", "ip location"),
                    List.of("8.8.8.8 是哪的", "查一下这个 IP", "114.114.114.114 在哪"),
                    "Use this tool for IP location, origin, ISP, country, city, or timezone lookups."
            );
            case "ReminderTool" -> new ToolMetadata(
                    "reminder",
                    "Create timed reminders, recurring reminders, or list pending reminders.",
                    List.of("提醒", "待办", "定时", "分钟后", "小时后", "每天", "待办事项"),
                    List.of("十分钟后提醒我开会", "今天下午五点提醒我", "查看待办事项"),
                    "Use this tool for reminder creation or pending reminder queries. Do not guess reminder times yourself."
            );
            case "WeatherQueryTool" -> new ToolMetadata(
                    "weather",
                    "Query real-time weather, forecast, and air quality for a city.",
                    List.of("天气", "气温", "下雨", "温度", "空气质量", "天气预报"),
                    List.of("北京今天天气", "明天上海会下雨吗", "深圳空气质量"),
                    "Use this tool for weather questions. If the location is missing, ask the user for the city first."
            );
            case "TranslationTool" -> new ToolMetadata(
                    "translation",
                    "Translate text between languages.",
                    List.of("翻译", "译成", "英文怎么说", "日文怎么说", "translate"),
                    List.of("把这句话翻译成英文", "日文怎么说", "帮我翻译"),
                    "Use this tool for translation tasks. Prefer it over free-form translation when the user clearly asks to translate text."
            );
            default -> {
                String toolId = StringUtils.hasText(declared.id()) ? declared.id() : simpleName;
                yield new ToolMetadata(
                        toolId,
                        declared.summary(),
                        declared.selectionKeywords(),
                        declared.exampleQueries(),
                        declared.routingInstruction()
                );
            }
        };
    }
}
