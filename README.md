# Polaris Server

企业一体化平台后端，基于 Spring Cloud 微服务 + Nacos 配置中心。

## 模块

| 模块 | 说明 | 对应前端 |
|---|---|---|
| polaris-sso | 统一认证与账号中心（登录、账号、权限、网关） | polaris-sso-web |
| polaris-ehr | 人力中心（组织、员工、假勤、审批、通知公告） | polaris-ehr-web |
| polaris-settings | 配置中心（字典、单号规则、审批流、安全配置） | polaris-settings-web |
| polaris-cms | 内容中心（文章、栏目、门户配置） | polaris-cms-web |
| polaris-portal | 内容门户聚合接口 | polaris-portal-web |
| polaris-blog | 博客服务 | polaris-blog-web |
| polaris-common | 公共依赖（异常、工具、鉴权） | - |
| polaris-nacos | Nacos 配置（local/prod，敏感值加密或走环境变量） | - |

## 技术栈

Java 17 · Spring Boot / Spring Cloud · MyBatis-Plus · MySQL 5.7+ · Redis · Nacos · JWT（RS256）

## 本地启动

1. 准备 MySQL（root/root）与 Redis，数据库无需手动建表：各服务启动时自动执行 `resources/schema.sql`（建表）与 `resources/data.sql`（种子数据），均为幂等脚本。
2. 启动 Nacos 并导入 `polaris-nacos/config/local` 下的配置。
3. 按需启动服务：网关 → sso-account → 其余业务服务。

```bash
mvn -q compile          # 编译（需 Maven 3.6.3+，JDK 17）
```

## 约定

- 敏感配置（数据库密码、密钥、app-secret）不入库：local 环境用 `ENC(...)` 密文或环境变量，密钥材料在 `polaris-nacos/.secrets/`（已被 gitignore）。
- 初始密码默认随机生成（安全配置 `initial_password_mode=RANDOM`），可在配置中心修改。
- `polaris-uploads*/` 为运行期上传目录，不入库。
