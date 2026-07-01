# Fast Knowledge — 单 Jar 部署：前端 build 嵌入 Spring Boot static/
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")

$JdkHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot" }
$env:JAVA_HOME = $JdkHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$StaticDir = Join-Path $Root "backend\src\main\resources\static"

Write-Host ""
Write-Host "Fast Knowledge — 单 Jar 构建" -ForegroundColor Cyan
Write-Host ""

Write-Host "[1/3] 前端 npm run build ..." -ForegroundColor Green
Push-Location "$Root\frontend"
if (-not (Test-Path node_modules)) { npm install }
npm run build
Pop-Location

Write-Host ""
Write-Host "[2/3] 拷贝 frontend/dist -> backend/.../static ..." -ForegroundColor Green
if (Test-Path $StaticDir) {
    Remove-Item -Recurse -Force $StaticDir
}
New-Item -ItemType Directory -Path $StaticDir -Force | Out-Null
Copy-Item -Path "$Root\frontend\dist\*" -Destination $StaticDir -Recurse -Force

Write-Host ""
Write-Host "[3/3] 后端 mvn package ..." -ForegroundColor Green
Push-Location "$Root\backend"
mvn package -DskipTests
Pop-Location

Write-Host ""
Write-Host "单 Jar 构建完成：" -ForegroundColor Green
Write-Host "  backend\target\fast-knowledge-backend-*.jar"
Write-Host ""
Write-Host "运行：java -jar backend\target\fast-knowledge-backend-*.jar --spring.profiles.active=prod,bundle"
Write-Host "访问：http://localhost:8088/ （页面 + /api 同端口）"
Write-Host ""
