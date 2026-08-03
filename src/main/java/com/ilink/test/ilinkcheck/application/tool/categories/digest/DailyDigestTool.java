package com.ilink.test.ilinkcheck.application.tool.categories.digest;

import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestContent;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestComposeService;
import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestSubscriptionService;
import com.ilink.test.ilinkcheck.application.reminder.context.ReminderRequestContext;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleParseResult;
import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import com.ilink.test.ilinkcheck.application.tool.ToolMetadata;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 技术推送 Tool。
 * 这里保留统一入口，底层仍然复用同一套定时解析和订阅逻辑。
 */
@Component
public class DailyDigestTool implements ChatFunctionTool {

    private final DailyDigestComposeService dailyDigestComposeService;
    private final DailyDigestSubscriptionService dailyDigestSubscriptionService;

    @Autowired
    public DailyDigestTool(DailyDigestComposeService dailyDigestComposeService,
                           DailyDigestSubscriptionService dailyDigestSubscriptionService) {
        this.dailyDigestComposeService = dailyDigestComposeService;
        this.dailyDigestSubscriptionService = dailyDigestSubscriptionService;
    }

    @Tool(
            name = "subscribeDailyDigest",
            description = """
                    订阅技术推送，并根据给定的推送计划生成一份预览。
                    支持每天、每周、每月以及每 2 周这类重复规则。
                    """
    )
    public String subscribeDailyDigest(
            @ToolParam(required = true, description = "推送计划，例如“每天早上8点”“每周二上午9点”“每月5号晚上8点”")
            String scheduleTime) {

        ScheduleParseResult parsed = dailyDigestSubscriptionService.resolveSchedulePlan(scheduleTime);
        if (!parsed.resolved()) {
            return parsed.prompt();
        }
        if (parsed.plan().recurrence() == ReminderRecurrence.ONCE) {
            return "技术推送需要重复执行，请改成 `每天...`、`每周...`、`每月...` 或 `每2周...` 这类表达。";
        }

        DigestContent digestContent = dailyDigestComposeService.collect();
        String scheduleLabel = parsed.plan().scheduleLabel();
        String userId = ReminderRequestContext.currentUserId().orElse(null);
        if (userId != null && !userId.isBlank()) {
            dailyDigestSubscriptionService.subscribe(userId, parsed.plan());
            return dailyDigestComposeService.buildPreview(scheduleLabel, digestContent);
        }

        return dailyDigestComposeService.buildPreview(scheduleLabel, digestContent)
                + System.lineSeparator()
                + System.lineSeparator()
                + "注意：当前没有可用的用户上下文，所以这里只生成了预览，还没有创建真实订阅。";
    }

    @Tool(
            name = "previewDailyDigest",
            description = """
                    生成一份技术推送预览，但不创建订阅。
                    用户想先看每周 GitHub 好项目时可以先调用这个工具。
                    """
    )
    public String previewDailyDigest() {
        DigestContent digestContent = dailyDigestComposeService.collect();
        return dailyDigestComposeService.buildPreview("未设置", digestContent);
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                "dailyDigest",
                "订阅或预览技术推送，支持按每天、每周、每月、每 2 周等计划推送 GitHub 好项目。",
                List.of("晨报", "订阅", "推送", "daily digest", "GitHub 好项目", "技术推送"),
                List.of("每周二上午9点给我推送 GitHub 好项目", "每月5号晚上8点提醒我看技术项目", "先帮我预览今天会推什么"),
                "当用户想订阅技术推送，或先预览一份 GitHub 周报时，优先使用此工具。"
        );
    }
}
