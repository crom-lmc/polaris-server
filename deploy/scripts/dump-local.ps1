# 导出本地 6 个业务库（结构和数据），用于首次上云初始化
# 用法：powershell -ExecutionPolicy Bypass -File deploy\scripts\dump-local.ps1 -Password root
# 产物：deploy\dump\*.sql，上传后在服务器执行：
#   for f in /opt/polaris/dump/*.sql; do mysql -uroot -p < "$f"; done

param(
  [string]$Mysql    = 'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysqldump.exe',
  [string]$User     = 'root',
  [string]$Password = '',
  [string]$Host_    = '127.0.0.1',
  [int]$Port        = 3306,
  [string]$OutDir   = (Join-Path (Split-Path -Parent $PSScriptRoot) 'dump')
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$databases = @('sso_ms', 'settings', 'ehr', 'cms', 'blog', 'polaris_job')
foreach ($db in $databases) {
  $file = Join-Path $OutDir "$db.sql"
  # --single-transaction：InnoDB 一致性快照，不锁表
  & $Mysql "-u$User" "-p$Password" "-h$Host_" "-P$Port" `
    --default-character-set=utf8mb4 --single-transaction --quick --routines `
    --databases $db --result-file="$file" 2>$null
  if ($LASTEXITCODE -ne 0) { throw "导出失败：$db" }
  Write-Host ("  {0,-14} {1} MB" -f $db, [math]::Round((Get-Item $file).Length / 1MB, 1))
}

Write-Host "导出完成：$OutDir" -ForegroundColor Green
Write-Host '上传后先导入库结构，再把 .env 里的 *_SQL_INIT_MODE 改回 never'
