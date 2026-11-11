#!/usr/bin/env bash
# 服务器侧一键更新：拉代码 → 构建 → 发布产物 → 重启
#
#   sudo /opt/polaris/src/polaris-server/deploy/scripts/server-deploy.sh            # 后端+前端
#   sudo .../server-deploy.sh --backend                                            # 只更新后端
#   sudo .../server-deploy.sh --web                                                # 只更新前端
#   sudo .../server-deploy.sh --sync                                               # 同时用仓库里的 compose / nginx 配置覆盖线上
#
# 约定：代码在 /opt/polaris/src/{polaris-server,polaris-web}，产物在 /opt/polaris/{jars,www}
set -euo pipefail

ROOT=${ROOT:-/opt/polaris}
SRC=${SRC:-$ROOT/src}
DEPLOY=$SRC/polaris-server/deploy
DOMAIN=${DOMAIN:-laimengchao.site}
DO_BACKEND=1
DO_WEB=1
DO_SYNC=0
[ "${1:-}" = "--backend" ] && DO_WEB=0
[ "${1:-}" = "--web" ] && DO_BACKEND=0
[ "${1:-}" = "--sync" ] && DO_SYNC=1

say() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }

say "1/5 拉取代码"
git -C "$SRC/polaris-server" pull --ff-only
git -C "$SRC/polaris-web" pull --ff-only

if [ "$DO_BACKEND" = 1 ]; then
  say "2/5 构建后端"
  ( cd "$SRC/polaris-server" && mvn -B -DskipTests package )

  say "3/5 发布 jar"
  declare -A JARS=(
    [sso-auth.jar]=polaris-sso/polaris-sso-auth/target/polaris-sso-auth-1.0.0.jar
    [sso-account.jar]=polaris-sso/polaris-sso-account/target/polaris-sso-account-1.0.0.jar
    [sso-gateway.jar]=polaris-sso/polaris-sso-gateway/target/polaris-sso-gateway-1.0.0.jar
    [settings.jar]=polaris-settings/polaris-settings-service/target/settings-service.jar
    [ehr.jar]=polaris-ehr/polaris-ehr-service/target/ehr-service.jar
    [cms.jar]=polaris-cms/polaris-cms-service/target/cms-service.jar
    [blog.jar]=polaris-blog/polaris-blog-service/target/blog-service.jar
    [portal.jar]=polaris-portal/polaris-portal-service/target/portal-service.jar
    [job.jar]=polaris-job/polaris-job-service/target/job-service.jar
  )
  mkdir -p "$ROOT/jars"
  for name in "${!JARS[@]}"; do
    src="$SRC/polaris-server/${JARS[$name]}"
    [ -f "$src" ] || { echo "缺少产物：$src"; exit 1; }
    install -m 644 "$src" "$ROOT/jars/$name"
    printf '  %-16s %s MB\n' "$name" "$(( $(stat -c%s "$src") / 1024 / 1024 ))"
  done
fi

if [ "$DO_WEB" = 1 ]; then
  say "4/5 构建前端"
  export VITE_SSO_ORIGIN="https://polaris-sso.$DOMAIN"
  export VITE_WEB_ORIGIN="https://portal-blog-web.$DOMAIN"

  build_site() { # $1=工程目录 $2=站点目录
    local app="$SRC/polaris-web/$1" out="$ROOT/www/$2"
    ( cd "$app" && { [ -d node_modules ] || npm ci; } && npm run build )
    rm -rf "$out" && mkdir -p "$out" && cp -r "$app/dist/." "$out/"
    echo "  -> www/$2"
  }

  build_site polaris-sso-web      sso
  build_site polaris-settings-web settings
  build_site polaris-ehr-web      ehr
  build_site polaris-portal-web   portal
  build_site polaris-job-web      job

  # 博客是一仓两端
  blog="$SRC/polaris-web/polaris-blog-web"
  ( cd "$blog" && { [ -d node_modules ] || npm ci; } && npm run build )
  for pair in "packages/web/dist:blog-web" "packages/admin/dist:blog-admin"; do
    src="${pair%%:*}"; out="$ROOT/www/${pair##*:}"
    rm -rf "$out" && mkdir -p "$out" && cp -r "$blog/$src/." "$out/"
    echo "  -> www/${pair##*:}"
  done
fi

if [ "$DO_SYNC" = 1 ]; then
  say "同步编排与 nginx 配置"
  install -m 644 "$DEPLOY/docker-compose.yml" "$ROOT/docker-compose.yml"
  install -m 644 "$DEPLOY/nginx/api-proxy.inc" /etc/nginx/snippets/polaris-api.inc
  DOMAIN="$DOMAIN" envsubst '${DOMAIN}' < "$DEPLOY/nginx/polaris.conf.tpl" > /tmp/polaris.conf
  install -m 644 /tmp/polaris.conf /etc/nginx/conf.d/polaris.conf
  nginx -t && nginx -s reload
fi

say "5/5 重启服务"
cd "$ROOT"
COMPOSE_PARALLEL_LIMIT=1 docker compose up -d --remove-orphans
docker compose ps --format '{{.Name}}	{{.Status}}'
