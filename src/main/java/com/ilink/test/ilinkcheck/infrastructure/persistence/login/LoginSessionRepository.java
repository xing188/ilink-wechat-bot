package com.ilink.test.ilinkcheck.infrastructure.persistence.login;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;

@Repository
@Slf4j
public class LoginSessionRepository {

    private static final String SESSION_FILE = "bot-session.json";

    private final ObjectMapper objectMapper;

    public LoginSessionRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void save(LoginContext context) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(SESSION_FILE), context);
            log.info("登录会话已保存到 {}", SESSION_FILE);
        }
        catch (IOException e) {
            log.warn("保存登录会话失败", e);
        }
    }

    public LoginContext load() {
        File file = new File(SESSION_FILE);
        if (!file.exists()) {
            return null;
        }
        try {
            LoginContext context = objectMapper.readValue(file, LoginContext.class);
            log.info("已加载上次登录会话");
            return context;
        }
        catch (Exception e) {
            log.warn("加载登录会话失败，将重新扫码", e);
            try {
                file.delete();
            }
            catch (Exception ignored) {
            }
            return null;
        }
    }

    public void delete() {
        File file = new File(SESSION_FILE);
        if (file.exists()) {
            file.delete();
        }
    }
}
