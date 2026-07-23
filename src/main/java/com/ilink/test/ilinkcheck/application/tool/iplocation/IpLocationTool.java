package com.ilink.test.ilinkcheck.application.tool.iplocation;

import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * IP 地址位置查询工具
 *
 * <p>通过 Spring AI @Tool 注解暴露给模型，
 * 当用户询问 IP 归属地、IP 位置、IP 来源时自动调用。</p>
 *
 * <h3>微信 iLink Bot 使用场景：</h3>
 * <pre>
 * 用户：8.8.8.8 这个 IP 是哪里的？
 * Bot → 调用 queryIpLocation("8.8.8.8") → 返回位置信息
 *
 * 用户：查一下这个 IP 114.114.114.114
 * Bot → 调用 queryIpLocation("114.114.114.114") → 返回结果
 * </pre>
 *
 * @author 李佳霖
 */
@Component
public class IpLocationTool implements ChatFunctionTool {

    private final IpLocationService ipLocationService;

    public IpLocationTool(IpLocationService ipLocationService) {
        this.ipLocationService = ipLocationService;
    }

    /**
     * 查询 IP 地址的地理位置信息
     *
     * <p>支持查询 IP 的国家、省份、城市、运营商、经纬度、时区等信息。</p>
     *
     * @param ip 要查询的 IP 地址（必填）
     * @param lang 返回语言（可选，zh=中文默认，en=英文）
     * @return 格式化的位置信息字符串
     */
    @Tool(
            name = "queryIpLocation",
            description = """
                    查询 IP 地址的地理位置信息，包括国家、省份、城市、运营商、经纬度、时区等。
                    当用户询问 IP 归属地、IP 位置、IP 来源、这个 IP 是哪里的、IP 在哪个国家/城市 时调用此工具。
                    支持 IPv4 和 IPv6 地址。
                    【重要】此工具返回的结果已经是格式化的完整回复，请直接将返回内容原样发送给用户，不要修改或重新组织语言，保持原有的换行和 emoji 格式。
                    """
    )
    public String queryIpLocation(
            @ToolParam(required = true, description = "要查询的 IP 地址，例如 8.8.8.8、114.114.114.114")
            String ip,
            @ToolParam(required = false, description = "返回语言，zh=中文（默认），en=英文")
            String lang) {

        IpLocationReport report = ipLocationService.queryIpLocation(ip, lang);
        return ipLocationService.formatResult(report);
    }
}
