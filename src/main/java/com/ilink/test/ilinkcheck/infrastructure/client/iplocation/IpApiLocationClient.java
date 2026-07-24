package com.ilink.test.ilinkcheck.infrastructure.client.iplocation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ilink.test.ilinkcheck.application.tool.categories.iplocation.IpLocationReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class IpApiLocationClient {

    private final RestClient restClient;

    public IpApiLocationClient(RestClient.Builder restClientBuilder,
                               @Value("${app.iplocation.base-url:http://ip-api.com}") String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(trimTrailingSlash(baseUrl))
                .build();
    }

    public IpLocationReport query(String ip, String lang) {
        try {
            ApiIpLocationResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/json/{ip}")
                            .queryParam("lang", lang)
                            .build(ip))
                    .retrieve()
                    .body(ApiIpLocationResponse.class);

            if (response == null) {
                throw new IpLocationClientException(IpLocationClientException.Type.INVALID_RESPONSE,
                        "IP location API returned an empty body");
            }

            return response.toReport(ip);
        }
        catch (RestClientResponseException e) {
            throw new IpLocationClientException(IpLocationClientException.Type.UPSTREAM_ERROR,
                    e.getMessage(), e);
        }
        catch (IpLocationClientException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IpLocationClientException(IpLocationClientException.Type.UPSTREAM_ERROR,
                    "IP location API call failed", e);
        }
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiIpLocationResponse(
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

        IpLocationReport toReport(String queryIp) {
            if (!"success".equals(status)) {
                return new IpLocationReport(
                        queryIp, status, null, null, null, null,
                        null, null, null, null, null, null, null, null,
                        StringUtils.hasText(message) ? message : "查询失败"
                );
            }

            return new IpLocationReport(
                    queryIp, status, country, countryCode, region, regionName,
                    city, zip, lat, lon, timezone, isp, org, as, null
            );
        }
    }
}
