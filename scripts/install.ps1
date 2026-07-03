# Fast Knowledge — Windows 全栈安装（Docker Compose）
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$DockerDir = Join-Path $Root "docker"
$EnvFile = Join-Path $DockerDir ".env"
$WeakJwt = "fast-knowledge-jwt-secret-change-in-production-32chars"

function New-RandomJwtSecret {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [BitConverter]::ToString($bytes).Replace("-", "").ToLower()
}

function Set-EnvVar {
    param([string]$Key, [string]$Value, [string]$File)
    $lines = @()
    $found = $false
    if (Test-Path $File) {
        foreach ($line in Get-Content $File) {
            if ($line -match "^$([regex]::Escape($Key))=") {
                $lines += "$Key=$Value"
                $found = $true
            } else {
                $lines += $line
            }
        }
    }
    if (-not $found) {
        $lines += "$Key=$Value"
    }
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllLines($File, $lines, $utf8NoBom)
}

function Get-EnvVarValue {
    param([string]$Key, [string]$File)
    if (-not (Test-Path $File)) { return "" }
    foreach ($line in Get-Content $File) {
        if ($line -match "^$([regex]::Escape($Key))=(.*)$") {
            return $Matches[1]
        }
    }
    return ""
}

Write-Host ""
Write-Host "==> Fast Knowledge 全栈安装（Docker Compose）" -ForegroundColor Cyan
Write-Host "    将启动 PostgreSQL、Redis、MinIO 与应用容器"
Write-Host ""

if (-not (Test-Path $EnvFile)) {
    $example = Join-Path $DockerDir ".env.example"
    if (-not (Test-Path $example)) {
        $example = Join-Path $Root ".env.example"
    }
    if (-not (Test-Path $example)) {
        throw "未找到 .env.example，请从仓库根目录复制后重试。"
    }
    Copy-Item $example $EnvFile
    Write-Host "    已从 .env.example 创建 docker/.env" -ForegroundColor Yellow
}

$currentJwt = Get-EnvVarValue -Key "JWT_SECRET" -File $EnvFile
if ([string]::IsNullOrWhiteSpace($currentJwt) -or $currentJwt -eq $WeakJwt) {
    $secret = New-RandomJwtSecret
    Set-EnvVar -Key "JWT_SECRET" -Value $secret -File $EnvFile
    Write-Host "    已自动生成 JWT_SECRET（≥32 字符）" -ForegroundColor Yellow
}

$currentLlmKey = Get-EnvVarValue -Key "LLM_API_KEY" -File $EnvFile
if ([string]::IsNullOrWhiteSpace($currentLlmKey)) {
    Set-EnvVar -Key "LLM_API_KEY" -Value "ollama" -File $EnvFile
    Write-Host "    已设置 LLM_API_KEY=ollama（请按实际 LLM 提供商修改 docker/.env）" -ForegroundColor Yellow
}

Push-Location $DockerDir
try {
    docker compose -f docker-compose.full.yml up -d --build
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose 启动失败，请确认 Docker Desktop 已运行且 docker/.env 配置正确。"
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "安装完成。访问 http://localhost:8088" -ForegroundColor Green
Write-Host "默认账号 admin / admin123（首次登录须完成设置向导）"
Write-Host "请确认 docker/.env 中 LLM_API_KEY 与 LLM_BASE_URL 符合你的 LLM 提供商"
