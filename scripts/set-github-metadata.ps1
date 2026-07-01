#Requires -Version 5.1
<#
.SYNOPSIS
  为 GitHub 仓库设置描述、Topics（关键词）并推送版本标签。

.DESCRIPTION
  依赖 GitHub CLI（gh）。首次使用请先执行：gh auth login

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/set-github-metadata.ps1
#>

$ErrorActionPreference = "Stop"

$Repo = "lixuanqun/fast-knowledge"
$Description = "面向中小企业的开源私有化知识库：单实例部署、数据本地不出网，支持混合检索、RAG 问答与智能对话。"
$Topics = @(
    "knowledge-base",
    "knowledge-management",
    "document-management",
    "rag",
    "semantic-search",
    "hybrid-search",
    "self-hosted",
    "private-deployment",
    "on-premise",
    "enterprise-search",
    "spring-boot",
    "java",
    "vue3",
    "typescript",
    "langchain4j",
    "lucene",
    "docker",
    "ollama",
    "open-source",
    "agpl-3.0"
)

function Assert-Gh {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw "未找到 gh 命令。请先安装 GitHub CLI：https://cli.github.com/"
    }
    gh auth status 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "gh 未登录。请先执行：gh auth login"
    }
}

Write-Host ">> 检查 GitHub CLI 登录状态..."
Assert-Gh

Write-Host ">> 设置仓库描述..."
gh repo edit $Repo --description $Description

Write-Host ">> 设置 Topics（关键词）..."
$topicArgs = $Topics | ForEach-Object { "--add-topic"; $_ }
gh repo edit $Repo @topicArgs

Write-Host ">> 完成。仓库： https://github.com/$Repo"
Write-Host "   Topics: $($Topics -join ', ')"
