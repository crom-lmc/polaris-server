package com.laimengchao.sso.ehr.module.announcement.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 通知表兜底初始化。
 * <p>
 * 正常情况下由 {@code schema.sql} 随 {@code spring.sql.init} 建表；但部分环境把
 * {@code EHR_SQL_INIT_MODE} 设成 {@code never}（或库用户无 DDL 权限以外的历史原因），
 * 重启后表仍然缺失，接口直接报 {@code Table 'ehr.ehr_announcement' doesn't exist}。
 * 这里在启动时补一次 {@code CREATE TABLE IF NOT EXISTS}，与 sso-account 的 migrateSchema 同思路：
 * 幂等、只补自己这两张表，失败只记录 error 不阻断服务启动（缺表时功能会在接口层明确报错）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ehr_announcement (
                        id                   BIGINT        NOT NULL COMMENT '雪花ID',
                        announcement_no      VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '业务编号，按 settings 单号规则生成（前缀 NOTICE）',
                        title                VARCHAR(200)  NOT NULL COMMENT '标题',
                        summary              VARCHAR(500)  DEFAULT '' COMMENT '摘要（列表展示）',
                        category             VARCHAR(32)   NOT NULL DEFAULT 'OTHER' COMMENT 'HOLIDAY节假日/COMMENDATION表彰/DEPARTMENT部门/POLICY制度/APPOINTMENT部门任命/OTHER其他；字典 ehr.announcement_category',
                        content              LONGTEXT      COMMENT '富文本正文（HTML）',
                        target_type          TINYINT       NOT NULL DEFAULT 0 COMMENT '0全员（集团） 1按部门',
                        target_org_ids       VARCHAR(1000) DEFAULT '' COMMENT '目标组织ID，逗号分隔；全员时为空',
                        target_org_names     VARCHAR(1000) DEFAULT '' COMMENT '目标组织名称快照，逗号分隔',
                        pinned               TINYINT       NOT NULL DEFAULT 0 COMMENT '1置顶',
                        status               TINYINT       NOT NULL DEFAULT 0 COMMENT '0编辑中 1已发布 2已下架',
                        publish_at           DATETIME      DEFAULT NULL COMMENT '发布时间',
                        offline_at           DATETIME      DEFAULT NULL COMMENT '下架时间',
                        publisher_account_id BIGINT        DEFAULT NULL COMMENT '发布人账号ID',
                        publisher_name       VARCHAR(64)   DEFAULT '' COMMENT '发布人登录名快照',
                        read_count           INT           NOT NULL DEFAULT 0 COMMENT '阅读人数',
                        target_count         INT           NOT NULL DEFAULT 0 COMMENT '应读人数（发布时快照）',
                        remark               VARCHAR(255)  DEFAULT '' COMMENT '备注',
                        deleted              TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_ann_status (status, pinned, publish_at),
                        KEY idx_ann_deleted (deleted)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部通知'
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ehr_announcement_read (
                        id              BIGINT   NOT NULL COMMENT '雪花ID',
                        announcement_id BIGINT   NOT NULL COMMENT '通知ID',
                        account_id      BIGINT   NOT NULL COMMENT '账号ID',
                        read_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次阅读时间',
                        deleted         TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_ann_read (announcement_id, account_id),
                        KEY idx_ann_read_account (account_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部通知阅读记录'
                    """);
            // 已存在的旧表没有业务编号列，补一列（幂等）
            addColumnIfMissing("ehr_announcement", "announcement_no",
                    "VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务编号，按 settings 单号规则生成（前缀 NOTICE）' AFTER id");
            log.info("通知表检查完成：ehr_announcement / ehr_announcement_read 均已存在");
        } catch (Exception e) {
            log.error("通知表初始化失败（通知管理 / 通知中心将不可用，请手工执行 schema.sql 末尾的建表语句）：{}",
                    e.getMessage(), e);
        }
    }

    /** 列不存在才执行 ALTER，避免每次启动都改表 */
    private void addColumnIfMissing(String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        log.info("已为表 {} 补充字段 {}", table, column);
    }
}
