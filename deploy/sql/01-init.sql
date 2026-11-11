-- Polarís 生产初始化（MySQL 5.7）
-- 执行：mysql -uroot -p < 01-init.sql
-- 密码请先替换，且与服务器 .env 中的 MYSQL_PASSWORD / *_DB_PASSWORD 保持一致

-- 1) 建库
CREATE DATABASE IF NOT EXISTS sso_ms       DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS settings     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS ehr          DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS cms          DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS blog         DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS polaris_job  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 2) 业务账号（只给业务库权限，不给全局）
CREATE USER IF NOT EXISTS 'polaris'@'127.0.0.1' IDENTIFIED BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON sso_ms.*      TO 'polaris'@'127.0.0.1';
GRANT ALL PRIVILEGES ON settings.*    TO 'polaris'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ehr.*         TO 'polaris'@'127.0.0.1';
GRANT ALL PRIVILEGES ON cms.*         TO 'polaris'@'127.0.0.1';
GRANT ALL PRIVILEGES ON blog.*        TO 'polaris'@'127.0.0.1';
GRANT ALL PRIVILEGES ON polaris_job.* TO 'polaris'@'127.0.0.1';

-- 若服务后续以容器 127.0.0.1 之外的地址连库，再按需增加 'polaris'@'%'
FLUSH PRIVILEGES;

-- 3) 表结构
-- 首次启动时各服务的 *_SQL_INIT_MODE=always 会自动建表（job/settings/ehr/cms/blog/account 的 schema.sql 均幂等）；
-- 建表完成后把 .env 中这些变量改回 never，避免每次启动跑 DDL。
-- 注意：blog 的历史结构来自 tools/blog-migrate.sql，如果库里已有数据，不要用 always 覆盖。

-- 4) SSO 应用回调地址改成正式域名
--    sso-center 是登录页本身，只需要 base_url
UPDATE sso_application SET base_url = 'https://polaris-sso.laimengchao.site' WHERE code = 'sso-center';
UPDATE sso_application SET base_url = 'https://polaris-ehr.laimengchao.site',      redirect_uri = 'https://polaris-ehr.laimengchao.site/callback'      WHERE code = 'ehr';
UPDATE sso_application SET base_url = 'https://polaris-cms.laimengchao.site',      redirect_uri = 'https://polaris-cms.laimengchao.site/callback'      WHERE code = 'cms';
UPDATE sso_application SET base_url = 'https://portal-blog-admin.laimengchao.site', redirect_uri = 'https://portal-blog-admin.laimengchao.site/#/sso/callback' WHERE code = 'blog';
UPDATE sso_application SET base_url = 'https://polaris-portal.laimengchao.site',   redirect_uri = 'https://polaris-portal.laimengchao.site/callback'   WHERE code = 'portal';
UPDATE sso_application SET base_url = 'https://polaris-settings.laimengchao.site', redirect_uri = 'https://polaris-settings.laimengchao.site/callback' WHERE code = 'settings';
UPDATE sso_application SET base_url = 'https://polaris-job.laimengchao.site',      redirect_uri = 'https://polaris-job.laimengchao.site/callback'      WHERE code = 'job';

-- 5) 核对 app_secret（.env 里的 EHR/CMS/BLOG/SETTINGS/JOB_APP_SECRET 必须与这里一致）
SELECT code, name, base_url, redirect_uri, app_secret FROM sso_application ORDER BY code;

-- 6) 管理员账号：全新库没有账号时需要插入一条（本地已有数据的话跳过）
--    本地导出：mysqldump -uroot -p sso_ms sso_account sso_account_app sso_account_role sso_role sso_permission sso_role_permission sso_application > sso-init-data.sql
--    密码为加盐哈希，直接沿用本地库里的哈希值即可，不要明文写入。
