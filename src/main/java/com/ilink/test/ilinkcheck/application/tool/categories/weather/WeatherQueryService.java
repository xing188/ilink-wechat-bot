package com.ilink.test.ilinkcheck.application.tool.categories.weather;

import com.ilink.test.ilinkcheck.infrastructure.client.weather.UapisWeatherClient;
import com.ilink.test.ilinkcheck.infrastructure.client.weather.WeatherClientException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.DecimalFormat;
import java.util.StringJoiner;

@Service
public class WeatherQueryService {

    private static final String MISSING_LOCATION_MESSAGE = """
            查询天气需要城市名或 adcode。
            当前工具运行在服务端，不能根据最终用户的真实 IP 自动定位，所以当用户没有提供地点时，不要硬查，请先追问用户想查哪个城市。
            """;

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.#");

    private final UapisWeatherClient weatherClient;

    public WeatherQueryService(UapisWeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    public String queryWeather(String city, String adcode, Boolean includeForecast, Boolean includeDetails) {
        String normalizedCity = normalize(city);
        String normalizedAdcode = normalize(adcode);

        if (!StringUtils.hasText(normalizedCity) && !StringUtils.hasText(normalizedAdcode)) {
            return MISSING_LOCATION_MESSAGE.trim();
        }

        boolean forecast = Boolean.TRUE.equals(includeForecast);
        boolean details = Boolean.TRUE.equals(includeDetails);

        try {
            WeatherReport report = weatherClient.query(normalizedCity, normalizedAdcode, forecast, details);
            return formatReport(report, forecast, details);
        }
        catch (WeatherClientException e) {
            return switch (e.type()) {
                case INVALID_PARAMETER -> "天气查询参数无效，请检查城市名、adcode 或查询选项。";
                case NOT_FOUND -> "未找到该城市的天气数据，请确认城市名或 adcode 是否正确。";
                case SERVICE_UNAVAILABLE, UPSTREAM_ERROR -> "天气服务暂时不可用，请稍后再试。";
            };
        }
        catch (Exception e) {
            return "天气查询失败，请稍后再试。";
        }
    }

    private String formatReport(WeatherReport report, boolean includeForecast, boolean includeDetails) {
        StringBuilder sb = new StringBuilder();
        sb.append("天气查询结果：");
        sb.append(resolveLocation(report));
        sb.append("，当前");
        sb.append(defaultText(report.weather(), "天气未知"));

        if (report.temperature() != null) {
            sb.append("，").append(formatNumber(report.temperature())).append("°C");
        }
        if (StringUtils.hasText(report.windDirection()) || StringUtils.hasText(report.windPower())) {
            sb.append("，风况 ");
            sb.append(defaultText(report.windDirection(), "未知风向"));
            if (StringUtils.hasText(report.windPower())) {
                sb.append(" ").append(report.windPower());
            }
        }
        if (report.humidity() != null) {
            sb.append("，相对湿度 ").append(report.humidity()).append("%");
        }
        if (StringUtils.hasText(report.reportTime())) {
            sb.append("。更新时间：").append(report.reportTime()).append("。");
        }
        else {
            sb.append("。");
        }

        if (includeDetails) {
            StringJoiner detailJoiner = new StringJoiner("；", "扩展信息：", "。");
            boolean hasDetail = false;
            if (report.feelsLike() != null) {
                detailJoiner.add("体感温度 " + formatNumber(report.feelsLike()) + "°C");
                hasDetail = true;
            }
            if (report.visibility() != null) {
                detailJoiner.add("能见度 " + formatNumber(report.visibility()) + " km");
                hasDetail = true;
            }
            if (report.pressure() != null) {
                detailJoiner.add("气压 " + formatNumber(report.pressure()) + " hPa");
                hasDetail = true;
            }
            if (report.uv() != null) {
                detailJoiner.add("紫外线指数 " + formatNumber(report.uv()));
                hasDetail = true;
            }
            if (report.aqi() != null || StringUtils.hasText(report.aqiCategory())) {
                StringBuilder aqiText = new StringBuilder("空气质量 ");
                if (StringUtils.hasText(report.aqiCategory())) {
                    aqiText.append(report.aqiCategory());
                }
                if (report.aqi() != null) {
                    if (StringUtils.hasText(report.aqiCategory())) {
                        aqiText.append(" ");
                    }
                    aqiText.append("(AQI ").append(report.aqi()).append(")");
                }
                if (StringUtils.hasText(report.aqiPrimary())) {
                    aqiText.append("，首要污染物 ").append(report.aqiPrimary());
                }
                detailJoiner.add(aqiText.toString());
                hasDetail = true;
            }
            if (hasDetail) {
                sb.append(detailJoiner);
            }
        }

        if (report.hasAlerts()) {
            WeatherReport.WeatherAlert alert = report.alerts().getFirst();
            sb.append("天气预警：");
            sb.append(defaultText(alert.title(), "有气象预警"));
            if (StringUtils.hasText(alert.level()) && !defaultText(alert.title(), "").contains(alert.level())) {
                sb.append("（").append(alert.level()).append("）");
            }
            if (StringUtils.hasText(alert.text())) {
                sb.append("。说明：").append(alert.text());
            }
            sb.append("。");
        }

        if (includeForecast && report.hasForecast()) {
            sb.append("未来天气：");
            for (WeatherReport.WeatherForecastDay day : report.forecast()) {
                sb.append(day.date())
                        .append(" ")
                        .append(defaultText(day.week(), ""))
                        .append("，");

                if (StringUtils.hasText(day.weatherDay()) || StringUtils.hasText(day.weatherNight())) {
                    sb.append(defaultText(day.weatherDay(), "天气未知"));
                    if (StringUtils.hasText(day.weatherNight())
                            && !day.weatherNight().equals(day.weatherDay())) {
                        sb.append("转").append(day.weatherNight());
                    }
                    sb.append("，");
                }

                if (day.tempMin() != null || day.tempMax() != null) {
                    sb.append(formatTemperatureRange(day.tempMin(), day.tempMax())).append("，");
                }

                if (day.pop() != null) {
                    sb.append("降水概率 ").append(day.pop()).append("%，");
                }

                if (StringUtils.hasText(day.windDirDay()) || StringUtils.hasText(day.windScaleDay())) {
                    sb.append(defaultText(day.windDirDay(), ""))
                            .append(defaultText(day.windScaleDay(), ""))
                            .append("，");
                }

                trimTrailingPunctuation(sb);
                sb.append("；");
            }
            trimTrailingPunctuation(sb);
            sb.append("。");
        }

        return sb.toString().trim();
    }

    private String resolveLocation(WeatherReport report) {
        if (StringUtils.hasText(report.city()) && StringUtils.hasText(report.district())
                && !report.city().equals(report.district())) {
            return report.city() + report.district();
        }

        if (StringUtils.hasText(report.province()) && StringUtils.hasText(report.city())
                && !report.province().equals(report.city())
                && !report.city().startsWith(report.province())) {
            return report.province() + report.city();
        }

        if (StringUtils.hasText(report.city())) {
            return report.city();
        }

        if (StringUtils.hasText(report.province())) {
            return report.province();
        }

        if (StringUtils.hasText(report.adcode())) {
            return "adcode=" + report.adcode();
        }

        return "未知地点";
    }

    private String formatTemperatureRange(Double min, Double max) {
        if (min != null && max != null) {
            return formatNumber(min) + "-" + formatNumber(max) + "°C";
        }
        if (min != null) {
            return "最低 " + formatNumber(min) + "°C";
        }
        if (max != null) {
            return "最高 " + formatNumber(max) + "°C";
        }
        return "";
    }

    private String formatNumber(Double value) {
        return NUMBER_FORMAT.format(value);
    }

    private String normalize(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void trimTrailingPunctuation(StringBuilder sb) {
        while (!sb.isEmpty()) {
            char last = sb.charAt(sb.length() - 1);
            if (last == '，' || last == '；' || last == ' ') {
                sb.deleteCharAt(sb.length() - 1);
                continue;
            }
            break;
        }
    }
}
