package com.laimengchao.sso.forum.config;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForumSnowflakeMigration {

    static final String VERSION = "forum-snowflake-id-v1";
    private static final String LOCK_NAME = "polaris_forum_snowflake_id_migration";
    private static final long MAX_JAVASCRIPT_SAFE_INTEGER = 9_007_199_254_740_991L;

    private static final List<String> TABLES = List.of(
            "forum_thread", "forum_comment", "forum_like", "forum_point_log", "forum_checkin",
            "forum_lottery_log", "forum_gift", "forum_exchange", "forum_draft", "forum_attachment",
            "forum_poll", "forum_poll_option", "forum_poll_vote", "forum_sensitive_word", "forum_favorite",
            "forum_badge", "forum_member_badge", "forum_notification", "forum_tag", "forum_report",
            "forum_friend_link");

    private static final List<Reference> REFERENCES = List.of(
            ref("forum_comment", "thread_id", "forum_thread"),
            ref("forum_comment", "parent_comment_id", "forum_comment"),
            ref("forum_like", "target_id", "forum_thread", "s.target_type='thread'"),
            ref("forum_like", "target_id", "forum_comment", "s.target_type='comment'"),
            pointRef("PUBLISH", "forum_thread"),
            pointRef("COMMENT", "forum_comment"),
            pointRef("LOTTERY_AWARD", "forum_lottery_log"),
            pointRef("GIFT_EXCHANGE", "forum_gift"),
            ref("forum_exchange", "gift_id", "forum_gift"),
            ref("forum_thread", "cover_attachment_id", "forum_attachment"),
            ref("forum_draft", "cover_attachment_id", "forum_attachment"),
            ref("forum_draft", "published_thread_id", "forum_thread"),
            ref("forum_member", "avatar_attachment_id", "forum_attachment"),
            ref("forum_member", "cover_attachment_id", "forum_attachment"),
            ref("forum_attachment", "thread_id", "forum_thread"),
            ref("forum_attachment", "draft_id", "forum_draft"),
            ref("forum_poll", "thread_id", "forum_thread"),
            ref("forum_poll_option", "poll_id", "forum_poll"),
            ref("forum_poll_vote", "poll_id", "forum_poll"),
            ref("forum_poll_vote", "option_id", "forum_poll_option"),
            ref("forum_favorite", "thread_id", "forum_thread"),
            ref("forum_member_badge", "badge_id", "forum_badge"),
            ref("forum_notification", "thread_id", "forum_thread"),
            ref("forum_notification", "biz_id", "forum_comment",
                    "s.type IN ('THREAD_COMMENT','COMMENT_REPLY')"),
            ref("forum_notification", "biz_id", "forum_member_badge", "s.type='BADGE_AWARDED'"),
            ref("forum_notification", "biz_id", "forum_report", "s.type='REPORT_RESULT'"),
            ref("forum_report", "thread_id", "forum_thread"),
            ref("forum_report", "target_id", "forum_thread", "s.target_type='thread'"),
            ref("forum_report", "target_id", "forum_comment", "s.target_type='comment'"));

    private final DataSource dataSource;

    public void migrate() {
        try (Connection connection = dataSource.getConnection()) {
            createMigrationTables(connection);
            if (!acquireLock(connection)) {
                throw new IllegalStateException("获取论坛雪花 ID 迁移锁超时");
            }
            try {
                if (completed(connection)) {
                    return;
                }
                List<Mapping> pending = prepareMappings(connection);
                migrateData(connection, pending);
                int changedTables = removeAutoIncrement(connection);
                validateData(connection);
                validateNoAutoIncrement(connection);
                markCompleted(connection);
                log.info("论坛雪花 ID 迁移完成：新增映射 {} 条，移除自增表 {} 张", pending.size(), changedTables);
            } finally {
                releaseLock(connection);
            }
        } catch (Exception e) {
            throw new IllegalStateException("论坛历史 ID 雪花化迁移失败", e);
        }
    }

    private void createMigrationTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS forum_id_migration (
                      table_name VARCHAR(64) NOT NULL COMMENT '论坛表名',
                      old_id BIGINT NOT NULL COMMENT '迁移前自增主键ID',
                      new_id BIGINT NOT NULL COMMENT '迁移后雪花主键ID',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '映射创建时间',
                      PRIMARY KEY(table_name,old_id), UNIQUE KEY uk_id_migration_new(table_name,new_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛历史自增ID与雪花ID永久映射'
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS forum_schema_migration (
                      version VARCHAR(64) NOT NULL COMMENT '数据库迁移版本',
                      completed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '迁移完成时间',
                      PRIMARY KEY(version)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛数据库迁移完成记录'
                    """);
        }
    }

    private List<Mapping> prepareMappings(Connection connection) throws SQLException {
        List<Mapping> pending = new ArrayList<>();
        for (String table : TABLES) {
            Set<Long> ids = queryIds(connection, table);
            List<Mapping> mappings = queryMappings(connection, table);
            Map<Long, Long> oldIds = new HashMap<>();
            Map<Long, Long> newIds = new HashMap<>();
            for (Mapping mapping : mappings) {
                if (mapping.oldId() <= 0 || mapping.newId() <= MAX_JAVASCRIPT_SAFE_INTEGER
                        || mapping.oldId().equals(mapping.newId())) {
                    throw new IllegalStateException("论坛 ID 映射无效：" + table + "," + mapping);
                }
                if (oldIds.put(mapping.oldId(), mapping.newId()) != null
                        || newIds.put(mapping.newId(), mapping.oldId()) != null) {
                    throw new IllegalStateException("论坛 ID 映射重复：" + table);
                }
            }
            List<Mapping> tablePending = new ArrayList<>();
            for (Long id : ids) {
                if (oldIds.containsKey(id) || newIds.containsKey(id)) {
                    continue;
                }
                long newId = nextId(ids, newIds.keySet());
                Mapping mapping = new Mapping(table, id, newId);
                tablePending.add(mapping);
                oldIds.put(id, newId);
                newIds.put(newId, id);
            }
            List<Mapping> all = new ArrayList<>(mappings);
            all.addAll(tablePending);
            validateMappings(table, ids, all);
            pending.addAll(tablePending);
        }
        return pending;
    }

    private long nextId(Set<Long> existingIds, Set<Long> assignedIds) {
        long id;
        do {
            id = IdWorker.getId();
        } while (id <= MAX_JAVASCRIPT_SAFE_INTEGER || existingIds.contains(id) || assignedIds.contains(id));
        return id;
    }

    private void validateMappings(String table, Set<Long> ids, List<Mapping> mappings) {
        Set<Long> oldIds = new HashSet<>();
        Set<Long> newIds = new HashSet<>();
        for (Mapping mapping : mappings) {
            if (!oldIds.add(mapping.oldId()) || !newIds.add(mapping.newId())) {
                throw new IllegalStateException("论坛 ID 映射冲突：" + table + "," + mapping);
            }
            if (ids.contains(mapping.oldId()) && ids.contains(mapping.newId())) {
                throw new IllegalStateException("雪花 ID 与同表现有主键冲突：" + table + "," + mapping);
            }
        }
        for (Long id : ids) {
            int matches = 0;
            for (Mapping mapping : mappings) {
                if (mapping.oldId().equals(id) || mapping.newId().equals(id)) {
                    matches++;
                }
            }
            if (matches != 1) {
                throw new IllegalStateException("论坛主键映射覆盖异常：" + table + ",id=" + id + ",matches=" + matches);
            }
        }
    }

    private void migrateData(Connection connection, List<Mapping> pending) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            insertMappings(connection, pending);
            for (Reference reference : REFERENCES) {
                updateReference(connection, reference);
            }
            for (String table : TABLES) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE " + table + " t JOIN forum_id_migration m "
                                + "ON m.table_name=? AND m.old_id=t.id SET t.id=m.new_id")) {
                    statement.setString(1, table);
                    statement.executeUpdate();
                }
            }
            validateData(connection);
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            if (e instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw new IllegalStateException("论坛 ID 数据迁移校验失败", e);
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void insertMappings(Connection connection, List<Mapping> pending) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO forum_id_migration(table_name,old_id,new_id) VALUES(?,?,?)")) {
            for (Mapping mapping : pending) {
                statement.setString(1, mapping.tableName());
                statement.setLong(2, mapping.oldId());
                statement.setLong(3, mapping.newId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void updateReference(Connection connection, Reference reference) throws SQLException {
        String sql = "UPDATE " + reference.tableName() + " s JOIN forum_id_migration m "
                + "ON m.table_name=? AND m.old_id=" + reference.valueExpression("s")
                + " SET s." + reference.columnName() + "=" + reference.targetExpression() + reference.whereClause();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reference.targetTable());
            statement.executeUpdate();
        }
    }

    private void validateData(Connection connection) throws SQLException {
        for (String table : TABLES) {
            long uncovered = scalar(connection,
                    "SELECT COUNT(1) FROM " + table + " t LEFT JOIN forum_id_migration m "
                            + "ON m.table_name=? AND m.new_id=t.id WHERE m.new_id IS NULL", table);
            if (uncovered != 0) {
                throw new IllegalStateException("论坛主键未完成雪花化：" + table + ",rows=" + uncovered);
            }
        }
        for (Reference reference : REFERENCES) {
            String condition = reference.whereClause();
            long oldReferences = scalar(connection,
                    "SELECT COUNT(1) FROM " + reference.tableName() + " s JOIN forum_id_migration m "
                            + "ON m.table_name=? AND m.old_id=" + reference.valueExpression("s")
                            + condition, reference.targetTable());
            if (oldReferences != 0) {
                throw new IllegalStateException("论坛引用仍使用旧 ID：" + reference + ",rows=" + oldReferences);
            }
            long orphans = scalar(connection,
                    "SELECT COUNT(1) FROM " + reference.tableName() + " s LEFT JOIN " + reference.targetTable()
                            + " t ON t.id=" + reference.valueExpression("s")
                            + " WHERE t.id IS NULL AND " + reference.presentCondition("s")
                            + reference.andCondition());
            if (orphans != 0) {
                throw new IllegalStateException("论坛引用存在孤儿数据：" + reference + ",rows=" + orphans);
            }
        }
    }

    private int removeAutoIncrement(Connection connection) throws SQLException {
        int changed = 0;
        for (String table : TABLES) {
            Column column = queryIdColumn(connection, table);
            if (!column.extra().toLowerCase().contains("auto_increment")) {
                continue;
            }
            StringBuilder definition = new StringBuilder(column.type());
            definition.append(" YES".equals(column.nullable()) ? " NULL" : " NOT NULL");
            if (column.defaultValue() != null) {
                definition.append(" DEFAULT '").append(column.defaultValue().replace("'", "''")).append("'");
            }
            definition.append(" COMMENT '").append(column.comment().replace("'", "''")).append("'");
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " MODIFY COLUMN id " + definition);
            }
            changed++;
        }
        return changed;
    }

    private void validateNoAutoIncrement(Connection connection) throws SQLException {
        for (String table : TABLES) {
            if (queryIdColumn(connection, table).extra().toLowerCase().contains("auto_increment")) {
                throw new IllegalStateException("论坛表仍启用自增主键：" + table);
            }
        }
    }

    private Column queryIdColumn(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_type,is_nullable,column_default,extra,column_comment "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND table_name=? AND column_name='id'")) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("论坛表缺少主键字段：" + table + ".id");
                }
                return new Column(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5));
            }
        }
    }

    private Set<Long> queryIds(Connection connection, String table) throws SQLException {
        Set<Long> ids = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id FROM " + table + " ORDER BY id")) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private List<Mapping> queryMappings(Connection connection, String table) throws SQLException {
        List<Mapping> mappings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT old_id,new_id FROM forum_id_migration WHERE table_name=? ORDER BY old_id")) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    mappings.add(new Mapping(table, rs.getLong(1), rs.getLong(2)));
                }
            }
        }
        return mappings;
    }

    private boolean acquireLock(Connection connection) throws SQLException {
        return scalar(connection, "SELECT GET_LOCK('" + LOCK_NAME + "',60)") == 1;
    }

    private void releaseLock(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT RELEASE_LOCK('" + LOCK_NAME + "')");
        } catch (SQLException e) {
            log.warn("释放论坛雪花 ID 迁移锁失败：{}", e.getMessage());
        }
    }

    private boolean completed(Connection connection) throws SQLException {
        return scalar(connection, "SELECT COUNT(1) FROM forum_schema_migration WHERE version='" + VERSION + "'") > 0;
    }

    private void markCompleted(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO forum_schema_migration(version) VALUES(?)")) {
            statement.setString(1, VERSION);
            statement.executeUpdate();
        }
    }

    private long scalar(Connection connection, String sql, String... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static Reference ref(String table, String column, String target) {
        return ref(table, column, target, "");
    }

    private static Reference ref(String table, String column, String target, String condition) {
        return new Reference(table, column, target, condition, false);
    }

    private static Reference pointRef(String bizType, String target) {
        return new Reference("forum_point_log", "biz_id", target,
                "s.biz_type='" + bizType + "' AND s.biz_id REGEXP '^[0-9]+$'", true);
    }

    record Mapping(String tableName, Long oldId, Long newId) {
        @Override
        public String toString() {
            return "oldId=" + oldId + ",newId=" + newId;
        }
    }

    private record Reference(String tableName, String columnName, String targetTable,
                             String condition, boolean stringValue) {
        String valueExpression(String alias) {
            String value = alias + "." + columnName;
            return stringValue ? "CAST(" + value + " AS SIGNED)" : value;
        }

        String targetExpression() {
            return stringValue ? "CAST(m.new_id AS CHAR)" : "m.new_id";
        }

        String whereClause() {
            return condition.isBlank() ? "" : " WHERE " + condition;
        }

        String andCondition() {
            return condition.isBlank() ? "" : " AND " + condition;
        }

        String presentCondition(String alias) {
            String column = alias + "." + columnName;
            return stringValue ? column + " REGEXP '^[0-9]+$'" : column + " IS NOT NULL";
        }
    }

    private record Column(String type, String nullable, String defaultValue, String extra, String comment) {
    }
}
