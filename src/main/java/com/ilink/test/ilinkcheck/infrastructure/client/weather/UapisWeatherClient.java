package com.ilink.test.ilinkcheck.infrastructure.client.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilink.test.ilinkcheck.application.tool.weather.WeatherReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class UapisWeatherClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public UapisWeatherClient(RestClient.Builder restClientBuilder,
                              ObjectMapper objectMapper,
                              @Value("${app.weather.base-url:https://uapis.cn/api/v1}") String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(trimTrailingSlash(baseUrl))
                .build();
        this.objectMapper = objectMapper;
    }

    public WeatherReport query(String city, String adcode, boolean includeForecast, boolean includeDetails) {
        try {
            ApiWeatherResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/misc/weather");
                        if (StringUtils.hasText(adcode)) {
                            builder = builder.queryParam("adcode", adcode);
                        }
                        else if (StringUtils.hasText(city)) {
                            builder = builder.queryParam("city", city);
                        }
                        builder = builder.queryParam("lang", "zh");
                        if (includeForecast) {
                            builder = builder.queryParam("forecast", true);
                        }
                        if (includeDetails) {
                            builder = builder.queryParam("extended", true);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(ApiWeatherResponse.class);

            if (response == null) {
                throw new WeatherClientException(WeatherClientException.Type.UPSTREAM_ERROR,
                        "Weather API returned an empty body");
            }

            return response.toWeatherReport();
        }
        catch (RestClientResponseException e) {
            throw translateException(e);
        }
        catch (WeatherClientException e) {
            throw e;
        }
        catch (Exception e) {
            throw new WeatherClientException(WeatherClientException.Type.UPSTREAM_ERROR,
                    "Weather API call failed", e);
        }
    }

    private WeatherClientException translateException(RestClientResponseException e) {
        ApiErrorResponse error = null;
        String message = e.getMessage();
        try {
            error = objectMapper.readValue(e.getResponseBodyAsString(), ApiErrorResponse.class);
            if (error != null && StringUtils.hasText(error.message())) {
                message = error.message();
            }
        }
        catch (Exception ignored) {
        }

        return switch (e.getStatusCode().value()) {
            case 400 -> new WeatherClientException(WeatherClientException.Type.INVALID_PARAMETER, message, e);
            case 404 -> new WeatherClientException(WeatherClientException.Type.NOT_FOUND, message, e);
            case 503 -> new WeatherClientException(WeatherClientException.Type.SERVICE_UNAVAILABLE, message, e);
            default -> new WeatherClientException(WeatherClientException.Type.UPSTREAM_ERROR, message, e);
        };
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiWeatherResponse(
            String province,
            String city,
            String district,
            String adcode,
            String weather,
            @JsonProperty("weather_icon")
            String weatherIcon,
            Double temperature,
            @JsonProperty("wind_direction")
            String windDirection,
            @JsonProperty("wind_power")
            String windPower,
            Integer humidity,
            @JsonProperty("report_time")
            String reportTime,
            @JsonProperty("feels_like")
            Double feelsLike,
            Double visibility,
            Double pressure,
            Double uv,
            Integer aqi,
            @JsonProperty("aqi_category")
            String aqiCategory,
            @JsonProperty("aqi_primary")
            String aqiPrimary,
            List<ApiWeatherAlert> alerts,
            List<ApiWeatherForecastDay> forecast
    ) {

        WeatherReport toWeatherReport() {
            List<WeatherReport.WeatherAlert> weatherAlerts = alerts == null ? List.of() : alerts.stream()
                    .map(alert -> new WeatherReport.WeatherAlert(
                            alert.title(),
                            alert.type(),
                            alert.level(),
                            alert.text(),
                            alert.publishTime(),
                            alert.publisher()
                    ))
                    .toList();

            List<WeatherReport.WeatherForecastDay> weatherForecast = forecast == null ? List.of() : forecast.stream()
                    .map(day -> new WeatherReport.WeatherForecastDay(
                            day.date(),
                            day.week(),
                            day.tempMax(),
                            day.tempMin(),
                            day.weatherDay(),
                            day.weatherNight(),
                            day.windDirDay(),
                            day.windDirNight(),
                            day.windScaleDay(),
                            day.windScaleNight(),
                            day.pop()
                    ))
                    .toList();

            return new WeatherReport(
                    province,
                    city,
                    district,
                    adcode,
                    weather,
                    weatherIcon,
                    temperature,
                    windDirection,
                    windPower,
                    humidity,
                    reportTime,
                    feelsLike,
                    visibility,
                    pressure,
                    uv,
                    aqi,
                    aqiCategory,
                    aqiPrimary,
                    weatherAlerts,
                    weatherForecast
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiWeatherAlert(
            String title,
            String type,
            String level,
            String text,
            @JsonProperty("publish_time")
            String publishTime,
            String publisher
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiWeatherForecastDay(
            String date,
            String week,
            @JsonProperty("temp_max")
            Double tempMax,
            @JsonProperty("temp_min")
            Double tempMin,
            @JsonProperty("weather_day")
            String weatherDay,
            @JsonProperty("weather_night")
            String weatherNight,
            @JsonProperty("wind_dir_day")
            String windDirDay,
            @JsonProperty("wind_dir_night")
            String windDirNight,
            @JsonProperty("wind_scale_day")
            String windScaleDay,
            @JsonProperty("wind_scale_night")
            String windScaleNight,
            Integer pop
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiErrorResponse(
            String code,
            String message
    ) {
    }
}
