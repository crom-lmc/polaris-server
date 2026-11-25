<div align="center">
  <img src="https://img.shields.io/badge/STATUS-ARCHIVED-red.svg" alt="Archived">
  <img src="https://img.shields.io/badge/MIGRATED_TO-polaris-blue.svg" alt="Migrated">
</div>

<h2 align="center">⚠️ 本仓库已停止更新</h2>
<p align="center">前端/后端代码已合并迁移至新仓库：<b><a href="https://github.com/crom-lmc/polaris">crom-lmc/polaris</a></b></p>


# Polaris Server

Polaris 一体化企业平台后端，提供统一认证、账号与权限、人力管理、配置与审批、内容管理、门户聚合、博客和任务调度能力。

项目采用 Java 17 + Spring Boot 3 + Spring Cloud 微服务架构。Gateway 是统一访问入口，MySQL 按业务分库，Redis 用于令牌吊销和缓存。Nacos 配置中心、注册中心均为可选项，本地开发可以不安装 Nacos。

> 前端仓库：[polaris-web](https://github.com/crom-lmc/polaris-web)。本文只介绍后端；前端安装、端口和 SSO 联调请同时阅读前端仓库 README。

## 一、技术栈

| 分类 | 技术 |
|---|---|
| 运行环境 | Java 17、Maven 3.6.3+ |
| 核心框架 | Spring Boot 3.2.5、Spring Cloud 2023.0.1 |
| 微服务组件 | Spring Cloud Alibaba 2023.0.1.0、Spring Cloud Gateway |
| 数据访问 | MyBatis-Plus 3.5.7、MySQL 5.7+ |
| 缓存与会话 | Redis，保存令牌吊销名单及部分权限缓存 |
| 认证 | OAuth2 授权码/一次性 ticket 换票、RS256 JWT |
| 服务调用 | OpenFeign、内部接口共享令牌 |
| 配置中心 | Nacos，可选，默认关闭 |
| 构建 | Maven 多模块、Spring Boot 可执行 jar |

## 二、整体架构

```text
浏览器 / 各前端应用
        │
        ▼
polaris-sso-gateway :9080
        │  RS256 公钥验签、令牌吊销检查、路由、可信用户头
        ├──────────────► sso-auth :9001       登录、签发/刷新/吊销令牌
        ├──────────────► sso-account :9002    账号、角色、权限、应用、数据范围
        ├──────────────► cms-service :9004    内容与栏目管理
        ├──────────────► portal-service :9005 门户内容聚合、统一待办
        ├──────────────► ehr-service :9006    人力业务
        ├──────────────► settings-service :9007 字典、审批、消息、安全配置
        └──────────────► job-service :9008    调度任务、执行日志、报警

业务服务之间通过 Feign + X-Internal-Token 调用受保护的内部接口。
```

### 一次请求的认证链路

```text
1. 用户在账号中心登录，auth 校验账号/邮箱、密码、验证码及失败锁定状态。
2. auth 使用 RSA 私钥签发 RS256 access token 和 refresh token。
3. 业务应用通过 OAuth2 一次性 ticket 换取自己的登录态，避免在 URL 中长期传递令牌。
4. 前端请求统一进入 Gateway，Gateway 使用 RSA 公钥验签并检查吊销名单。
5. Gateway 向业务服务传递可信用户头，业务服务建立 LoginUser 上下文。
6. Controller 通过 @Permit 校验按钮/接口权限，数据权限组件再按组织范围过滤数据。
```

auth 保管私钥，Gateway 只持有公钥。生产环境不得把私钥提交到仓库或下发给前端。

## 三、仓库结构

```text
polaris-server/
├── pom.xml                         # Maven 父工程与统一依赖版本
├── polaris-common/                 # 公共响应、异常、认证上下文、加解密等
├── polaris-sso/
│   ├── polaris-sso-gateway/        # 统一网关 :9080
│   ├── polaris-sso-auth/           # 认证服务 :9001
│   ├── polaris-sso-account/        # 账号权限服务 :9002
│   └── polaris-sso-client/         # 认证、账号跨服务客户端
├── polaris-cms/
│   └── polaris-cms-service/        # 内容服务 :9004
├── polaris-portal/
│   └── polaris-portal-service/     # 门户聚合服务 :9005
├── polaris-ehr/
│   └── polaris-ehr-service/        # EHR 服务 :9006
├── polaris-settings/
│   ├── polaris-settings-service/   # 配置、审批与消息服务 :9007
│   └── polaris-settings-client/    # 字典、审批、消息跨服务客户端
├── polaris-job/
│   ├── polaris-job-service/        # 调度中心 :9008
│   └── polaris-job-client/         # 执行器客户端契约与调用支持
├── polaris-nacos/                  # 可选 Nacos 配置和密钥模板
├── deploy/                         # Docker Compose、Nginx 和初始化 SQL
└── scripts/                        # 构建、发布、备份及 Nacos 导入脚本
```

`polaris-common`、各 `*-client` 是依赖模块，不需要单独启动。其余服务均可独立运行。

## 四、功能模块

### 4.1 Gateway：统一网关

- 对外统一监听 `9080`，前端只需要配置一个后端入口。
- 按路径把请求转发到 auth、account、cms、portal、ehr、settings、job。
- 使用 RS256 公钥验证 JWT，检查注销、踢下线产生的吊销记录。
- 将认证后的用户信息转换为业务服务可信头，避免各服务重复实现登录。
- 默认通过 `127.0.0.1:9001~9008` 直连服务；开启 Nacos Discovery 后可走服务发现。

### 4.2 SSO：统一认证与账号中心

**认证服务 sso-auth**

- 支持账号或邮箱登录、图形/邮箱验证码、密码错误计数与临时锁定。
- 签发和刷新 access token、refresh token，支持退出登录和令牌吊销。
- 支持跨应用 OAuth2 授权码/一次性 ticket 换票。
- 令牌有效期、安全配置可配置，不依赖前端保存角色作为鉴权依据。

**账号服务 sso-account**

- 账号创建、启停、锁定、重置密码、登录日志和员工账号关联。
- 角色、权限码、角色授权和菜单/按钮权限初始化。
- 应用登记及跨应用访问授权，应用密钥用于业务应用换票。
- 组织数据范围配置，为 EHR 等系统提供按组织隔离数据的基础。
- 新建账号的初始密码策略由 settings 安全配置控制，支持随机或固定模式。

### 4.3 EHR：人力中心

| 业务域 | 主要能力 |
|---|---|
| 工作台 | 在职、试用期、待入职、离职、本月入离职、待审批、合同到期等指标；按当前账号权限和数据范围统计 |
| 组织架构 | 集团、公司、部门、项目等组织树，节点维护、负责人、排序及组织路径 |
| 员工档案 | 新增、查看、编辑、入转调离、个人/银行卡/教育/工作/家庭信息、附件和变更审批 |
| 敏感信息 | 手机号、身份证、紧急联系电话等列表脱敏；查看明文走独立权限并记录审计 |
| 合同管理 | 固定期限/无固定期限合同、新增、续签、终止、到期提醒、导出及合同历史 |
| 司龄与年假 | 司龄台账、前司工作记录、年假额度和使用台账 |
| 招聘管理 | 招聘需求、候选人及入职衔接 |
| 考勤假勤 | 考勤规则、工作日历、打卡/日结果、考勤汇总、加班、请假和数据权限过滤 |
| 转正与离职 | 转正申请、离职申请、审批状态及员工状态联动 |
| 审批协同 | 人员信息变更、请假、加班、转正、离职、合同等接入统一审批中心 |
| 通知公告 | 富文本、分类、全员/部门定向、草稿、发布、下架、置顶、已读统计 |
| 导入导出 | 员工等模板导入，以及员工、合同、考勤等 Excel 导出 |
| 人力台账 | 合同到期、人员流动、考勤异常只读接口；统一按组织数据权限过滤，汇总、图表与分页明细共享筛选口径 |

EHR 同时应用“功能权限 + 组织数据权限”。没有数据范围时按空集处理，不会因为范围为空而放行全部人员数据。

### 4.4 Settings：配置、审批与消息中心

- 数据字典及字典项维护，为各应用提供统一业务枚举。
- 单号规则，为工号、公告编号等提供前缀、日期和流水号。
- 审批模板、审批流程、业务场景、业务点和消息配置。
- 统一待办、已办、我发起的审批，以及审批通过后对业务服务的回调。
- 人员信息、请假、加班、合同、内容发布等业务审批的集中编排。
- 消息中心支持站内消息；配置 SMTP 后可发送邮件。
- 安全配置包括初始密码策略、密码复杂度、登录失败锁定和会话相关参数。
- 向其他服务提供 `/internal/dicts/**`、`/internal/approval/**` 等内部接口。

### 4.5 CMS：内容中心

- 文章草稿、编辑、发布、下架及栏目归属。
- Markdown 正文、摘要、封面、发布时间和内容 HTML 安全清洗。
- 树形栏目、排序及门户 Banner/推荐位配置。
- 内容发布可接入 settings 统一审批，终审结果回调 CMS。
- 向门户提供公开内容接口，上传目录可通过环境变量调整。

### 4.6 Portal：门户聚合

- 聚合 CMS 公开内容，为门户首页、栏目列表和文章详情提供数据。
- 聚合 settings 统一待办，作为员工工作入口。
- 本身不保存独立业务库，重点是登录态接入、内容聚合和后续门户扩展。

### 4.7 Job：调度中心

- 任务定义、Cron/调度配置、启停、手工触发和执行日志。
- 执行器注册、Handler 清单、心跳和运行状态管理。
- 调度中心只负责触发，具体业务 Handler 留在 EHR、settings、CMS 等业务应用中。
- 支持失败报警，报警通知复用 settings 消息中心。
- 默认开启调度扫描；本地不需要定时任务时可关闭 `JOB_SCHEDULER_ENABLED`。

### 4.9 Common 与各 Client

- `R<T>` 统一响应、`BizException`/`ErrorCode` 统一业务异常。
- `LoginUser` 登录上下文、JWT/可信头处理、通用安全配置。
- `EncryptedPropertyEnvironmentPostProcessor` 在启动期解密 `ENC(...)` 配置。
- 各 `*-client` 封装 Feign 接口、DTO 和内部令牌传递，避免业务服务重复编写调用协议。

## 五、服务、数据库与启动类对照

| 服务 | 默认端口 | 数据库 | 默认 SQL 初始化 | 启动类 | 可执行 jar |
|---|---:|---|---|---|---|
| Gateway | 9080 | 无 | 不适用 | `SsoGatewayApplication` | `polaris-sso/polaris-sso-gateway/target/polaris-sso-gateway-1.0.0.jar` |
| Auth | 9001 | 使用账号体系 | 不适用 | `SsoAuthApplication` | `polaris-sso/polaris-sso-auth/target/polaris-sso-auth-1.0.0.jar` |
| Account | 9002 | `sso_ms` | `never` | `SsoAccountApplication` | `polaris-sso/polaris-sso-account/target/polaris-sso-account-1.0.0.jar` |
| CMS | 9004 | `cms` | `always` | `CmsApplication` | `polaris-cms/polaris-cms-service/target/cms-service.jar` |
| Portal | 9005 | 无独立库 | 不适用 | `PortalApplication` | `polaris-portal/polaris-portal-service/target/portal-service.jar` |
| EHR | 9006 | `ehr` | `always` | `EhrApplication` | `polaris-ehr/polaris-ehr-service/target/ehr-service.jar` |
| Settings | 9007 | `settings` | `always` | `SettingsApplication` | `polaris-settings/polaris-settings-service/target/settings-service.jar` |
| Job | 9008 | `polaris_job` | `always` | `JobApplication` | `polaris-job/polaris-job-service/target/job-service.jar` |

> `always` 主要用于全新本地库，通过 `schema.sql`/`data.sql` 或幂等初始化器准备表和种子数据。生产环境必须关闭自动初始化，使用经过审核的 SQL 迁移流程。

## 六、快速开始：本地无 Nacos 模式

以下步骤适合第一次拿到项目、希望在本机启动 SSO + EHR 的开发者。其他业务服务可按需追加。

### 6.1 准备软件

- JDK 17，确认 `java -version` 显示 17。
- Maven 3.6.3+，确认 `mvn -version` 使用的是同一 JDK。
- MySQL 5.7+。
- Redis 5+，默认连接 `127.0.0.1:6379`。
- IDEA 可选，用于直接运行各启动类。
- 不需要安装 Nacos；保持 `SSO_NACOS_ENABLED=false`、`SSO_NACOS_DISCOVERY=false` 即可。

Windows 可用 PowerShell 设置环境变量。变量只在当前 PowerShell 窗口有效，每个用于启动服务的窗口都需要设置；IDEA 启动时应填到 Run Configuration 的 Environment variables 中。

### 6.2 获取代码

```bash
git clone https://github.com/crom-lmc/polaris-server.git
git clone https://github.com/crom-lmc/polaris-web.git
```

建议把两个仓库放在同一父目录。进入后端根目录后再执行 Maven 命令：

```bash
cd polaris-server
```

### 6.3 创建数据库

使用有建库权限的 MySQL 账号执行：

```sql
CREATE DATABASE IF NOT EXISTS sso_ms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS ehr DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS settings DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS cms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS polaris_job DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

只启动 SSO + EHR 时，先建 `sso_ms`、`ehr`、`settings` 即可。使用独立低权限数据库账号时，应提前授予对应库的权限。

### 6.4 初始化五个业务库

五个服务统一使用各自 `src/main/resources/` 下的 `schema.sql` 和 `data.sql`。全新空库必须先执行 `schema.sql`，再执行同目录的 `data.sql`；建议按 Account → Settings → EHR → CMS → Job 的顺序初始化。未启动的服务可以暂不初始化对应库。

在仓库根目录使用 MySQL 客户端执行（以下为重定向命令，适用于 Bash、Git Bash 或 cmd）：

```bash
mysql -uroot -p --default-character-set=utf8mb4 sso_ms < polaris-sso/polaris-sso-account/src/main/resources/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 sso_ms < polaris-sso/polaris-sso-account/src/main/resources/data.sql
mysql -uroot -p --default-character-set=utf8mb4 settings < polaris-settings/polaris-settings-service/src/main/resources/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 settings < polaris-settings/polaris-settings-service/src/main/resources/data.sql
mysql -uroot -p --default-character-set=utf8mb4 ehr < polaris-ehr/polaris-ehr-service/src/main/resources/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 ehr < polaris-ehr/polaris-ehr-service/src/main/resources/data.sql
mysql -uroot -p --default-character-set=utf8mb4 cms < polaris-cms/polaris-cms-service/src/main/resources/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 cms < polaris-cms/polaris-cms-service/src/main/resources/data.sql
mysql -uroot -p --default-character-set=utf8mb4 polaris_job < polaris-job/polaris-job-service/src/main/resources/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 polaris_job < polaris-job/polaris-job-service/src/main/resources/data.sql
```

这些基线只用于全新库，不承担存量库升级；已有数据的库不要重复导入全量 `data.sql`。完成首次初始化后再启动服务。Account 默认 `sql.init.mode=never`，其余服务本地默认 `always`；`always` 会在启动时执行相同基线，生产环境应统一设为 `never`。

Account 首次启动时可通过 `SSO_ADMIN_USERNAME`、`SSO_ADMIN_PASSWORD`、`SSO_ADMIN_EMAIL` 创建内置管理员。若管理员已存在，启动不会重置其密码；未提供 `SSO_ADMIN_PASSWORD` 时只跳过自动建号，不影响执行 SQL 中已有账号和权限数据。

### 6.5 准备 RSA 密钥

开发环境可使用 OpenSSL 生成一对新密钥，并放在仓库外的私有目录：

```bash
openssl genpkey -algorithm RSA -out jwt-private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem
```

PowerShell 中读取为环境变量：

```powershell
$env:SSO_JWT_PRIVATE_KEY = Get-Content -Raw "C:\path\to\jwt-private.pem"
$env:SSO_JWT_PUBLIC_KEY  = Get-Content -Raw "C:\path\to\jwt-public.pem"
```

auth 必须配置私钥，Gateway 必须配置与之匹配的公钥。其他服务通常只通过 Gateway 和统一认证链路工作。更换密钥后，旧 JWT 会失效。

### 6.6 配置本地环境变量

以下示例请替换为自己的数据库账号、随机密钥和路径，**不要照抄为生产配置**。

```powershell
# 关闭 Nacos：使用 application.yml 中的本地直连配置
$env:SSO_NACOS_ENABLED = "false"
$env:SSO_NACOS_DISCOVERY = "false"

# Account 数据库；已按 6.4 手工初始化，保持 never
$env:SSO_DB_URL = "jdbc:mysql://127.0.0.1:3306/sso_ms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
$env:SSO_DB_USER = "your_db_user"
$env:SSO_DB_PASSWORD = "your_db_password"
$env:SSO_SQL_INIT_MODE = "never"

# EHR / Settings 数据库
$env:EHR_DB_URL = "jdbc:mysql://127.0.0.1:3306/ehr?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
$env:EHR_DB_USER = "your_db_user"
$env:EHR_DB_PASSWORD = "your_db_password"
$env:SETTINGS_DB_URL = "jdbc:mysql://127.0.0.1:3306/settings?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
$env:SETTINGS_DB_USER = "your_db_user"
$env:SETTINGS_DB_PASSWORD = "your_db_password"

# 服务间内部令牌：所有涉及内部调用的服务必须一致
$env:SSO_INTERNAL_TOKEN = "replace-with-a-long-random-token"

# 业务应用换票密钥：必须与账号中心应用登记中的 appSecret 一致
$env:EHR_APP_SECRET = "replace-with-ehr-app-secret"
$env:SETTINGS_APP_SECRET = "replace-with-settings-app-secret"

# 初始管理员：只在初始化时使用，请替换为本地强密码
$env:SSO_ADMIN_USERNAME = "admin"
$env:SSO_ADMIN_PASSWORD = "replace-with-a-local-admin-password"
$env:SSO_ADMIN_EMAIL = "admin@example.local"
```

Redis 默认无密码、地址 `127.0.0.1:6379`。auth 默认使用 Redis database 2 保存吊销名单；如只是单实例开发且明确不需要 Redis，可将 `SSO_REVOCATION_STORE=memory`，但多实例和生产环境推荐使用 `redis`。

其他应用启动时再设置对应的 `CMS_APP_SECRET`、`PORTAL_APP_SECRET`、`JOB_APP_SECRET`，以及相应数据库配置。appSecret 不是任意服务私钥，必须和 account 中该应用的登记值匹配。

### 6.7 编译项目

```bash
mvn clean package -DskipTests
```

只编译 EHR 及其依赖：

```bash
mvn -pl polaris-ehr/polaris-ehr-service -am clean package -DskipTests
```

日常只检查编译可使用：

```bash
mvn -DskipTests compile
```

### 6.8 推荐启动顺序

| 顺序 | 服务 | SSO + EHR 是否必需 |
|---:|---|---|
| 1 | MySQL、Redis | 必需；Redis 仅在使用 memory 吊销存储时可省 |
| 2 | Account、Settings | 必需 |
| 3 | Auth | 必需 |
| 4 | EHR | 使用 EHR 时必需 |
| 5 | Gateway | 必需，前端统一入口 |
| 6 | CMS、Portal、Job | 按业务需要启动 |

无需严格等待每个服务完全启动后再启动下一个，但首次初始化时建议先确认 Account、Settings 日志没有数据库错误。

**IDEA 启动：** 分别运行第五节表格中的 Application 启动类，并为每个 Run Configuration 配置相同的环境变量/密钥。

**命令行启动：** 每个服务使用一个终端，先设置该终端环境变量，再运行：

```bash
java -jar polaris-sso/polaris-sso-account/target/polaris-sso-account-1.0.0.jar
java -jar polaris-settings/polaris-settings-service/target/settings-service.jar
java -jar polaris-sso/polaris-sso-auth/target/polaris-sso-auth-1.0.0.jar
java -jar polaris-ehr/polaris-ehr-service/target/ehr-service.jar
java -jar polaris-sso/polaris-sso-gateway/target/polaris-sso-gateway-1.0.0.jar
```

这些命令应分别执行，不是在同一终端依次阻塞等待。生产环境可通过服务管理器或部署脚本管理进程。

### 6.9 验证后端

服务启动后可检查本机健康端点，例如：

```text
http://127.0.0.1:9001/actuator/health
http://127.0.0.1:9002/actuator/health
http://127.0.0.1:9006/actuator/health
http://127.0.0.1:9007/actuator/health
http://127.0.0.1:9080/actuator/health
```

然后按前端 README 启动 `polaris-sso-web` 和 `polaris-ehr-web`。首次使用前，在账号中心确认：

1. 管理员能够登录。
2. EHR 应用已登记，开发地址与前端实际 origin 一致。
3. EHR 的 appSecret 与后端 `EHR_APP_SECRET` 一致。
4. 当前账号已授予 EHR 应用、角色权限和正确的数据范围。

## 七、常用环境变量

### 7.1 全局与认证

| 变量 | 用途 | 默认/说明 |
|---|---|---|
| `SSO_NACOS_ENABLED` | 是否加载 Nacos 配置 | `false` |
| `SSO_NACOS_DISCOVERY` | 是否启用 Nacos 服务发现 | `false` |
| `SSO_GATEWAY_PORT` | Gateway 端口 | `9080` |
| `SSO_JWT_PRIVATE_KEY` | auth 签发 JWT 的 RSA 私钥 | 必须安全提供 |
| `SSO_JWT_PUBLIC_KEY` | Gateway 验签公钥 | 必须与私钥匹配 |
| `SSO_INTERNAL_TOKEN` | 服务间内部接口令牌 | 相关服务必须一致 |
| `SSO_REVOCATION_STORE` | 吊销名单存储 | 默认 `redis`，单实例可用 `memory` |
| `SSO_CONF_KEY` | `ENC(...)` 配置主密钥 | 与 `SSO_CONF_KEY_FILE` 二选一 |
| `SSO_CONF_KEY_FILE` | 主密钥文件路径 | 文件不纳入 Git |

### 7.2 数据库与应用

| 服务 | 数据库变量 | SQL 初始化变量 | 换票密钥 |
|---|---|---|---|
| Account | `SSO_DB_URL`、`SSO_DB_USER`、`SSO_DB_PASSWORD` | `SSO_SQL_INIT_MODE` | 不适用 |
| CMS | `CMS_DB_URL`、`CMS_DB_USER`、`CMS_DB_PASSWORD` | `CMS_SQL_INIT_MODE` | `CMS_APP_SECRET` |
| EHR | `EHR_DB_URL`、`EHR_DB_USER`、`EHR_DB_PASSWORD` | `EHR_SQL_INIT_MODE` | `EHR_APP_SECRET` |
| Settings | `SETTINGS_DB_URL`、`SETTINGS_DB_USER`、`SETTINGS_DB_PASSWORD` | `SETTINGS_SQL_INIT_MODE` | `SETTINGS_APP_SECRET` |
| Job | `JOB_DB_URL`、`JOB_DB_USER`、`JOB_DB_PASSWORD` | `JOB_SQL_INIT_MODE` | `JOB_APP_SECRET` |
| Portal | 无独立业务库 | 不适用 | `PORTAL_APP_SECRET` |

常用补充配置：

| 变量 | 用途 |
|---|---|
| `EHR_SELF_URL` | settings 审批完成后回调 EHR 的可访问地址 |
| `EHR_WEB_URL` | 消息/审批链接指向的 EHR 前端地址 |
| `CMS_SELF_BASE_URL` | 内容审批回调 CMS 的地址 |
| `CMS_UPLOAD_ROOT` | CMS 上传目录，默认 `./uploads-cms` |
| `JOB_SCHEDULER_ENABLED` | 是否开启调度扫描，默认 `true` |
| `SETTINGS_SMTP_HOST/PORT/USER/PASSWORD/SSL` | 可选邮件通道；不配置时只落站内消息 |

完整可配置项以各服务 `src/main/resources/application.yml` 为准。

## 八、可选：使用 Nacos

本地直连跑通后再启用 Nacos，不建议首次部署同时排查数据库、JWT 和 Nacos 三组问题。

1. 准备 Nacos 2.x，并确认 HTTP 与 gRPC 端口均可访问。
2. 参考 [polaris-nacos/README.md](polaris-nacos/README.md) 和 `polaris-nacos/.env.example` 创建本地私有 `.env`、密钥文件。
3. 检查 `polaris-nacos/config/local/` 中命名空间、Group、服务地址和 `ENC(...)` 配置。
4. 使用导入脚本发布 local 配置：

```bash
python scripts/import-config.py --env local
```

5. 在应用启动环境中设置 Nacos 地址、账号、命名空间、Group 和配置主密钥，并开启：

```powershell
$env:SSO_NACOS_ENABLED = "true"
$env:SSO_NACOS_DISCOVERY = "true"  # 只需要集中配置时可保持 false
```

local 配置通常使用命名空间 `sso-ms-local`、Group `SSO`，以实际导入配置为准。`.env` 主要供导入工具读取，不能假设 Spring Boot 会自动加载该文件；应用本身仍需要系统环境变量或 IDEA 环境变量。

`polaris-nacos/config/prod/` 仅作结构参考，线上配置、密钥和地址以实际环境为准。不要把 `.env`、`.secrets/`、JWT 私钥、数据库明文密码提交到 Git。

## 九、生产部署注意事项

`deploy/` 提供 Docker Compose、Nginx 和初始化 SQL，`scripts/` 统一存放构建、发布、备份等可执行脚本。使用前先逐文件核对域名、路径、端口和环境变量，不要直接套用本机开发配置。

- 全新业务库按 6.4 执行统一基线；存量库升级使用单独审核的迁移流程。生产 `sql.init.mode` 必须设为 `never`。
- 私钥、配置主密钥、内部令牌、appSecret 使用密钥管理系统或受限密钥文件注入。
- auth 与 Gateway 使用匹配的 RSA 密钥对；所有内部调用方使用一致的 `SSO_INTERNAL_TOKEN`。
- 多实例 auth 使用 Redis 吊销存储，避免某个实例退出后其他实例仍接受令牌。
- Gateway 是唯一业务入口，业务服务端口不应直接暴露到公网。
- 审批回调地址必须从 settings 所在网络可访问，不能填写只能在开发者本机解析的地址。
- 上传目录、日志目录和数据库需要持久化及备份。
- 开启 HTTPS，保证各应用登记的 `base_url`、前端 origin 和实际访问地址一致。
- 启动后检查健康端点、Gateway 路由、SSO 登录换票、权限过滤、审批回调和调度执行器心跳。

## 十、开发规范

### 10.1 新增功能

| 要做什么 | 推荐方式 |
|---|---|
| 新增接口 | 在对应服务 `module/.../controller` 添加 Controller，统一返回 `R<T>`，需要权限的接口添加 `@Permit` |
| 新增表 | 增加实体、Mapper、Service；本地初始化 DDL 写入 `schema.sql`，生产另提供受审迁移 SQL |
| 新增种子数据 | 使用幂等写法，避免重复启动产生重复角色、权限或字典 |
| 新增权限码 | 统一使用 `资源:动作`，同步账号权限种子、角色授权和前端页签/按钮控制 |
| 新增字典 | 通过 settings 维护，业务代码读取字典而不是散落硬编码 |
| 新增跨服接口 | 在对应 client 模块定义 DTO/Feign 契约，内部端点校验共享内部令牌 |
| 新增定时任务 | Handler 留在业务应用，通过 job-client 注册，由调度中心统一配置和触发 |

### 10.2 代码与配置约定

- 分层使用 `controller → service → mapper`，Controller 不堆业务逻辑，Service 不随意拼接 SQL。
- 业务错误抛出 `BizException`，关键链路不要捕获后吞掉异常。
- 数据权限必须在服务端执行，不能只靠前端隐藏菜单或按钮。
- 空数据范围返回空集；组织、人员、合同、考勤等查询都应评估数据隔离。
- 时间统一使用 `LocalDateTime`，向前端输出 `yyyy-MM-dd HH:mm:ss`。
- 入库敏感配置只允许 `${ENV_VAR}` 或 `ENC(...)`，不得写入真实密码和私钥。
- `target/`、`logs/`、上传目录、`.secrets/` 和本地 `.env` 不提交。
- 迭代文档统一放在仓库外的 `../迭代文档/`，不纳入 Git；提交信息使用中文并准确描述目的。

## 十一、常见问题

### 11.1 Account 启动后没有表

Account 默认 `SSO_SQL_INIT_MODE=never`。全新本地库需要显式改为 `always`，或先手工执行 account 的 `schema.sql`、`data.sql`。

### 11.2 登录成功但请求立即 401

重点检查：

- auth 私钥和 Gateway 公钥是否是一对。
- access token 是否过期，服务器时间是否明显偏差。
- auth 是否使用 Redis 吊销存储，Redis 地址、密码、database 是否正确。
- 请求是否经过 Gateway，而不是直接请求业务服务。

### 11.3 能登录但无法进入某应用或换票失败

检查 account 中应用是否启用、账号是否有应用授权、`base_url` 是否与前端 origin 完全一致，以及业务服务 `*_APP_SECRET` 是否与登记值一致。`localhost` 和 `127.0.0.1` 在 origin 匹配中应视为不同地址。

### 11.4 服务报内部接口无权限

确认调用方和被调用方的 `SSO_INTERNAL_TOKEN` 一致，请求路径确实是 `/internal/**`，并检查是否错误地绕过了内部 Feign 客户端。

### 11.5 页面只有菜单，没有数据或只有本部门数据

这通常是角色权限或组织数据范围导致。分别检查功能权限码、应用授权、账号角色和数据范围；不要通过把空范围解释为全量来“修复”。

### 11.6 审批通过但业务状态没有变化

检查 settings 日志中的回调结果，以及业务服务的 `EHR_SELF_URL`、`CMS_SELF_BASE_URL` 等地址是否从 settings 网络可达，同时核对内部令牌。

### 11.7 邮件没有发送

未配置 SMTP 时系统只保存站内消息，这是预期行为。需要邮件时配置 `SETTINGS_SMTP_*`，并检查邮件服务商的认证、SSL 和发件人限制。
