package com.ilink.test.ilinkcheck.application.tool.iplocation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * IP 地址查询服务
 *
 * <p>调用 ip-api.com API 查询 IP 归属地信息。</p>
 *
 * <h3>API 说明：</h3>
 * <ul>
 *   <li>免费，无需注册和 API Key</li>
 *   <li>限制：每分钟最多 45 次请求</li>
 *   <li>支持 HTTP/HTTPS</li>
 * </ul>
 *
 * @author wq
 */
@Service
public class IpLocationService {

    private static final String API_BASE = "http://ip-api.com/json/";
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

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
            return new IpLocationReport(
                    null, "fail", null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    "IP 地址不能为空"
            );
        }

        try {
            String json = fetchFromApi(normalizedIp, normalizedLang);
            return parseResponse(json, normalizedIp);
        } catch (Exception e) {
            return new IpLocationReport(
                    normalizedIp, "fail", null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    "查询失败: " + e.getMessage()
            );
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

        // 简单的趣味描述
        appendFunFact(sb, report);

        return sb.toString().trim();
    }

    private void appendFunFact(StringBuilder sb, IpLocationReport report) {
        if ("8.8.8.8".equals(report.query()) || "8.8.4.4".equals(report.query())) {
            sb.append("\n💡 这是 Google 公共 DNS 服务器！");
        } else if ("1.1.1.1".equals(report.query()) || "1.0.0.1".equals(report.query())) {
            sb.append("\n💡 这是 Cloudflare 公共 DNS 服务器！");
        } else if ("114.114.114.114".equals(report.query())) {
            sb.append("\n💡 这是 114DNS 公共服务器！");
        }
    }

    private String fetchFromApi(String ipAddress, String lang) throws Exception {
        String urlStr = String.format("%s%s?lang=%s", API_BASE, ipAddress, lang);
        URL url = new URL(urlStr);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } finally {
            conn.disconnect();
        }
    }

    private IpLocationReport parseResponse(String jsonStr, String queryIp) {
        JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();

        String status = json.get("status").getAsString();

        if ("fail".equals(status)) {
            return new IpLocationReport(
                    queryIp, status, null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    json.has("message") ? json.get("message").getAsString() : "查询失败"
            );
        }

        // 成功响应 - 提取所有字段
        String country = getString(json, "country");
        String countryCode = getString(json, "countryCode");
        String region = getString(json, "region");
        String regionName = getString(json, "regionName");
        String city = getString(json, "city");
        String zip = getString(json, "zip");
        Double lat = getDouble(json, "lat");
        Double lon = getDouble(json, "lon");
        String timezone = getString(json, "timezone");
        String isp = getString(json, "isp");
        String org = getString(json, "org");
        String asInfo = getString(json, "as");

        return new IpLocationReport(
                queryIp, status, country, countryCode, region, regionName,
                city, zip, lat, lon, timezone, isp, org, asInfo, null
        );
    }

    private String getString(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : null;
    }

    private Double getDouble(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsDouble() : null;
    }

    private String normalize(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String normalizeLang(String lang) {
        if (!StringUtils.hasText(lang)) {
            return "zh_CN"; // 默认中文
        }
        return "en".equalsIgnoreCase(lang) || "english".equalsIgnoreCase(lang)
                ? "en" : "zh_CN";
    }
}
