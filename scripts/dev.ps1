# Fast Knowledge — 本地开发：同时启动后端与前端
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")

$JdkHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot" }
if (-not (Test-Path $JdkHome)) {
    Write-Warning "未找到 JDK 21，请设置 JAVA_HOME 后重试。"
}

Write-Host ""
Write-Host "Fast Knowledge — 启动开发环境" -ForegroundColor Cyan
Write-Host "  根目录: $Root"
Write-Host ""

$backendCmd = @"
Set-Location '$Root\backend'
`$env:JAVA_HOME='$JdkHome'
`$env:Path="`$env:JAVA_HOME\bin;`$env:Path"
Write-Host '[backend] mvn spring-boot:run' -ForegroundColor Green
mvn spring-boot:run
"@

$frontendCmd = @"
Set-Location '$Root\frontend'
if (-not (Test-Path node_modules)) {
    Write-Host '[frontend] npm install ...' -ForegroundColor Yellow
    npm install
}
Write-Host '[frontend] npm run dev' -ForegroundColor Green
npm run dev
"@

Start-Process powershell -ArgumentList "-NoExit", "-Command", $backendCmd
Start-Sleep -Seconds 2
Start-Process powershell -ArgumentList "-NoExit", "-Command", $frontendCmd

Write-Host "已在新窗口启动：" -ForegroundColor Green
Write-Host "  后端 API   http://localhost:8088/api"
Write-Host "  前端页面   http://localhost:5174"
Write-Host "  Swagger    http://localhost:8088/api/swagger-ui.html"
Write-Host ""
