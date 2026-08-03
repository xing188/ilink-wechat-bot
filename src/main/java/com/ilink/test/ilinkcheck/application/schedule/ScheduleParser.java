package com.ilink.test.ilinkcheck.application.schedule;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleIntervalUnit;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleParseResult;
import com.ilink.test.ilinkcheck.application.schedule.model.ScheduleParseStatus;
import com.ilink.test.ilinkcheck.application.schedule.model.SchedulePlan;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一的自然语言定时解析器。
 * 当前优先支持：
 * 1. 一次性：十分钟后、明天上午 9 点、2026.10.05 21:30
 * 2. 每日：每天晚上 9 点
 * 3. 每周：每周二、每周一三五
 * 4. 每月：每月 5 号晚上 8 点
 * 5. 间隔型：每 5 分钟、每 2 周、每 2 个月 5 号上午 9 点
 */
@Service
public class ScheduleParser {

    private static final Pattern DAILY_PREFIX = Pattern.compile("每天|每日");
    private static final Pattern WEEKLY_PREFIX = Pattern.compile("每(?:周|星期|礼拜)");
    private static final Pattern MONTHLY_PREFIX = Pattern.compile("每(?:月|个月)");
    private static final Pattern INTERVAL_PREFIX = Pattern.compile(
            "每(?:隔)?\\s*(?<value>[0-9零〇一二两三四五六七八九十]+)?\\s*(?<unit>秒钟?|分钟|分|小时|时|天|周|星期|礼拜|个月|月)"
    );
    private static final Pattern RELATIVE_AFTER = Pattern.compile(
            "(?<value>[0-9零〇一二两三四五六七八九十]+)\\s*(?<unit>秒钟?|分钟|分|小时|时|天|周|星期|礼拜|个月|月)\\s*(?:后|之后)"
    );
    private static final Pattern RELATIVE_DAY = Pattern.compile("今天|明天|后天");
    private static final Pattern EXPLICIT_DATE = Pattern.compile(
            "(?:(?<year>\\d{4})\\s*(?:[./-]|年)\\s*)?"
                    + "(?<month>\\d{1,2})\\s*(?:[./-]|月)\\s*"
                    + "(?<day>\\d{1,2})(?:\\s*(?:日|号))?"
    );
    private static final Pattern MONTH_DAY = Pattern.compile("(?<day>[0-9零〇一二两三四五六七八九十]+)\\s*(?:日|号)");
    private static final Pattern EXACT_TIME = Pattern.compile("\\b(?<hour>[01]?\\d|2[0-3]):(?<minute>[0-5]\\d)\\b");
    private static final Pattern CHINESE_TIME = Pattern.compile(
            "(?<period>凌晨|清晨|今晨|今早|早晨|早上|上午|中午|下午|傍晚|晚上|今晚|晚间)?\\s*"
                    + "(?<hour>[0-9零〇一二两三四五六七八九十]+)\\s*(?:点|时)"
                    + "(?:\\s*(?<minute>[0-9零〇一二两三四五六七八九十]+|半)\\s*(?:分|分钟)?)?"
    );
    private static final DateTimeFormatter LABEL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINESE);
    private static final DateTimeFormatter LABEL_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINESE);
    private static final String DEFAULT_PROMPT = "没看懂时间，请说“十分钟后”“明天上午9点”“每周二上午9点”或“每月5号晚上8点”。";

    public ScheduleParseResult parse(String request, Clock clock) {
        String normalized = normalize(request);
        if (!StringUtils.hasText(normalized)) {
            return absent("请先告诉我执行时间，例如“明天上午9点提醒我开会”。");
        }

        ZonedDateTime now = ZonedDateTime.now(clock);

        ScheduleParseResult recurring = firstResolved(
                parseDaily(normalized, now),
                parseWeekly(normalized, now),
                parseMonthly(normalized, now),
                parseIntervalRecurring(normalized, now)
        );
        if (recurring != null) {
            return recurring;
        }

        ScheduleParseResult oneOff = firstResolved(
                parseExplicitDate(normalized, now),
                parseRelativeDay(normalized, now),
                parseRelativeAfter(normalized, now),
                parseAbsoluteTimeOnly(normalized, now)
        );
        if (oneOff != null) {
            return oneOff;
        }

        return absent(DEFAULT_PROMPT);
    }

    private ScheduleParseResult parseDaily(String text, ZonedDateTime now) {
        Matcher prefix = DAILY_PREFIX.matcher(text);
        if (!prefix.find()) {
            return null;
        }

        TimeParseResult time = parseTime(text.substring(prefix.end()));
        if (time.status == TimeParseStatus.ABSENT) {
            return ambiguous("你想每天几点执行？例如 `每天早上8点` 或 `每天 21:30`。");
        }
        if (time.status == TimeParseStatus.AMBIGUOUS) {
            return ambiguous(time.prompt);
        }

        ZonedDateTime candidate = now.withHour(time.hour)
                .withMinute(time.minute)
                .withSecond(0)
                .withNano(0);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }

        return resolved(
                new SchedulePlan(
                        "每天 " + candidate.format(LABEL_TIME),
                        ReminderRecurrence.DAILY,
                        candidate.toInstant(),
                        null,
                        null,
                        null
                ),
                text.substring(prefix.start(), prefix.end() + time.end)
        );
    }

    private ScheduleParseResult parseWeekly(String text, ZonedDateTime now) {
        Matcher prefix = WEEKLY_PREFIX.matcher(text);
        if (!prefix.find()) {
            return null;
        }

        String tail = text.substring(prefix.end());
        TimeParseResult time = parseTime(tail);
        String dayText = time.status == TimeParseStatus.ABSENT ? tail : tail.substring(0, time.start);
        List<DayOfWeek> days = parseWeekdays(dayText);
        if (days.isEmpty()) {
            return ambiguous("你想每周哪几天执行？例如 `每周二上午9点`、`每周一三五晚上8点`。");
        }
        if (time.status == TimeParseStatus.ABSENT) {
            return ambiguous("你想" + formatWeeklyLabel(days) + "几点执行？例如 `" + formatWeeklyLabel(days) + "上午9点`。");
        }
        if (time.status == TimeParseStatus.AMBIGUOUS) {
            return ambiguous(time.prompt);
        }

        String cron = buildWeeklyCron(days, time.hour, time.minute);
        Instant nextFireAt = nextCronFireAt(cron, now).orElse(now.toInstant());
        return resolved(
                new SchedulePlan(
                        formatWeeklyLabel(days) + " " + formatHourMinute(time.hour, time.minute),
                        ReminderRecurrence.CRON,
                        nextFireAt,
                        cron,
                        null,
                        null
                ),
                text.substring(prefix.start(), prefix.end() + time.end)
        );
    }

    private ScheduleParseResult parseMonthly(String text, ZonedDateTime now) {
        Matcher prefix = MONTHLY_PREFIX.matcher(text);
        if (!prefix.find()) {
            return null;
        }

        String tail = text.substring(prefix.end());
        Matcher dayMatcher = MONTH_DAY.matcher(tail);
        if (!dayMatcher.find()) {
            return ambiguous("你想每月几号执行？例如 `每月5号晚上8点`。");
        }

        int dayOfMonth = parseNumber(dayMatcher.group("day"));
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            return absent("每月日期只能是 1 到 31 号。");
        }

        String remainder = tail.substring(dayMatcher.end());
        TimeParseResult time = parseTime(remainder);
        if (time.status == TimeParseStatus.ABSENT) {
            return ambiguous("你想每月" + dayOfMonth + "号几点执行？例如 `每月" + dayOfMonth + "号晚上8点`。");
        }
        if (time.status == TimeParseStatus.AMBIGUOUS) {
            return ambiguous(time.prompt);
        }

        String cron = buildMonthlyCron(dayOfMonth, time.hour, time.minute);
        Instant nextFireAt = nextCronFireAt(cron, now).orElse(now.toInstant());
        return resolved(
                new SchedulePlan(
                        "每月" + dayOfMonth + "号 " + formatHourMinute(time.hour, time.minute),
                        ReminderRecurrence.CRON,
                        nextFireAt,
                        cron,
                        null,
                        null
                ),
                text.substring(prefix.start(), prefix.end() + dayMatcher.end() + time.end)
        );
    }

    private ScheduleParseResult parseIntervalRecurring(String text, ZonedDateTime now) {
        Matcher prefix = INTERVAL_PREFIX.matcher(text);
        if (!prefix.find()) {
            return null;
        }

        String valueText = prefix.group("value");
        String unitText = prefix.group("unit");
        int value = StringUtils.hasText(valueText) ? parseNumber(valueText) : 1;
        if (value <= 0) {
            return absent("间隔时间必须大于 0。");
        }

        ScheduleIntervalUnit unit = parseIntervalUnit(unitText);
        if (unit == null) {
            return absent(DEFAULT_PROMPT);
        }

        String tail = text.substring(prefix.end());
        return switch (unit) {
            case SECOND, MINUTE, HOUR -> parseShortInterval(text, prefix, now, value, unit);
            case DAY -> parseDayInterval(text, prefix, tail, now, value);
            case WEEK -> parseWeekInterval(text, prefix, tail, now, value);
            case MONTH -> parseMonthInterval(text, prefix, tail, now, value);
        };
    }

    private ScheduleParseResult parseShortInterval(String text,
                                                   Matcher prefix,
                                                   ZonedDateTime now,
                                                   int value,
                                                   ScheduleIntervalUnit unit) {
        Instant nextFireAt = unit.addTo(now.withNano(0), value).toInstant();
        return resolved(
                new SchedulePlan(
                        "每" + value + intervalDisplay(unit),
                        ReminderRecurrence.INTERVAL,
                        nextFireAt,
                        null,
                        value,
                        unit
                ),
                prefix.group()
        );
    }

    private ScheduleParseResult parseDayInterval(String text,
                                                 Matcher prefix,
                                                 String tail,
                                                 ZonedDateTime now,
                                                 int value) {
        TimeParseResult time = parseTime(tail);
        Instant nextFireAt;
        String label = "每" + value + "天";
        int matchedEnd = prefix.end();
        if (time.status == TimeParseStatus.RESOLVED) {
            ZonedDateTime candidate = now.withHour(time.hour)
                    .withMinute(time.minute)
                    .withSecond(0)
                    .withNano(0);
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(1);
            }
            nextFireAt = candidate.toInstant();
            label = label + " " + formatHourMinute(time.hour, time.minute);
            matchedEnd += time.end;
        }
        else if (time.status == TimeParseStatus.AMBIGUOUS) {
            return ambiguous(time.prompt);
        }
        else {
            nextFireAt = ScheduleIntervalUnit.DAY.addTo(now.withSecond(0).withNano(0), value).toInstant();
        }

        return resolved(
                new SchedulePlan(label, ReminderRecurrence.INTERVAL, nextFireAt, null, value, ScheduleIntervalUnit.DAY),
                text.substring(prefix.start(), matchedEnd)
        );
    }

    private ScheduleParseResult parseWeekInterval(String text,
                                                  Matcher prefix,
                                                  String tail,
                                                  ZonedDateTime now,
                                                  int value) {
        TimeParseResult time = parseTime(tail);
        if (time.status == TimeParseStatus.AMBIGUOUS) {
            return ambiguous(time.prompt);
        }

        String leading = time.status == TimeParseStatus.RESOLVED ? tail.substring(0, time.start) : tail;
        List<DayOfWeek> days = parseWeekdays(leading);
        if (days.size() > 1) {
            return ambiguous("像“每两周周二和周四”这种规则一次会产生多个时间点，当前先不支持。请拆成两条提醒，或者改成 `每周二` / `每周四`。");
        }

        ZonedDateTime candidate;
        String label = "每" + value + "周";
        int matchedEnd = prefix.end();
        if (days.size() == 1) {
            if (time.status == TimeParseStatus.ABSENT) {
                return ambiguous("你想每" + value + "周的周" + weekdayText(days.getFirst()) + "几点执行？");
            }
            candidate = nextWeekdayOccurrence(now, days.getFirst(), time.hour, time.minute);
            label = label + "周" + weekdayText(days.getFirst()) + " " + formatHourMinute(time.hour, time.minute);
            matchedEnd += time.end;
        }
        else if (time.status == TimeParseStatus.RESOLVED) {
            candidate = now.withHour(time.hour)
                    .withMinute(time.minute)
                    .withSecond(0)
                    .withNano(0);
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(1);
            }
            label = label + " " + formatHourMinute(time.hour, time.minute);
            matchedEnd += time.end;
        }
        else {
            candidate = ScheduleIntervalUnit.WEEK.addTo(now.withSecond(0).withNano(0), value);
        }

        return resolved(
                new SchedulePlan(label, ReminderRecurrence.INTERVAL, candidate.toInstant(), null, value, ScheduleIntervalUnit.WEEK),
                text.substring(prefix.start(), matchedEnd)
        );
    }

    private ScheduleParseResult parseMonthInterval(String text,
                                                   Matcher prefix,
                                                   String tail,
                                                   ZonedDateTime now,
                                                   int value) {
        Matcher dayMatcher = MONTH_DAY.matcher(tail);
        if (!dayMatcher.find()) {
            return ambiguous("你想每" + value + "个月的几号执行？例如 `每" + value + "个月5号上午9点`。");
        }

        int dayOfMonth = parseNumber(dayMatcher.group("day"));
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            return absent("每月日期只能是 1 到 31 号。");
        }

        String remainder = tail.substring(dayMatcher.end());
        TimeParseResult time = parseTime(remainder);
        if (time.status == TimeParseStatus.ABSENT) {
            return ambiguous("你想每" + value + "个月的" + dayOfMonth + "号几点执行？");
        }
        if (time.status == TimeParseStatus.AMBIGUOUS) {
            return ambiguous(time.prompt);
        }

        ZonedDateTime candidate = nextMonthDayOccurrence(now, dayOfMonth, time.hour, time.minute);
        return resolved(
                new SchedulePlan(
                        "每" + value + "个月" + dayOfMonth + "号 " + formatHourMinute(time.hour, time.minute),
                        ReminderRecurrence.INTERVAL,
                        candidate.toInstant(),
                        null,
                        value,
                        ScheduleIntervalUnit.MONTH
                ),
                text.substring(prefix.start(), prefix.end() + dayMatcher.end() + time.end)
        );
    }

    private ScheduleParseResult parseExplicitDate(String text, ZonedDateTime now) {
        Matcher dateMatcher = EXPLICIT_DATE.matcher(text);
        if (!dateMatcher.find()) {
            return null;
        }

        int year = StringUtils.hasText(dateMatcher.group("year"))
                ? Integer.parseInt(dateMatcher.group("year"))
                : now.getYear();
        int month = Integer.parseInt(dateMatcher.group("month"));
        int day = Integer.parseInt(dateMatcher.group("day"));

        LocalDate candidateDate;
        try {
            candidateDate = LocalDate.of(year, month, day);
        }
        catch (Exception e) {
            return absent("日期格式不正确，请检查年月日。");
        }
        if (!StringUtils.hasText(dateMatcher.group("year")) && candidateDate.isBefore(now.toLocalDate())) {
            candidateDate = candidateDate.plusYears(1);
        }

        String remainder = text.substring(dateMatcher.end());
        TimeParseResult time = parseTime(remainder);
        String dateLabel = candidateDate.toString();
        if (time.status == TimeParseStatus.ABSENT) {
            return ambiguous("你想在 " + dateLabel + " 几点执行？例如 `" + dateLabel + " 21:00`。");
        }
        if (time.status == TimeParseStatus.AMBIGUOUS) {
            return ambiguous(time.prompt);
        }

        ZonedDateTime candidate = ZonedDateTime.of(
                candidateDate.getYear(),
                candidateDate.getMonthValue(),
                candidateDate.getDayOfMonth(),
                time.hour,
                time.minute,
                0,
                0,
                now.getZone()
        );
        if (!candidate.isAfter(now)) {
            return absent("这个时间已经过去了，请改成未来时间。");
        }

        return resolved(
                new SchedulePlan(
                        candidate.format(LABEL_DATETIME),
                        ReminderRecurrence.ONCE,
                        candidate.toInstant(),
                        null,
                        null,
                        null
                ),
                text.substring(dateMatcher.start(), dateMatcher.end() + time.end)
        );
    }

    private ScheduleParseResult parseRelativeDay(String text, ZonedDateTime now) {
        Matcher dayMatcher = RELATIVE_DAY.matcher(text);
        if (!dayMatcher.find()) {
            return null;
        }

        String dayText = dayMatcher.group();
        String remainder = text.substring(dayMatcher.end());
        TimeParseResult time = parseTime(remainder);
        if (time.status == TimeParseStatus.ABSENT) {
            return ambiguous("你想" + dayText + "几点执行？例如 `" + dayText + "上午9点`。");
        }
        if (time.status == TimeParseStatus.AMBIGUOUS) {
            return ambiguous(time.prompt);
        }

        LocalDate date = switch (dayText) {
            case "今天" -> now.toLocalDate();
            case "明天" -> now.toLocalDate().plusDays(1);
            case "后天" -> now.toLocalDate().plusDays(2);
            default -> now.toLocalDate();
        };
        ZonedDateTime candidate = ZonedDateTime.of(
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                time.hour,
                time.minute,
                0,
                0,
                now.getZone()
        );
        if (!candidate.isAfter(now)) {
            return absent("这个时间已经过去了，请改成未来时间。");
        }

        return resolved(
                new SchedulePlan(
                        dayText + " " + formatHourMinute(time.hour, time.minute),
                        ReminderRecurrence.ONCE,
                        candidate.toInstant(),
                        null,
                        null,
                        null
                ),
                text.substring(dayMatcher.start(), dayMatcher.end() + time.end)
        );
    }

    private ScheduleParseResult parseRelativeAfter(String text, ZonedDateTime now) {
        Matcher matcher = RELATIVE_AFTER.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        int value = parseNumber(matcher.group("value"));
        ScheduleIntervalUnit unit = parseIntervalUnit(matcher.group("unit"));
        if (value <= 0 || unit == null) {
            return absent(DEFAULT_PROMPT);
        }

        Instant nextFireAt = unit.addTo(now.withNano(0), value).toInstant();
        return resolved(
                new SchedulePlan(
                        value + intervalDisplay(unit) + "后",
                        ReminderRecurrence.ONCE,
                        nextFireAt,
                        null,
                        null,
                        null
                ),
                matcher.group()
        );
    }

    private ScheduleParseResult parseAbsoluteTimeOnly(String text, ZonedDateTime now) {
        TimeParseResult time = parseTime(text);
        if (time.status == TimeParseStatus.ABSENT) {
            return null;
        }
        if (time.status == TimeParseStatus.AMBIGUOUS) {
            return ambiguous(time.prompt);
        }

        ZonedDateTime candidate = now.withHour(time.hour)
                .withMinute(time.minute)
                .withSecond(0)
                .withNano(0);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }

        String dayLabel = candidate.toLocalDate().equals(now.toLocalDate()) ? "今天" : "明天";
        return resolved(
                new SchedulePlan(
                        dayLabel + " " + formatHourMinute(time.hour, time.minute),
                        ReminderRecurrence.ONCE,
                        candidate.toInstant(),
                        null,
                        null,
                        null
                ),
                time.matchedText
        );
    }

    private ScheduleParseResult firstResolved(ScheduleParseResult... candidates) {
        for (ScheduleParseResult candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (candidate.status() == ScheduleParseStatus.RESOLVED) {
                return candidate;
            }
            if (candidate.status() == ScheduleParseStatus.AMBIGUOUS) {
                return candidate;
            }
            if (candidate.status() == ScheduleParseStatus.ABSENT && StringUtils.hasText(candidate.prompt())) {
                return candidate;
            }
        }
        return null;
    }

    private Optional<Instant> nextCronFireAt(String cron, ZonedDateTime now) {
        ZonedDateTime next = CronExpression.parse(cron).next(now);
        return next == null ? Optional.empty() : Optional.of(next.toInstant());
    }

    private TimeParseResult parseTime(String text) {
        if (!StringUtils.hasText(text)) {
            return TimeParseResult.absent();
        }

        Matcher exactMatcher = EXACT_TIME.matcher(text);
        if (exactMatcher.find()) {
            return TimeParseResult.resolved(
                    exactMatcher.start(),
                    exactMatcher.end(),
                    Integer.parseInt(exactMatcher.group("hour")),
                    Integer.parseInt(exactMatcher.group("minute")),
                    exactMatcher.group()
            );
        }

        Matcher chineseMatcher = CHINESE_TIME.matcher(text);
        if (!chineseMatcher.find()) {
            return TimeParseResult.absent();
        }

        int hour = parseNumber(chineseMatcher.group("hour"));
        String minuteText = chineseMatcher.group("minute");
        int minute = "半".equals(minuteText) ? 30 : (StringUtils.hasText(minuteText) ? parseNumber(minuteText) : 0);
        String period = normalizePeriod(chineseMatcher.group("period")).orElse(null);
        if (!StringUtils.hasText(period) && hour >= 1 && hour <= 12) {
            return TimeParseResult.ambiguous(
                    chineseMatcher.start(),
                    chineseMatcher.end(),
                    chineseMatcher.group(),
                    "你提到了 `" + chineseMatcher.group().trim()
                            + "`，但我还不确定是上午还是下午。请补充成 `上午9点`、`晚上9点` 或 `21:00`。"
            );
        }

        int normalizedHour = to24Hour(hour, period);
        if (normalizedHour < 0 || normalizedHour > 23 || minute < 0 || minute > 59) {
            return TimeParseResult.absent();
        }
        return TimeParseResult.resolved(
                chineseMatcher.start(),
                chineseMatcher.end(),
                normalizedHour,
                minute,
                chineseMatcher.group()
        );
    }

    private List<DayOfWeek> parseWeekdays(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        if (raw.contains("工作日")) {
            return List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        if (raw.contains("周末")) {
            return List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        }

        Set<DayOfWeek> days = new LinkedHashSet<>();
        for (int i = 0; i < raw.length(); i++) {
            DayOfWeek dayOfWeek = parseWeekdayToken(raw.charAt(i));
            if (dayOfWeek != null) {
                days.add(dayOfWeek);
            }
        }
        return days.stream()
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .toList();
    }

    private DayOfWeek parseWeekdayToken(char token) {
        return switch (token) {
            case '一', '1' -> DayOfWeek.MONDAY;
            case '二', '两', '2' -> DayOfWeek.TUESDAY;
            case '三', '3' -> DayOfWeek.WEDNESDAY;
            case '四', '4' -> DayOfWeek.THURSDAY;
            case '五', '5' -> DayOfWeek.FRIDAY;
            case '六', '6' -> DayOfWeek.SATURDAY;
            case '日', '天', '七', '7' -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private String buildWeeklyCron(List<DayOfWeek> days, int hour, int minute) {
        String dayPart = days.stream()
                .map(this::cronWeekdayText)
                .reduce((a, b) -> a + "," + b)
                .orElse("MON");
        return "0 " + minute + " " + hour + " ? * " + dayPart;
    }

    private String buildMonthlyCron(int dayOfMonth, int hour, int minute) {
        return "0 " + minute + " " + hour + " " + dayOfMonth + " * ?";
    }

    private String cronWeekdayText(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }

    private String formatWeeklyLabel(List<DayOfWeek> days) {
        StringBuilder sb = new StringBuilder("每周");
        List<String> items = new ArrayList<>();
        for (DayOfWeek day : days) {
            items.add(weekdayText(day));
        }
        sb.append(String.join("、", items));
        return sb.toString();
    }

    private String weekdayText(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "一";
            case TUESDAY -> "二";
            case WEDNESDAY -> "三";
            case THURSDAY -> "四";
            case FRIDAY -> "五";
            case SATURDAY -> "六";
            case SUNDAY -> "日";
        };
    }

    private ZonedDateTime nextWeekdayOccurrence(ZonedDateTime now, DayOfWeek dayOfWeek, int hour, int minute) {
        ZonedDateTime candidate = now.with(TemporalAdjusters.nextOrSame(dayOfWeek))
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate;
    }

    private ZonedDateTime nextMonthDayOccurrence(ZonedDateTime now, int dayOfMonth, int hour, int minute) {
        ZonedDateTime cursor = now.withDayOfMonth(1)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);
        for (int i = 0; i < 24; i++) {
            int maxDay = cursor.toLocalDate().lengthOfMonth();
            if (dayOfMonth <= maxDay) {
                ZonedDateTime candidate = cursor.withDayOfMonth(dayOfMonth);
                if (candidate.isAfter(now)) {
                    return candidate;
                }
            }
            cursor = cursor.plusMonths(1).withDayOfMonth(1);
        }
        return now.plusMonths(1);
    }

    private String formatHourMinute(int hour, int minute) {
        return "%02d:%02d".formatted(hour, minute);
    }

    private Optional<String> normalizePeriod(String rawPeriod) {
        if (!StringUtils.hasText(rawPeriod)) {
            return Optional.empty();
        }
        return Optional.of(switch (rawPeriod.trim()) {
            case "清晨", "今晨", "今早", "早晨", "早上" -> "上午";
            case "傍晚", "今晚", "晚间" -> "晚上";
            default -> rawPeriod.trim();
        });
    }

    private int to24Hour(int hour, String period) {
        if (!StringUtils.hasText(period)) {
            return hour;
        }
        return switch (period) {
            case "凌晨" -> hour == 12 ? 0 : hour;
            case "上午" -> hour == 12 ? 0 : hour;
            case "中午" -> hour == 12 ? 12 : hour + 12;
            case "下午" -> hour == 12 ? 12 : hour + 12;
            case "晚上" -> hour == 12 ? 0 : hour + 12;
            default -> hour;
        };
    }

    private ScheduleIntervalUnit parseIntervalUnit(String rawUnit) {
        if (!StringUtils.hasText(rawUnit)) {
            return null;
        }
        return switch (rawUnit.trim()) {
            case "秒", "秒钟" -> ScheduleIntervalUnit.SECOND;
            case "分钟", "分" -> ScheduleIntervalUnit.MINUTE;
            case "小时", "时" -> ScheduleIntervalUnit.HOUR;
            case "天" -> ScheduleIntervalUnit.DAY;
            case "周", "星期", "礼拜" -> ScheduleIntervalUnit.WEEK;
            case "个月", "月" -> ScheduleIntervalUnit.MONTH;
            default -> null;
        };
    }

    private String intervalDisplay(ScheduleIntervalUnit unit) {
        return switch (unit) {
            case SECOND -> "秒";
            case MINUTE -> "分钟";
            case HOUR -> "小时";
            case DAY -> "天";
            case WEEK -> "周";
            case MONTH -> "个月";
        };
    }

    private int parseNumber(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("时间数字不能为空");
        }

        String normalized = value.replace('两', '二').replace("〇", "零").trim();
        if (normalized.matches("\\d+")) {
            return Integer.parseInt(normalized);
        }

        return switch (normalized) {
            case "零" -> 0;
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> parseChineseTens(normalized);
        };
    }

    private int parseChineseTens(String value) {
        if (!value.contains("十")) {
            throw new IllegalArgumentException("暂时只支持 0-99 的中文数字");
        }
        String[] parts = value.split("十", -1);
        int tens = StringUtils.hasText(parts[0]) ? parseSingleChineseDigit(parts[0]) : 1;
        int ones = parts.length > 1 && StringUtils.hasText(parts[1]) ? parseSingleChineseDigit(parts[1]) : 0;
        return tens * 10 + ones;
    }

    private int parseSingleChineseDigit(String value) {
        return switch (value) {
            case "零" -> 0;
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> throw new IllegalArgumentException("暂时只支持 0-99 的中文数字");
        };
    }

    private String normalize(String text) {
        return text == null ? null : text.replace('\u3000', ' ').replaceAll("\\s+", " ").trim();
    }

    private ScheduleParseResult resolved(SchedulePlan plan, String matchedText) {
        return new ScheduleParseResult(ScheduleParseStatus.RESOLVED, plan, matchedText, null);
    }

    private ScheduleParseResult ambiguous(String prompt) {
        return new ScheduleParseResult(ScheduleParseStatus.AMBIGUOUS, null, null, prompt);
    }

    private ScheduleParseResult absent(String prompt) {
        return new ScheduleParseResult(ScheduleParseStatus.ABSENT, null, null, prompt);
    }

    private enum TimeParseStatus {
        RESOLVED,
        AMBIGUOUS,
        ABSENT
    }

    private static final class TimeParseResult {
        private final TimeParseStatus status;
        private final int start;
        private final int end;
        private final int hour;
        private final int minute;
        private final String matchedText;
        private final String prompt;

        private TimeParseResult(TimeParseStatus status,
                                int start,
                                int end,
                                int hour,
                                int minute,
                                String matchedText,
                                String prompt) {
            this.status = status;
            this.start = start;
            this.end = end;
            this.hour = hour;
            this.minute = minute;
            this.matchedText = matchedText;
            this.prompt = prompt;
        }

        private static TimeParseResult resolved(int start, int end, int hour, int minute, String matchedText) {
            return new TimeParseResult(TimeParseStatus.RESOLVED, start, end, hour, minute, matchedText, null);
        }

        private static TimeParseResult ambiguous(int start, int end, String matchedText, String prompt) {
            return new TimeParseResult(TimeParseStatus.AMBIGUOUS, start, end, -1, -1, matchedText, prompt);
        }

        private static TimeParseResult absent() {
            return new TimeParseResult(TimeParseStatus.ABSENT, -1, -1, -1, -1, null, null);
        }
    }
}
