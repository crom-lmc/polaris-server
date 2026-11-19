package com.laimengchao.sso.forum.config;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForumSchemaInitializer implements ApplicationRunner {

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "CREATE TABLE IF NOT EXISTS\\s+(\\w+)\\s*\\((.*?)\\)\\s*ENGINE=", Pattern.DOTALL);
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
            "^\\s*(\\w+)\\s+(.+?)\\s+COMMENT\\s+'([^']*)',?\\s*$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ForumSnowflakeMigration snowflakeMigration;

    @Override
    public void run(ApplicationArguments args) {
        try {
            createTables();
            if (tableExists("forum_thread")) {
                addColumnIfMissing("forum_thread", "cover_attachment_id", "BIGINT NULL AFTER reward");
                addColumnIfMissing("forum_thread", "recommended", "TINYINT NOT NULL DEFAULT 0 AFTER solved");
                addColumnIfMissing("forum_thread", "recommended_at", "DATETIME NULL AFTER recommended");
                addIndexIfMissing("forum_thread", "idx_thread_recommended", "deleted,recommended,recommended_at");
            }
            if (tableExists("forum_member")) {
                addColumnIfMissing("forum_member", "nickname", "VARCHAR(80) NOT NULL DEFAULT '' AFTER username");
                addColumnIfMissing("forum_member", "bio", "VARCHAR(500) NOT NULL DEFAULT '' AFTER nickname");
                addColumnIfMissing("forum_member", "location", "VARCHAR(100) NOT NULL DEFAULT '' AFTER bio");
                addColumnIfMissing("forum_member", "website", "VARCHAR(255) NOT NULL DEFAULT '' AFTER location");
                addColumnIfMissing("forum_member", "avatar_attachment_id", "BIGINT NULL AFTER website");
                addColumnIfMissing("forum_member", "cover_attachment_id", "BIGINT NULL AFTER avatar_attachment_id");
            }
            if (tableExists("forum_comment")) {
                addColumnIfMissing("forum_comment", "parent_comment_id", "BIGINT NULL AFTER thread_id");
                addIndexIfMissing("forum_comment", "idx_comment_parent", "parent_comment_id,deleted,created_at");
            }
            if (tableExists("forum_attachment")) {
                addColumnIfMissing("forum_attachment", "profile_account_id", "BIGINT NULL AFTER draft_id");
                addIndexIfMissing("forum_attachment", "idx_attachment_profile", "profile_account_id,usage_type");
            }
            if (tableExists("forum_exchange")) {
                addColumnIfMissing("forum_exchange", "tracking_no", "VARCHAR(100) NOT NULL DEFAULT ''");
                addColumnIfMissing("forum_exchange", "issued_by", "BIGINT NULL");
                addColumnIfMissing("forum_exchange", "issued_by_name", "VARCHAR(80) NOT NULL DEFAULT ''");
                addColumnIfMissing("forum_exchange", "issued_at", "DATETIME NULL");
                addColumnIfMissing("forum_exchange", "issue_remark", "VARCHAR(500) NOT NULL DEFAULT ''");
                String defaultStatus = jdbcTemplate.queryForObject(
                        "SELECT column_default FROM information_schema.columns "
                                + "WHERE table_schema=DATABASE() AND table_name='forum_exchange' AND column_name='status'",
                        String.class);
                if (!"PENDING_ISSUE".equals(defaultStatus)) {
                    jdbcTemplate.execute("ALTER TABLE forum_exchange MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING_ISSUE'");
                }
                addIndexIfMissing("forum_exchange", "idx_exchange_status", "status,created_at");
                jdbcTemplate.update("UPDATE forum_exchange SET status='PENDING_ISSUE' WHERE status='CREATED'");
            }
            snowflakeMigration.migrate();
            synchronizeColumnComments();
            seedBoards();
            backfillTags();
            seedSiteConfig();
            log.info("论坛数据库结构检查完成");
        } catch (Exception e) {
            log.error("论坛数据库结构初始化失败，请检查 schema.sql 与数据库权限：{}", e.getMessage(), e);
            throw new IllegalStateException("论坛数据库结构初始化失败", e);
        }
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_draft (
                  id BIGINT NOT NULL, account_id BIGINT NOT NULL,
                  kind VARCHAR(16) NOT NULL DEFAULT 'topic', board_key VARCHAR(32) NOT NULL,
                  title VARCHAR(160) NOT NULL DEFAULT '', content TEXT NOT NULL,
                  tags VARCHAR(600) NOT NULL DEFAULT '[]', reward INT NOT NULL DEFAULT 0,
                  cover_attachment_id BIGINT NULL, poll_config TEXT NULL,
                  published_thread_id BIGINT NULL, deleted TINYINT NOT NULL DEFAULT 0,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id), KEY idx_draft_owner(account_id,deleted,updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛服务端草稿'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_attachment (
                  id BIGINT NOT NULL, owner_account_id BIGINT NOT NULL,
                  usage_type VARCHAR(16) NOT NULL DEFAULT 'ATTACHMENT',
                  original_name VARCHAR(255) NOT NULL DEFAULT '', storage_path VARCHAR(500) NOT NULL,
                  mime_type VARCHAR(100) NOT NULL, file_size BIGINT NOT NULL DEFAULT 0,
                  thread_id BIGINT NULL, draft_id BIGINT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id),
                  KEY idx_attachment_owner(owner_account_id,created_at), KEY idx_attachment_draft(draft_id),
                  KEY idx_attachment_thread(thread_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛附件'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_poll (
                  id BIGINT NOT NULL, thread_id BIGINT NOT NULL,
                  mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE', max_choices INT NOT NULL DEFAULT 1,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id), UNIQUE KEY uk_poll_thread(thread_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛投票'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_poll_option (
                  id BIGINT NOT NULL, poll_id BIGINT NOT NULL,
                  option_text VARCHAR(200) NOT NULL, sort_no INT NOT NULL DEFAULT 0,
                  PRIMARY KEY(id), KEY idx_poll_option_sort(poll_id,sort_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛投票选项'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_poll_vote (
                  id BIGINT NOT NULL, poll_id BIGINT NOT NULL,
                  account_id BIGINT NOT NULL, option_id BIGINT NOT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id),
                  UNIQUE KEY uk_poll_vote(poll_id,account_id,option_id),
                  KEY idx_poll_vote_account(poll_id,account_id), KEY idx_poll_vote_option(option_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛投票选票'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_sensitive_word (
                  id BIGINT NOT NULL, word VARCHAR(100) NOT NULL,
                  enabled TINYINT NOT NULL DEFAULT 1,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id), UNIQUE KEY uk_sensitive_word(word)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛敏感词'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_favorite (
                  id BIGINT NOT NULL, account_id BIGINT NOT NULL, thread_id BIGINT NOT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id),
                  UNIQUE KEY uk_favorite_account_thread(account_id,thread_id), KEY idx_favorite_thread(thread_id,created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛帖子收藏'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_badge (
                  id BIGINT NOT NULL, name VARCHAR(80) NOT NULL,
                  description VARCHAR(500) NOT NULL DEFAULT '', icon VARCHAR(64) NOT NULL DEFAULT '',
                  color VARCHAR(16) NOT NULL DEFAULT '', sort_no INT NOT NULL DEFAULT 0,
                  rule_type VARCHAR(32) NOT NULL, threshold INT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
                  created_by BIGINT NOT NULL, published_at DATETIME NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id), KEY idx_badge_status(status,sort_no,id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛勋章规则'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_member_badge (
                  id BIGINT NOT NULL, account_id BIGINT NOT NULL, badge_id BIGINT NOT NULL,
                  awarded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id),
                  UNIQUE KEY uk_member_badge(account_id,badge_id), KEY idx_member_badge_badge(badge_id,awarded_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛成员已获得勋章'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_notification (
                  id BIGINT NOT NULL, recipient_account_id BIGINT NOT NULL, actor_account_id BIGINT NULL,
                  type VARCHAR(32) NOT NULL, biz_id BIGINT NOT NULL, thread_id BIGINT NULL,
                  title VARCHAR(160) NOT NULL, summary VARCHAR(500) NOT NULL DEFAULT '', read_at DATETIME NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id),
                  UNIQUE KEY uk_notification_recipient_biz(recipient_account_id,type,biz_id),
                  KEY idx_notification_recipient_read(recipient_account_id,read_at,created_at),
                  KEY idx_notification_thread(thread_id,biz_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛独立通知'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_board (
                  board_key VARCHAR(32) NOT NULL, name VARCHAR(80) NOT NULL,
                  description VARCHAR(300) NOT NULL DEFAULT '', color VARCHAR(16) NOT NULL DEFAULT '#5b67f1',
                  sort_no INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(board_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛板块'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_tag (
                  id BIGINT NOT NULL, board_key VARCHAR(32) NOT NULL, name VARCHAR(20) NOT NULL,
                  sort_no INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id), UNIQUE KEY uk_tag_board_name(board_key,name),
                  KEY idx_tag_board(board_key,enabled,sort_no,id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛板块标签'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_follow (
                  account_id BIGINT NOT NULL, followed_account_id BIGINT NOT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY(account_id,followed_account_id), KEY idx_follow_followed(followed_account_id,created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛成员关注关系'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_report (
                  id BIGINT NOT NULL, reporter_account_id BIGINT NOT NULL,
                  target_type VARCHAR(16) NOT NULL, target_id BIGINT NOT NULL, thread_id BIGINT NULL,
                  reason VARCHAR(32) NOT NULL, detail VARCHAR(1000) NOT NULL DEFAULT '',
                  status VARCHAR(16) NOT NULL DEFAULT 'PENDING', handler_account_id BIGINT NULL,
                  handler_name VARCHAR(80) NOT NULL DEFAULT '', handle_result VARCHAR(500) NOT NULL DEFAULT '',
                  handled_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id), UNIQUE KEY uk_report_reporter_target(reporter_account_id,target_type,target_id),
                  KEY idx_report_status(status,created_at), KEY idx_report_target(target_type,target_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛内容举报'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_site_config (
                  id TINYINT NOT NULL, about_content TEXT NOT NULL, footer_text VARCHAR(500) NOT NULL DEFAULT '',
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY(id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛站点配置'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_friend_link (
                  id BIGINT NOT NULL, name VARCHAR(80) NOT NULL, url VARCHAR(500) NOT NULL,
                  sort_no INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id), KEY idx_friend_link(enabled,sort_no,id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛友情链接'
                """);
    }

    private void synchronizeColumnComments() throws IOException {
        String schema = new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
        int updated = 0;
        Matcher tables = CREATE_TABLE_PATTERN.matcher(schema);
        while (tables.find()) {
            String table = tables.group(1);
            if (!tableExists(table)) continue;
            for (String line : tables.group(2).split("\\R")) {
                Matcher column = COLUMN_PATTERN.matcher(line);
                if (!column.matches()) continue;
                String name = column.group(1);
                String definition = column.group(2);
                String comment = column.group(3);
                List<String> current = jdbcTemplate.query(
                        "SELECT column_comment FROM information_schema.columns "
                                + "WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",
                        (rs, rowNum) -> rs.getString(1), table, name);
                if (current.isEmpty() || comment.equals(current.get(0))) continue;
                jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN " + name + " " + definition
                        + " COMMENT '" + comment.replace("'", "''") + "'");
                updated++;
            }
        }
        log.info("论坛字段注释同步完成，本次更新 {} 个字段", updated);
    }

    private void seedBoards() {
        seedBoard("general", "综合讨论", "技术之外，也聊聊工作与生活", "#5b67f1", 1);
        seedBoard("frontend", "前端开发", "Vue、React、工程化与用户体验", "#1677ff", 2);
        seedBoard("backend", "后端架构", "Java、微服务、数据库与系统设计", "#13a8a8", 3);
        seedBoard("devops", "运维部署", "Docker、Linux、CI/CD 与可观测性", "#fa8c16", 4);
        seedBoard("design", "产品设计", "产品思考、设计方法与创意灵感", "#a855f7", 5);
        seedBoard("career", "职场成长", "经验分享、学习方法与职业发展", "#eb4b8d", 6);
    }

    private void seedBoard(String key, String name, String description, String color, int sortNo) {
        jdbcTemplate.update("INSERT IGNORE INTO forum_board(board_key,name,description,color,sort_no) VALUES(?,?,?,?,?)",
                key, name, description, color, sortNo);
    }

    private void backfillTags() {
        if (tableExists("forum_thread")) {
            backfillTags("forum_thread");
        }
        if (tableExists("forum_draft")) {
            backfillTags("forum_draft");
        }
    }

    private void backfillTags(String table) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT board_key AS boardKey,tags FROM " + table + " WHERE deleted=0");
        for (Map<String, Object> row : rows) {
            String boardKey = String.valueOf(row.get("boardKey"));
            try {
                List<String> tags = objectMapper.readValue(String.valueOf(row.get("tags")), new TypeReference<>() {});
                for (String raw : tags) {
                    String tag = raw == null ? "" : raw.trim().replace("#", "");
                    if (!tag.isBlank() && tag.length() <= 20) {
                        jdbcTemplate.update("INSERT IGNORE INTO forum_tag(id,board_key,name) VALUES(?,?,?)",
                                IdWorker.getId(), boardKey, tag);
                    }
                }
            } catch (Exception e) {
                log.warn("跳过无法解析的历史标签：table={},board={}", table, boardKey);
            }
        }
    }

    private void seedSiteConfig() {
        jdbcTemplate.update("""
                INSERT IGNORE INTO forum_site_config(id,about_content,footer_text)
                VALUES(1,'Polaris 社区用于技术交流、经验分享与共同成长。请尊重他人，发布真实、有价值的内容。',
                '© Polaris 社区 · 让每一次分享都有价值')
                """);
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",
                Integer.class, table, column);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("已为表 {} 补充字段 {}", table, column);
        }
    }

    private void addIndexIfMissing(String table, String index, String columns) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.statistics "
                        + "WHERE table_schema=DATABASE() AND table_name=? AND index_name=?",
                Integer.class, table, index);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD INDEX " + index + "(" + columns + ")");
        }
    }
}
