package com.ilink.test.ilinkcheck.adapter.inbound.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/login")
public class QRCodeController {

    private final LoginState loginState;

    public QRCodeController(LoginState loginState) {
        this.loginState = loginState;
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getLoginStatus() {
        return Map.of(
                "status", loginState.getStatus().name(),
                "qrCodeContent", loginState.getQrCodeContent(),
                "botId", loginState.getBotId(),
                "errorMessage", loginState.getErrorMessage()
        );
    }
}
