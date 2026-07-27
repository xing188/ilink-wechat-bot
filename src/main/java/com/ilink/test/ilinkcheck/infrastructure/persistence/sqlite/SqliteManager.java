package com.ilink.test.ilinkcheck.infrastructure.persistence.sqlite;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Component
public class SqliteManager {

    private static final String DB_URL = "jdbc:sqlite:ilink_bot.db";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Connection connection;

    public SqliteManager() {
        initDatabase();
    }

    public void initDatabase() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            log.info("SQLite 数据库已连接：{}", DB_URL);

            String sql = """
                    CREATE TABLE IF NOT EXISTS sessions (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """;
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
            log.info("表 sessions 已就绪");
        }
        catch (SQLException e) {
            log.error("SQLite 初始化失败", e);
            throw new RuntimeException("SQLite 初始化失败", e);
        }
    }

    public void saveSession(String id, String userId, String title, String content) {
        String now = LocalDateTime.now().format(DTF);
        String sql = """
                INSERT INTO sessions (id, user_id, title, content, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, userId);
            ps.setString(3, title);
            ps.setString(4, content);
            ps.setString(5, now);
            ps.setString(6, now);
            ps.executeUpdate();
            log.info("会话已保存：id={}, title={}", id, title);
        }
        catch (SQLException e) {
            log.error("保存会话失败", e);
        }
    }

    public Optional<String> loadSession(String id) {
        String sql = "SELECT title, content FROM sessions WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String result = "标题：" + rs.getString("title")
                        + "\n内容：" + (rs.getString("content") != null ? rs.getString("content") : "无");
                return Optional.of(result);
            }
        }
        catch (SQLException e) {
            log.error("加载会话失败", e);
        }
        return Optional.empty();
    }

    public void updateSession(String id, String newTitle, String newContent) {
        String now = LocalDateTime.now().format(DTF);
        String sql = "UPDATE sessions SET title = ?, content = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newTitle);
            ps.setString(2, newContent);
            ps.setString(3, now);
            ps.setString(4, id);
            int rows = ps.executeUpdate();
            log.info("会话更新：id={}, 影响行数={}", id, rows);
        }
        catch (SQLException e) {
            log.error("更新会话失败", e);
        }
    }

    public void deleteSession(String id) {
        String sql = "DELETE FROM sessions WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            int rows = ps.executeUpdate();
            log.info("会话删除：id={}, 影响行数={}", id, rows);
        }
        catch (SQLException e) {
            log.error("删除会话失败", e);
        }
    }

    public void listAllSessions() {
        String sql = "SELECT id, user_id, title, created_at FROM sessions ORDER BY created_at DESC";
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            log.info("=== 所有会话列表 ===");
            while (rs.next()) {
                log.info("id={}, user={}, title={}, created={}",
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getString("title"),
                        rs.getString("created_at"));
            }
        }
        catch (SQLException e) {
            log.error("查询会话列表失败", e);
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("SQLite 连接已关闭");
            }
        }
        catch (SQLException e) {
            log.warn("关闭 SQLite 连接失败", e);
        }
    }
}
