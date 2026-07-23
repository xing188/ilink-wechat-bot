package com.ilink.test.ilinkcheck.application.tool.weather;

import com.ilink.test.ilinkcheck.infrastructure.client.weather.UapisWeatherClient;
import com.ilink.test.ilinkcheck.infrastructure.client.weather.WeatherClientException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherQueryServiceTest {

    @Test
    void shouldFormatCurrentWeatherForecastAndAlert() {
        UapisWeatherClient weatherClient = mock(UapisWeatherClient.class);
        WeatherQueryService service = new WeatherQueryService(weatherClient);

        WeatherReport report = new WeatherReport(
                "北京市",
                "北京",
                null,
                "110000",
                "多云",
                "101",
                25.0,
                "东南风",
                "3级",
                78,
                "9 分钟前发布",
                26.0,
                19.7,
                1000.0,
                4.0,
                36,
                "优",
                "O3",
                List.of(new WeatherReport.WeatherAlert(
                        "雷电黄色预警",
                        "雷电",
                        "黄色",
                        "今天下午到前半夜有雷阵雨。",
                        "2026-07-22 09:07:46",
                        "北京市气象台"
                )),
                List.of(new WeatherReport.WeatherForecastDay(
                        "2026-07-22",
                        "星期三",
                        27.0,
                        22.0,
                        "雷阵雨",
                        "雷阵雨",
                        "东南风",
                        "东南风",
                        "2级",
                        "2级",
                        80
                ))
        );

        when(weatherClient.query("北京", null, true, true)).thenReturn(report);

        String result = service.queryWeather("北京", null, true, true);

        assertThat(result)
                .contains("天气查询结果：北京")
                .contains("当前多云")
                .contains("25°C")
                .contains("扩展信息：体感温度 26°C")
                .contains("空气质量 优 (AQI 36)")
                .contains("天气预警：雷电黄色预警")
                .contains("未来天气：2026-07-22 星期三")
                .contains("降水概率 80%");
    }

    @Test
    void shouldAskForLocationWhenCityAndAdcodeAreMissing() {
        UapisWeatherClient weatherClient = mock(UapisWeatherClient.class);
        WeatherQueryService service = new WeatherQueryService(weatherClient);

        String result = service.queryWeather(null, null, false, false);

        assertThat(result).contains("查询天气需要城市名或 adcode");
        verify(weatherClient, never()).query(null, null, false, false);
    }

    @Test
    void shouldReturnFriendlyMessageWhenCityIsNotFound() {
        UapisWeatherClient weatherClient = mock(UapisWeatherClient.class);
        WeatherQueryService service = new WeatherQueryService(weatherClient);

        when(weatherClient.query("火星", null, false, false))
                .thenThrow(new WeatherClientException(WeatherClientException.Type.NOT_FOUND, "not found"));

        String result = service.queryWeather("火星", null, false, false);

        assertThat(result).isEqualTo("未找到该城市的天气数据，请确认城市名或 adcode 是否正确。");
    }
}
