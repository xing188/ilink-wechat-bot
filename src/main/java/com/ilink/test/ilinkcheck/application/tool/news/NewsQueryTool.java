package com.ilink.test.ilinkcheck.application.tool.news;

import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class NewsQueryTool implements ChatFunctionTool {

    private final NewsQueryService newsQueryService;

    public NewsQueryTool(NewsQueryService newsQueryService) {
        this.newsQueryService = newsQueryService;
    }

    @Tool(
            name = "queryNews",
            description = """
                    查询最新新闻资讯。支持按关键词搜索和按分类筛选。
                    如果用户询问新闻、最新消息、热点、资讯等，使用本工具。
                    分类支持：domestic(国内)、social(社会)、international(国际)、tech(科技)、entertainment(娱乐)、sports(体育)、military(军事)。
                    如果不指定分类，默认查询综合新闻。
                    如果用户没有明确指定分类，建议传空字符串查询综合新闻。
                    """
    )
    public String queryNews(
            @ToolParam(required = false, description = "搜索关键词，例如 人工智能、世界杯、股市")
            String keyword,
            @ToolParam(required = false, description = "新闻分类：domestic/social/international/tech/entertainment/sports/military，不传则查询综合新闻")
            String category,
            @ToolParam(required = false, description = "返回新闻条数，默认5条，最多10条")
            Integer num) {

        return newsQueryService.queryNews(keyword, category, num);
    }
}
