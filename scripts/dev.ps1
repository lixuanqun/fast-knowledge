# Fast Knowledge — 本地开发：依赖服务检查 + server + web（无 Docker 依赖）
# 前置：本机或可达的开发库上运行 MySQL 5.7+（必需）、Redis（可选）、MinIO（文件上传需要）
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")

$JdkHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot" }
if (-not (Test-Path $JdkHome)) {
    Write-Warning "未找到 JDK 21，请设置 JAVA_HOME 后重试。"
}

function Test-Port {
    param([string]$Name, [int]$Port, [string]$Hint)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync("127.0.0.1", $Port)
        if ($task.Wait(800) -and $client.Connected) {
            Write-Host "  [OK]   $Name (127.0.0.1:$Port)" -ForegroundColor Green
            return $true
        }
    } catch {} finally { $client.Dispose() }
    Write-Host "  [MISS] $Name — $Hint" -ForegroundColor Yellow
    return $false
}

Write-Host ""
Write-Host "Fast Knowledge — 启动开发环境" -ForegroundColor Cyan
Write-Host "  根目录: $Root"
Write-Host ""
Write-Host "[deps] 检查本机依赖服务 ..." -ForegroundColor Yellow

$mysqlOk = Test-Port "MySQL 3306" 3306 "安装 MySQL 或在 .env 中把 DB_URL 指向开发库（必需，缺失时后端无法启动）"
$redisOk = Test-Port "Redis 6379" 6379 "可选：缺失时自动改用 CACHE_PROVIDER=caffeine"
$minioOk = Test-Port "MinIO 9000" 9000 "文件上传需要：本机安装 MinIO 或指向远端对象存储"

if (-not $mysqlOk) {
    Write-Warning "MySQL 不可用，后端将启动失败。可先只启动前端: cd web; npm run dev"
}

$serverCmd = @"
Set-Location '$Root'
`$env:JAVA_HOME='$JdkHome'
`$env:Path="`$env:JAVA_HOME\bin;`$env:Path"
if (-not `$env:CACHE_PROVIDER -and -not $redisOk) { `$env:CACHE_PROVIDER='caffeine' }
Write-Host '[server] profile=bundle（MySQL/Redis/MinIO 见上方检查结果）' -ForegroundColor Green
mvn -pl apps/server spring-boot:run '-Dspring-boot.run.profiles=bundle'
"@

$webCmd = @"
Set-Location '$Root\web'
if (-not (Test-Path node_modules)) {
    Write-Host '[web] npm install ...' -ForegroundColor Yellow
    npm install
}
Write-Host '[web] npm run dev' -ForegroundColor Green
npm run dev
"@

Start-Process powershell -ArgumentList "-NoExit", "-Command", $serverCmd
Start-Sleep -Seconds 2
Start-Process powershell -ArgumentList "-NoExit", "-Command", $webCmd

Write-Host "已在新窗口启动：" -ForegroundColor Green
Write-Host "  后端 API   http://localhost:8088/api"
Write-Host "  前端页面   http://localhost:5174"
Write-Host "  Swagger    http://localhost:8088/api/swagger-ui.html"
Write-Host ""
