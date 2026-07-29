package com.ilink.test.ilinkcheck.adapter.inbound.web;

import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * 共享登录状态，供 WechatBotRuntime 写入、QRCodeController 读取。
 */
@Data
@Component
public class LoginState {

    /** 登录状态 */
    public enum Status {
        WAITING_SCAN,
        SCANNING,
        LOGGING_IN,
        SUCCESS,
        FAILED
    }

    private volatile Status status = Status.WAITING_SCAN;
    private volatile String qrCodeContent = "";
    private volatile String botId = "";
    private volatile String errorMessage = "";

    public void reset() {
        this.status = Status.WAITING_SCAN;
        this.qrCodeContent = "";
        this.botId = "";
        this.errorMessage = "";
    }
}
