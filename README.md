# Polaris Server

Polaris 一体化企业平台后端：统一认证 + 人力中心 + 配置中心 + 内容中心 + 门户/博客。
Spring Cloud 微服务架构，Nacos 配置/注册中心，Gateway 统一入口，MySQL 分库存储。

## 一、架构总览

![Polaris 架构图](docs/architecture.svg)

## 二、模块明细

### polaris-sso —— 统一认证与账号中心（库：`sso_ms`）

| 子模块 | 端口 | 功能 |
|---|---|---|
| sso-gateway | 9080 | 统一网关：JWT(RS256) 校验、吊销名单轮询、路由转发、内部调用 token 校验 |
| sso-auth | 9001 | 登录认证：账号/邮箱双登录、图形/邮箱验证码、access/refresh token 签发与刷新、登录失败计数锁定 |
| sso-account | 9002 | 账号中心：开户（默认**随机初始密码**）、状态/锁定管理、密码重置、角色与权限码、菜单权限播种 |

关键表：`sso_account`（账号）、`sso_role` / `sso_permission`（权限）、`sso_login_log`（登录日志）。

### polaris-ehr —— 人力中心（库：`ehr`，端口 9006）

- **组织架构**：集团/公司/部门树，`ehr_org_node` 含 path 祖先链与负责人，支持拖拽调整
- **员工管理**：在职/离职、入转调离、工号自动生成（`ehr_employee`）
- **假勤审批**：请假/转正/离职申请单，对接审批流
- **通知公告**：`ehr_announcement` 富文本、全员/部门定向（含子部门，按 path 祖先链判定）、置顶、状态流转（草稿/发布/下架）、编号规则 NOTICE-、`ehr_announcement_read` 幂等已读（唯一键防重复计数）

### polaris-settings —— 配置中心（库：`settings`，端口 9007）

- **数据字典**：`cfg_dict_type` / `cfg_dict_item`，按应用分组（如通知类型 `ehr.announcement_category`）
- **单号规则**：`cfg_code_rule` 前缀 + 流水号（如公告编号规则 11304）
- **审批配置**：审批模板、业务场景、业务点、消息配置；渲染模式支持「分组卡片 / 自定义页面路由」
- **安全配置**：`cfg_security_config` 初始密码规则（默认 RANDOM 随机生成）、密码复杂度、登录失败锁定、会话超时

### polaris-cms —— 内容中心（库：`cms`，端口 9004）

- 文章管理：Markdown 内容、封面、栏目归属、状态流转（草稿/发布/下架）
- 栏目管理：树形结构、排序
- 门户配置：首页 Banner、推荐位（供 portal 聚合）

### polaris-portal —— 门户聚合（端口 9005）

- 面向访客的只读聚合接口：Banner、推荐文章、栏目文章列表、文章详情（数据来自 cms 库）

### polaris-blog —— 博客（库：`blog`，端口 9003）

- 博客文章/标签/分类接口；schema 在 `resources/db/`，**手动 migrate**（`sql.init=never`），不随启动执行

### polaris-common —— 公共依赖

- 统一响应体、异常码 `ErrorCode`/`BizException`、鉴权工具、`EncryptedPropertyEnvironmentPostProcessor`（Nacos 配置 `ENC(...)` 启动期解密，AES-256-GCM）

### polaris-nacos —— 配置中心资料

- `config/local/` 本地配置集（入库）；`config/prod/` 线上配置（不入库）
- `import-config.py` 一键导入 Nacos；密钥材料 `.env` / `.secrets/` 不入库
- 文件级说明见 [polaris-nacos/README.md](polaris-nacos/README.md)

## 三、技术栈

| 层 | 选型 |
|---|---|
| 语言/框架 | Java 17 · Spring Boot · Spring Cloud（Gateway / OpenFeign） |
| 数据 | MySQL 5.7+ · MyBatis-Plus · Redis |
| 认证 | JWT RS256（私钥只在 `.secrets/`，公钥打进 jar） |
| 配置 | Nacos（`ENC(...)` 密文 + 环境变量占位） |
| 构建 | Maven 3.6.3+（多模块聚合） |

## 四、本地启动

```bash
# 0. 前置：MySQL（root/root）、Redis、Nacos 已就绪
# 1. 导入 Nacos 配置（local）
python polaris-nacos/import-config.py --env local

# 2. 编译（JDK 17）
mvn -q compile

# 3. 启动（顺序：网关 → 认证/账号 → 业务服务，也可全部在 IDEA 里跑）
#    无需手动建表：各服务启动自动执行 resources/schema.sql + data.sql（幂等）
```

服务端口：gateway 9080 / auth 9001 / account 9002 / blog 9003 / cms 9004 / portal 9005 / ehr 9006 / settings 9007

## 五、约定

- **敏感配置**：入库的 local 配置只含 `ENC(...)` 密文或 `${ENV}` 占位；私钥、token、主密钥在 `polaris-nacos/.secrets/`、`.env`（均已 gitignore）；prod 配置不入库
- **初始密码**：默认随机生成（`cfg_security_config.initial_password_mode=RANDOM`），配置中心可改为固定
- **种子数据**：sso/ehr/settings/cms 四库 `sql.init=always`，`schema.sql`（DDL）+ `data.sql`（INSERT IGNORE）幂等执行；blog 手动
- **上传目录**：`polaris-uploads*/` 运行期产生，不入库
- **变更记录**：`update.md` 维护在仓库根目录（不入库），所有改动按日期倒序追加
