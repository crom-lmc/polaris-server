# 雷池（SafeLine）+ 宿主机 nginx 备份与重装恢复

> 适用场景：雷池重装、服务器迁移、发版前留档。备份脚本见 `deploy/scripts/safeline-backup.sh`。
> 原则：备份文件只落在服务器 `/opt/polaris/backups/`，权限 600；不要把 `.env`、证书、DB dump 提交进仓库。

## 1. 备份（服务器上执行）

```bash
# 仓库内脚本统一 644，按约定用 bash 调用
sudo bash /opt/polaris/src/polaris-server/deploy/scripts/safeline-backup.sh
# 产物：/opt/polaris/backups/safeline-<时间戳>.tgz
```

包含：

| 内容 | 路径 | 重装后是否必须 |
| --- | --- | --- |
| 雷池编排 | `/opt/safeline/compose.yaml`、`.env` | 必须 |
| 雷池配置 | `/opt/safeline/resources/nginx/conf.d`（含 `zz-realip.conf`、`_safeline_sites.conf`）、`resources/detector`、`resources/mgt` | 必须 |
| 站点/证书/策略/统计 | PostgreSQL 库 `safeline-ce`（`pg_dump -Fc`） | 必须 |
| 宿主机 nginx | `/etc/nginx/nginx.conf`、`conf.d/`、`snippets/`、`certs/`、`/etc/letsencrypt` | 必须 |
| 发版配置 | `/opt/polaris/docker-compose.yml`、`nginx-polaris.conf.tpl`、`polaris-*.inc`、`.env`、`nacos/`、`keys/` | 建议 |

## 1.1 有意不入库的内容（含恢复来源）

以下内容属于私密材料或运行期数据，按要求不进 git；已全部纳入 `safeline-backup.sh` 的备份包（服务器上 600 权限），恢复时从备份取：

| 内容 | 服务器路径 | 恢复来源 |
| --- | --- | --- |
| 环境变量与口令 | `/opt/polaris/.env`、`/opt/safeline/.env` | 备份包内 `polaris.env` / `safeline.env` |
| JWT 私钥/公钥 | `/opt/polaris/keys/*.pem` | 备份包内 `polaris-deploy.tgz`（keys/） |
| Nacos 配置（含明文口令、内部 token、app-secret） | `/opt/polaris/nacos/*.yaml` | 备份包内 `polaris-deploy.tgz`（nacos/） |
| 各域名证书与私钥 | `/etc/nginx/certs/*`、`/etc/letsencrypt/*` | 备份包内 `host-nginx.tgz` |
| 站点/证书/策略/统计 | PostgreSQL 库 `safeline-ce` | 备份包内 `safeline-ce.dump` |
| 业务库数据 | MySQL 数据卷 | 不在本脚本范围，按业务库自身备份策略恢复 |
| mgt 补丁二进制 | 容器内 `/app/mgt` | 镜像 `safeline-mgt:patched-<date>` 或 `docker cp` 产物 |
| 历史备份/灰度残留 | `conf.d/*.bak*`、`/opt/polaris/polaris-*-root.gray.inc`、`/etc/nginx/api-proxy.inc` | 仅现场参考，不恢复 |

证书目录结构固定为 `/etc/nginx/certs/<域名>/<域名>_nginx/<域名>_bundle.crt` 与同名 `.key`（共 18 个域名目录，含 `vault`、`waf.laimengchao.site`、`nacos`）；`waf` 域名的证书同时存在于 `/etc/letsencrypt/live/`。

## 2. 重装前额外保留 mgt 补丁

本环境 `safeline-mgt` 容器里的 `/app/mgt` 不是镜像原版（14MB，另有 `mgt.bak` / `mgt.patched` / `mgt.patched.keep`），**重建容器会丢**。二选一：

```bash
# 方式 A：冻结成镜像（推荐）
docker commit safeline-mgt safeline-mgt:patched-$(date +%Y%m%d)
# 方式 B：把二进制拷出来
docker cp safeline-mgt:/app/mgt ./mgt.patched-$(date +%Y%m%d)
```

## 3. 重装恢复顺序

1. 安装 Docker 与 compose，创建 `/opt/safeline`，解包备份：
   `tar -xzf safeline-<ts>.tgz -C /tmp && cp -a /tmp/safeline-<ts>/. /opt/safeline/`
2. 修正权限：`chmod 600 /opt/safeline/.env`。
3. 只起数据库：
   `cd /opt/safeline && docker compose up -d postgres`
4. 恢复库（库为空或先 drop 重建）：
   ```bash
   docker exec -i safeline-pg sh -c \
     'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -U safeline-ce -d safeline-ce --clean --if-exists' \
     < /opt/polaris/backups/safeline-<ts>/safeline-ce.dump
   ```
   若还有其它库，同样 `createdb` 后 `pg_restore`。
5. 恢复 mgt 补丁：若用方式 A，把 compose 中 mgt 的 `image:` 指向 `safeline-mgt:patched-<日期>` 后再启动。
6. 启动全栈：`docker compose up -d`，确认 8 个服务 healthy（mgt/tengine/detector/luigi/fvm/chaos/pg）。
7. 恢复宿主机 nginx 与证书：
   ```bash
   sudo tar -xzf /opt/polaris/backups/safeline-<ts>/host-nginx.tgz -C /
   sudo nginx -t && sudo nginx -s reload
   ```
8. 重新下发业务配置：`sudo bash /opt/polaris/src/polaris-server/deploy/scripts/server-deploy.sh --sync`（会把站点片段默认装成 tengine 代理）。
9. 自检：脚本末尾自动 curl 9 个域名 + `127.0.0.1:8443`，全 200 即通。

## 4. 依赖关系与坑

- 外层 nginx 的 9 个站点默认走 `127.0.0.1:8443` → tengine → `172.17.0.1:18080`（`50-tengine-backends.conf`）。docker0 地址变了要改这个文件。
- `zz-realip.conf` 依赖雷池 compose 的网络子网 `172.22.222.0/24`，子网变了要同步改。
- 证书：业务域名在 `/etc/nginx/certs/`，`waf.laimengchao.site` 在 `/etc/letsencrypt/live/`，都要一起备份。
- `nginx.conf` 的 main 段（`user www-data`、`worker_processes 1`、`worker_cpu_affinity auto`、`error_log`、`events { worker_connections 768; }`）不在 `nginx-base.inc` 的合并范围（该片段只处理 http 段），重装后按 Debian 默认值即可，建议保留 `worker_connections 768`。
- `00-default-ports.conf` 的 8848 绑定 `10.2.4.8`，换机必须改成新机器内网 IP，否则 `nginx -t` 会 bind 失败。
- 时区：各容器挂载 `/etc/localtime:ro`，迁移后保留。
- `safeline-pg` 数据目录是 `/opt/safeline/resources/postgres/data`，运行中不要直接拷；用 `pg_dump`。
- 统计/流量依赖 tengine 站点配置（由 mgt 生成到 `/opt/safeline/resources/nginx/sites-enabled`），恢复库后 mgt 会重新生成。
