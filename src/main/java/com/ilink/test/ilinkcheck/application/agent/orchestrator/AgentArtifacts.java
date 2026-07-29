package com.ilink.test.ilinkcheck.application.agent.orchestrator;

/**
 * 工作流共享上下文里使用的键。
 * 先集中定义，避免各阶段硬编码字符串。
 */
public final class AgentArtifacts {

    public static final String HOTSPOT_KEYWORD = "hotspotKeyword";
    public static final String HOT_SEARCH_RESULTS = "hotSearchResults";
    public static final String SENTIMENT_REPORT = "sentimentReport";
    public static final String TOPIC_OPTIONS = "topicOptions";
    public static final String SELECTION_PROMPT_SENT = "selectionPromptSent";
    public static final String SELECTED_TOPIC = "selectedTopic";
    public static final String WRITING_DRAFT = "writingDraft";
    public static final String KNOWLEDGE_ITEMS = "knowledgeItems";

    private AgentArtifacts() {
    }
}
