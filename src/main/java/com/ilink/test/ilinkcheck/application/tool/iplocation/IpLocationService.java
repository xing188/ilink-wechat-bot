package com.ilink.test.ilinkcheck.application.tool.iplocation;

import com.ilink.test.ilinkcheck.infrastructure.client.iplocation.IpApiLocationClient;
import com.ilink.test.ilinkcheck.infrastructure.client.iplocation.IpLocationClientException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * IP 地址查询服务
 *
 * <p>负责参数归一化、错误兜底和结果格式化。</p>
 */
@Service
public class IpLocationService {

    private final IpApiLocationClient ipLocationClient;

    public IpLocationService(IpApiLocationClient ipLocationClient) {
        this.ipLocationClient = ipLocationClient;
    }

    /**
     * 查询 IP 地址位置信息
     *
     * @param ip IP 地址（支持 IPv4 和 IPv6）
     * @param lang 语言（zh=中文，en=英文）
     * @return IpLocationReport 位置报告
     */
    public IpLocationReport queryIpLocation(String ip, String lang) {
        String normalizedIp = normalize(ip);
        String normalizedLang = normalizeLang(lang);

        if (!StringUtils.hasText(normalizedIp)) {
            return failedReport(null, "IP 地址不能为空");
        }

        try {
            return ipLocationClient.query(normalizedIp, normalizedLang);
        }
        catch (IpLocationClientException e) {
            return failedReport(normalizedIp, "查询失败: " + e.getMessage());
        }
        catch (Exception e) {
            return failedReport(normalizedIp, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 格式化查询结果为自然语言回复
     */
    public String formatResult(IpLocationReport report) {
        if (!report.isSuccess()) {
            return report.message() != null ? report.message() : "IP 地址查询失败，请检查输入是否正确。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("IP 地址 ").append(report.query()).append(" 的位置信息：\n");
        sb.append("📍 位置: ").append(report.formatAddress()).append("\n");

        if (StringUtils.hasText(report.isp())) {
            sb.append("📡 运营商: ").append(report.isp());
            if (StringUtils.hasText(report.org()) && !report.org().equals(report.isp())) {
                sb.append(" (").append(report.org()).append(")");
            }
            sb.append("\n");
        }

        if (report.lat() != null && report.lon() != null) {
            sb.append("🌍 坐标: ").append(report.formatCoordinates()).append("\n");
        }

        if (StringUtils.hasText(report.timezone())) {
            sb.append("🕐 时区: ").append(report.timezone()).append("\n");
        }

        appendFunFact(sb, report);
        return sb.toString().trim();
    }

    private void appendFunFact(StringBuilder sb, IpLocationReport report) {
        if ("8.8.8.8".equals(report.query()) || "8.8.4.4".equals(report.query())) {
            sb.append("\n💡 这是 Google 公共 DNS 服务器！");
        }
        else if ("1.1.1.1".equals(report.query()) || "1.0.0.1".equals(report.query())) {
            sb.append("\n💡 这是 Cloudflare 公共 DNS 服务器！");
        }
        else if ("114.114.114.114".equals(report.query())) {
            sb.append("\n💡 这是 114DNS 公共服务器！");
        }
    }

    private IpLocationReport failedReport(String queryIp, String message) {
        return new IpLocationReport(
                queryIp, "fail", null, null, null, null,
                null, null, null, null, null, null, null, null,
                message
        );
    }

    private String normalize(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String normalizeLang(String lang) {
        if (!StringUtils.hasText(lang)) {
            return "zh_CN";
        }
        return "en".equalsIgnoreCase(lang) || "english".equalsIgnoreCase(lang)
                ? "en" : "zh_CN";
    }
}
