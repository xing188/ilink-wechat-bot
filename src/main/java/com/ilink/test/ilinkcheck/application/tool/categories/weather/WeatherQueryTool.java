package com.ilink.test.ilinkcheck.application.tool.categories.weather;

import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WeatherQueryTool implements ChatFunctionTool {

    private final WeatherQueryService weatherQueryService;

    public WeatherQueryTool(WeatherQueryService weatherQueryService) {
        this.weatherQueryService = weatherQueryService;
    }

    @Tool(
            name = "queryWeather",
            description = """
                    查询指定城市的实时天气、天气预警和可选天气预报。
                    必须至少提供 city 或 adcode 其中一个。
                    如果用户没有明确提供地点，不要调用本工具，先追问用户要查询哪个城市。
                    当用户询问明天、未来几天、本周天气时，把 includeForecast 设为 true。
                    当用户询问体感温度、湿度、紫外线、能见度、气压、空气质量时，把 includeDetails 设为 true。
                    """
    )
    public String queryWeather(
            @ToolParam(required = false, description = "城市名称，支持中文或英文，例如 北京、上海、Tokyo")
            String city,
            @ToolParam(required = false, description = "中国行政区划 adcode，例如 110000。若提供则优先于 city")
            String adcode,
            @ToolParam(required = false, description = "是否需要未来几天天气预报。询问明天、未来几天、本周天气时传 true")
            Boolean includeForecast,
            @ToolParam(required = false, description = "是否需要扩展气象信息，如体感温度、能见度、气压、紫外线、空气质量")
            Boolean includeDetails) {

        return weatherQueryService.queryWeather(city, adcode, includeForecast, includeDetails);
    }
}
