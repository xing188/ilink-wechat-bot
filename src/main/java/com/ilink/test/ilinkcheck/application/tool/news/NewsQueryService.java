package com.ilink.test.ilinkcheck.application.tool.news;

import com.ilink.test.ilinkcheck.infrastructure.client.news.NewsClientException;
import com.ilink.test.ilinkcheck.infrastructure.client.news.TianApiNewsClient;
import com.ilink.test.ilinkcheck.model.NewsResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NewsQueryService {

    private static final int DEFAULT_NUM = 5;
    private static final int MAX_NUM = 10;
    private static final String NO_RESULT_MESSAGE = "未找到相关新闻，请尝试更换关键词或分类。";

    private final TianApiNewsClient newsClient;

    public NewsQueryService(TianApiNewsClient newsClient) {
        this.newsClient = newsClient;
    }

    public String queryNews(String keyword, String category, Integer num) {
        int resolvedNum = resolveNum(num);

        try {
            NewsResult result = newsClient.query(keyword, category, resolvedNum, 1);
            return formatResult(result, keyword, category);
        }
        catch (NewsClientException e) {
            return switch (e.type()) {
                case INVALID_PARAMETER -> "新闻查询参数无效，请检查关键词和分类。";
                case NOT_FOUND -> "未找到相关新闻数据，请确认分类是否正确。";
                case SERVICE_UNAVAILABLE, UPSTREAM_ERROR -> "新闻服务暂时不可用，请稍后再试。";
            };
        }
        catch (Exception e) {
            return "新闻查询失败，请稍后再试。";
        }
    }

    private int resolveNum(Integer num) {
        if (num == null || num <= 0) {
            return DEFAULT_NUM;
        }
        return Math.min(num, MAX_NUM);
    }

    private String formatResult(NewsResult result, String keyword, String category) {
        if (result.isEmpty()) {
            return NO_RESULT_MESSAGE;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📰 新闻查询结果：");

        if (StringUtils.hasText(keyword)) {
            sb.append("关键词「").append(keyword).append("」");
        }
        if (StringUtils.hasText(category)) {
            sb.append("，分类：").append(resolveCategoryLabel(category));
        }
        sb.append("，共 ").append(result.totalCount()).append(" 条相关新闻");

        if (result.newsList().size() < result.totalCount()) {
            sb.append("，以下为前 ").append(result.newsList().size()).append(" 条");
        }
        sb.append("：\n\n");

        int index = 1;
        for (NewsResult.NewsItem item : result.newsList()) {
            sb.append(index++).append(". ");
            sb.append("**").append(defaultText(item.title(), "无标题")).append("**");

            if (StringUtils.hasText(item.source())) {
                sb.append(" — ").append(item.source());
            }
            if (StringUtils.hasText(item.ctime())) {
                sb.append(" (").append(item.ctime()).append(")");
            }
            sb.append("\n");

            if (StringUtils.hasText(item.description())) {
                String desc = item.description().length() > 120
                        ? item.description().substring(0, 120) + "…"
                        : item.description();
                sb.append("   ").append(desc).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private String resolveCategoryLabel(String category) {
        if (!StringUtils.hasText(category)) {
            return "综合";
        }
        return switch (category.trim().toLowerCase()) {
            case "domestic", "国内" -> "国内";
            case "social", "社会" -> "社会";
            case "international", "国际" -> "国际";
            case "tech", "科技" -> "科技";
            case "entertainment", "娱乐" -> "娱乐";
            case "sports", "体育" -> "体育";
            case "military", "军事" -> "军事";
            default -> category.trim();
        };
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
