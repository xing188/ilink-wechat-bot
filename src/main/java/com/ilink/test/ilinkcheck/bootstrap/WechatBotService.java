package com.ilink.test.ilinkcheck.bootstrap;

import com.ilink.test.ilinkcheck.adapter.inbound.wechat.WechatBotRuntime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.ilink", name = "enabled", havingValue = "true")
public class WechatBotService implements CommandLineRunner {

    private final WechatBotRuntime wechatBotRuntime;

    public WechatBotService(WechatBotRuntime wechatBotRuntime) {
        this.wechatBotRuntime = wechatBotRuntime;
    }

    @Override
    public void run(String... args) {
        wechatBotRuntime.start();
    }
}
