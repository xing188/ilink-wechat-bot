package com.ilink.test.ilinkcheck.application.agent.dto.github;

public record TrendingRepo(
        String owner,
        String name,
        String description,
        String language,
        int starMetricValue,
        String url,
        MetricType metricType,
        Source source
) {

    public enum MetricType {
        DAILY_STARS("今日新增 Star"),
        TOTAL_STARS("当前 Star");

        private final String label;

        MetricType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Source {
        COMMUNITY_TRENDING_API,
        GITHUB_SEARCH_API,
        MOCK
    }

    public String formattedStarMetric() {
        return metricType.label() + ": " + starMetricValue;
    }

    public boolean isFallbackMetric() {
        return metricType == MetricType.TOTAL_STARS;
    }
}
