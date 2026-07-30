package com.ilink.test.ilinkcheck.infrastructure.client.search;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.DevToArticleProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.WebSearchProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AggregatedSearchService {

    private final DevToArticleProvider devToArticleProvider;
    private final WebSearchProvider webSearchProvider;

    public AggregatedSearchService(DevToArticleProvider devToArticleProvider,
                                    WebSearchProvider webSearchProvider) {
        this.devToArticleProvider = devToArticleProvider;
        this.webSearchProvider = webSearchProvider;
    }

    public AggregatedResults search(String query, int limitPerSource) {
        List<DevToArticle> articles = devToArticleProvider.fetchByTag(query, limitPerSource);
        List<SearchResult> webResults = webSearchProvider.search(query, limitPerSource);
        log.info("聚合搜索: query={}, Dev.to={}篇, Web={}条", query, articles.size(), webResults.size());
        return new AggregatedResults(articles, webResults);
    }

    public List<UnifiedItem> searchUnified(String query, int limit) {
        List<DevToArticle> articles = devToArticleProvider.fetchByTag(query, limit);
        List<SearchResult> webResults = webSearchProvider.search(query, limit);

        List<UnifiedItem> unified = new ArrayList<>();

        for (DevToArticle a : articles) {
            unified.add(new UnifiedItem(
                    a.title(),
                    a.url(),
                    a.description(),
                    "devto",
                    a.tags(),
                    a.positiveReactions()
            ));
        }

        for (SearchResult r : webResults) {
            unified.add(new UnifiedItem(
                    r.title(),
                    r.url(),
                    r.snippet(),
                    "web",
                    "",
                    0
            ));
        }

        log.info("归一化搜索: query={}, 共 {} 条", query, unified.size());
        return unified;
    }

    public List<UnifiedItem> searchTech(String query, int limit) {
        List<DevToArticle> articles = devToArticleProvider.fetchByTag(query, limit / 2 + 1);
        List<SearchResult> webResults = webSearchProvider.search(query, limit / 2 + 1);

        List<UnifiedItem> unified = new ArrayList<>();

        for (DevToArticle a : articles) {
            unified.add(new UnifiedItem(
                    a.title(), a.url(), a.description(),
                    "article", a.tags(), a.positiveReactions()
            ));
        }

        for (SearchResult r : webResults) {
            unified.add(new UnifiedItem(
                    r.title(), r.url(), r.snippet(),
                    "resource", "", 0
            ));
        }

        if (unified.size() > limit) {
            unified = unified.subList(0, limit);
        }

        return unified;
    }

    public record UnifiedItem(
            String title,
            String url,
            String summary,
            String type,
            String tags,
            int popularity
    ) {}

    public record AggregatedResults(
            List<DevToArticle> articles,
            List<SearchResult> webResults
    ) {
        public boolean isEmpty() {
            return articles.isEmpty() && webResults.isEmpty();
        }
        public int totalCount() {
            return articles.size() + webResults.size();
        }
    }
}
