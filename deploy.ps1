# HIS local Windows 11 installer/deployer. NO Docker, NO Nginx, NO existing MySQL installation required.
# One command: powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1
[CmdletBinding()]
param(
    [switch]$InstallOnly,
    [switch]$Rebuild,
    [switch]$SkipTests,
    [switch]$NoBrowser
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = $PSScriptRoot
Set-Location -LiteralPath $root
. (Join-Path $root 'windows\common.ps1')
. (Join-Path $root 'windows\install-tools.ps1')
$settings = Initialize-DotEnv $root
$tools = Ensure-WindowsPrerequisites $root
if ($InstallOnly) { Write-Step '仅安装环境：完成。重新双击 start-his.bat 可构建并启动项目。'; exit 0 }

$runtime = $tools.Runtime
$dataDir = Join-Path $runtime 'mysql-data'
$configDir = Join-Path $runtime 'config'
$runDir = Join-Path $runtime 'run'
$logDir = Join-Path $runtime 'logs'
$dbPort = [int]$settings['DB_PORT']
$webPort = [int]$settings['WEB_PORT']
$mysqlExe = Join-Path $tools.MySQL 'bin\mysql.exe'
$mysqldExe = Join-Path $tools.MySQL 'bin\mysqld.exe'
$mysqlIni = Join-Path $configDir 'mysql-server.ini'
$mysqlClientIni = Join-Path $configDir 'mysql-client.ini'
$bootstrapSql = Join-Path $configDir 'mysql-first-run.sql'
$bootstrapMarker = Join-Path $configDir 'database-provisioned.ok'
$mysqlPidPath = Join-Path $runDir 'mysql.pid'
$javaPidPath = Join-Path $runDir 'java.pid'

function MySqlPath([string]$path) { return $path.Replace('\','/') }
function Quote-IniPath([string]$path) { return '"' + (MySqlPath $path) + '"' }
function Invoke-HisSql([string]$Query) {
    & $mysqlExe "--defaults-extra-file=$mysqlClientIni" '--database=his' '--batch' '--skip-column-names' '--execute' $Query
    return $LASTEXITCODE
}
function Test-HisDatabase {
    try {
        $result = & $mysqlExe "--defaults-extra-file=$mysqlClientIni" '--database=his' '--batch' '--skip-column-names' '--execute=SELECT 1;' 2>$null
        return ($LASTEXITCODE -eq 0 -and ($result -join '') -match '^1\s*$')
    } catch { return $false }
}
function Test-HisHttp {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$webPort/actuator/health" -UseBasicParsing -TimeoutSec 4
        $jsonText = if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { [string]$response.Content }; $body = $jsonText | ConvertFrom-Json
        return ($response.StatusCode -eq 200 -and $body.status -eq 'UP')
    } catch { return $false }
}

Write-Step '初始化或连接 HIS 专用 MySQL（仅绑定 127.0.0.1）'
$serverIni = @"
[mysqld]
basedir=$(Quote-IniPath $tools.MySQL)
datadir=$(Quote-IniPath $dataDir)
port=$dbPort
bind-address=127.0.0.1
mysqlx=OFF
default-time-zone=+08:00
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
log-error=$(Quote-IniPath (Join-Path $logDir 'mysql-error.log'))
pid-file=$(Quote-IniPath (Join-Path $runDir 'mysql-internal.pid'))
"@
Write-Utf8 $mysqlIni $serverIni
$clientIni = @"
[client]
protocol=tcp
host=127.0.0.1
port=$dbPort
user=his
password=$($settings['DB_PASSWORD'])
default-character-set=utf8mb4
"@
Write-Utf8 $mysqlClientIni $clientIni
$firstRun = -not (Test-Path -LiteralPath (Join-Path $dataDir 'mysql'))
if ($firstRun) {
    if (Test-TcpPort $dbPort) { throw "MySQL 端口 $dbPort 已被占用，不能初始化独立数据库。可修改 .env 中 DB_PORT。" }
    if (Test-Path $dataDir) {
        $leftover = @(Get-ChildItem -LiteralPath $dataDir -Force)
        if ($leftover.Count -gt 0) { throw "数据库目录 $dataDir 存在不完整数据，已保护现场，请检查日志后处理；脚本不会自动删除数据。" }
    }
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    $initIni = @"
[mysqld]
basedir=$(Quote-IniPath $tools.MySQL)
datadir=$(Quote-IniPath $dataDir)
"@
    $initConfig = Join-Path $configDir 'mysql-init.ini'
    Write-Utf8 $initConfig $initIni
    & $mysqldExe "--defaults-file=$initConfig" '--initialize-insecure' '--console'
    Assert-ExitCode 'MySQL 数据目录初始化'
    Remove-Item $initConfig -Force
}
if (-not (Test-Path $bootstrapMarker)) {
    # A first-start SQL file runs *before* connections are accepted. No network-exposed blank root password.
    # Values are generated from a hex-only random source, preventing SQL injection from the credentials.
    $dbPassword = $settings['DB_PASSWORD']
    $rootPassword = $settings['MYSQL_ROOT_PASSWORD']
    $sql = @"
CREATE DATABASE IF NOT EXISTS his CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'his'@'127.0.0.1' IDENTIFIED BY '$dbPassword';
ALTER USER 'his'@'127.0.0.1' IDENTIFIED BY '$dbPassword';
GRANT ALL PRIVILEGES ON his.* TO 'his'@'127.0.0.1';
CREATE USER IF NOT EXISTS 'his'@'localhost' IDENTIFIED BY '$dbPassword';
ALTER USER 'his'@'localhost' IDENTIFIED BY '$dbPassword';
GRANT ALL PRIVILEGES ON his.* TO 'his'@'localhost';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';
FLUSH PRIVILEGES;
"@
    Write-Utf8 $bootstrapSql $sql
}
$runningDb = Get-RecordedProcess $mysqlPidPath 'mysqld'
if ($runningDb) {
    if (-not (Wait-TcpPort $dbPort 8)) { throw '已记录的 MySQL 进程存在，但端口未就绪。请查看 mysql-error.log。' }
} else {
    if (Test-TcpPort $dbPort) { throw "端口 $dbPort 被其他程序占用。脚本不会连接或修改陌生的 MySQL 数据库。" }
    $arguments = @("--defaults-file=`"$mysqlIni`"")
    if (-not (Test-Path $bootstrapMarker)) { $arguments += "--init-file=`"$bootstrapSql`"" }
    $dbProcess = Start-Process -FilePath $mysqldExe -ArgumentList $arguments -WorkingDirectory $root -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDir 'mysql-stdout.log') -RedirectStandardError (Join-Path $logDir 'mysql-stderr.log')
    Save-Pid $mysqlPidPath $dbProcess.Id
    if (-not (Wait-TcpPort $dbPort 80)) { throw "MySQL 启动超时，请查看 $logDir\mysql-error.log" }
}
$authenticated = $false
for ($attempt = 0; $attempt -lt 15; $attempt++) {
    if (Test-HisDatabase) { $authenticated = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $authenticated) { throw "MySQL 已启动但 HIS 用户认证/数据库连接失败，请查看 $logDir\mysql-error.log。不要删除已有数据。" }
if (-not (Test-Path $bootstrapMarker)) {
    Write-Utf8 $bootstrapMarker 'Database initialized by local Windows installer. Do not delete.'
    if (Test-Path $bootstrapSql) { Remove-Item -LiteralPath $bootstrapSql -Force }
    Write-Step '本地 MySQL 已创建 his 数据库与独立应用账号。'
}

Write-Step '构建 Vue2 前端并复制到 Spring Boot 静态资源目录'
$frontend = Join-Path $root 'frontend'
$backend = Join-Path $root 'backend'
$npmExe = Join-Path $tools.Node 'npm.cmd'
$mavenExe = Join-Path $tools.Maven 'bin\mvn.cmd'
$staticDir = Join-Path $backend 'src\main\resources\static'
$jar = Join-Path $backend 'target\his-1.0.0.jar'
$mustBuild = $Rebuild -or -not (Test-Path $jar)
if ($Rebuild) {
    # On Windows an executing JAR is file-locked. Stop only our recorded process before Maven clean.
    $runningJava = Get-RecordedProcess $javaPidPath 'java'
    if ($runningJava) { Stop-Process -Id $runningJava.Id -Force; Start-Sleep -Seconds 2 }
    if (Test-Path $javaPidPath) { Remove-Item $javaPidPath -Force }
}
if ($mustBuild) {
    Push-Location $frontend
    try {
        if (Test-Path 'package-lock.json') { & $npmExe ci '--no-audit' '--no-fund' }
        else { & $npmExe install '--no-audit' '--no-fund' }
        Assert-ExitCode 'npm 依赖安装'
        & $npmExe run build
        Assert-ExitCode '前端编译'
    } finally { Pop-Location }
    $dist = Join-Path $frontend 'dist'
    if (-not (Test-Path (Join-Path $dist 'index.html'))) { throw '前端构建缺少 dist/index.html。' }
    if (Test-Path $staticDir) { Remove-Item -LiteralPath $staticDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $staticDir | Out-Null
    Copy-Item -Path (Join-Path $dist '*') -Destination $staticDir -Recurse -Force
    Push-Location $backend
    try {
        if ($SkipTests) { & $mavenExe '-B' '-DskipTests' 'clean' 'package' }
        else { & $mavenExe '-B' 'clean' 'verify' }
        Assert-ExitCode '后端编译 / JUnit 测试'
    } finally { Pop-Location }
    if (-not (Test-Path $jar)) { throw '构建后未找到 backend/target/his-1.0.0.jar。' }
} else {
    Write-Host '已存在完整 JAR，跳过重新编译；需更新代码时使用 deploy.ps1 -Rebuild。'
}

Write-Step "启动 Spring Boot，并在 http://127.0.0.1:$webPort 检查健康状态"
$env:DB_URL = "jdbc:mysql://127.0.0.1:$dbPort/his?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USER = 'his'
$env:DB_PASSWORD = $settings['DB_PASSWORD']
$env:BOOTSTRAP_ADMIN = 'admin'
$env:BOOTSTRAP_PASSWORD = $settings['BOOTSTRAP_PASSWORD']
$env:DEMO_MODE = if ($settings.ContainsKey('DEMO_MODE')) { $settings['DEMO_MODE'] } else { 'true' }
$env:DEMO_PASSWORD = $settings['DEMO_PASSWORD']
$env:DATA_ENCRYPTION_KEY = $settings['DATA_ENCRYPTION_KEY']
$env:SERVER_PORT = [string]$webPort
$env:GUEST_ACCESS = if ($settings.ContainsKey('GUEST_ACCESS')) { $settings['GUEST_ACCESS'] } else { 'false' }
$env:SERVER_ADDRESS = '127.0.0.1'
$oldJava = Get-RecordedProcess $javaPidPath 'java'
if ($oldJava) {
    if (-not (Test-HisHttp)) { throw '已记录的 HIS Java 进程不健康，请运行 stop-his.bat 然后检查日志。' }
} else {
    if (Test-TcpPort $webPort) { throw "访问端口 $webPort 已占用；脚本不会停止其他程序，请调整 .env 的 WEB_PORT。" }
    $javaExe = Join-Path $tools.Jdk 'bin\java.exe'
    $appProcess = Start-Process -FilePath $javaExe -ArgumentList @('-Duser.timezone=Asia/Shanghai','-jar',"`"$jar`"") -WorkingDirectory $root -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDir 'his-stdout.log') -RedirectStandardError (Join-Path $logDir 'his-stderr.log')
    Save-Pid $javaPidPath $appProcess.Id
}
$ready = $false
for ($i=0; $i -lt 75; $i++) {
    if (Test-HisHttp) { $ready = $true; break }
    $app = Get-RecordedProcess $javaPidPath 'java'
    if (-not $app) { break }
    Start-Sleep -Seconds 2
}
if (-not $ready) { throw "Spring Boot 健康检查未通过。查看 $logDir\his-stdout.log 与 his-stderr.log。" }
try {
    $index = Invoke-WebRequest -Uri "http://127.0.0.1:$webPort/" -UseBasicParsing -TimeoutSec 6
    $indexText = if ($index.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($index.Content) } else { [string]$index.Content }
    if ($index.StatusCode -ne 200 -or $indexText -notmatch 'id="app"') { throw '未返回 Vue 前端页面。' }
} catch { throw "后端健康但前端页面验证失败：$($_.Exception.Message)" }
Write-Host "`n==============================================" -ForegroundColor Green
Write-Host "  HIS Windows 11 本机部署成功" -ForegroundColor Green
Write-Host "  地址：http://127.0.0.1:$webPort/" -ForegroundColor Green
Write-Host "  管理员：admin" -ForegroundColor Green
Write-Host "  密码：见 .env 中 BOOTSTRAP_PASSWORD" -ForegroundColor Green
Write-Host "  演示密码：见 .env 中 DEMO_PASSWORD" -ForegroundColor Green
Write-Host '  停止服务：stop-his.bat' -ForegroundColor Green
Write-Host '  重新部署：start-his.bat' -ForegroundColor Green
Write-Host '==============================================`n' -ForegroundColor Green
if (-not $NoBrowser) { Start-Process "http://127.0.0.1:$webPort/" }
