package com.ilink.test.ilinkcheck.application.tool.time;

import com.ilink.test.ilinkcheck.application.tool.categories.time.CurrentTimeTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentTimeToolTest {

    private final CurrentTimeTool tool = new CurrentTimeTool();

    @Test
    void shouldReturnShanghaiTimeByDefault() {
        String result = tool.getCurrentTime(null);
        assertThat(result).contains("Asia/Shanghai");
        assertThat(result).contains("年").contains("月").contains("日");
        assertThat(result).contains(":");
    }

    @Test
    void shouldReturnTimeForSpecifiedTimezone() {
        String result = tool.getCurrentTime("America/New_York");
        assertThat(result).contains("America/New_York");
        assertThat(result).contains("年").contains("月").contains("日");
        assertThat(result).contains(":");
    }

    @Test
    void shouldReturnErrorForInvalidTimezone() {
        String result = tool.getCurrentTime("Invalid/Zone");
        assertThat(result).contains("不支持的时区");
    }
}
