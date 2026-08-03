package com.ilink.test.ilinkcheck.application.agent.feature.digest;

import com.ilink.test.ilinkcheck.application.agent.dto.digest.DigestSubscription;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.schedule.ScheduleParser;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleParseResult;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleParseStatus;
import com.ilink.test.ilinkcheck.application.schedule.model.SchedulePlan;
import com.ilink.test.ilinkcheck.infrastructure.scheduler.digest.TechDigestScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 晨报订阅服务。
 * 负责校验/保存订阅，并通知调度器注册真实的定时任务。
 */
@Service
public class DailyDigestSubscriptionService {

    private final DigestSubscriptionRepository repository;
    private final TechDigestScheduler techDigestScheduler;
    private final Clock clock;
    private final String defaultZoneId;
    private final ScheduleParser scheduleParser;

    @Autowired
    public DailyDigestSubscriptionService(DigestSubscriptionRepository repository,
                                          TechDigestScheduler techDigestScheduler,
                                          Clock clock,
                                          @Value("${app.digest.zone:Asia/Shanghai}") String defaultZoneId,
                                          ScheduleParser scheduleParser) {
        this.repository = repository;
        this.techDigestScheduler = techDigestScheduler;
        this.clock = clock;
        this.defaultZoneId = defaultZoneId;
        this.scheduleParser = scheduleParser;
    }

    /**
     * 兼容旧测试构造方式。
     */
    public DailyDigestSubscriptionService(DigestSubscriptionRepository repository,
                                          TechDigestScheduler techDigestScheduler,
                                          Clock clock,
                                          String defaultZoneId) {
        this(repository, techDigestScheduler, clock, defaultZoneId, new ScheduleParser());
    }

    public DigestSubscription subscribe(String userId, String scheduleText) {
        ScheduleParseResult parsed = resolveSchedulePlan(scheduleText);
        if (!parsed.resolved()) {
            throw new IllegalArgumentException(parsed.prompt());
        }
        return subscribe(userId, parsed.plan());
    }

    public DigestSubscription subscribe(String userId, SchedulePlan plan) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (plan == null) {
            throw new IllegalArgumentException("schedule plan must not be null");
        }
        //约束：晨报订阅不允许一次性任务
        if (plan.recurrence() == ReminderRecurrence.ONCE) {
            throw new IllegalArgumentException("晨报订阅需要重复执行，请使用“每天/每周/每月/每2周”这类表达。");
        }

        Instant now = clock.instant();
        Instant createdAt = repository.findByUserId(userId.trim())
                .map(DigestSubscription::createdAt)
                .orElse(now);

        DigestSubscription subscription = new DigestSubscription(
                userId.trim(),
                plan.scheduleLabel(),
                defaultZoneId,
                plan.recurrence(),
                plan.nextFireAt(),
                plan.cronExpression(),
                plan.intervalValue(),
                plan.intervalUnit(),
                createdAt,
                now
        );
        DigestSubscription saved = repository.save(subscription);
        techDigestScheduler.scheduleSubscription(saved);
        return saved;
    }

    /**
     * 晨报订阅场景下允许用户只说一个时间点，比如“08:30”“晚上9点”，
     * 这时默认理解成“每天这个时间推送”。
     * 但像“明天 8 点”“2026.10.05 8 点”这种明显的一次性表达，则不做这种兜底。
     */
    public ScheduleParseResult resolveSchedulePlan(String scheduleText) {
        // 第一步：先用通用日程解析器尝试解析
        ScheduleParseResult parsed = scheduleParser.parse(scheduleText, digestClock());
        // 解析成功且本身就是循环任务 → 直接返回，不干预
        if (parsed.resolved() && parsed.plan().recurrence() != ReminderRecurrence.ONCE) {
            return parsed;
        }
        // 判断文本是否【符合纯时间简写格式】，不符合直接返回原始解析结果
        if (!looksLikeDailyShortcut(scheduleText)) {
            return parsed;
        }

        // 走到这里：用户输入仅仅是时间，没有日期，兜底构造【每日任务】
        AgentTextUtils.ScheduleTimeParseResult timeParsed = AgentTextUtils.parseScheduleTime(scheduleText);
        return switch (timeParsed.status()) {
            case RESOLVED -> new ScheduleParseResult(
                    ScheduleParseStatus.RESOLVED,
                    buildDailyPlan(timeParsed.normalizedTime()),
                    timeParsed.matchedText(),
                    null
            );
            case AMBIGUOUS -> new ScheduleParseResult(
                    ScheduleParseStatus.AMBIGUOUS,
                    null,
                    timeParsed.matchedText(),
                    "你刚才提到了 `" + timeParsed.matchedText().trim()
                            + "`，但我还不确定是上午还是下午。请直接回复 `上午9点`、`下午9点` 或 `21:00`。"
            );
            case ABSENT -> parsed;
        };
    }

    public Optional<DigestSubscription> findByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        return repository.findByUserId(userId.trim());
    }

    public List<DigestSubscription> findAllSubscriptions() {
        return repository.findAll();
    }

    public boolean cancel(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }

        Optional<DigestSubscription> existing = repository.findByUserId(userId.trim());
        if (existing.isEmpty()) {
            return false;
        }

        repository.deleteByUserId(userId.trim());
        techDigestScheduler.cancelSubscription(userId.trim());
        return true;
    }

    private boolean looksLikeDailyShortcut(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.trim();
        return !normalized.matches(".*(今天|明天|后天|之后|\\d{4}[./-]|\\d{1,2}月\\d{1,2}|\\d{1,2}[./-]\\d{1,2}|号|日).*");
    }

    //构造每日定时计划
    private SchedulePlan buildDailyPlan(String normalizedTime) {
        String[] parts = normalizedTime.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        ZonedDateTime now = ZonedDateTime.now(digestClock());
        ZonedDateTime candidate = now.withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);

        // 如果当前时间已经超过今天目标时间，起始时间顺延到明天
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }
        return new SchedulePlan(
                "每天 " + normalizedTime,
                ReminderRecurrence.DAILY,
                candidate.toInstant(),
                null,
                null,
                null
        );
    }

    private Clock digestClock() {
        return clock.withZone(ZoneId.of(defaultZoneId));
    }
}
