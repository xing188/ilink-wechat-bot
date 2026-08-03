package com.ilink.test.ilinkcheck.application.agent.orchestrator.model;

/**
 * Agent 工作流共享上下文里使用的键。
 * 统一集中定义，避免各阶段到处硬编码字符串。
 */
public final class AgentArtifacts {

    public static final String REQUEST_REPOSITORY = "requestRepository";
    public static final String REQUEST_TOPIC = "requestTopic";
    public static final String REQUEST_DRAFT = "requestDraft";
    public static final String REQUEST_SCHEDULE_TIME = "requestScheduleTime";
    public static final String REQUEST_SCHEDULE_HOUR = "requestScheduleHour";
    public static final String REQUEST_SCHEDULE_MINUTE = "requestScheduleMinute";
    public static final String REQUEST_SCHEDULE_PENDING_TEXT = "requestSchedulePendingText";
    public static final String REQUEST_SCHEDULE_MATCHED_TEXT = "requestScheduleMatchedText";

    public static final String REPO_INFO = "repoInfo";
    public static final String README_RAW = "readmeRaw";
    public static final String README_SUMMARY_JSON = "readmeSummaryJson";
    public static final String TRENDING_REPOS = "trendingRepos";
    public static final String ROADMAP_ARTICLES = "roadmapArticles";
    public static final String ROADMAP_REFERENCES = "roadmapReferences";
    public static final String ROADMAP_PLAN = "roadmapPlan";
    public static final String ROADMAP_TRANSLATE = "roadmapTranslate";
    public static final String REVIEW_SUGGESTIONS = "reviewSuggestions";
    public static final String REVIEW_REPORT = "reviewReport";
    public static final String REVIEW_FIXED_TEXT = "reviewFixedText";
    public static final String DIGEST_CONTENT = "digestContent";

    private AgentArtifacts() {
    }
}
