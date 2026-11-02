[CmdletBinding()]
param([switch]$KeepDatabase)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'windows\common.ps1')
$root = $PSScriptRoot
$run = Join-Path $root '.runtime\run'
$app = Get-RecordedProcess (Join-Path $run 'java.pid') 'java'
if ($app) {
    Stop-Process -Id $app.Id -Force
    Write-Step "已停止 HIS 应用进程 PID=$($app.Id)"
}
Remove-Item (Join-Path $run 'java.pid') -Force -ErrorAction SilentlyContinue
if (-not $KeepDatabase) {
    $mysqld = Get-RecordedProcess (Join-Path $run 'mysql.pid') 'mysqld'
    if ($mysqld) {
        $toolPaths = Get-ProjectToolPaths $root
        if (-not $toolPaths) { throw '缺少工具路径清单；请运行 check-his.bat。' }
        $mysqlAdmin = Join-Path $toolPaths.MySQL 'bin\mysqladmin.exe'
        $config = Join-Path $root '.runtime\config\mysql-root.ini'
        if (-not (Test-Path $config)) {
            if (-not (Test-Path (Join-Path $root '.env'))) { throw '缺少 .env，无法安全停止 MySQL。' }
            $envValues = Read-DotEnv (Join-Path $root '.env')
            $port = if ($envValues.ContainsKey('DB_PORT')) { $envValues['DB_PORT'] } else { '33306' }
            $ini = "[client]`nprotocol=tcp`nhost=127.0.0.1`nport=$port`nuser=root`npassword=$($envValues['MYSQL_ROOT_PASSWORD'])`n"
            Write-Utf8 $config $ini
        }
        & $mysqlAdmin "--defaults-extra-file=$config" shutdown
        if ($LASTEXITCODE -ne 0) { throw 'MySQL 正常关闭失败；未强制终止数据库进程。请查看日志。' }
        if (Test-Path $config) { Remove-Item -LiteralPath $config -Force }
        Write-Step '已安全关闭 HIS 专用 MySQL。数据库文件仍保留。'
    }
    Remove-Item (Join-Path $run 'mysql.pid') -Force -ErrorAction SilentlyContinue
}
Write-Host '完成。不会删除 .env、数据库文件或构建产物。'
