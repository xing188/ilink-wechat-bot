package com.ilink.test.ilinkcheck.application.tool.time;

import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class CurrentTimeTool implements ChatFunctionTool {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINESE);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    @Tool(
            name = "getCurrentTime",
            description = """
                    获取指定时区的当前日期和时间。
                    如果用户没有明确指定时区，使用中国标准时间（Asia/Shanghai，UTC+8）。
                    支持常见时区名称，例如 Asia/Shanghai、America/New_York、Europe/London 等。
                    """
    )
    public String getCurrentTime(
            @ToolParam(required = false, description = "时区名称，例如 Asia/Shanghai、America/New_York、Europe/London。不传则默认中国标准时间")
            String timezone) {

        String zoneIdStr = (timezone != null && !timezone.isBlank()) ? timezone.trim() : "Asia/Shanghai";
        ZoneId zoneId;

        try {
            zoneId = ZoneId.of(zoneIdStr);
        } catch (Exception e) {
            return "不支持的时区：" + zoneIdStr + "，请使用标准时区名称，例如 Asia/Shanghai、America/New_York。";
        }

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        String date = now.format(DATE_FORMATTER);
        String time = now.format(TIME_FORMATTER);

        return String.format("当前时间（%s）：%s %s", zoneIdStr, date, time);
    }
}
