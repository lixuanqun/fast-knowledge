# Fast Knowledge — 生产构建：分别打包后端 Jar 与前端静态资源
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")

$JdkHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot" }
$env:JAVA_HOME = $JdkHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host ""
Write-Host "Fast Knowledge — 构建" -ForegroundColor Cyan
Write-Host ""

Write-Host "[1/2] 后端 mvn package ..." -ForegroundColor Green
Push-Location "$Root\backend"
mvn package -DskipTests
Pop-Location

Write-Host ""
Write-Host "[2/2] 前端 npm run build ..." -ForegroundColor Green
Push-Location "$Root\frontend"
if (-not (Test-Path node_modules)) { npm install }
npm run build
Pop-Location

Write-Host ""
Write-Host "构建完成：" -ForegroundColor Green
Write-Host "  后端 Jar   backend\target\fast-knowledge-backend-*.jar"
Write-Host "  前端静态   frontend\dist\"
Write-Host ""
Write-Host "部署方式见 deploy/ 目录（Nginx 或单 Jar：npm run build:jar）"
Write-Host ""
