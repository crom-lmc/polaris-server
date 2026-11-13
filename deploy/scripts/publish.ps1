# 发布到服务器：上传 jar / 静态站点 / compose / nginx 配置，然后滚动重启
# 前置：本机已能免密 ssh <user>@<host>；deploy\keys 下已放好 prod-jwt-private.pem / prod-jwt-public.pem
# 用法：powershell -ExecutionPolicy Bypass -File deploy\scripts\publish.ps1 -Server 1.2.3.4 -User root

param(
  [Parameter(Mandatory = $true)][string]$Server,
  [string]$User       = 'root',
  [string]$Remote     = '/opt/polaris',
  [string]$DeployRoot = (Split-Path -Parent $PSScriptRoot),
  [switch]$SkipNginx
)

$ErrorActionPreference = 'Stop'
$target = "$User@$Server"

Write-Host '=== 1/4 准备远端目录 ===' -ForegroundColor Cyan
ssh $target "mkdir -p $Remote/logs $Remote/uploads/blog"

Write-Host '=== 2/4 上传产物 ===' -ForegroundColor Cyan
scp (Join-Path $DeployRoot 'docker-compose.yml')     "${target}:$Remote/"
scp (Join-Path $DeployRoot '.env')                   "${target}:$Remote/"
scp (Join-Path $DeployRoot 'nginx\polaris.conf.tpl') "${target}:$Remote/nginx-polaris.conf.tpl"
scp (Join-Path $DeployRoot 'nginx\api-proxy.inc')    "${target}:$Remote/api-proxy.inc"
scp (Join-Path $DeployRoot 'nginx\security.inc')     "${target}:$Remote/security.inc"
scp (Join-Path $DeployRoot 'nginx\waf.conf')         "${target}:$Remote/waf.conf"
scp (Join-Path $DeployRoot 'nginx\50-tengine-backends.conf') "${target}:$Remote/50-tengine-backends.conf"
scp (Join-Path $DeployRoot 'nginx\zz-realip.conf')   "${target}:$Remote/zz-realip.conf"
scp (Join-Path $DeployRoot 'nginx\vault.conf')       "${target}:$Remote/vault.conf"
scp (Join-Path $DeployRoot 'nginx\00-default-ssl.conf') "${target}:$Remote/00-default-ssl.conf"
scp (Join-Path $DeployRoot 'nginx\00-default-ports.conf') "${target}:$Remote/00-default-ports.conf"
scp (Join-Path $DeployRoot 'nginx\nacos.conf')       "${target}:$Remote/nacos.conf"
scp (Join-Path $DeployRoot 'nginx\nginx-base.inc')   "${target}:$Remote/nginx-base.inc"
scp (Join-Path $DeployRoot 'scripts\apply-nginx-base.sh') "${target}:$Remote/apply-nginx-base.sh"
scp (Join-Path $DeployRoot 'nginx\polaris-tengine-proxy.inc') "${target}:$Remote/polaris-tengine-proxy.inc"
scp (Join-Path $DeployRoot 'nginx\polaris-direct.inc')        "${target}:$Remote/polaris-direct.inc"
scp -r (Join-Path $DeployRoot 'jars') "${target}:$Remote/"
scp -r (Join-Path $DeployRoot 'www')  "${target}:$Remote/"
if (Test-Path (Join-Path $DeployRoot 'keys')) {
  scp -r (Join-Path $DeployRoot 'keys') "${target}:$Remote/"
}

Write-Host '=== 3/4 重启容器 ===' -ForegroundColor Cyan
$composeCmd = @'
set -e
cd REMOTE_DIR
chmod 600 .env || true
chmod 600 keys/*.pem 2>/dev/null || true
# 2C 机器串行启动，避免 9 个 JVM 同时冷启把 CPU 打满
COMPOSE_PARALLEL_LIMIT=1 docker compose up -d --remove-orphans
docker compose ps
'@
$composeCmd.Replace('REMOTE_DIR', $Remote) | ssh $target 'bash -s'

if (-not $SkipNginx) {
  Write-Host '=== 4/4 下发 nginx 配置 ===' -ForegroundColor Cyan
  $nginxCmd = @'
set -e
cd REMOTE_DIR
export DOMAIN=$(grep -E '^DOMAIN=' .env | cut -d= -f2)
mkdir -p /etc/nginx/snippets
cp api-proxy.inc /etc/nginx/snippets/polaris-api.inc
cp security.inc /etc/nginx/snippets/security.inc
cp waf.conf /etc/nginx/conf.d/waf.conf
cp vault.conf /etc/nginx/conf.d/vault.conf
cp 00-default-ssl.conf /etc/nginx/conf.d/00-default-ssl.conf
cp 00-default-ports.conf /etc/nginx/conf.d/00-default-ports.conf
cp nacos.conf /etc/nginx/conf.d/nacos.conf
cp 50-tengine-backends.conf /etc/nginx/conf.d/50-tengine-backends.conf
# 宿主机 nginx.conf 只幂等补缺失的限流 zone / bad_bot map / 日志格式，不整文件覆盖
bash apply-nginx-base.sh nginx-base.inc
# 站点默认经雷池 tengine(8443)；直连 9080/静态 是显式回滚路径（polaris-direct.inc）
cp polaris-tengine-proxy.inc /etc/nginx/snippets/polaris-cms-root.inc
cp polaris-tengine-proxy.inc /etc/nginx/snippets/polaris-batch1-root.inc
cp polaris-tengine-proxy.inc /etc/nginx/snippets/polaris-batch2-root.inc
# zz-realip 属于 tengine 容器挂载目录，不是 /etc/nginx/conf.d
if [ -d /opt/safeline/resources/nginx/conf.d ]; then
  cp zz-realip.conf /opt/safeline/resources/nginx/conf.d/zz-realip.conf
fi
envsubst '${DOMAIN}' < nginx-polaris.conf.tpl > /etc/nginx/conf.d/polaris.conf
nginx -t && nginx -s reload
echo "nginx ok, domain=$DOMAIN"

# 发版自检：只告警、不自动回滚，保留现场
echo "=== 发版自检（9 站点 + tengine 8443） ==="
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
if [ "$FAIL" != "0" ]; then
  echo "!!! 自检失败：配置已生效，未自动回滚；请保留现场并按下面命令回滚"
  if [ -f /opt/polaris/polaris-direct.inc ]; then
    echo "  sudo install -m 644 /opt/polaris/polaris-direct.inc /etc/nginx/snippets/polaris-batch1-root.inc"
    echo "  sudo install -m 644 /opt/polaris/polaris-direct.inc /etc/nginx/snippets/polaris-batch2-root.inc"
    echo "  sudo install -m 644 /opt/polaris/polaris-direct.inc /etc/nginx/snippets/polaris-cms-root.inc"
  else
    echo "  # /opt/polaris/polaris-direct.inc 尚未生成（首次发版前），用下面三条写入直连内容："
    echo "  echo 'try_files \$uri \$uri/ /index.html;' | sudo tee /etc/nginx/snippets/polaris-batch1-root.inc"
    echo "  echo 'try_files \$uri \$uri/ /index.html;' | sudo tee /etc/nginx/snippets/polaris-batch2-root.inc"
    echo "  echo 'try_files \$uri \$uri/ /index.html;' | sudo tee /etc/nginx/snippets/polaris-cms-root.inc"
  fi
  echo "  sudo nginx -t && sudo nginx -s reload"
  exit 1
fi
echo "自检通过：9 域名均 200，tengine 8443 可达"
'@
  $nginxCmd.Replace('REMOTE_DIR', $Remote) | ssh $target 'bash -s'
}

Write-Host '=== 完成；最近日志 ===' -ForegroundColor Green
ssh $target "cd $Remote && docker compose logs --tail=15 --no-log-prefix | tail -45"
