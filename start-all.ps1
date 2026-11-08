#Requires -Version 5.1
<#
  Polaris 一键启动 / 重启全部前后端。
  已在监听目标端口的进程会先被整棵进程树杀掉，再重新启动（= 重启语义）。

  必须在你自己的控制台运行（不要在 agent/IDE 任务里调用，子进程会随父进程退出被回收）：
    powershell -ExecutionPolicy Bypass -File F:\code\polaris-server\start-all.ps1
    ... -Mode local                 # 不走 Nacos，密钥从 polaris-nacos\.secrets 注入（默认 nacos）
    ... -OnlyBackend | -OnlyWeb     # 只起后端 / 只起前端
    ... -Services "gateway,auth"    # 只起指定后端服务
    ... -Web "sso,blog"             # 只起指定前端
#>
param(
    [ValidateSet("nacos","local")] [string]$Mode = "nacos",
    [string]$NacosAddr = "127.0.0.1:18848",
    [string]$Namespace = "sso-ms-local",
    [string]$Group = "SSO",
    [switch]$OnlyBackend,
    [switch]$OnlyWeb,
    [string]$Services = "",
    [string]$Web = "",
    [string]$Java = "java"
)
$ErrorActionPreference = "Stop"

# 清掉会干扰的环境变量（代理重复键会让 Start-Process 抛异常；SERVER__PORT 会被 Spring 误绑）
foreach ($pn in 'HTTP_PROXY','http_proxy','HTTPS_PROXY','https_proxy','ALL_PROXY','all_proxy','NO_PROXY','no_proxy') {
    [Environment]::SetEnvironmentVariable($pn, $null, 'Process')
}
Remove-Item env:SERVER__PORT -ErrorAction SilentlyContinue

$ROOT    = Split-Path -Parent $MyInvocation.MyCommand.Path          # F:\code\polaris-server
$WEBROOT = Split-Path -Parent $ROOT | Join-Path -ChildPath "polaris-web"  # F:\code\polaris-web
$LOGS    = Join-Path $ROOT "logs"
$SEC     = Join-Path $ROOT "polaris-nacos\.secrets"
New-Item -ItemType Directory -Force $LOGS | Out-Null
$PIDS_FILE = Join-Path $LOGS "pids-all.txt"

function Test-Port([int]$Port, [int]$TimeoutMs = 1200) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $iar = $c.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
        if ($ok) { $c.EndConnect($iar); $c.Close(); return $true }
        $c.Close(); return $false
    } catch { return $false }
}
function Read-Secret([string]$File) {
    $p = Join-Path $SEC $File
    if (Test-Path $p) { return (Get-Content $p -Raw).Trim() }
    return ""
}
function Stop-Tree([int]$ProcId) {
    foreach ($c in (Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcId" -ErrorAction SilentlyContinue)) {
        Stop-Tree $c.ProcessId
    }
    Stop-Process -Id $ProcId -Force -ErrorAction SilentlyContinue
}
function Restart-Port([string]$Name, [int]$Port) {
    $owners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
              Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($procId in $owners) {
        $pn = (Get-Process -Id $procId -ErrorAction SilentlyContinue).ProcessName
        Write-Host ("kill   {0}: port {1} held by {2}(pid={3}) -> restarting" -f $Name, $Port, $pn, $procId)
        Stop-Tree $procId
    }
    if ($owners) { Start-Sleep -Milliseconds 800 }
}
function Jdbc([string]$Db) {
    return "jdbc:mysql://127.0.0.1:3306/$Db`?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
}

# ---------- 环境注入 ----------
if ($Mode -eq "nacos") {
    $envFile = Join-Path $ROOT "polaris-nacos\.env"
    if (Test-Path $envFile) {
        foreach ($line in (Get-Content $envFile)) {
            if ($line -match '^(SSO_CONF_KEY)=(.*)$') { $env:SSO_CONF_KEY = $Matches[2].Trim() }
        }
    }
    if (-not $env:SSO_CONF_KEY) { Write-Error "nacos 模式需要 SSO_CONF_KEY（写在 polaris-nacos\.env 或环境变量）"; exit 2 }
    $env:SSO_NACOS_ENABLED = "true"; $env:SSO_NACOS_DISCOVERY = "true"
    $env:SSO_NACOS_ADDR = $NacosAddr; $env:SSO_NACOS_NAMESPACE = $Namespace; $env:SSO_NACOS_GROUP = $Group
    if (-not (Test-Port ([int]($NacosAddr -split ':')[1]))) {
        Write-Warning "Nacos $NacosAddr 不可达（远程 Nacos 需先开 SSH 隧道: 18848+19848）；服务将启动失败"
    } else { Write-Host "nacos mode: $NacosAddr ns=$Namespace group=$Group" }
} else {
    $env:SSO_NACOS_ENABLED = "false"; $env:SSO_NACOS_DISCOVERY = "false"; $env:SSO_NACOS_ADDR = $NacosAddr
    $env:SSO_INTERNAL_TOKEN = Read-Secret 'internal-token-local.txt'
    $env:SSO_ADMIN_PASSWORD = Read-Secret 'admin-password.txt'
    $pk = Join-Path $SEC 'jwt-private.pem'
    if (-not (Test-Path $pk)) { Write-Error "missing $pk"; exit 2 }
    $env:SSO_JWT_PRIVATE_KEY = "file:" + $pk.Replace('\','/')
    if (-not $env:SSO_INTERNAL_TOKEN) { Write-Error "missing internal-token-local.txt"; exit 2 }
    $env:SSO_DB_URL = Jdbc 'sso_ms'; $env:SSO_DB_USER = "root"; $env:SSO_DB_PASSWORD = "root"
    $env:SSO_SQL_INIT_MODE = "always"
    $env:SSO_ACCOUNT_URL = "http://127.0.0.1:9002"
    $env:SSO_AUTH_ROUTE = "http://127.0.0.1:9001"; $env:SSO_ACCOUNT_ROUTE = "http://127.0.0.1:9002"
    foreach ($app in 'blog','cms','ehr','settings') {
        $prefix = $app.ToUpperInvariant()
        Set-Item -Path ("env:${prefix}_DB_URL") -Value (Jdbc $app)
        Set-Item -Path ("env:${prefix}_DB_USER") -Value "root"
        Set-Item -Path ("env:${prefix}_DB_PASSWORD") -Value "root"
        Set-Item -Path ("env:${prefix}_APP_SECRET") -Value (Read-Secret "$app-app-secret.txt")
    }
    $env:PORTAL_APP_SECRET = Read-Secret 'portal-app-secret.txt'
    $env:CMS_UPLOAD_ROOT = Join-Path $ROOT "polaris-uploads-cms"
    Write-Host "local mode: secrets from polaris-nacos\.secrets"
}

$JavaPath = (Get-Command $Java -ErrorAction SilentlyContinue).Source
if (-not $JavaPath -and $env:JAVA_HOME) { $JavaPath = Join-Path $env:JAVA_HOME "bin\java.exe" }
if (-not (Test-Path $JavaPath)) { Write-Error "java not found"; exit 2 }

# ---------- 服务清单 ----------
$ALL_SVCS = @(
    @{name="auth";     port=9001; jar="polaris-sso\polaris-sso-auth\target\polaris-sso-auth-1.0.0.jar"},
    @{name="account";  port=9002; jar="polaris-sso\polaris-sso-account\target\polaris-sso-account-1.0.0.jar"},
    @{name="blog";     port=9003; jar="polaris-blog\blog-service\target\blog-service.jar"},
    @{name="cms";      port=9004; jar="polaris-cms\cms-service\target\cms-service.jar"},
    @{name="portal";   port=9005; jar="polaris-portal\portal-service\target\portal-service.jar"},
    @{name="ehr";      port=9006; jar="polaris-ehr\ehr-service\target\ehr-service.jar"},
    @{name="settings"; port=9007; jar="polaris-settings\polaris-settings-service\target\settings-service.jar"},
    @{name="gateway";  port=9080; jar="polaris-sso\polaris-sso-gateway\target\polaris-sso-gateway-1.0.0.jar"}
)
$ALL_WEB = @(
    @{name="sso-web";      port=5173; dir="polaris-sso-web";      script="dev"},
    @{name="blog-admin";   port=5174; dir="polaris-blog-web";     script="dev:admin"},
    @{name="blog-web";     port=5175; dir="polaris-blog-web";     script="dev:web"},
    @{name="cms-web";      port=5177; dir="polaris-cms-web";      script="dev"},
    @{name="portal-web";   port=5178; dir="polaris-portal-web";   script="dev"},
    @{name="ehr-web";      port=5179; dir="polaris-ehr-web";      script="dev"},
    @{name="settings-web"; port=5180; dir="polaris-settings-web"; script="dev"}
)
$SVCS = @(); $WebList = @()
if (-not $OnlyWeb) {
    $SVCS = if ($Services) { @($ALL_SVCS | Where-Object { $Services.Split(',').Trim() -contains $_.name }) } else { @($ALL_SVCS) }
}
if (-not $OnlyBackend) {
    $WebList = if ($Web) { @($ALL_WEB | Where-Object { $Web.Split(',').Trim() -contains $_.name }) } else { @($ALL_WEB) }
}

# ---------- 启动 ----------
$pids = @()
foreach ($svc in $SVCS) {
    $jar = Join-Path $ROOT $svc.jar
    if (-not (Test-Path $jar)) { Write-Warning ("skip  backend {0}: jar missing ({1})，先 mvn package" -f $svc.name, $svc.jar); continue }
    Restart-Port $svc.name $svc.port
    $log = Join-Path $LOGS "$($svc.name)-$($svc.port).log"; $err = Join-Path $LOGS "$($svc.name)-$($svc.port).err.log"
    $proc = Start-Process -FilePath $JavaPath `
        -ArgumentList @("-Xmx512m","-Dserver.address=0.0.0.0","-Dserver.port=$($svc.port)","-Dfile.encoding=UTF-8","-jar",$jar) `
        -WorkingDirectory $ROOT -RedirectStandardOutput $log -RedirectStandardError $err -PassThru
    $pids += "$($svc.name)=$($proc.Id)"
    Write-Host ("start backend {0,-9} pid={1,-7} port={2}" -f $svc.name, $proc.Id, $svc.port)
    Start-Sleep -Milliseconds 600
}
foreach ($w in $WebList) {
    $dir = Join-Path $WEBROOT $w.dir
    if (-not (Test-Path $dir)) { Write-Warning ("skip  web {0}: dir missing {1}" -f $w.name, $dir); continue }
    if (-not (Test-Path (Join-Path $dir "node_modules"))) {
        Write-Host ("install web  {0}: npm install ..." -f $w.name)
        $ip = Start-Process -FilePath $env:ComSpec -ArgumentList @("/c","npm install") -WorkingDirectory $dir -Wait -PassThru
        if ($ip.ExitCode -ne 0) { Write-Warning ("skip  web {0}: npm install failed" -f $w.name); continue }
    }
    Restart-Port $w.name $w.port
    $log = Join-Path $LOGS "$($w.name).log"; $err = Join-Path $LOGS "$($w.name).err.log"
    $proc = Start-Process -FilePath $env:ComSpec -ArgumentList @("/c","npm run $($w.script)") -WorkingDirectory $dir `
        -RedirectStandardOutput $log -RedirectStandardError $err -PassThru
    $pids += "$($w.name)=$($proc.Id)"
    Write-Host ("start web     {0,-9} pid={1,-7} port={2}" -f $w.name, $proc.Id, $w.port)
}
if ($pids.Count) { Set-Content $PIDS_FILE ($pids -join "`n") }

# ---------- 就绪等待（全局 150s）----------
$deadline = (Get-Date).AddSeconds(150)
$pending = @(); foreach ($s in $SVCS) { $pending += @{name=$s.name; port=$s.port} }
foreach ($w in $WebList) { $pending += @{name=$w.name; port=$w.port} }
while ((Get-Date) -lt $deadline -and $pending.Count) {
    $pending = @($pending | Where-Object { -not (Test-Port $_.port) })
    if ($pending.Count) { Start-Sleep -Seconds 2 }
}
Write-Host ""
if (-not $pending.Count) { Write-Host "ALL READY" } else {
    foreach ($p in $pending) { Write-Warning ("NOT READY: {0} port {1} -- see logs\{2}*.log" -f $p.name, $p.port, $p.name) }
}
Write-Host "Gateway: http://127.0.0.1:9080    Logs: $LOGS"
