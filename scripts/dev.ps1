# Fast Knowledge — 本地开发：Docker 依赖 + server + web
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$DockerDir = Join-Path $Root "docker"

$JdkHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot" }
if (-not (Test-Path $JdkHome)) {
    Write-Warning "未找到 JDK 21，请设置 JAVA_HOME 后重试。"
}

Write-Host ""
Write-Host "Fast Knowledge — 启动开发环境" -ForegroundColor Cyan
Write-Host "  根目录: $Root"
Write-Host ""

Write-Host "[docker] 检查并启动 PostgreSQL + Redis + MinIO ..." -ForegroundColor Yellow
Push-Location $DockerDir
try {
    docker compose up -d postgres redis minio minio-init
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose 启动失败，请确认 Docker Desktop 已运行。"
    }
} finally {
    Pop-Location
}

$serverCmd = @"
Set-Location '$Root'
`$env:JAVA_HOME='$JdkHome'
`$env:Path="`$env:JAVA_HOME\bin;`$env:Path"
Write-Host '[server] profile=bundle（PostgreSQL + Redis + MinIO）' -ForegroundColor Green
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
Write-Host "  MinIO 控制台 http://localhost:9001 （minioadmin / minioadmin）"
Write-Host ""
