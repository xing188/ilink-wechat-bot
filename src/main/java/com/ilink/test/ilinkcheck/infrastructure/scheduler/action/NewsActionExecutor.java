package com.ilink.test.ilinkcheck.infrastructure.scheduler.action;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderAction;
import com.ilink.test.ilinkcheck.application.tool.categories.news.NewsQueryService;
import org.springframework.stereotype.Component;

@Component
public class NewsActionExecutor implements ActionExecutor {

    private final NewsQueryService newsQueryService;

    public NewsActionExecutor(NewsQueryService newsQueryService) {
        this.newsQueryService = newsQueryService;
    }

    @Override
    public ReminderAction supportedAction() {
        return ReminderAction.FETCH_NEWS;
    }

    @Override
    public String execute(String userId, String params) {
        String category = (params != null && !params.isBlank()) ? params : null;
        return newsQueryService.queryNews(null, category, 5);
    }
}
