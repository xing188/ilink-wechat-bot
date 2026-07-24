package com.ilink.test.ilinkcheck.adapter.inbound.wechat;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.ilink.test.ilinkcheck.infrastructure.persistence.login.LoginSessionRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.ilink", name = "enabled", havingValue = "true")
public class WechatBotRuntime {

    private final MessageDispatcher messageDispatcher;
    private final long pollIntervalMs;
    private final ExecutorService pollExecutor;
    private final LoginSessionRepository loginSessionRepository;

    private volatile ILinkClient iLinkClient;
    private volatile boolean running = true;

    public WechatBotRuntime(MessageDispatcher messageDispatcher,
                            @Value("${app.ilink.poll-interval-ms:1000}") long pollIntervalMs,
                            LoginSessionRepository loginSessionRepository) {
        this.messageDispatcher = messageDispatcher;
        this.pollIntervalMs = pollIntervalMs;
        this.pollExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ilink-poll-loop");
            thread.setDaemon(true);
            return thread;
        });
        this.loginSessionRepository = loginSessionRepository;
    }

    public void start() {
        pollExecutor.submit(this::startBotLoop);
    }

    public Optional<ILinkClient> currentClient() {
        return Optional.ofNullable(iLinkClient);
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

            LoginContext savedContext = loginSessionRepository.load();
            boolean resumed = false;

            if (savedContext != null) {
                iLinkClient = buildClient(config, ResumeContext.of(savedContext));
                if (iLinkClient.isLoggedIn()) {
                    log.info("恢复上次登录成功，跳过扫码。botId={}", savedContext.getBotId());
                    resumed = true;
                }
                else {
                    log.info("上次登录会话已过期，需要重新扫码");
                    iLinkClient.close();
                }
            }

            if (!resumed) {
                iLinkClient = buildClient(config, null);

                String qrCodeContent = iLinkClient.executeLogin();
                log.info("\n-------------------------------------------\n{}\n{}\n-------------------------------------------",
                        "请用微信扫码：",
                        qrCodeContent);

                LoginContext context = iLinkClient.getLoginFuture().get();
                loginSessionRepository.save(context);
                log.info("Bot准备就绪。正在监听中... botId={}", context.getBotId());
            }

            startPollLoop();
        }
        catch (Exception e) {
            log.error("WeChat bot 轮询意外中断！", e);
        }
    }

    private ILinkClient buildClient(ILinkConfig config, ResumeContext resumeContext) {
        var builder = ILinkClient.builder()
                .config(config)
                .onLogin(new com.github.wechat.ilink.sdk.core.listener.OnLoginListener() {
                    @Override
                    public void onLoginSuccess(LoginContext context) {
                        log.info("登录成功。botId={}", context.getBotId());
                        loginSessionRepository.save(context);
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
                        log.info("iLink重连中, attempt={}", attempt);
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
                });

        if (resumeContext != null) {
            builder = builder.resumeContext(resumeContext);
        }

        return builder.build();
    }

    private void startPollLoop() {
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

    @PreDestroy
    public void close() throws Exception {
        running = false;
        pollExecutor.shutdownNow();
        if (iLinkClient != null) {
            iLinkClient.close();
        }
    }
}
