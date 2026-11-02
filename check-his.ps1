$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'windows\common.ps1')
$project = $PSScriptRoot
$envFile = Join-Path $project '.env'
if (-not (Test-Path $envFile)) { throw '尚未部署：找不到 .env。请双击 start-his.bat。' }
$settings = Read-DotEnv $envFile
$port = if ($settings.ContainsKey('WEB_PORT')) { [int]$settings['WEB_PORT'] } else { 8088 }
$databasePort = if ($settings.ContainsKey('DB_PORT')) { [int]$settings['DB_PORT'] } else { 33306 }
Write-Host 'HIS Windows 11 本机诊断（不显示密码）' -ForegroundColor Cyan
$selected = Get-ProjectToolPaths $project
if ($selected) {
    Write-Host "HIS 专用运行 JDK: $($selected.Jdk)"
    Write-Host "HIS 专用 Maven: $($selected.Maven)"
    Write-Host "HIS 专用 Node: $($selected.Node)"
    Write-Host "HIS 专用 MySQL: $($selected.MySQL)"
} else { Write-Warning '尚未生成工具路径清单。' }
foreach ($key in @('JAVA_HOME','MAVEN_HOME','NODE_HOME','MYSQL_HOME')) {
    $value = [Environment]::GetEnvironmentVariable($key,'User')
    Write-Host "$key = $value"
}
Write-Host "数据库端口 $databasePort TCP: $(Test-TcpPort $databasePort)"
Write-Host "网页端口 $port TCP: $(Test-TcpPort $port)"
foreach ($name in @('mysql','java')) {
    $pidFile = Join-Path $project ".runtime\run\$name.pid"
    $p = Get-RecordedProcess $pidFile $(if ($name -eq 'mysql') {'mysqld'} else {'java'})
    Write-Host "记录的 $name 进程：$(if ($p) { 'PID='+$p.Id } else { '未运行' })"
}
try {
    $response = Invoke-WebRequest "http://127.0.0.1:$port/actuator/health" -UseBasicParsing -TimeoutSec 5
    Write-Host "后端健康检查: HTTP $($response.StatusCode) $($response.Content)"
} catch { Write-Warning "后端健康检查失败：$($_.Exception.Message)" }
try {
    $front = Invoke-WebRequest "http://127.0.0.1:$port/" -UseBasicParsing -TimeoutSec 5
    Write-Host "前端首页：HTTP $($front.StatusCode)"
} catch { Write-Warning "前端访问失败：$($_.Exception.Message)" }
$logPath = Join-Path $project '.runtime\logs'
foreach ($f in @('mysql-error.log','his-stdout.log','his-stderr.log')) {
    $file = Join-Path $logPath $f
    if (Test-Path $file) {
        Write-Host "`n[$f] 最近 12 行" -ForegroundColor Cyan
        Get-Content -LiteralPath $file -Tail 12
    }
}
