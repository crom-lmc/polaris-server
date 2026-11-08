# Nacos 接入说明

本目录是 Nacos 的**配置包 + 导入工具**，与 `polaris-server` 的三个服务配套。

**默认不依赖它**：三个服务的 `spring.cloud.nacos.config.enabled` / `discovery.enabled`
默认都是 `false`，不认识 Nacos 也能正常启动。要用时把配置推上去，再打开这两个开关。

---

## 0. 目录里每个文件是干嘛的

| 文件/目录 | 用途 | 是否入库 |
|---|---|---|
| `config/local/` | 本地环境配置集（本机 MySQL、服务直连 IP、`sql.init=always`），每条 yaml 对应 Nacos 一个 dataId | ✅ 入库（含 `ENC(...)` 密文，无明文敏感项） |
| `config/prod/` | 线上环境配置集（`lb://` 服务发现、口令走环境变量占位），仅作参考，线上实际不使用本仓库这份 | ✅ 入库（无真实敏感值） |
| `.env` | **配置主密钥** `SSO_CONF_KEY`（AES-256-GCM，Base64），服务启动解密 `ENC(...)` 的唯一凭据 | ❌ 已 gitignore，只存本机/部署机 |
| `.env.example` | `.env` 的模板，新环境复制后填入自己的密钥 | ✅ 入库 |
| `.secrets/` | 密钥材料：JWT 私钥（local/prod）、公钥、各应用 `app-secret`、内部调用 token、主密钥备份、SQL 备份 | ❌ 已 gitignore，绝不入库 |
| `import-config.py` | 配置导入脚本：把 `config/<env>` 推到对应 Nacos 命名空间（含密文试解校验、推后回读比对） | ✅ 入库 |
| `README.md` | 本文件 | ✅ 入库 |

> 一句话：**能进仓库的都是「配置和工具」，不能进的是「钥匙」**（`.env` + `.secrets/`）。

---

## 1. 环境与命名空间

两套环境靠**命名空间**隔离，dataId / group 两侧完全一样，切环境只改命名空间 ID。

| 环境 | 命名空间 ID | 配置目录 | 特点 |
|---|---|---|---|
| local | `sso-ms-local` | `config/local/` | 本机 MySQL，服务间**直连 IP**，`sql.init=always` |
| prod | `sso-ms-prod` | `config/prod/` | 远端 MySQL，服务间走 **`lb://`**，`sql.init=never`，口令由环境变量注入 |

每条配置都对应一个 dataId，group 统一为 `SSO`：

| dataId | 覆盖的内容 |
|---|---|
| `sso-gateway.yaml` | 路由表、吊销名单同步间隔、auth 地址 |
| `sso-auth.yaml` | access/refresh TTL、account 地址、内部令牌 |
| `sso-account.yaml` | 数据源、MyBatis-Plus 日志、锁定策略、管理员初始化 |

> 配置集里只放「需要集中管理或按环境变化」的项，不是全量拷贝。
> 未出现在配置集里的属性，继续用 jar 内 `application.yml` 的值。
> 优先级：**Nacos > jar 内 application.yml > 代码默认值**。

---

## 2. 前置：Nacos 与 SSH 隧道

客户端只要求能访问 Nacos 的 **HTTP 端口**，但 **2.x 客户端还会连 gRPC 端口 = HTTP 端口 + 1000**。
走 SSH 隧道访问远端 Nacos 时，两条端口转发都要建（以本机 18848 为例）：

```bash
ssh -L 18848:127.0.0.1:8848 -L 19848:127.0.0.1:9848 user@your-server
```

验证：`Test-NetConnection 127.0.0.1 -Port 18848` 与 `19848` 都应返回 True。

启动后自检：

```bash
curl http://127.0.0.1:18848/nacos/v1/console/health/readiness     # OK
curl http://127.0.0.1:18848/nacos/v1/console/server/state          # 含 version / auth_enabled
```

---

## 3. 鉴权

远端实例一般开了鉴权（`auth_enabled=true`），配置接口没有 token 一律 403 `user not found!`。
导入脚本按下面的顺序取凭据，**不用把密码写进命令行**：

| 变量 | 用途 | 回退 |
|---|---|---|
| `SSO_NACOS_ADDR` | 地址，如 `127.0.0.1:18848` | `NACOS_SERVER_ADDR` → `127.0.0.1:8848` |
| `SSO_NACOS_USER` | 用户名 | `NACOS_USERNAME` → `nacos` |
| `SSO_NACOS_PASSWORD` | 密码 | `NACOS_PASSWORD` |
| `SSO_NACOS_GROUP` | group，默认 `SSO` | — |
| `SSO_CONF_KEY` | **配置主密钥**（Base64，16/24/32 字节） | 无，缺失时含密文的配置无法解密 |
| `SSO_CONF_KEY_FILE` | 主密钥文件路径 | 无，`SSO_CONF_KEY` 优先 |

三个服务用的是同一套变量，所以环境变量配好一次，脚本和 JVM 都能用。
`SSO_CONF_KEY` 通常放在 `nacos/.env`（已 gitignore），`tools/start-local.ps1` 会自动读取。

---

## 4. 导入配置

```bash
# 预览将推送什么，不写任何东西
python nacos/import-config.py --env local --dry-run

# 推送到 sso-ms-local（命名空间已存在）
python nacos/import-config.py --env local

# 命名空间还没建时自动创建
python nacos/import-config.py --env prod --create-namespace

# 看命名空间里现在有哪些配置
python nacos/import-config.py --env local --list
```

脚本行为：

1. 探活 `/v1/console/health/readiness`，不通直接退出（不会逐条报错刷屏）；
2. 用账号密码换 `accessToken`；
3. 确认/创建命名空间（参数名是 `customNamespaceId`）；
4. 逐条 `POST /v1/cs/configs`，**命名空间参数用的是 `tenant` 不是 `namespaceId`**；
5. **推送前校验密文**：扫描 `ENC(...)` 值并用主密钥试解一遍，解不开就跳过该文件不推——
   避免把「打不开的密文」发上去，那种故障现场会被误判成 Nacos 的问题（`--skip-seal-check` 可跳过）；
6. **回读比对**：推完立刻 GET 一次，内容和本地文件逐字节一致才算 OK——能挡住「写到 public 去了」
   这类看似成功实则无效的失误。

---

## 5. 让服务用上 Nacos

```bash
export SSO_NACOS_ENABLED=true            # 开配置中心
export SSO_NACOS_DISCOVERY=true          # 开注册中心（要用 lb:// 必须开）
export SSO_NACOS_NAMESPACE=sso-ms-local  # 默认就是它，可省；上线改 sso-ms-prod
set -a; . nacos/.env; set +a             # 主密钥 SSO_CONF_KEY（配置里有密文就必须有它）

java -jar sso-account/target/sso-account-1.0.0.jar --server.port=9002 &
java -jar sso-auth/target/sso-auth-1.0.0.jar       --server.port=9001 &
java -jar sso-gateway/target/sso-gateway-1.0.0.jar --server.port=9080 &
```

`tools/start-local.ps1` 会自动读 `nacos/.env`，不需要手工 export。

### 验证是否真的生效

```bash
# 1) 配置中心：日志里应有 Load config ... success，且没有 MalformedInput
grep -E "Load config|Malformed" <服务日志>

# 2) 解密：启动阶段应能看到（这行是 stdout，绕过了尚未初始化的日志系统）
grep "\[SSO\]" <服务日志>
# → [SSO] 主密钥已加载（来源：环境变量 SSO_CONF_KEY）
# → [SSO] 已解密 4 个配置项：spring.datasource.username, spring.datasource.password, ...
```
# 2) 注册中心：三个服务都在对应命名空间的 SSO 组里
curl "http://127.0.0.1:18848/nacos/v1/ns/service/list?pageNo=1&pageSize=20&namespaceId=sso-ms-local&groupName=SSO&accessToken=<token>"
# → {"count":3,"doms":["sso-gateway","sso-auth","sso-account"]}

# 3) 业务链路照旧
python tools/micro-e2e-check.py        # 期望 27 passed, 0 failed
```

> 服务列表接口不加 `groupName=SSO` 会查 `DEFAULT_GROUP`，看到 `count:0` 是正常的，
> 不代表没注册——本工程的配置与注册 group 都是 `SSO`。

### 切到服务发现路由

local 环境故意保持直连（排障少一层），prod 环境用 `lb://`。本地想验证服务发现，
把对应配置改成 `lb://sso-account` / `lb://sso-auth`（或临时用环境变量覆盖），然后重启服务即可：

- 网关路由：`spring.cloud.gateway.routes[*].uri`
- 网关轮询吊销名单：`sso.gateway.auth-base-url`（`lb://` 会自动切到带负载均衡的 WebClient）
- auth → account：`sso.auth.account-base-url`（`lb://` 会自动切到 `@LoadBalanced` 的 RestTemplate）

---

## 6. 敏感配置加密（ENC）

### 为什么不能靠 Nacos 权限兜底

这台 Nacos 是**共用**的（博客后端也在用），控制台里任何有该命名空间读权限的账号
都能看到配置原文；备份、快照、`/v1/cs/configs` 抓包也都会带出明文。
所以库口令、服务间令牌、JWT 私钥这些**落进 Nacos 的必须是密文**。

### 机制

配置值写成 `ENC(base64)`，服务启动时由
`com.laimengchao.sso.common.crypto.EncryptedPropertyEnvironmentPostProcessor`
在 Environment 准备阶段统一解密（早于数据源、`JwtService` 等任何 Bean 创建），
业务代码拿到的永远是明文。

- 算法：**AES-256-GCM**，密文结构 `base64(iv[12] || ciphertext || tag[16])`，每次随机 IV；
- 主密钥：**只**来自环境变量 `SSO_CONF_KEY`，或 `SSO_CONF_KEY_FILE` 指向的文件；
- 只依赖 JDK 自带 JCE，没有第三方加密库；
- **发现密文却没有主密钥 → 直接启动失败**（不会把 `ENC(...)` 当口令去连库）。

### 工具

```bash
java tools/SecretTool.java genkey                          # 生成 32 字节主密钥（Base64）
java tools/SecretTool.java encrypt '<明文>'                 # → ENC(...)
java tools/SecretTool.java encrypt --file nacos/.secrets/jwt-private.pem
java tools/SecretTool.java decrypt 'ENC(...)'              # 核对
java tools/SecretTool.java selftest                        # 算法自检
```

`--stdin` 可以避免明文进 shell 历史（私钥这类长文本建议用 `--file`）。

### 哪些项是密文

| dataId | 配置项 | 说明 |
|---|---|---|
| `sso-account.yaml` | `spring.datasource.username` / `password` | 库账号，jar 内已无默认值 |
| `sso-account.yaml` | `sso.account.internal-token` | 服务间准入令牌 |
| `sso-account.yaml` | `sso.bootstrap.admin-password` | 仅首次建号生效 |
| `sso-auth.yaml` | `sso.auth.internal-token` | 必须与 account 侧**解密后**一致 |
| `sso-auth.yaml` | `sso.auth.private-key` | RS256 私钥，**不再打进 jar** |
| `sso-gateway.yaml` | `sso.gateway.public-key` | 公钥不加密，prod 用独立密钥对时内联 |

`prod/` 里 `SSO_DB_USER` / `SSO_DB_PASSWORD` / `SSO_ADMIN_PASSWORD` 仍是**无默认值**的
环境变量占位：生产口令不该由模板作者代填。拿到真实口令后跑一次 `SecretTool encrypt`
替换成 `ENC(...)`，这两个环境变量就可以彻底去掉。

### 密钥轮换

主密钥**不能随手换**：换了以后历史密文全部解不开，服务会以「配置解密失败」中止。
正确顺序是——用新密钥重新加密**全部**密文 → 一次性推送 → 同时更新部署环境的 `SSO_CONF_KEY` → 重启。

---

## 7. 注意事项

1. **推上去的内容必须是纯 ASCII**。Spring 用 JVM 默认字符集读 Nacos 拉回的字节，JDK17 在中文
   Windows 下是 GBK；配置里一旦有中文，snakeyaml 会抛
   `MalformedInputException: Input length = 1`，而 `optional:` 会把它变成「服务起来了但配置没生效」。
   **本目录所有 yaml 注释一律用英文**，中文说明只写在这里。要么保持 ASCII，要么给 JVM 加
   `-Dfile.encoding=UTF-8`，两者建议都做。
2. **列表型配置是整体覆盖**。`spring.cloud.gateway.routes` 是 List，Nacos 里写两条就只有两条，
   不会和 jar 内置的合并——加路由时要把原有的一起写上。
3. **namespace 参数填 ID 不填名称**，且 OpenAPI 的查询/发布参数名是 `tenant`。
4. **`optional:` 前缀不能省**。它保证 Nacos 抖动/不可达时服务仍能启动，而不是全站起不来；
   此时配置回退到 jar 内骨架 + 同名环境变量（库连接这类必需项仍要通过环境变量补齐）。
5. **别用命令行下标覆盖路由**。`--spring.cloud.gateway.routes[0].uri=...` 会让同一份配置里的
   `predicates` 丢失，启动报 `routes[0].predicates 不能为空`。要改就改整份配置。
6. **生产绝不要把口令以明文写进 Nacos**。要么用 `ENC(...)` 密文（见第 6 节），要么保留
   `${SSO_DB_USER}` 这类**无默认值**占位，由部署机环境变量注入——缺了直接启动失败（fail fast），
   比静默连到错误的库好得多。
7. **`.env` 与 `.secrets/` 绝不入库**。`nacos/.env`（主密钥）、`nacos/.secrets/`（JWT 私钥、
   内部令牌、库备份）都已在 `.gitignore` 里；示例文件是 `nacos/.env.example`。
8. **鉴权 token 会过期**（默认 5 小时）。导入脚本每次都重新登录，长时间运行的服务由客户端自动续期。
