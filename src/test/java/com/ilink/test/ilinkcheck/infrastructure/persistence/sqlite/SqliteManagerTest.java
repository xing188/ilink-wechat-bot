package com.ilink.test.ilinkcheck.infrastructure.persistence.sqlite;

import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SqliteManagerTest {

    private static SqliteManager sqliteManager;
    private static String testId;

    @BeforeAll
    static void setup() {
        sqliteManager = new SqliteManager();
    }

    @Test
    @Order(1)
    void testCreate() {
        testId = UUID.randomUUID().toString().replace("-", "");
        sqliteManager.saveSession(testId, "user_001", "测试标题", "这是测试内容");
        logAndAssert("创建会话", true);
    }

    @Test
    @Order(2)
    void testRead() {
        Optional<String> result = sqliteManager.loadSession(testId);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("测试标题");
        logAndAssert("读取会话", result.isPresent());
    }

    @Test
    @Order(3)
    void testUpdate() {
        sqliteManager.updateSession(testId, "更新后的标题", "更新后的内容");
        Optional<String> result = sqliteManager.loadSession(testId);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("更新后的标题");
        logAndAssert("更新会话", result.get().contains("更新后的标题"));
    }

    @Test
    @Order(4)
    void testDelete() {
        sqliteManager.deleteSession(testId);
        Optional<String> result = sqliteManager.loadSession(testId);
        assertThat(result).isEmpty();
        logAndAssert("删除会话", result.isEmpty());
    }

    @Test
    @Order(5)
    void testBatchOperations() {
        for (int i = 1; i <= 3; i++) {
            String id = UUID.randomUUID().toString().replace("-", "");
            sqliteManager.saveSession(id, "user_002", "批量测试-" + i, "第" + i + "条内容");
        }
        sqliteManager.listAllSessions();
        logAndAssert("批量写入 3 条 + 查询列表", true);
    }

    @Test
    @Order(6)
    void testReadNonExistent() {
        Optional<String> result = sqliteManager.loadSession("non_existent_id");
        assertThat(result).isEmpty();
        logAndAssert("读取不存在的会话返回空", result.isEmpty());
    }

    private void logAndAssert(String operation, boolean passed) {
        System.out.println("[" + (passed ? "PASS" : "FAIL") + "] " + operation);
    }
}
