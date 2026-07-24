package com.ilink.test.ilinkcheck.application.tool.categories.weather;

import java.util.List;

public record WeatherReport(
        String province,
        String city,
        String district,
        String adcode,
        String weather,
        String weatherIcon,
        Double temperature,
        String windDirection,
        String windPower,
        Integer humidity,
        String reportTime,
        Double feelsLike,
        Double visibility,
        Double pressure,
        Double uv,
        Integer aqi,
        String aqiCategory,
        String aqiPrimary,
        List<WeatherAlert> alerts,
        List<WeatherForecastDay> forecast
) {

    public WeatherReport {
        alerts = alerts == null ? List.of() : List.copyOf(alerts);
        forecast = forecast == null ? List.of() : List.copyOf(forecast);
    }

    public boolean hasAlerts() {
        return !alerts.isEmpty();
    }

    public boolean hasForecast() {
        return !forecast.isEmpty();
    }

    public record WeatherAlert(
            String title,
            String type,
            String level,
            String text,
            String publishTime,
            String publisher
    ) {
    }

    public record WeatherForecastDay(
            String date,
            String week,
            Double tempMax,
            Double tempMin,
            String weatherDay,
            String weatherNight,
            String windDirDay,
            String windDirNight,
            String windScaleDay,
            String windScaleNight,
            Integer pop
    ) {
    }
}
