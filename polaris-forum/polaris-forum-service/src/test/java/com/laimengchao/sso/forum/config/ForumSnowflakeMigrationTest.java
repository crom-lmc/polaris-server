package com.laimengchao.sso.forum.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "FORUM_MIGRATION_TEST_URL", matches = ".+")
class ForumSnowflakeMigrationTest {

    private static final String DATABASE = "forum_snowflake_migration_test";
    private static final long MAX_JAVASCRIPT_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final List<String> TABLES = List.of(
            "forum_thread", "forum_comment", "forum_like", "forum_point_log", "forum_checkin",
            "forum_lottery_log", "forum_gift", "forum_exchange", "forum_draft", "forum_attachment",
            "forum_poll", "forum_poll_option", "forum_poll_vote", "forum_sensitive_word", "forum_favorite",
            "forum_badge", "forum_member_badge", "forum_notification", "forum_tag", "forum_report",
            "forum_friend_link");

    private String testUrl;
    private String username;
    private String password;
    private DriverManagerDataSource dataSource;
    private ForumSnowflakeMigration migration;

    @BeforeEach
    void setUp() throws SQLException {
        testUrl = System.getenv("FORUM_MIGRATION_TEST_URL");
        username = System.getenv().getOrDefault("FORUM_MIGRATION_TEST_USER", "root");
        password = System.getenv().getOrDefault("FORUM_MIGRATION_TEST_PASSWORD", "root");
        recreateDatabase();
        dataSource = new DriverManagerDataSource(testUrl, username, password);
        migration = new ForumSnowflakeMigration(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (testUrl != null) {
            executeOnServer("DROP DATABASE IF EXISTS " + DATABASE);
        }
    }

    @Test
    void rewritesAllPrimaryKeysAndReferencesAndResumesIdempotently() throws Exception {
        createLegacySchema();
        insertLegacyData();

        migration.migrate();

        assertAllTablesMigrated();
        assertEquals(newId("forum_thread", 1), queryLong("SELECT thread_id FROM forum_comment WHERE id="
                + newId("forum_comment", 2)));
        assertEquals(newId("forum_comment", 2), queryLong("SELECT parent_comment_id FROM forum_comment WHERE id="
                + newId("forum_comment", 3)));
        assertEquals(newId("forum_comment", 2), queryLong("SELECT target_id FROM forum_like WHERE id="
                + newId("forum_like", 5)));
        assertEquals(String.valueOf(newId("forum_lottery_log", 9)), queryString(
                "SELECT biz_id FROM forum_point_log WHERE id=" + newId("forum_point_log", 8)));
        assertEquals(String.valueOf(newId("forum_gift", 10)), queryString(
                "SELECT biz_id FROM forum_point_log WHERE id=" + newId("forum_point_log", 9)));
        assertEquals(newId("forum_comment", 3), queryLong("SELECT biz_id FROM forum_notification WHERE id="
                + newId("forum_notification", 23)));
        assertEquals(newId("forum_member_badge", 21), queryLong("SELECT biz_id FROM forum_notification WHERE id="
                + newId("forum_notification", 24)));
        assertEquals(newId("forum_report", 26), queryLong("SELECT biz_id FROM forum_notification WHERE id="
                + newId("forum_notification", 25)));
        assertEquals(newId("forum_attachment", 14), queryLong("SELECT cover_attachment_id FROM forum_member"));
        assertEquals(100L, queryLong("SELECT account_id FROM forum_member"));
        assertEquals(1L, queryLong("SELECT id FROM forum_site_config"));

        long threadId = newId("forum_thread", 1);
        migration.migrate();
        assertEquals(threadId, newId("forum_thread", 1));
        assertEquals(29L, queryLong("SELECT COUNT(1) FROM forum_id_migration"));

        execute("DELETE FROM forum_schema_migration WHERE version='" + ForumSnowflakeMigration.VERSION + "'");
        execute("ALTER TABLE forum_thread MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'");
        migration.migrate();
        assertAllTablesMigrated();
        assertEquals(threadId, newId("forum_thread", 1));
        assertEquals(29L, queryLong("SELECT COUNT(1) FROM forum_id_migration"));
    }

    @Test
    void rollsBackDataChangesWhenAnOrphanReferenceIsFound() throws Exception {
        createLegacySchema();
        execute("INSERT INTO forum_thread(id,board_key,content) VALUES(1,'general','thread')");
        execute("INSERT INTO forum_comment(id,thread_id,author_account_id,content) VALUES(2,999,100,'orphan')");

        assertThrows(IllegalStateException.class, migration::migrate);

        assertEquals(1L, queryLong("SELECT id FROM forum_thread"));
        assertEquals(2L, queryLong("SELECT id FROM forum_comment"));
        assertEquals(999L, queryLong("SELECT thread_id FROM forum_comment"));
        assertEquals(0L, queryLong("SELECT COUNT(1) FROM forum_id_migration"));
        assertEquals(0L, queryLong("SELECT COUNT(1) FROM forum_schema_migration"));
    }

    @Test
    void seedsDoNotDuplicateAfterHistoricalIdsAreRewritten() throws Exception {
        createLegacySchema();
        executeScript("data.sql");
        migration.migrate();
        long mappings = queryLong("SELECT COUNT(1) FROM forum_id_migration");
        long threads = queryLong("SELECT COUNT(1) FROM forum_thread");
        long comments = queryLong("SELECT COUNT(1) FROM forum_comment");
        long gifts = queryLong("SELECT COUNT(1) FROM forum_gift");
        long words = queryLong("SELECT COUNT(1) FROM forum_sensitive_word");

        executeScript("data.sql");
        migration.migrate();

        assertAllTablesMigrated();
        assertEquals(mappings, queryLong("SELECT COUNT(1) FROM forum_id_migration"));
        assertEquals(threads, queryLong("SELECT COUNT(1) FROM forum_thread"));
        assertEquals(comments, queryLong("SELECT COUNT(1) FROM forum_comment"));
        assertEquals(gifts, queryLong("SELECT COUNT(1) FROM forum_gift"));
        assertEquals(words, queryLong("SELECT COUNT(1) FROM forum_sensitive_word"));
    }

    private void createLegacySchema() throws IOException, SQLException {
        String schema = resource("schema.sql");
        String idDefinition = "id BIGINT NOT NULL COMMENT '主键ID'";
        int first = schema.indexOf(idDefinition);
        for (int i = 0; i < TABLES.size(); i++) {
            assertTrue(first >= 0, "缺少第 " + (i + 1) + " 个论坛自增主键定义");
            schema = schema.substring(0, first) + "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'"
                    + schema.substring(first + idDefinition.length());
            first = schema.indexOf(idDefinition, first + 1);
        }
        executeStatements(schema);
    }

    private void insertLegacyData() throws SQLException {
        execute("INSERT INTO forum_board(board_key,name) VALUES('general','综合')");
        execute("INSERT INTO forum_site_config(id,about_content) VALUES(1,'about')");
        execute("INSERT INTO forum_member(account_id,username) VALUES(100,'member')");
        execute("INSERT INTO forum_follow(account_id,followed_account_id) VALUES(100,101)");
        execute("INSERT INTO forum_thread(id,board_key,content,cover_attachment_id) VALUES(1,'general','thread',14)");
        execute("INSERT INTO forum_comment(id,thread_id,author_account_id,content) VALUES(2,1,100,'parent'),(3,1,100,'reply')");
        execute("UPDATE forum_comment SET parent_comment_id=2 WHERE id=3");
        execute("INSERT INTO forum_like(id,account_id,target_type,target_id) VALUES(4,100,'thread',1),(5,100,'comment',2)");
        execute("INSERT INTO forum_lottery_log(id,account_id,prize_key,prize_name) VALUES(9,100,'points','积分')");
        execute("INSERT INTO forum_gift(id,name,category,points) VALUES(10,'gift','test',10)");
        execute("INSERT INTO forum_point_log(id,account_id,change_points,balance,biz_type,biz_id) VALUES"
                + "(6,100,1,1,'PUBLISH','1'),(7,100,1,2,'COMMENT','2'),"
                + "(8,100,1,3,'LOTTERY_AWARD','9'),(9,100,-1,2,'GIFT_EXCHANGE','10')");
        execute("INSERT INTO forum_checkin(id,account_id,checkin_date) VALUES(12,100,'2026-01-01')");
        execute("INSERT INTO forum_exchange(id,account_id,gift_id,gift_name,points) VALUES(11,100,10,'gift',10)");
        execute("INSERT INTO forum_draft(id,account_id,board_key,content,cover_attachment_id,published_thread_id)"
                + " VALUES(13,100,'general','draft',14,1)");
        execute("INSERT INTO forum_attachment(id,owner_account_id,storage_path,mime_type,thread_id,draft_id)"
                + " VALUES(14,100,'test','text/plain',1,13)");
        execute("UPDATE forum_member SET avatar_attachment_id=14,cover_attachment_id=14 WHERE account_id=100");
        execute("INSERT INTO forum_poll(id,thread_id) VALUES(15,1)");
        execute("INSERT INTO forum_poll_option(id,poll_id,option_text) VALUES(16,15,'option')");
        execute("INSERT INTO forum_poll_vote(id,poll_id,account_id,option_id) VALUES(17,15,100,16)");
        execute("INSERT INTO forum_sensitive_word(id,word) VALUES(18,'word')");
        execute("INSERT INTO forum_favorite(id,account_id,thread_id) VALUES(19,100,1)");
        execute("INSERT INTO forum_badge(id,name,rule_type,threshold,created_by) VALUES(20,'badge','THREAD_COUNT',1,100)");
        execute("INSERT INTO forum_member_badge(id,account_id,badge_id) VALUES(21,100,20)");
        execute("INSERT INTO forum_report(id,reporter_account_id,target_type,target_id,thread_id,reason)"
                + " VALUES(26,100,'comment',2,1,'SPAM')");
        execute("INSERT INTO forum_notification(id,recipient_account_id,type,biz_id,thread_id,title) VALUES"
                + "(22,100,'THREAD_COMMENT',2,1,'comment'),(23,100,'COMMENT_REPLY',3,1,'reply'),"
                + "(24,100,'BADGE_AWARDED',21,NULL,'badge'),(25,100,'REPORT_RESULT',26,NULL,'report')");
        execute("INSERT INTO forum_tag(id,board_key,name) VALUES(27,'general','tag')");
        execute("INSERT INTO forum_friend_link(id,name,url) VALUES(28,'link','https://example.com')");
    }

    private void assertAllTablesMigrated() throws SQLException {
        assertEquals(1L, queryLong("SELECT COUNT(1) FROM forum_schema_migration WHERE version='"
                + ForumSnowflakeMigration.VERSION + "'"));
        for (String table : TABLES) {
            if (queryLong("SELECT COUNT(1) FROM " + table) > 0) {
                assertTrue(queryLong("SELECT MIN(id) FROM " + table) > MAX_JAVASCRIPT_SAFE_INTEGER, table);
            }
            assertEquals("", queryString("SELECT extra FROM information_schema.columns WHERE table_schema=DATABASE()"
                    + " AND table_name='" + table + "' AND column_name='id'"), table);
        }
    }

    private long newId(String table, long oldId) throws SQLException {
        return queryLong("SELECT new_id FROM forum_id_migration WHERE table_name='" + table + "' AND old_id=" + oldId);
    }

    private void executeScript(String resource) throws IOException, SQLException {
        executeStatements(resource(resource));
    }

    private String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void executeStatements(String sql) throws SQLException {
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) execute(statement);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long queryLong(String sql) throws SQLException {
        String value = queryString(sql);
        return value == null ? 0L : Long.parseLong(value);
    }

    private String queryString(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return rs.getString(1);
        }
    }

    private void recreateDatabase() throws SQLException {
        executeOnServer("DROP DATABASE IF EXISTS " + DATABASE);
        executeOnServer("CREATE DATABASE " + DATABASE + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }

    private void executeOnServer(String sql) throws SQLException {
        String serverUrl = testUrl.replace("/" + DATABASE, "/");
        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
