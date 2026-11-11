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
envsubst '${DOMAIN}' < nginx-polaris.conf.tpl > /etc/nginx/conf.d/polaris.conf
nginx -t
systemctl reload nginx
echo "nginx ok, domain=$DOMAIN"
'@
  $nginxCmd.Replace('REMOTE_DIR', $Remote) | ssh $target 'bash -s'
}

Write-Host '=== 完成；最近日志 ===' -ForegroundColor Green
ssh $target "cd $Remote && docker compose logs --tail=15 --no-log-prefix | tail -45"
