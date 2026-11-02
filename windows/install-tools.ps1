# Download vetted portable Windows x64 tools and configure persistent per-user environment variables.
# Called from deploy.ps1; does not require Docker or machine-wide PATH changes.
function Get-JsonData([string]$Uri) {
    Write-Host "查询官方版本元数据：$Uri"
    return Invoke-RestMethod -Uri $Uri -TimeoutSec 45
}
function Test-Jdk([string]$Home) {
    if (-not $Home) { return $false }
    $javac = Join-Path $Home 'bin\javac.exe'
    $java = Join-Path $Home 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javac) -or -not (Test-Path -LiteralPath $java)) { return $false }
    try {
        $version = (& $javac -version 2>&1 | Out-String).Trim()
        return $version -match '^javac 21\.'
    } catch { return $false }
}
function Test-Node22([string]$Home) {
    if (-not $Home) { return $false }
    $exe = Join-Path $Home 'node.exe'
    $npm = Join-Path $Home 'npm.cmd'
    if (-not (Test-Path $exe) -or -not (Test-Path $npm)) { return $false }
    try { return ((& $exe --version) -match '^v22\.') } catch { return $false }
}
function Test-Maven39([string]$Home) {
    if (-not $Home) { return $false }
    return (Test-Path (Join-Path $Home 'bin\mvn.cmd'))
}
function Find-ExecutableHome([string]$Command,[int]$ParentLevels) {
    $c = Get-Command $Command -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $c -or -not $c.Source) { return '' }
    $path = $c.Source
    for ($i=0; $i -lt $ParentLevels; $i++) { $path = Split-Path -Parent $path }
    return $path
}
function Register-UserVariableIfMissing([string]$Name,[string]$Value) {
    $machine = [Environment]::GetEnvironmentVariable($Name,'Machine')
    $user = [Environment]::GetEnvironmentVariable($Name,'User')
    if (-not $machine -and -not $user) {
        [Environment]::SetEnvironmentVariable($Name,$Value,'User')
        Write-Host "新增用户环境变量 $Name（不覆盖其他项目的配置）"
    } else {
        Write-Host "$Name 已有持久化配置，保持原值不变。"
    }
}
function Ensure-WindowsPrerequisites([string]$ProjectRoot) {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT -or -not [Environment]::Is64BitOperatingSystem) {
        throw '仅支持 Windows 11 x64。'
    }
    $runtime = Join-Path $ProjectRoot '.runtime'
    $toolsDir = Join-Path $runtime 'tools'
    $cache = Join-Path $runtime 'cache'
    foreach ($folder in @($toolsDir,$cache,(Join-Path $runtime 'logs'),(Join-Path $runtime 'run'),(Join-Path $runtime 'config'))) {
        New-Item -ItemType Directory -Force -Path $folder | Out-Null
    }

    # Respect the computer's actual install paths; the folder name need not equal the installed version.
    $jdkHome = ''
    foreach ($candidate in @([Environment]::GetEnvironmentVariable('JAVA_HOME','Machine'),
            [Environment]::GetEnvironmentVariable('JAVA_HOME','User'),
            (Find-ExecutableHome 'javac.exe' 2),
            (Join-Path $toolsDir 'jdk21'))) {
        if (Test-Jdk $candidate) { $jdkHome = $candidate; break }
    }
    if (-not $jdkHome) {
        Write-Step '未检测到可复用的 JDK 21，下载项目专用 JDK 21（不修改系统默认 Java）'
        $jdkHome = Join-Path $toolsDir 'jdk21'
        $metadata = Get-JsonData 'https://api.adoptium.net/v3/assets/latest/21/hotspot?os=windows&architecture=x64&image_type=jdk&jvm_impl=hotspot&vendor=eclipse'
        $asset = @($metadata | Where-Object { $_.binary.package.link -and $_.binary.package.checksum }) | Select-Object -First 1
        if (-not $asset) { throw '无法获取 Adoptium JDK 21 官方校验元数据。' }
        $downloadFile = Join-Path $cache 'temurin-jdk21-windows-x64.zip'
        Download-Verified $asset.binary.package.link $downloadFile $asset.binary.package.checksum 'SHA256'
        Expand-Portable $downloadFile $jdkHome 'bin\javac.exe'
    }

    $nodeHome = ''
    foreach ($candidate in @((Find-ExecutableHome 'node.exe' 1),(Join-Path $toolsDir 'node22'))) {
        if (Test-Node22 $candidate) { $nodeHome = $candidate; break }
    }
    if (-not $nodeHome) {
        Write-Step '未检测到 Node.js 22，下载项目专用便携 Node.js'
        $nodeHome = Join-Path $toolsDir 'node22'
        $index = Get-JsonData 'https://nodejs.org/dist/index.json'
        $release = @($index | Where-Object { $_.version -match '^v22\.' }) | Select-Object -First 1
        if (-not $release) { throw 'Node.js 官方发布列表中未找到 v22.x。' }
        $version = $release.version
        $archiveName = "node-$version-win-x64.zip"
        $baseUrl = "https://nodejs.org/dist/$version"
        $shaText = (Invoke-WebRequest -Uri "$baseUrl/SHASUMS256.txt" -UseBasicParsing -TimeoutSec 45).Content
        $entry = @($shaText -split "`n" | Where-Object { $_ -match ([regex]::Escape($archiveName) + '$') }) | Select-Object -First 1
        $sha256 = ([regex]::Match($entry, '^[a-fA-F0-9]{64}')).Value
        if (-not $sha256) { throw "无法验证 Node.js 官方 SHA256：$archiveName" }
        $downloadFile = Join-Path $cache $archiveName
        Download-Verified "$baseUrl/$archiveName" $downloadFile $sha256 'SHA256'
        Expand-Portable $downloadFile $nodeHome 'node.exe'
    }

    $mavenHome = ''
    foreach ($candidate in @([Environment]::GetEnvironmentVariable('MAVEN_HOME','Machine'),
            [Environment]::GetEnvironmentVariable('MAVEN_HOME','User'),
            (Find-ExecutableHome 'mvn.cmd' 2),
            (Join-Path $toolsDir 'maven39'))) {
        if (Test-Maven39 $candidate) { $mavenHome = $candidate; break }
    }
    if (-not $mavenHome) {
        Write-Step '未检测到 Maven 3.9，下载项目专用 Maven'
        $mavenHome = Join-Path $toolsDir 'maven39'
        $version = '3.9.11'
        $archiveName = "apache-maven-$version-bin.zip"
        $baseUrl = "https://archive.apache.org/dist/maven/maven-3/$version/binaries"
        $shaText = (Invoke-WebRequest -Uri "$baseUrl/$archiveName.sha512" -UseBasicParsing -TimeoutSec 45).Content
        $sha512 = ([regex]::Match($shaText, '[a-fA-F0-9]{128}')).Value
        if (-not $sha512) { throw '无法取得 Maven 官方 SHA512 校验信息。' }
        $downloadFile = Join-Path $cache $archiveName
        Download-Verified "$baseUrl/$archiveName" $downloadFile $sha512 'SHA512'
        Expand-Portable $downloadFile $mavenHome 'bin\mvn.cmd'
    }

    # The reported mysql.exe 8.4.4 proves *only* a client exists, not an 8.0 server.
    # Avoid modifying a pre-existing 3306 service; use a separate MySQL 8.0 binary/data directory/port.
    $mysqlHome = Join-Path $toolsDir 'mysql80'
    if (-not (Test-Path (Join-Path $mysqlHome 'bin\mysqld.exe'))) {
        Write-Step '准备隔离的 MySQL 8.0.46 服务端（不替换系统 MySQL 8.4 客户端）'
        $archiveName = 'mysql-8.0.46-winx64.zip'
        $downloadFile = Join-Path $cache $archiveName
        Download-Verified "https://cdn.mysql.com/Downloads/MySQL-8.0/$archiveName" $downloadFile '28e9eda019d88eff4478d811ea2110b83f02a3966be157fe91cc55def3ab0d4d' 'SHA256'
        Expand-Portable $downloadFile $mysqlHome 'bin\mysqld.exe'
    }

    # Only the MySQL runtime might require installing a system-level prerequisite.
    $vcRegistry = Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64' -ErrorAction SilentlyContinue
    if (-not $vcRegistry -or $vcRegistry.Installed -ne 1) {
        Write-Step '缺少 VC++ x64 运行库；从微软下载签名安装包（可能弹出管理员授权）'
        $redistributable = Join-Path $cache 'vc_redist.x64.exe'
        if (-not (Test-Path $redistributable)) {
            Invoke-WebRequest -Uri 'https://aka.ms/vs/17/release/vc_redist.x64.exe' -UseBasicParsing -OutFile $redistributable -TimeoutSec 120
        }
        $sig = Get-AuthenticodeSignature -LiteralPath $redistributable
        if ($sig.Status -ne 'Valid' -or $sig.SignerCertificate.Subject -notmatch 'Microsoft') { throw 'VC++ 安装文件签名无法验证。' }
        $process = Start-Process -FilePath $redistributable -ArgumentList @('/install','/quiet','/norestart') -Verb RunAs -Wait -PassThru
        if ($process.ExitCode -notin @(0,1638,3010)) { throw "VC++ 安装失败，退出代码 $($process.ExitCode)" }
        if ($process.ExitCode -eq 3010) { throw 'VC++ 安装需要重启 Windows。请重启后再运行 start-his.bat。' }
    }

    $commandFoundBeforeSelection = @{
        java = [bool](Get-Command 'java.exe' -ErrorAction SilentlyContinue)
        maven = [bool](Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue)
        node = [bool](Get-Command 'node.exe' -ErrorAction SilentlyContinue)
        mysql = [bool](Get-Command 'mysql.exe' -ErrorAction SilentlyContinue)
    }
    $env:JAVA_HOME = $jdkHome
    $env:MAVEN_HOME = $mavenHome
    $env:M2_HOME = $mavenHome
    $env:NODE_HOME = $nodeHome
    $env:MYSQL_HOME = $mysqlHome
    # Session-only prioritization: do not change system/default JDK or global PATH ordering.
    $preferred = @((Join-Path $jdkHome 'bin'),(Join-Path $mavenHome 'bin'),$nodeHome,(Join-Path $mysqlHome 'bin'))
    $env:Path = (@($preferred) + @($env:Path -split ';' | Where-Object { $_ })) -join ';'
    # Only add missing user variables. Existing machine-level Java and Maven are untouched.
    Register-UserVariableIfMissing 'JAVA_HOME' $jdkHome
    Register-UserVariableIfMissing 'MAVEN_HOME' $mavenHome
    Register-UserVariableIfMissing 'NODE_HOME' $nodeHome
    $existingMySqlClient = Find-ExecutableHome 'mysql.exe' 2
    if (Test-Path (Join-Path $existingMySqlClient 'bin\mysql.exe')) {
        Register-UserVariableIfMissing 'MYSQL_HOME' $existingMySqlClient
    } else {
        Register-UserVariableIfMissing 'MYSQL_HOME' $mysqlHome
    }
    # Persist only *missing* command paths; never prepend and override existing tools.
    $userPath = [Environment]::GetEnvironmentVariable('Path','User')
    $machinePath = [Environment]::GetEnvironmentVariable('Path','Machine')
    $combinedPath = @($userPath -split ';') + @($machinePath -split ';')
    $addPaths = @()
    foreach ($entry in @((Join-Path $jdkHome 'bin'),(Join-Path $mavenHome 'bin'),$nodeHome,(Join-Path $mysqlHome 'bin'))) {
        if (-not (@($combinedPath | Where-Object { $_.TrimEnd('\') -ieq $entry.TrimEnd('\') }).Count)) {
            # Only if no globally resolvable command already exists; user PATH is not reordered.
            $commandKey = if ($entry -eq (Join-Path $jdkHome 'bin')) {'java'} elseif ($entry -eq (Join-Path $mavenHome 'bin')) {'maven'} elseif ($entry -eq $nodeHome) {'node'} else {'mysql'}
            if (-not $commandFoundBeforeSelection[$commandKey]) { $addPaths += $entry }
        }
    }
    if ($addPaths.Count -gt 0) {
        [Environment]::SetEnvironmentVariable('Path', ((@($userPath) + @($addPaths)) -join ';').TrimStart(';'),'User')
        Write-Host '仅补充缺失的用户 PATH 项，不修改已有顺序。'
    }
    $selected = @{ Jdk=$jdkHome; Node=$nodeHome; Maven=$mavenHome; MySQL=$mysqlHome; Runtime=$runtime }
    $selected | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $runtime 'config\tool-paths.json') -Encoding UTF8
    Write-Step '最终选择（当前项目进程环境，保留你的系统默认 Java21/Maven）'
    Write-Host "JDK: $jdkHome"
    Write-Host "Maven: $mavenHome"
    Write-Host "Node: $nodeHome"
    Write-Host "MySQL 私有服务: $mysqlHome"
    & (Join-Path $jdkHome 'bin\java.exe') -version
    & (Join-Path $nodeHome 'node.exe') --version
    & (Join-Path $mavenHome 'bin\mvn.cmd') -version
    & (Join-Path $mysqlHome 'bin\mysqld.exe') --version
    return $selected
}
