package com.ilink.test.ilinkcheck.application.reminder.parser;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRaw;
import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecurrence;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReminderParser {

    private static final Pattern RELATIVE_PATTERN = Pattern.compile(
            "(?<value>[0-9零〇一二两三四五六七八九十]+)\\s*(?<unit>分钟|分|小时|天)\\s*(?:后|之后)"
    );
    private static final Pattern ABSOLUTE_PATTERN = Pattern.compile(
            "(?:(?<day>今天|明天|后天)\\s*)?(?<period>凌晨|早上|上午|中午|下午|晚上)?\\s*"
                    + "(?<hour>[0-9零〇一二两三四五六七八九十]+)\\s*(?:点|时)"
                    + "(?:\\s*(?<minute>[0-9零〇一二两三四五六七八九十]+|半)\\s*(?:分|分钟)?)?"
    );
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINESE);

    public ReminderRaw parse(String request, Clock clock) {
        String normalized = normalize(request);
        if (!StringUtils.hasText(normalized)) {
            throw new ReminderParseException("提醒内容不能为空");
        }

        ZonedDateTime now = ZonedDateTime.now(clock);

        if (normalized.startsWith("每天") || normalized.startsWith("每日")) {
            return parseDaily(normalized, now);
        }

        Matcher relative = RELATIVE_PATTERN.matcher(normalized);
        if (relative.find()) {
            return parseRelative(normalized, relative, now);
        }

        Matcher absolute = ABSOLUTE_PATTERN.matcher(normalized);
        if (absolute.find()) {
            return parseAbsolute(normalized, absolute, now);
        }

        throw new ReminderParseException("没看懂时间，请说“十分钟后”“今天下午5点”或“每天早上8点”");
    }

    private ReminderRaw parseDaily(String request, ZonedDateTime now) {
        String body = request.replaceFirst("^每天\\s*", "").replaceFirst("^每日\\s*", "");
        Matcher matcher = ABSOLUTE_PATTERN.matcher(body);
        if (!matcher.find()) {
            throw new ReminderParseException("每天提醒需要具体时间，例如“每天早上8点”");
        }

        ParsedTime parsedTime = buildParsedTime(matcher);
        ZonedDateTime candidate = now.withHour(parsedTime.hour())
                .withMinute(parsedTime.minute())
                .withSecond(0)
                .withNano(0);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }

        String content = cleanupContent(removeMatchedSegment(body, matcher));
        if (!StringUtils.hasText(content)) {
            throw new ReminderParseException("请补充提醒内容，例如“每天早上8点推送科技新闻”");
        }

        return new ReminderRaw(
                content,
                "每天 " + candidate.format(HOUR_MINUTE),
                ReminderRecurrence.DAILY,
                candidate.toInstant()
        );
    }

    private ReminderRaw parseRelative(String request, Matcher matcher, ZonedDateTime now) {
        long minutesToAdd = parseRelativeMinutes(matcher.group("value"), matcher.group("unit"));
        Instant nextFireAt = now.plusMinutes(minutesToAdd).toInstant();
        String content = cleanupContent(removeMatchedSegment(request, matcher));
        if (!StringUtils.hasText(content)) {
            throw new ReminderParseException("请补充提醒内容，例如“十分钟后开会”");
        }
        return new ReminderRaw(
                content,
                formatRelativeLabel(minutesToAdd),
                ReminderRecurrence.ONCE,
                nextFireAt
        );
    }

    private ReminderRaw parseAbsolute(String request, Matcher matcher, ZonedDateTime now) {
        ParsedTime parsedTime = buildParsedTime(matcher);
        LocalDate targetDate = resolveDate(matcher.group("day"), now.toLocalDate(), now);
        ZonedDateTime candidate = ZonedDateTime.of(targetDate, parsedTime.time(), now.getZone());
        boolean explicitDay = StringUtils.hasText(matcher.group("day"));

        if (!explicitDay && !candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }
        if (explicitDay && !candidate.isAfter(now)) {
            throw new ReminderParseException("这个时间已经过去了，请改成未来时间");
        }

        String content = cleanupContent(removeMatchedSegment(request, matcher));
        if (!StringUtils.hasText(content)) {
            throw new ReminderParseException("请补充提醒内容，例如“今天下午5点给张三发微信”");
        }

        return new ReminderRaw(
                content,
                formatAbsoluteLabel(matcher.group("day"), candidate),
                ReminderRecurrence.ONCE,
                candidate.toInstant()
        );
    }

    private long parseRelativeMinutes(String value, String unit) {
        long amount = parseNumber(value);
        return switch (unit) {
            case "分钟", "分" -> amount;
            case "小时" -> amount * 60;
            case "天" -> amount * 24 * 60;
            default -> throw new ReminderParseException("不支持的时间单位");
        };
    }

    private ParsedTime buildParsedTime(Matcher matcher) {
        int hour = parseNumber(matcher.group("hour"));
        String minuteGroup = matcher.group("minute");
        int minute = "半".equals(minuteGroup) ? 30 : (StringUtils.hasText(minuteGroup) ? parseNumber(minuteGroup) : 0);
        String period = matcher.group("period");

        if (StringUtils.hasText(period)) {
            if (("下午".equals(period) || "晚上".equals(period)) && hour < 12) {
                hour += 12;
            }
            if ("中午".equals(period)) {
                if (hour == 0) {
                    hour = 12;
                }
                else if (hour < 12) {
                    hour += 12;
                }
            }
            if ("凌晨".equals(period) && hour == 12) {
                hour = 0;
            }
        }

        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new ReminderParseException("时间格式不正确，请检查小时和分钟");
        }

        return new ParsedTime(hour, minute);
    }

    private LocalDate resolveDate(String day, LocalDate today, ZonedDateTime now) {
        if (!StringUtils.hasText(day)) {
            return today;
        }
        return switch (day) {
            case "今天" -> today;
            case "明天" -> today.plusDays(1);
            case "后天" -> today.plusDays(2);
            default -> throw new ReminderParseException("不支持的日期表达");
        };
    }

    private String formatRelativeLabel(long minutesToAdd) {
        if (minutesToAdd % 1440 == 0) {
            return (minutesToAdd / 1440) + "天后";
        }
        if (minutesToAdd % 60 == 0) {
            return (minutesToAdd / 60) + "小时后";
        }
        return minutesToAdd + "分钟后";
    }

    private String formatAbsoluteLabel(String day, ZonedDateTime candidate) {
        if (!StringUtils.hasText(day)) {
            return candidate.toLocalDate().equals(ZonedDateTime.now(candidate.getZone()).toLocalDate())
                    ? "今天 " + candidate.format(HOUR_MINUTE)
                    : "明天 " + candidate.format(HOUR_MINUTE);
        }
        return day + " " + candidate.format(HOUR_MINUTE);
    }

    private String removeMatchedSegment(String request, Matcher matcher) {
        return (request.substring(0, matcher.start()) + request.substring(matcher.end())).trim();
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

    private int parseNumber(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ReminderParseException("时间数字不能为空");
        }

        String normalized = value.replace('两', '二').replace("〇", "零");
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
            throw new ReminderParseException("暂时只支持 0-99 的中文数字");
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
            default -> throw new ReminderParseException("暂时只支持 0-99 的中文数字");
        };
    }

    private record ParsedTime(int hour, int minute) {

        LocalTime time() {
            return LocalTime.of(hour, minute);
        }
    }
}
