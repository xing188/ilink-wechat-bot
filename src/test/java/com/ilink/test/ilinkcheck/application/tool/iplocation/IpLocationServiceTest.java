package com.ilink.test.ilinkcheck.application.tool.iplocation;

import com.ilink.test.ilinkcheck.infrastructure.client.iplocation.IpApiLocationClient;
import com.ilink.test.ilinkcheck.infrastructure.client.iplocation.IpLocationClientException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IpLocationServiceTest {

    @Test
    void shouldReturnValidationFailureWhenIpIsMissing() {
        IpApiLocationClient client = mock(IpApiLocationClient.class);
        IpLocationService service = new IpLocationService(client);

        IpLocationReport report = service.queryIpLocation("  ", null);

        assertThat(report.isSuccess()).isFalse();
        assertThat(report.message()).isEqualTo("IP 地址不能为空");
    }

    @Test
    void shouldNormalizeLanguageAndFormatSuccessfulResult() {
        IpApiLocationClient client = mock(IpApiLocationClient.class);
        IpLocationService service = new IpLocationService(client);
        IpLocationReport report = new IpLocationReport(
                "8.8.8.8",
                "success",
                "美国",
                "US",
                "CA",
                "加利福尼亚州",
                "山景城",
                "94043",
                37.386,
                -122.084,
                "America/Los_Angeles",
                "Google LLC",
                "Google Public DNS",
                "AS15169 Google LLC",
                null
        );

        when(client.query("8.8.8.8", "en")).thenReturn(report);

        IpLocationReport result = service.queryIpLocation(" 8.8.8.8 ", "en");
        String formatted = service.formatResult(result);

        assertThat(result).isSameAs(report);
        assertThat(formatted)
                .contains("IP 地址 8.8.8.8 的位置信息")
                .contains("Google LLC")
                .contains("America/Los_Angeles")
                .contains("这是 Google 公共 DNS 服务器");
        verify(client).query("8.8.8.8", "en");
    }

    @Test
    void shouldReturnFriendlyFailureWhenClientThrows() {
        IpApiLocationClient client = mock(IpApiLocationClient.class);
        IpLocationService service = new IpLocationService(client);

        when(client.query("1.1.1.1", "zh_CN"))
                .thenThrow(new IpLocationClientException(IpLocationClientException.Type.UPSTREAM_ERROR, "timeout"));

        IpLocationReport report = service.queryIpLocation("1.1.1.1", null);

        assertThat(report.isSuccess()).isFalse();
        assertThat(report.message()).isEqualTo("查询失败: timeout");
    }
}
