# Shared functions for the Windows 11 native deployment. PowerShell 5.1 compatible.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function Write-Step([string]$Message) { Write-Host "`n[HIS] $Message" -ForegroundColor Cyan }
function Write-Utf8([string]$Path, [string]$Content) {
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    [IO.File]::WriteAllText($Path, $Content, (New-Object Text.UTF8Encoding($false)))
}
function New-Secret {
    $buffer = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($buffer)).Replace('-','').ToLowerInvariant()
}
function Read-DotEnv([string]$FilePath) {
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines($FilePath)) {
        $trim = $line.Trim()
        if (-not $trim -or $trim.StartsWith('#')) { continue }
        $parts = $line -split '=', 2
        if ($parts.Length -eq 2) { $values[$parts[0].Trim()] = $parts[1].Trim() }
    }
    return $values
}
function Initialize-DotEnv([string]$ProjectRoot) {
    $destination = Join-Path $ProjectRoot '.env'
    if (-not (Test-Path -LiteralPath $destination)) {
        $template = [IO.File]::ReadAllText((Join-Path $ProjectRoot '.env.example'))
        foreach ($key in @('DB_PASSWORD','MYSQL_ROOT_PASSWORD','BOOTSTRAP_PASSWORD','DEMO_PASSWORD','DATA_ENCRYPTION_KEY')) {
            $template = $template.Replace("${key}=REPLACE_ON_FIRST_DEPLOY", "${key}=$(New-Secret)")
        }
        Write-Utf8 $destination $template
        Write-Step '已创建 .env，本地密码和密钥不写入 Windows 全局环境变量。'
    }
    $settings = Read-DotEnv $destination
    foreach ($key in @('DB_PASSWORD','MYSQL_ROOT_PASSWORD','BOOTSTRAP_PASSWORD','DEMO_PASSWORD','DATA_ENCRYPTION_KEY')) {
        if (-not $settings.ContainsKey($key) -or -not $settings[$key] -or $settings[$key] -match 'REPLACE_ON_FIRST_DEPLOY') {
            throw ".env 的 $key 未配置。请恢复 .env.example 模板并重新部署（保留已有数据库时不要重置密码）。"
        }
        $minimumLength = 16
        if ($settings.ContainsKey('DEMO_MODE') -and $settings['DEMO_MODE'] -eq 'true' -and $key -in @('BOOTSTRAP_PASSWORD','DEMO_PASSWORD')) { $minimumLength = 6 }
        if ($settings[$key] -notmatch ('^[0-9A-Za-z_-]{' + $minimumLength + ',128}$')) {
            throw ".env 的 $key 只能使用 $minimumLength-128 位英文字母、数字、下划线或连字符。"
        }
    }
    if (-not $settings.ContainsKey('WEB_PORT')) { $settings['WEB_PORT'] = '8088' }
    if (-not $settings.ContainsKey('DB_PORT')) { $settings['DB_PORT'] = '33306' }
    foreach ($portName in @('WEB_PORT','DB_PORT')) {
        $portNumber = 0
        if (-not [int]::TryParse($settings[$portName], [ref]$portNumber) -or $portNumber -lt 1025 -or $portNumber -gt 65535) {
            throw ".env 的 $portName 必须为 1025-65535 的端口号。"
        }
    }
    if ($settings['WEB_PORT'] -eq $settings['DB_PORT']) { throw 'WEB_PORT 与 DB_PORT 不能相同。' }
    return $settings
}
function Test-TcpPort([int]$Port) {
    $socket = New-Object Net.Sockets.TcpClient
    try {
        $ar = $socket.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $ar.AsyncWaitHandle.WaitOne(400)) { return $false }
        $socket.EndConnect($ar)
        return $true
    } catch { return $false } finally { $socket.Dispose() }
}
function Wait-TcpPort([int]$Port, [int]$TimeoutSeconds) {
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    while ($stopwatch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        if (Test-TcpPort $Port) { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}
function Assert-ExitCode([string]$Task) {
    if ($LASTEXITCODE -ne 0) { throw "$Task 失败，退出代码 $LASTEXITCODE。请查看 .runtime\logs。" }
}
function Download-Verified([string]$Uri, [string]$Target, [string]$ExpectedHash, [string]$HashAlgorithm) {
    if (Test-Path -LiteralPath $Target) {
        $actual = (Get-FileHash -LiteralPath $Target -Algorithm $HashAlgorithm).Hash
        if ($actual -ieq $ExpectedHash) { Write-Host "缓存有效：$(Split-Path -Leaf $Target)"; return }
        Remove-Item -LiteralPath $Target -Force
    }
    Write-Step "正在下载 $(Split-Path -Leaf $Target)（首次运行需要联网）"
    $partial = "$Target.download"
    if (Test-Path $partial) { Remove-Item $partial -Force }
    try {
        Invoke-WebRequest -Uri $Uri -UseBasicParsing -OutFile $partial -MaximumRedirection 10 -TimeoutSec 1800
        $actual = (Get-FileHash -LiteralPath $partial -Algorithm $HashAlgorithm).Hash
        if ($actual -ine $ExpectedHash) { throw "下载文件校验失败：$Uri；期望 $ExpectedHash，实际 $actual" }
        Move-Item -LiteralPath $partial -Destination $Target -Force
    } catch {
        if (Test-Path $partial) { Remove-Item $partial -Force }
        throw
    }
}
function Expand-Portable([string]$Archive, [string]$Destination, [string]$ExpectedExecutable) {
    if (Test-Path (Join-Path $Destination $ExpectedExecutable)) { return }
    $stage = "$Destination.tmp"
    if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    try {
        Write-Step "解压 $(Split-Path -Leaf $Archive)"
        Expand-Archive -LiteralPath $Archive -DestinationPath $stage -Force
        $entries = @(Get-ChildItem -LiteralPath $stage -Force)
        if ($entries.Count -eq 1 -and $entries[0].PSIsContainer) { $unpacked = $entries[0].FullName }
        else { $unpacked = $stage }
        if (-not (Test-Path (Join-Path $unpacked $ExpectedExecutable))) { throw "归档文件结构异常，未发现 $ExpectedExecutable" }
        if (Test-Path $Destination) { Remove-Item $Destination -Recurse -Force }
        if ($unpacked -eq $stage) { Move-Item -LiteralPath $stage -Destination $Destination }
        else { Move-Item -LiteralPath $unpacked -Destination $Destination; Remove-Item $stage -Recurse -Force }
    } finally { if (Test-Path $stage) { Remove-Item $stage -Recurse -Force } }
}
function Save-Pid([string]$FilePath, [int]$ProcessId) { Write-Utf8 $FilePath ([string]$ProcessId) }
function Get-ProjectToolPaths([string]$ProjectRoot) {
    $file = Join-Path $ProjectRoot '.runtime\config\tool-paths.json'
    if (-not (Test-Path -LiteralPath $file)) { return $null }
    try { return (Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json) }
    catch { return $null }
}
function Get-RecordedProcess([string]$FilePath, [string]$ExpectedName) {
    if (-not (Test-Path $FilePath)) { return $null }
    $number = 0
    if (-not [int]::TryParse(([IO.File]::ReadAllText($FilePath)).Trim(), [ref]$number)) { return $null }
    $p = Get-Process -Id $number -ErrorAction SilentlyContinue
    if (-not $p -or $p.ProcessName -ine $ExpectedName) { return $null }
    # Protect unrelated Windows processes against PID recycling / stale PID files.
    $projectRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $FilePath))
    $toolPaths = Get-ProjectToolPaths $projectRoot
    if (-not $toolPaths) { return $null }
    $expectedBinary = if ($ExpectedName -eq 'java') {
        Join-Path $toolPaths.Jdk 'bin\java.exe'
    } elseif ($ExpectedName -eq 'mysqld') {
        Join-Path $toolPaths.MySQL 'bin\mysqld.exe'
    } else { return $null }
    try {
        $allowedExecutables = @($expectedBinary)
        $actualExe = [IO.Path]::GetFullPath($p.Path)
        $matchesSelected = @($allowedExecutables | Where-Object { [IO.Path]::GetFullPath($_) -ieq $actualExe })
        if ($matchesSelected.Count -eq 0) { return $null }
        $details = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId=$number" -ErrorAction Stop
        if (-not $details -or -not $details.CommandLine) { return $null }
        if ($ExpectedName -eq 'java' -and $details.CommandLine -notmatch 'his-1\.0\.0\.jar') { return $null }
        if ($ExpectedName -eq 'mysqld' -and $details.CommandLine -notmatch 'mysql-server\.ini') { return $null }
        return $p
    } catch { return $null }
}

