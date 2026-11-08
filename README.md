# Polaris Server

企业一体化平台后端：统一认证 + 人力 + 配置 + 内容，基于 Spring Cloud 微服务，Nacos 做配置中心，Gateway 统一入口。

## 模块与功能

### polaris-sso（统一认证与账号中心）
- **sso-auth**：登录认证（账号/邮箱双登录、验证码、JWT RS256 签发与刷新、登录失败锁定）
- **sso-account**：账号管理（开户随机初始密码、状态/锁定、密码重置）、角色与权限码、菜单权限播种
- **sso-gateway**：统一网关（鉴权、路由转发、内部调用 token 校验）

### polaris-ehr（人力中心）
- 组织架构：集团/公司/部门树、节点负责人、拖拽调整
- 员工管理：在职/离职、入转调离、工号生成
- 假勤审批：请假、转正、离职申请与审批流对接
- 通知公告：富文本发布、全员/部门（含子部门）定向、置顶、已读统计、编号 NOTICE-

### polaris-settings（配置中心）
- 数据字典：按应用分组维护（如通知类型 ehr.announcement_category）
- 单号规则：编号前缀 + 流水规则（如公告编号 11304）
- 审批配置：审批模板、业务场景、业务点、消息配置（分组卡片/自定义路由渲染）
- 安全配置：初始密码规则（默认随机生成）、密码复杂度、登录失败锁定、会话超时

### polaris-cms（内容中心）
- 文章管理：Markdown 编辑、封面、状态流转（草稿/发布/下架）
- 栏目管理：树形栏目、排序
- 门户配置：首页 Banner、推荐位配置（供门户聚合）

### polaris-portal（内容门户）
- 面向访客的聚合接口：首页 Banner、推荐文章、栏目文章列表、文章详情

### polaris-blog（博客）
- 博客文章/标签/分类接口（schema 在 `resources/db`，手动 migrate，不随启动执行）

### polaris-common / polaris-nacos
- common：统一响应、异常码、鉴权工具等公共依赖
- nacos：各环境配置（local 入库 / prod 不入库）、`import-config.py` 一键导入、密钥材料在 `.secrets/`（不入库）

## 技术栈

Java 17 · Spring Boot / Spring Cloud · MyBatis-Plus · MySQL 5.7+ · Redis · Nacos · JWT（RS256）

## 本地启动

1. 准备 MySQL（root/root）与 Redis。无需手动建表：各服务启动自动执行 `resources/schema.sql`（建表）+ `data.sql`（种子数据），幂等可重复。
2. 启动 Nacos，执行 `polaris-nacos/import-config.py` 导入 `config/local`。
3. 启动顺序：Gateway → sso-auth/sso-account → 其余业务服务。

```bash
mvn -q compile          # 需 Maven 3.6.3+，JDK 17
```

## 约定

- 敏感配置不入库：local 用 `ENC(...)` 密文（密钥在 `polaris-nacos/.env`）或环境变量；私钥/token 在 `polaris-nacos/.secrets/`（已 gitignore）
- 员工/账号初始密码默认随机生成（安全配置 `initial_password_mode=RANDOM`），可在配置中心修改
- `polaris-uploads*/` 为运行期上传目录，不入库
