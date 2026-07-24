package com.ilink.test.ilinkcheck.application.tool.categories.iplocation;

/**
 * IP 地址位置信息数据模型
 *
 * <p>封装 ip-api.com 返回的 IP 归属地信息。</p>
 *
 * @author 李佳霖
 */
public record IpLocationReport(
        String query,
        String status,
        String country,
        String countryCode,
        String region,
        String regionName,
        String city,
        String zip,
        Double lat,
        Double lon,
        String timezone,
        String isp,
        String org,
        String as,
        String message
) {

    /**
     * 查询是否成功
     */
    public boolean isSuccess() {
        return "success".equals(status);
    }

    /**
     * 格式化为易读的中文地址
     */
    public String formatAddress() {
        if (!isSuccess()) {
            return "查询失败: " + (message != null ? message : "未知错误");
        }
        StringBuilder sb = new StringBuilder();
        if (country != null) sb.append(country);
        if (regionName != null && !regionName.equals(country)) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(regionName);
        }
        if (city != null && !city.equals(regionName)) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(city);
        }
        return sb.toString();
    }

    /**
     * 格式化坐标
     */
    public String formatCoordinates() {
        if (lat != null && lon != null) {
            return String.format("%.4f°N, %.4f°E", lat, Math.abs(lon));
        }
        return "未知";
    }
}
