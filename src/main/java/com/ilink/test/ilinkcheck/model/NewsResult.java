package com.ilink.test.ilinkcheck.model;

import java.util.List;

public record NewsResult(
        List<NewsItem> newsList,
        int totalCount,
        int currentPage
) {

    public NewsResult {
        newsList = newsList == null ? List.of() : List.copyOf(newsList);
    }

    public boolean isEmpty() {
        return newsList.isEmpty();
    }

    public static NewsResult empty() {
        return new NewsResult(List.of(), 0, 0);
    }

    public record NewsItem(
            String id,
            String title,
            String url,
            String ctime,
            String description,
            String picUrl,
            String source
    ) {
    }
}
