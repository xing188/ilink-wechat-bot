package com.ilink.test.ilinkcheck.application.reminder.parser;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderAction;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRaw;
import com.ilink.test.ilinkcheck.application.schedule.ScheduleParser;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleParseResult;
import com.ilink.test.ilinkcheck.application.schedule.model.SchedulePlan;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.regex.Pattern;

/**
 * 提醒解析器。
 * 这里只保留“提炼提醒内容”的职责，具体的定时规则统一交给 ScheduleParser。
 */
@Service
public class ReminderParser {

    private final ScheduleParser scheduleParser;

    public ReminderParser(ScheduleParser scheduleParser) {
        this.scheduleParser = scheduleParser;
    }

    public ReminderRaw parse(String request, Clock clock) {
        String normalized = normalize(request);
        if (!StringUtils.hasText(normalized)) {
            throw new ReminderParseException("提醒内容不能为空");
        }

        ScheduleParseResult parsed = scheduleParser.parse(normalized, clock);
        if (!parsed.resolved()) {
            throw new ReminderParseException(parsed.prompt());
        }

        SchedulePlan plan = parsed.plan();
        String content = cleanupContent(removeMatchedSegment(normalized, parsed.matchedText()));
        if (!StringUtils.hasText(content)) {
            throw new ReminderParseException("请补充提醒内容，例如“明天上午9点提醒我开会”或“每周二上午9点推送科技新闻”。");
        }

        return new ReminderRaw(
                content,
                plan.scheduleLabel(),
                plan.recurrence(),
                plan.nextFireAt(),
                plan.cronExpression(),
                plan.intervalValue(),
                plan.intervalUnit(),
                resolveAction(content)
        );
    }

    private ReminderAction resolveAction(String content) {
        if (content.contains("新闻") || content.contains("热点") || content.contains("资讯")) {
            return ReminderAction.FETCH_NEWS;
        }
        return ReminderAction.NONE;
    }

    private String removeMatchedSegment(String request, String matchedText) {
        if (!StringUtils.hasText(request) || !StringUtils.hasText(matchedText)) {
            return request;
        }
        return request.replaceFirst(Pattern.quote(matchedText), "").trim();
    }

    private String cleanupContent(String text) {
        String result = normalize(text);
        result = result.replaceFirst("^(提醒我|帮我|请帮我|请|麻烦|记得|定时|自动)\\s*", "");
        result = result.replaceFirst("^(在|于|给我)\\s*", "");
        return result.replaceAll("[\\s，。,.!！?？]+$", "").trim();
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.replace('\u3000', ' ').replaceAll("\\s+", " ").trim();
    }
}
