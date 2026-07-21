package com.ilink.test.ilinkcheck.service;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.ilink.test.ilinkcheck.processor.MessageDispatcher;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.ilink", name = "enabled", havingValue = "true")
public class WechatBotService implements CommandLineRunner {

    private final MessageDispatcher messageDispatcher;
    private final long pollIntervalMs;
    private final ExecutorService pollExecutor;

    private volatile ILinkClient iLinkClient;
    private volatile boolean running = true;

    public WechatBotService(MessageDispatcher messageDispatcher,
                            @Value("${app.ilink.poll-interval-ms:1000}") long pollIntervalMs) {
        this.messageDispatcher = messageDispatcher;
        this.pollIntervalMs = pollIntervalMs;
        //创建独立守护线程池，单独跑消息轮询循环，不占用主线程
        this.pollExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ilink-poll-loop");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void run(String... args) {
        pollExecutor.submit(this::startBotLoop);
    }

    private void startBotLoop() {
        try {
            log.info("初始化 WeChat bot...");

            ILinkConfig config = ILinkConfig.builder()
                    .connectTimeoutMs(35000)
                    .readTimeoutMs(35000)
                    .writeTimeoutMs(35000)
                    .httpMaxRetries(3)
                    .retryBaseDelayMs(1000)
                    .retryMaxDelayMs(10000)
                    .heartbeatEnabled(true)
                    .heartbeatIntervalMs(30000)
                    .autoReconnectEnabled(true)
                    .reconnectMaxAttempts(20)
                    .reconnectBaseDelayMs(1000)
                    .reconnectMaxDelayMs(10000)
                    .channelVersion("1.0.0")
                    .build();

            iLinkClient = ILinkClient.builder()
                    .config(config)
                    .onLogin(new com.github.wechat.ilink.sdk.core.listener.OnLoginListener() {
                        @Override
                        public void onLoginSuccess(LoginContext context) {
                            log.info("登录成功. botId={}", context.getBotId());
                        }

                        @Override
                        public void onLoginFailure(Throwable throwable) {
                            log.error("登录失败: {}", throwable.getMessage(), throwable);
                        }
                    })
                    .onDisconnect(new com.github.wechat.ilink.sdk.core.listener.OnDisconnectListener() {
                        @Override
                        public void onDisconnect(Throwable throwable) {
                            log.warn("iLink连接断开: {}", throwable == null ? "unknown" : throwable.getMessage());
                        }

                        @Override
                        public void onReconnectStart(int attempt) {
                            log.info("iLink重连接, attempt={}", attempt);
                        }

                        @Override
                        public void onReconnectSuccess() {
                            log.info("iLink重连接成功！");
                        }

                        @Override
                        public void onReconnectFailed(Throwable throwable) {
                            log.error("iLink重连接失败: {}", throwable == null ? "unknown" : throwable.getMessage(), throwable);
                        }
                    })
                    .onHeartbeat(new com.github.wechat.ilink.sdk.core.listener.OnHeartbeatListener() {
                        @Override
                        public void onHeartbeatSuccess() {
                            log.debug("iLink heartbeat ok");
                        }

                        @Override
                        public void onHeartbeatFailure(Throwable throwable) {
                            log.warn("iLink heartbeat failed: {}", throwable == null ? "unknown" : throwable.getMessage());
                        }
                    })
                    .onMessage(messages -> {
                        for (var message : messages) {
                            messageDispatcher.dispatch(iLinkClient, message);
                        }
                    })
                    .build();

            String qrCodeContent = iLinkClient.executeLogin();
            log.info("\n-------------------------------------------\n{}\n{}\n-------------------------------------------",
                    "请用微信扫码：",
                    qrCodeContent);

            LoginContext context = iLinkClient.getLoginFuture().get();
            log.info("Bot准备就绪。正在监听中... botId={}", context.getBotId());

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    iLinkClient.getUpdates();
                    Thread.sleep(pollIntervalMs);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                catch (Exception e) {
                    log.warn("Polling failed, will retry: {}", e.getMessage(), e);
                    try {
                        Thread.sleep(pollIntervalMs);
                    }
                    catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        catch (Exception e) {
            log.error("WeChat bot 轮询意外中断！", e);
        }
    }

    @PreDestroy
    public void close() throws Exception {
        running = false;
        pollExecutor.shutdownNow();
        if (iLinkClient != null) {
            iLinkClient.close();
        }
    }
}
