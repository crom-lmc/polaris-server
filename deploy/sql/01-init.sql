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

-- 3) 表结构和种子数据
-- 本脚本只建库、建业务账号和授权，不依赖任何业务表。
-- 全新库请按根目录 README 的“初始化六个业务库”章节，逐库执行 schema.sql 后再执行 data.sql。
-- 初始化完成后保持 .env 中各服务的 *_SQL_INIT_MODE=never。

-- 4) 正式域名和应用密钥
-- 必须先完成 sso_ms 的 schema.sql/data.sql，再按实际部署域名调整 sso_application 的
-- base_url、redirect_uri，并核对各应用 app_secret 与 .env 中对应 *_APP_SECRET 一致。
-- 回调配置不属于本建库脚本，避免在表尚未创建时执行 UPDATE 失败。
