#!/usr/bin/env bash
# 雷池（SafeLine）与宿主机 nginx 配置备份。恢复步骤见 deploy/SAFELINE-RECOVERY.md。
#   sudo bash deploy/scripts/safeline-backup.sh [输出目录]
# 默认输出 /opt/polaris/backups/safeline-<时间戳>/，不写任何明文密码到本仓库。
set -euo pipefail

OUT=${1:-/opt/polaris/backups/safeline-$(date +%Y%m%d-%H%M%S)}
mkdir -p "$OUT"
chmod 700 "$OUT"

echo "== 1/5 雷池编排与配置 =="
cp -a /opt/safeline/compose.yaml "$OUT/"
cp -a /opt/safeline/.env "$OUT/safeline.env"
chmod 600 "$OUT/safeline.env"
tar -C /opt/safeline -czf "$OUT/safeline-resources.tgz" \
  resources/nginx/conf.d resources/detector resources/mgt 2>/dev/null || true

echo "== 2/5 PostgreSQL（站点/证书/策略/统计）=="
docker exec safeline-pg sh -c \
  'PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump -U safeline-ce -Fc safeline-ce' \
  > "$OUT/safeline-ce.dump"
for db in $(docker exec safeline-pg sh -c \
  'PGPASSWORD="${POSTGRES_PASSWORD}" psql -U safeline-ce -d postgres -t -A -c "select datname from pg_database where datistemplate=false and datname<>'"'"'postgres'"'"';"'); do
  case "$db" in
    safeline-ce) ;;
    *) docker exec safeline-pg sh -c \
         "PGPASSWORD=\"\$POSTGRES_PASSWORD\" pg_dump -U safeline-ce -Fc '$db'" > "$OUT/$db.dump" ;;
  esac
done

echo "== 3/5 宿主机 nginx 与证书 =="
tar -C /etc -czf "$OUT/host-nginx.tgz" \
  nginx/nginx.conf nginx/conf.d nginx/snippets nginx/certs letsencrypt 2>/dev/null || true
ls -1 /etc/nginx/conf.d > "$OUT/conf.d.list"

echo "== 4/5 polaris 发版配置（不含 jar/www）=="
tar -C /opt/polaris -czf "$OUT/polaris-deploy.tgz" \
  docker-compose.yml nginx-polaris.conf.tpl polaris-*.inc nacos keys 2>/dev/null || true
cp -a /opt/polaris/.env "$OUT/polaris.env" 2>/dev/null || true
chmod 600 "$OUT/polaris.env" 2>/dev/null || true

echo "== 5/5 镜像清单 =="
docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' > "$OUT/images.txt"
docker compose -f /opt/safeline/compose.yaml config --images > "$OUT/compose-images.txt" 2>/dev/null || true

tar -C "$(dirname "$OUT")" -czf "$OUT.tgz" "$(basename "$OUT")"
chmod 600 "$OUT.tgz"
echo "完成：$OUT.tgz"
echo "提示：mgt 容器内 /app/mgt 非原版镜像内容，重装前请参考 SAFELINE-RECOVERY.md 的补丁保留步骤。"
