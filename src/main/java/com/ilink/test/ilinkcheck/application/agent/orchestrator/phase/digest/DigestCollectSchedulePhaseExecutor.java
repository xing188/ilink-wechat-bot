package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.digest;

import com.ilink.test.ilinkcheck.application.agent.feature.digest.DailyDigestSubscriptionService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentArtifacts;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentPhase;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentState;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.AgentTaskType;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutionResult;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.PhaseExecutor;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.schedule.ScheduleParser;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleParseResult;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleParseStatus;
import com.ilink.test.ilinkcheck.application.schedule.model.SchedulePlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 晨报流程第一阶段。
 * 这里统一复用通用定时解析器，但保留“多轮补全”的对话能力：
 * 1. 首次只说“帮我订阅技术晨报”时，继续追问推送计划；
 * 2. 首次只说“每周二”时，下一轮补“上午9点”也能续上；
 * 3. 首次只说“9点”时，下一轮补“上午/下午/21:00”也能续上。
 */
@Service
public class DigestCollectSchedulePhaseExecutor implements PhaseExecutor {

    private static final Pattern SCHEDULE_FRAME_PATTERN = Pattern.compile(
            "每天|每日|每周|星期|礼拜|每月|个月|分钟|小时|今天|明天|后天|之后|号|日|周[一二三四五六日天1-7]"
    );
    private static final Pattern EXPLICIT_DATE_PATTERN = Pattern.compile(
            "\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}|\\d{1,2}月\\d{1,2}[号日]?|\\d{1,2}[./-]\\d{1,2}"
    );

    // 晨报订阅业务服务（封装订阅入库、定时解析、业务兜底规则）
    private final DailyDigestSubscriptionService dailyDigestSubscriptionService;
    private final ScheduleParser fallbackScheduleParser;
    private final Clock fallbackClock;

    @Autowired
    public DigestCollectSchedulePhaseExecutor(DailyDigestSubscriptionService dailyDigestSubscriptionService) {
        this.dailyDigestSubscriptionService = dailyDigestSubscriptionService;
        this.fallbackScheduleParser = null;
        this.fallbackClock = null;
    }

    /**
     * 兼容旧测试构造方式。
     */
    public DigestCollectSchedulePhaseExecutor() {
        this.dailyDigestSubscriptionService = null;
        this.fallbackScheduleParser = new ScheduleParser();
        this.fallbackClock = Clock.systemDefaultZone();
    }

    /**
     * PhaseExecutor 标准接口：声明当前执行器支持的任务类型+阶段
     * 只有任务类型=晨报订阅、阶段=定时收集阶段，才交给本类执行
     */
    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.DAILY_DIGEST_SUBSCRIBE && phase == AgentPhase.DIGEST_FETCH;
    }

    /**
     * 阶段核心执行入口
     */
    @Override
    public PhaseExecutionResult execute(AgentState state) {
        // 优先判断：是否存在上一轮未收集完成的【待补齐定时文本】
        // 如果存在，进入多轮拼接逻辑
        PhaseExecutionResult pendingFollowUp = continuePendingSchedule(state);
        if (pendingFollowUp != null) {
            return pendingFollowUp;
        }

        // 不存在待补全内容，直接解析本轮用户最新消息
        ScheduleParseResult latest = resolve(state.getLatestUserMessage());
        // 解析成功：进入校验，准备流转下一阶段
        if (latest.resolved()) {
            return onResolved(latest.plan());
        }
        // 解析结果存在歧义（例如只输入9点，分不清上午下午）
        if (latest.ambiguous()) {
            return waitForUser(latest.prompt(), pendingArtifacts(state.getLatestUserMessage()));
        }

        // 用户输入文本不像定时规则，引导用户输入推送时间
        if (!looksLikeScheduleInput(state.getLatestUserMessage())) {
            return waitForUser(defaultDigestPrompt(), clearPendingArtifacts());
        }

        String prompt = StringUtils.hasText(latest.prompt()) ? latest.prompt() : defaultDigestPrompt();
        return waitForUser(prompt, pendingArtifacts(state.getLatestUserMessage()));
    }

    /**
     * 【核心：多轮拼接逻辑】
     * 处理“上一轮已经拿到一半规则，这一轮只补一部分”的场景。
     * 例如：
     * - 上一轮：每周二
     * - 下一轮：上午9点
     * 或
     * - 上一轮：9点推送
     * - 下一轮：上午
     *
     * 返回非null → 需要继续等待用户补充；返回null → 没有待补全数据，正常走本轮解析
     */
    private PhaseExecutionResult continuePendingSchedule(AgentState state) {
        // 从Agent运行状态缓存取出上一轮未完成的定时文本
        String pendingText = state.getArtifact(AgentArtifacts.REQUEST_SCHEDULE_PENDING_TEXT, String.class);
        // 没有待补全文本，直接退出，走正常流程
        if (!StringUtils.hasText(pendingText)) {
            return null;
        }

        String latestMessage = state.getLatestUserMessage();
        // 如果本轮用户输入包含周期关键词，视为全新一轮输入，不再拼接旧文本
        if (!StringUtils.hasText(latestMessage) || containsScheduleFrame(latestMessage)) {
            return null;
        }

        // 拼接旧文本 + 用户本轮补充文本，生成完整待解析文本
        String candidateText = buildPendingCandidate(state, pendingText, latestMessage);
        ScheduleParseResult parsed = resolve(candidateText);
        // 拼接后解析成功，直接流转下一阶段
        if (parsed.resolved()) {
            return onResolved(parsed.plan());
        }

        // 仍然解析失败，继续等待用户输入，缓存新拼接后的文本
        String prompt = StringUtils.hasText(parsed.prompt()) ? parsed.prompt() : defaultDigestPrompt();
        return waitForUser(prompt, pendingArtifacts(candidateText));
    }

    /**
     * 组装拼接文本，支持两种智能替换策略，不粗暴简单字符串相加
     * 策略1：识别用户补充「上午/下午」，替换原有模糊时间文本
     * 策略2：识别用户补充完整时间，直接替换旧时间片段
     * 兜底：直接字符串拼接
     */
    private String buildPendingCandidate(AgentState state, String pendingText, String latestMessage) {
        String pendingMatchedText = state.getArtifact(AgentArtifacts.REQUEST_SCHEDULE_MATCHED_TEXT, String.class);
        Integer pendingHour = state.getArtifact(AgentArtifacts.REQUEST_SCHEDULE_HOUR, Integer.class);
        Integer pendingMinute = state.getArtifact(AgentArtifacts.REQUEST_SCHEDULE_MINUTE, Integer.class);

        // 场景A：旧文本存在模糊时间，用户补充「上午/下午」时段
        Optional<String> latestPeriod = AgentTextUtils.extractSchedulePeriod(latestMessage);
        if (StringUtils.hasText(pendingMatchedText) && latestPeriod.isPresent() && pendingHour != null && pendingMinute != null) {
            Optional<String> normalized = AgentTextUtils.composeScheduleTime(pendingHour, pendingMinute, latestPeriod.get());
            if (normalized.isPresent()) {
                return replacePendingTime(pendingText, pendingMatchedText, normalized.get());
            }
        }

        // 场景B：旧文本存在模糊时间，用户补充完整时间「21:00」
        AgentTextUtils.ScheduleTimeParseResult latestTime = AgentTextUtils.parseScheduleTime(latestMessage);
        if (StringUtils.hasText(pendingMatchedText) && latestTime.resolved()) {
            return replacePendingTime(pendingText, pendingMatchedText, latestTime.normalizedTime());
        }

        // 兜底：简单拼接
        return pendingText + " " + latestMessage.trim();
    }

    /**
     * 字符串替换：把旧文本里模糊时间片段，替换为标准化时间
     * 使用quote规避正则特殊字符导致替换失败
     */
    private String replacePendingTime(String pendingText, String pendingMatchedText, String normalizedTime) {
        return pendingText.replaceFirst(
                Pattern.quote(pendingMatchedText),
                Matcher.quoteReplacement(normalizedTime)
        );
    }

    /**
     * 定时计划解析成功后的处理逻辑
     */
    private PhaseExecutionResult onResolved(SchedulePlan plan) {
        // 业务约束：晨报不支持一次性任务
        if (plan.recurrence() == ReminderRecurrence.ONCE) {
            return waitRecurringPlan();
        }
        // 校验通过，携带定时文本，流转至下一个阶段 DIGEST_COMPOSE
        return nextToCompose(plan.scheduleLabel());
    }

    /**
     * 流转到【组装/创建订阅阶段】，把解析好的定时文本存入state上下文artifact
     */
    private PhaseExecutionResult nextToCompose(String scheduleText) {
        Map<String, Object> delta = clearPendingArtifacts();
        delta.put(AgentArtifacts.REQUEST_SCHEDULE_TIME, scheduleText);
        return PhaseExecutionResult.next(AgentPhase.DIGEST_COMPOSE, delta);
    }

    /**
     * 用户输入一次性任务，返回提示，继续停留在当前阶段等待用户重新输入循环定时
     */
    private PhaseExecutionResult waitRecurringPlan() {
        return PhaseExecutionResult.waitForUser(
                AgentPhase.DIGEST_FETCH,
                "晨报订阅需要重复执行，请改成 `每天...`、`每周...`、`每月...` 或 `每2周...` 这类表达。",
                clearPendingArtifacts()
        );
    }

    /**
     * 通用：等待用户继续输入，保持当前阶段不变，携带上下文变更
     */
    private PhaseExecutionResult waitForUser(String prompt, Map<String, Object> artifactsDelta) {
        return PhaseExecutionResult.waitForUser(AgentPhase.DIGEST_FETCH, prompt, artifactsDelta);
    }

    /**
     * 缓存【未完成的定时片段】存入AgentState的artifact
     * 用于多轮对话续接，保存小时、分钟、原始匹配文本，方便后续替换补齐
     */
    private Map<String, Object> pendingArtifacts(String pendingText) {
        Map<String, Object> delta = clearPendingArtifacts();
        delta.put(AgentArtifacts.REQUEST_SCHEDULE_PENDING_TEXT, pendingText);

        AgentTextUtils.ScheduleTimeParseResult timeParsed = AgentTextUtils.parseScheduleTime(pendingText);
        if (timeParsed.ambiguous()) {
            delta.put(AgentArtifacts.REQUEST_SCHEDULE_HOUR, timeParsed.hour());
            delta.put(AgentArtifacts.REQUEST_SCHEDULE_MINUTE, timeParsed.minute());
            delta.put(AgentArtifacts.REQUEST_SCHEDULE_MATCHED_TEXT, timeParsed.matchedText());
        }
        return delta;
    }

    /**
     * 清空所有多轮对话临时缓存
     */
    private Map<String, Object> clearPendingArtifacts() {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put(AgentArtifacts.REQUEST_SCHEDULE_PENDING_TEXT, "");
        delta.put(AgentArtifacts.REQUEST_SCHEDULE_MATCHED_TEXT, "");
        delta.put(AgentArtifacts.REQUEST_SCHEDULE_HOUR, -1);
        delta.put(AgentArtifacts.REQUEST_SCHEDULE_MINUTE, -1);
        return delta;
    }

    /**
     * 默认引导话术，询问用户推送时间
     */
    private String defaultDigestPrompt() {
        return "你希望我按什么计划推送技术晨报？例如 `每天 08:30`、`每周二上午9点`、`每月5号晚上8点` 或 `每2周周二上午9点`。";
    }

    /**
     * 判断用户输入文本是否疑似定时相关内容
     */
    private boolean looksLikeScheduleInput(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        AgentTextUtils.ScheduleTimeParseResult timeParsed = AgentTextUtils.parseScheduleTime(text);
        // 能识别时间 或者 时间模糊 → 判定为定时输入
        if (timeParsed.resolved() || timeParsed.ambiguous()) {
            return true;
        }
        // 文本包含周期关键词
        return containsScheduleFrame(text);
    }

    /**
     * 判断文本是否包含周期/日期关键字
     */
    private boolean containsScheduleFrame(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return SCHEDULE_FRAME_PATTERN.matcher(text).find() || EXPLICIT_DATE_PATTERN.matcher(text).find();
    }

    /**
     * 解析定时文本【关键：复用上层DailyDigestSubscriptionService内置解析逻辑】
     * 生产环境调用 subscriptionService.resolveSchedulePlan()
     * 保证：本阶段 和 订阅服务 使用同一套定时解析逻辑，不会出现解析结果不一致
     */
    private ScheduleParseResult resolve(String text) {
        if (dailyDigestSubscriptionService != null) {
            return dailyDigestSubscriptionService.resolveSchedulePlan(text);
        }

        ScheduleParseResult parsed = fallbackScheduleParser.parse(text, fallbackClock);
        if (parsed.resolved() && parsed.plan().recurrence() != ReminderRecurrence.ONCE) {
            return parsed;
        }

        AgentTextUtils.ScheduleTimeParseResult timeParsed = AgentTextUtils.parseScheduleTime(text);
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

    private SchedulePlan buildDailyPlan(String normalizedTime) {
        String[] parts = normalizedTime.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        ZonedDateTime now = ZonedDateTime.now(fallbackClock);
        ZonedDateTime candidate = now.withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);
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
}
