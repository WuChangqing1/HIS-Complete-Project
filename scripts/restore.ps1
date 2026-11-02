# Restore a SQL dump into the currently configured HIS database. Explicit opt-in required.
param([Parameter(Mandatory=$true)][string]$SqlFile,[switch]$IUnderstandThisOverwritesData)
$ErrorActionPreference = 'Stop'
if (-not $IUnderstandThisOverwritesData) { throw '恢复会修改现有数据。请同时传入 -IUnderstandThisOverwritesData。' }
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'windows\common.ps1')
$toolPaths = Get-ProjectToolPaths $root
if (-not $toolPaths) { throw '缺少工具路径配置；请先运行 start-his.bat。' }
$mysql = Join-Path $toolPaths.MySQL 'bin\mysql.exe'
$client = Join-Path $root '.runtime\config\mysql-client.ini'
$path = (Resolve-Path -LiteralPath $SqlFile -ErrorAction Stop).Path
if (-not (Test-Path $mysql) -or -not (Test-Path $client)) { throw '尚未部署 HIS 及数据库。' }
Write-Host "正在恢复 SQL：$path" -ForegroundColor Yellow
$p = Start-Process -FilePath $mysql -WorkingDirectory $root -ArgumentList @( "--defaults-extra-file=`"$client`"",'--database=his','--default-character-set=utf8mb4') -RedirectStandardInput $path -PassThru -Wait -NoNewWindow
if ($p.ExitCode -ne 0) { throw "恢复失败，退出代码 $($p.ExitCode)。请保留原始备份并检查数据库状态。" }
Write-Host 'SQL 导入已完成。请运行 check-his.bat 和业务回归测试。'
