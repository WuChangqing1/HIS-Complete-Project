# Backup SQL and the matching secrets file. Keep the result in a protected location.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'windows\common.ps1')
$toolPaths = Get-ProjectToolPaths $root
if (-not $toolPaths) { throw '缺少工具路径配置；请先运行 start-his.bat。' }
$mysqlDump = Join-Path $toolPaths.MySQL 'bin\mysqldump.exe'
$client = Join-Path $root '.runtime\config\mysql-client.ini'
if (-not (Test-Path $mysqlDump) -or -not (Test-Path $client)) { throw '请先部署 HIS。' }
$backupDir = Join-Path $root 'backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$file = Join-Path $backupDir "his-$stamp.sql"
Write-Host '正在生成 MySQL SQL 备份（保留原库，不修改数据）'
$p = Start-Process -FilePath $mysqlDump -WorkingDirectory $root -ArgumentList @( "--defaults-extra-file=`"$client`"",'--single-transaction','--routines','--triggers','--set-gtid-purged=OFF','--default-character-set=utf8mb4','his') -PassThru -Wait -NoNewWindow -RedirectStandardOutput $file
if ($p.ExitCode -ne 0 -or -not (Test-Path $file) -or (Get-Item $file).Length -eq 0) { throw '备份失败；请检查终端输出。' }
Copy-Item (Join-Path $root '.env') (Join-Path $backupDir "his-$stamp.env")
Write-Host "备份完成：$file，包含同名 .env。注意妥善保护病历数据和密码。"
