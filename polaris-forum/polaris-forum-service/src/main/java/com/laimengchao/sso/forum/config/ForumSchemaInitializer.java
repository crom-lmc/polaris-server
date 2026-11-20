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
import java.util.Arrays;
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
            if (tableExists("forum_board")) {
                addColumnIfMissing("forum_board", "parent_key", "VARCHAR(32) NOT NULL DEFAULT '' AFTER board_key");
                addIndexIfMissing("forum_board", "idx_board_parent", "parent_key,enabled,sort_no");
            }
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
                addColumnIfMissing("forum_member", "experience", "INT NOT NULL DEFAULT 0 AFTER points");
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
                addColumnIfMissing("forum_exchange", "source", "VARCHAR(16) NOT NULL DEFAULT 'EXCHANGE' AFTER points");
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
            if (tableExists("forum_lottery_prize")) {
                addColumnIfMissing("forum_lottery_prize", "prize_type", "VARCHAR(16) NOT NULL DEFAULT 'POINTS' AFTER prize_key");
                addColumnIfMissing("forum_lottery_prize", "gift_id", "BIGINT NULL AFTER points");
            }
            if (tableExists("forum_gift")) {
                addColumnIfMissing("forum_gift", "image_url", "VARCHAR(500) NOT NULL DEFAULT '' AFTER icon");
                addColumnIfMissing("forum_gift", "min_level", "INT NOT NULL DEFAULT 1 AFTER image_url");
            }
            if (tableExists("forum_level")) {
                addColumnIfMissing("forum_level", "min_experience", "INT NOT NULL DEFAULT 0 AFTER name");
                // 历史库兼容：只有老结构里才存在 min_points；新库/已迁移库无此列，直接跳过
                if (columnExists("forum_level", "min_points")) {
                    jdbcTemplate.update("UPDATE forum_level SET min_experience=min_points WHERE min_experience=0 AND min_points>0");
                }
                // 内置等级标记：存量库补列后，把 20 个种子等级（level_no 1-20）标记为内置，防止后台误删
                addColumnIfMissing("forum_level", "builtin", "TINYINT NOT NULL DEFAULT 0 AFTER enabled");
                jdbcTemplate.update("UPDATE forum_level SET builtin=1 WHERE level_no BETWEEN 1 AND 20 AND builtin=0");
            }
            snowflakeMigration.migrate();
            synchronizeColumnComments();
            seedBoards();
            migrateBoardTagsToChildren();
            backfillTags();
            seedSiteConfig();
            seedLotteryConfig();
            seedLotteryPrizes();
            normalizeLotteryWeights();
            seedLevels();
            seedExperienceRules();
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
                  board_key VARCHAR(32) NOT NULL, parent_key VARCHAR(32) NOT NULL DEFAULT '' COMMENT '父级板块标识，为空表示一级板块',
                  name VARCHAR(80) NOT NULL, description VARCHAR(300) NOT NULL DEFAULT '', color VARCHAR(16) NOT NULL DEFAULT '#5b67f1',
                  sort_no INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(board_key), KEY idx_board_parent(parent_key,enabled,sort_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛板块（一二级）'
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
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_banner (
                  id BIGINT NOT NULL, title VARCHAR(120) NOT NULL DEFAULT '',
                  image_attachment_id BIGINT NULL, link_url VARCHAR(500) NOT NULL DEFAULT '',
                  sort_no INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id), KEY idx_banner(enabled,sort_no,id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛首页轮播图'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_lottery_config (
                  id TINYINT NOT NULL, mode VARCHAR(16) NOT NULL DEFAULT 'SQUARE',
                  lottery_cost INT NOT NULL DEFAULT 50, daily_limit INT NOT NULL DEFAULT 3,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛抽奖配置'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_lottery_prize (
                  id BIGINT NOT NULL, prize_key VARCHAR(32) NOT NULL,
                  prize_type VARCHAR(16) NOT NULL DEFAULT 'POINTS', gift_id BIGINT NULL,
                  name VARCHAR(80) NOT NULL,
                  points INT NOT NULL DEFAULT 0, color VARCHAR(16) NOT NULL DEFAULT '#eef0ff',
                  weight INT NOT NULL DEFAULT 1, sort_no INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id), KEY idx_prize_sort(enabled,sort_no,id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛抽奖奖项'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_level (
                  id BIGINT NOT NULL, level_no INT NOT NULL, name VARCHAR(80) NOT NULL,
                  min_experience INT NOT NULL DEFAULT 0, color VARCHAR(16) NOT NULL DEFAULT '#5b67f1',
                  icon VARCHAR(64) NOT NULL DEFAULT '', description VARCHAR(500) NOT NULL DEFAULT '',
                  privileges VARCHAR(500) NOT NULL DEFAULT '', sort_no INT NOT NULL DEFAULT 0,
                  enabled TINYINT NOT NULL DEFAULT 1,
                  builtin TINYINT NOT NULL DEFAULT 0 COMMENT '1系统内置(不可删除)',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id), UNIQUE KEY uk_level_no(level_no), KEY idx_level_enabled(enabled,sort_no,level_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区等级晋升路线'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_experience_rule (
                  id BIGINT NOT NULL, rule_key VARCHAR(32) NOT NULL, name VARCHAR(80) NOT NULL,
                  experience INT NOT NULL DEFAULT 0, sort_no INT NOT NULL DEFAULT 0,
                  enabled TINYINT NOT NULL DEFAULT 1,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY(id), UNIQUE KEY uk_exp_rule_key(rule_key), KEY idx_exp_rule_enabled(enabled,sort_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区经验获取规则'
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
        seedBoard("devops", "运维部署", "Docker、Linux、CI/CD 与可观测性", "#fa8c16", 4);
        seedBoard("design", "产品设计", "产品思考、设计方法与创意灵感", "#a855f7", 5);
        seedBoard("career", "职场成长", "经验分享、学习方法与职业发展", "#eb4b8d", 6);
    }

    private void seedBoard(String key, String name, String description, String color, int sortNo) {
        jdbcTemplate.update("INSERT IGNORE INTO forum_board(board_key,name,description,color,sort_no) VALUES(?,?,?,?,?)",
                key, name, description, color, sortNo);
    }

    /**
     * 把历史 forum_tag 中的板块标签复制为 forum_board 的二级板块，供「一级/二级板块」导航使用。
     * 保留 forum_tag 原数据作为内容标签表，按 (parent_key,name) 幂等，重复启动不会生成重复板块。
     */
    private void migrateBoardTagsToChildren() {
        if (!tableExists("forum_tag")) return;
        // 仅迁移一次：一旦存在二级板块即视为已迁移，避免把发帖后新注册的内容标签持续复制成二级板块。
        Integer migrated = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM forum_board WHERE parent_key<>''", Integer.class);
        if (migrated != null && migrated > 0) return;
        List<Map<String, Object>> tags = jdbcTemplate.queryForList(
                "SELECT board_key AS boardKey,name,sort_no AS sortNo,enabled FROM forum_tag ORDER BY board_key,sort_no,id");
        for (Map<String, Object> tag : tags) {
            String parent = String.valueOf(tag.get("boardKey"));
            String name = String.valueOf(tag.get("name"));
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM forum_board WHERE parent_key=? AND name=?", Integer.class, parent, name);
            if (exists != null && exists > 0) continue;
            jdbcTemplate.update("INSERT IGNORE INTO forum_board(board_key,parent_key,name,sort_no,enabled,color) VALUES(?,?,?,?,?,?)",
                    "p" + IdWorker.getId(), parent, name, tag.get("sortNo"), tag.get("enabled"), "#5b67f1");
        }
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

    private void seedLotteryConfig() {
        jdbcTemplate.update("INSERT IGNORE INTO forum_lottery_config(id,mode,lottery_cost,daily_limit) VALUES(1,'SQUARE',50,3)");
    }

    /**
     * 把原先硬编码在 RewardService 中的 8 个奖项落到奖项表，按 prize_key 幂等。
     * 只在奖项表为空时灌入种子，后续由后台「抽奖配置」维护，不再覆盖运营修改。
     */
    private void seedLotteryPrizes() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM forum_lottery_prize", Integer.class);
        if (count != null && count > 0) return;
        // 权重为中奖概率百分比，启用奖项合计须为 100
        String[][] seeds = {
                {"points10", "10 积分", "10", "#eef0ff", "16", "1"},
                {"thanks", "谢谢参与", "0", "#f6f7fb", "21", "2"},
                {"points30", "30 积分", "30", "#eafaf5", "6", "3"},
                {"points5", "5 积分", "5", "#fff5e9", "21", "4"},
                {"points100", "100 积分", "100", "#fff0f6", "2", "5"},
                {"points20", "20 积分", "20", "#eef0ff", "10", "6"},
                {"thanks2", "再接再厉", "0", "#f6f7fb", "21", "7"},
                {"points50", "50 积分", "50", "#e6f4ff", "3", "8"}
        };
        for (String[] seed : seeds) {
            jdbcTemplate.update("INSERT INTO forum_lottery_prize(id,prize_key,name,points,color,weight,sort_no,enabled) "
                            + "VALUES(?,?,?,?,?,?,?,1)",
                    IdWorker.getId(), seed[0], seed[1], Integer.parseInt(seed[2]), seed[3],
                    Integer.parseInt(seed[4]), Integer.parseInt(seed[5]));
        }
    }

    /**
     * 社区等级晋升路线种子：按累计经验门槛配置（发帖+50、回复+25，见 seedExperienceRules）。
     * 共 20 级：1-10 级沿用原门槛与名称，11-20 级每级 +100 平滑扩展到 1550，
     * 各级配套独立名称、配色与权益（权益可与礼品等级门槛呼应）。按 level_no 幂等，后台可后续修改门槛、名称与权益。
     */
    private void seedLevels() {
        String[][] seeds = {
                {"1", "见习成员", "0", "#9aa4b2", "初入社区，完成资料完善即可开启成长之旅", "浏览公开内容、参与基础互动"},
                {"2", "注册成员", "150", "#5b8def", "开始活跃于社区，分享你的第一个观点", "发布内容、参与评论与签到"},
                {"3", "活跃成员", "200", "#13a8a8", "持续贡献优质内容，成为社区熟面孔", "创建投票、参与话题讨论"},
                {"4", "资深成员", "250", "#22a06b", "稳定输出，赢得社区伙伴的认可", "内容推荐权益、优先展示"},
                {"5", "社区达人", "300", "#fa8c16", "在领域内具备影响力，收获大量点赞", "专属身份标识、活动优先参与"},
                {"6", "社区专家", "350", "#a855f7", "成为社区中坚力量，助力他人成长", "专家标识、内容加权展示"},
                {"7", "社区领袖", "400", "#eb4b8d", "引领社区氛围，凝聚同好共同成长", "领袖标识、专属运营活动"},
                {"8", "荣耀元老", "450", "#d4a017", "长期陪伴社区，见证每一次成长", "元老标识、年度纪念权益"},
                {"9", "星光大使", "500", "#2f54eb", "社区标杆，以行动传递价值", "大使标识、核心共建资格"},
                {"10", "北极星传奇", "550", "#e0301e", "进入社区荣誉序列，开启进阶之旅", "传奇标识、进阶礼品兑换资格"},
                {"11", "星耀新锐", "650", "#7c3aed", "突破传奇之境，成为社区新星", "星耀标识、稀有礼品兑换资格"},
                {"12", "轨道先锋", "750", "#2563eb", "在社区建设中一马当先", "先锋标识、专属活动邀请"},
                {"13", "星云舵手", "850", "#0891b2", "凝聚团队，引领话题风向", "舵手标识、内容优先推荐"},
                {"14", "深空游侠", "950", "#0d9488", "自由探索，贡献多元优质内容", "游侠标识、高级礼品兑换资格"},
                {"15", "星轨执事", "1050", "#c026d3", "受托管理社区秩序与活动", "执事标识、限定礼品兑换资格"},
                {"16", "星河领事", "1150", "#db2777", "代表社区连接更广阔的伙伴", "领事标识、专属身份装扮"},
                {"17", "星际统帅", "1250", "#e11d48", "统筹大型活动与核心项目", "统帅标识、核心共建资格"},
                {"18", "星域领主", "1350", "#ea580c", "深耕领域，拥有广泛影响力", "领主标识、稀有礼品优先兑换"},
                {"19", "星穹守望", "1450", "#ca8a04", "长期守望，守护社区精神", "守望标识、全站荣誉展示"},
                {"20", "北极星至尊", "1550", "#b91c1c", "社区至高荣誉，北极星之巅", "至尊标识、全部权益解锁"}
        };
        for (String[] seed : seeds) {
            jdbcTemplate.update("INSERT IGNORE INTO forum_level(id,level_no,name,min_experience,color,description,privileges,sort_no,enabled,builtin) "
                            + "VALUES(?,?,?,?,?,?,?,?,1,1)",
                    IdWorker.getId(), Integer.parseInt(seed[0]), seed[1], Integer.parseInt(seed[2]), seed[3],
                    seed[4], seed[5], Integer.parseInt(seed[0]));
        }
    }

    /**
     * 经验获取规则种子：发帖 +50、回复 +25。按 rule_key 幂等，后台可调整每项经验值。
     */
    private void seedExperienceRules() {
        String[][] seeds = {
                {"POST", "发布内容", "50", "1"},
                {"COMMENT", "回复评论", "25", "2"}
        };
        for (String[] seed : seeds) {
            jdbcTemplate.update("INSERT IGNORE INTO forum_experience_rule(id,rule_key,name,experience,sort_no,enabled) "
                            + "VALUES(?,?,?,?,?,1)",
                    IdWorker.getId(), seed[0], seed[1], Integer.parseInt(seed[2]), Integer.parseInt(seed[3]));
        }
    }

    /**
     * 把启用奖项的权重按比例归一化为合计 100（百分比制）。仅当合计不为 100 时执行，
     * 保留运营设置的相对概率；最大余数法保证整数且总和精确为 100。
     */
    private void normalizeLotteryWeights() {
        if (!tableExists("forum_lottery_prize")) return;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,weight FROM forum_lottery_prize WHERE enabled=1 ORDER BY sort_no,id");
        if (rows.isEmpty()) return;
        int[] weights = new int[rows.size()];
        int total = 0;
        for (int i = 0; i < rows.size(); i++) {
            Object raw = rows.get(i).get("WEIGHT");
            if (raw == null) raw = rows.get(i).get("weight");
            weights[i] = raw == null ? 0 : Math.max(0, ((Number) raw).intValue());
            total += weights[i];
        }
        if (total == 100) return;
        int[] scaled = scaleWeights(weights, total, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            if (scaled[i] == weights[i]) continue;
            Object rawId = rows.get(i).get("ID");
            if (rawId == null) rawId = rows.get(i).get("id");
            jdbcTemplate.update("UPDATE forum_lottery_prize SET weight=? WHERE id=?", scaled[i],
                    ((Number) rawId).longValue());
        }
        log.info("论坛抽奖权重已归一化为合计 100（原合计 {}）", total);
    }

    /** 按占比分配到总和 100，最大余数法；total<=0 时平均分配。 */
    private int[] scaleWeights(int[] weights, int total, int n) {
        int[] result = new int[n];
        if (total <= 0) {
            int base = 100 / n;
            int rest = 100 - base * n;
            for (int i = 0; i < n; i++) result[i] = base + (i < rest ? 1 : 0);
            return result;
        }
        double[] exact = new double[n];
        int assigned = 0;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            exact[i] = weights[i] * 100.0 / total;
            result[i] = (int) Math.floor(exact[i]);
            assigned += result[i];
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(exact[b] - result[b], exact[a] - result[a]));
        int rest = 100 - assigned;
        for (int i = 0; i < rest; i++) result[order[i]] += 1;
        return result;
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",
                Integer.class, table, column);
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
