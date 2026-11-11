# 本地构建：打包 9 个后端 jar + 构建 8 个前端站点，产物集中到 deploy\jars 与 deploy\www
# 用法：powershell -ExecutionPolicy Bypass -File deploy\scripts\build-all.ps1
# 说明：服务器只有 2C4G，编译务必在本机/CI 完成，只把产物推上去。

param(
  [string]$Mvn        = 'F:\code\sso\.maven39\apache-maven-3.9.9\bin\mvn.cmd',
  [string]$ServerRoot = 'F:\code\polaris-server',
  [string]$WebRoot    = 'F:\code\polaris-web',
  [string]$DeployRoot = (Split-Path -Parent $PSScriptRoot),
  [string]$Domain     = 'laimengchao.site',
  [switch]$SkipBackend,
  [switch]$SkipWeb,
  # CMS 没有独立前端域名，默认不构建；需要时加 -WithCms
  [switch]$WithCms
)

$ErrorActionPreference = 'Stop'
# 前端登录跳转地址是构建期写死进 dist 的（import.meta.env.VITE_SSO_ORIGIN），必须在这里注入
$env:VITE_SSO_ORIGIN = "https://polaris-sso.$Domain"
$env:VITE_WEB_ORIGIN = "https://portal-blog-web.$Domain"
Write-Host "构建期环境：VITE_SSO_ORIGIN=$env:VITE_SSO_ORIGIN  VITE_WEB_ORIGIN=$env:VITE_WEB_ORIGIN"
$jarDir = Join-Path $DeployRoot 'jars'
$wwwDir = Join-Path $DeployRoot 'www'
New-Item -ItemType Directory -Force -Path $jarDir, $wwwDir | Out-Null

if (-not $SkipBackend) {
  Write-Host '=== 1/2 打包后端 ===' -ForegroundColor Cyan
  Push-Location $ServerRoot
  try {
    & $Mvn -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "maven package 失败，退出码 $LASTEXITCODE" }
  } finally { Pop-Location }

  # 组件 jar 在容器里统一短名，compose 只认这些名字
  $jarMap = [ordered]@{
    'sso-auth.jar'    = 'polaris-sso\polaris-sso-auth\target\polaris-sso-auth-1.0.0.jar'
    'sso-account.jar' = 'polaris-sso\polaris-sso-account\target\polaris-sso-account-1.0.0.jar'
    'sso-gateway.jar' = 'polaris-sso\polaris-sso-gateway\target\polaris-sso-gateway-1.0.0.jar'
    'settings.jar'    = 'polaris-settings\polaris-settings-service\target\settings-service.jar'
    'ehr.jar'         = 'polaris-ehr\polaris-ehr-service\target\ehr-service.jar'
    'cms.jar'         = 'polaris-cms\polaris-cms-service\target\cms-service.jar'
    'blog.jar'        = 'polaris-blog\polaris-blog-service\target\blog-service.jar'
    'portal.jar'      = 'polaris-portal\polaris-portal-service\target\portal-service.jar'
    'job.jar'         = 'polaris-job\polaris-job-service\target\job-service.jar'
  }
  foreach ($name in $jarMap.Keys) {
    $src = Join-Path $ServerRoot $jarMap[$name]
    if (-not (Test-Path $src)) { throw "缺少产物：$src" }
    Copy-Item $src (Join-Path $jarDir $name) -Force
    Write-Host ("  {0,-16} {1} MB" -f $name, [math]::Round((Get-Item $src).Length / 1MB))
  }
}

if (-not $SkipWeb) {
  Write-Host '=== 2/2 构建前端 ===' -ForegroundColor Cyan
  # app 目录 -> www 下的站点目录（blog 一仓两端，单独处理；cms 默认不构建）
  $sites = @(
    @{ dir = 'polaris-sso-web';      out = 'sso' },
    @{ dir = 'polaris-settings-web'; out = 'settings' },
    @{ dir = 'polaris-ehr-web';      out = 'ehr' },
    @{ dir = 'polaris-portal-web';   out = 'portal' },
    @{ dir = 'polaris-job-web';      out = 'job' }
  )
  if ($WithCms) { $sites += @{ dir = 'polaris-cms-web'; out = 'cms' } }

  foreach ($site in $sites) {
    $appDir = Join-Path $WebRoot $site.dir
    Push-Location $appDir
    try {
      if (-not (Test-Path 'node_modules')) { & npm ci; if ($LASTEXITCODE -ne 0) { throw "npm ci 失败：$($site.dir)" } }
      & npm run build
      if ($LASTEXITCODE -ne 0) { throw "npm run build 失败：$($site.dir)" }
    } finally { Pop-Location }

    $target = Join-Path $wwwDir $site.out
    Remove-Item $target -Recurse -Force -ErrorAction SilentlyContinue
    Copy-Item (Join-Path $appDir 'dist') $target -Recurse
    Write-Host "  -> www\$($site.out)"
  }

  # 博客：根目录一次构建，产出 packages\web\dist 与 packages\admin\dist
  $blogDir = Join-Path $WebRoot 'polaris-blog-web'
  Push-Location $blogDir
  try {
    if (-not (Test-Path 'node_modules')) { & npm ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci 失败：polaris-blog-web' } }
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw 'npm run build 失败：polaris-blog-web' }
  } finally { Pop-Location }

  foreach ($pair in @(@{ src = 'packages\web\dist'; out = 'blog-web' }, @{ src = 'packages\admin\dist'; out = 'blog-admin' })) {
    $target = Join-Path $wwwDir $pair.out
    Remove-Item $target -Recurse -Force -ErrorAction SilentlyContinue
    Copy-Item (Join-Path $blogDir $pair.src) $target -Recurse
    Write-Host "  -> www\$($pair.out)"
  }
}

Write-Host '=== 完成 ===' -ForegroundColor Green
Write-Host "jar: $jarDir"
Write-Host "静态: $wwwDir"
