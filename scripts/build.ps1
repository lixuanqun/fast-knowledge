# Fast Knowledge — 生产构建：Maven 统一打包（-Pbundle 嵌入 web 静态资源）
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")

$JdkHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot" }
$env:JAVA_HOME = $JdkHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host ""
Write-Host "Fast Knowledge — 构建" -ForegroundColor Cyan
Write-Host ""

Push-Location $Root
mvn -pl apps/server -am clean package -DskipTests -Pbundle
Pop-Location

Write-Host ""
Write-Host "构建完成：" -ForegroundColor Green
Write-Host "  单 Jar（含前端）  apps\server\target\fast-knowledge-server-*.jar"
Write-Host ""
Write-Host "运行：java -jar apps\server\target\fast-knowledge-server-*.jar --spring.profiles.active=prod,bundle"
Write-Host "部署方式见 docker/ 目录"
Write-Host ""
