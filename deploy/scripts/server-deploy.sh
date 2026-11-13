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
  export VITE_PORTAL_ORIGIN="https://polaris-portal.$DOMAIN"

  build_site() { # $1=工程目录 $2=站点目录
    local app="$SRC/polaris-web/$1" out="$ROOT/www/$2"
    ( cd "$app" && { [ -d node_modules ] || npm ci; } && npm run build )
    rm -rf "$out" && mkdir -p "$out" && cp -r "$app/dist/." "$out/"
    echo "  -> www/$2"
  }

  build_site polaris-sso-web      sso
  build_site polaris-settings-web settings
  build_site polaris-ehr-web      ehr
  build_site polaris-cms-web      cms
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
  install -m 644 "$DEPLOY/nginx/security.inc" /etc/nginx/snippets/security.inc
  install -m 644 "$DEPLOY/nginx/waf.conf" /etc/nginx/conf.d/waf.conf
  install -m 644 "$DEPLOY/nginx/vault.conf" /etc/nginx/conf.d/vault.conf
  install -m 644 "$DEPLOY/nginx/00-default-ssl.conf" /etc/nginx/conf.d/00-default-ssl.conf
  install -m 644 "$DEPLOY/nginx/00-default-ports.conf" /etc/nginx/conf.d/00-default-ports.conf
  install -m 644 "$DEPLOY/nginx/nacos.conf" /etc/nginx/conf.d/nacos.conf
  install -m 644 "$DEPLOY/nginx/50-tengine-backends.conf" /etc/nginx/conf.d/50-tengine-backends.conf
  # 站点默认经雷池 tengine(8443)；直连 9080/静态 是显式回滚路径（polaris-direct.inc）
  install -m 644 "$DEPLOY/nginx/polaris-tengine-proxy.inc" /etc/nginx/snippets/polaris-cms-root.inc
  install -m 644 "$DEPLOY/nginx/polaris-tengine-proxy.inc" /etc/nginx/snippets/polaris-batch1-root.inc
  install -m 644 "$DEPLOY/nginx/polaris-tengine-proxy.inc" /etc/nginx/snippets/polaris-batch2-root.inc
  install -m 644 "$DEPLOY/nginx/polaris-direct.inc" "$ROOT/polaris-direct.inc"
  # zz-realip 属于 tengine 容器挂载目录，不是 /etc/nginx/conf.d
  if [ -d /opt/safeline/resources/nginx/conf.d ]; then
    install -m 644 "$DEPLOY/nginx/zz-realip.conf" /opt/safeline/resources/nginx/conf.d/zz-realip.conf
  fi
  # 宿主机 nginx.conf 只幂等补缺失的限流 zone / bad_bot map / 日志格式，不整文件覆盖
  bash "$DEPLOY/scripts/apply-nginx-base.sh" "$DEPLOY/nginx/nginx-base.inc"
  DOMAIN="$DOMAIN" envsubst '${DOMAIN}' < "$DEPLOY/nginx/polaris.conf.tpl" > /tmp/polaris.conf
  install -m 644 /tmp/polaris.conf /etc/nginx/conf.d/polaris.conf
  nginx -t && nginx -s reload

  # 发版自检：只告警、不自动回滚，保留现场
  say "发版自检（9 站点 + tengine 8443）"
  FAIL=0
  for name in polaris-sso polaris-ehr polaris-cms polaris-settings polaris-job \
              polaris-portal portal-blog-admin portal-blog-web vault; do
    code=$(curl -sk -o /dev/null -w '%{http_code}' --resolve "$name.$DOMAIN:443:127.0.0.1" "https://$name.$DOMAIN/" || echo 000)
    printf '  %-20s %s\n' "$name" "$code"
    [ "$code" = "200" ] || FAIL=1
  done
  WAF=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: polaris-cms.$DOMAIN" http://127.0.0.1:8443/ || echo 000)
  printf '  %-20s %s\n' "tengine-8443" "$WAF"
  [ "$WAF" = "200" ] || FAIL=1
  if [ "$FAIL" != 0 ]; then
    echo "!!! 自检失败：配置已生效，未自动回滚；请保留现场并按下面命令回滚"
    if [ -f "$ROOT/polaris-direct.inc" ]; then
      echo "  install -m 644 $ROOT/polaris-direct.inc /etc/nginx/snippets/polaris-batch1-root.inc"
      echo "  install -m 644 $ROOT/polaris-direct.inc /etc/nginx/snippets/polaris-batch2-root.inc"
      echo "  install -m 644 $ROOT/polaris-direct.inc /etc/nginx/snippets/polaris-cms-root.inc"
    else
      echo "  # $ROOT/polaris-direct.inc 尚未生成（首次发版前），用下面三条写入直连内容："
      echo "  echo 'try_files \$uri \$uri/ /index.html;' | tee /etc/nginx/snippets/polaris-batch1-root.inc"
      echo "  echo 'try_files \$uri \$uri/ /index.html;' | tee /etc/nginx/snippets/polaris-batch2-root.inc"
      echo "  echo 'try_files \$uri \$uri/ /index.html;' | tee /etc/nginx/snippets/polaris-cms-root.inc"
    fi
    echo "  nginx -t && nginx -s reload"
    exit 1
  fi
  echo "自检通过：9 域名均 200，tengine 8443 可达"
fi

say "5/5 重启服务"
cd "$ROOT"
COMPOSE_PARALLEL_LIMIT=1 docker compose up -d --remove-orphans
docker compose ps --format '{{.Name}}	{{.Status}}'
