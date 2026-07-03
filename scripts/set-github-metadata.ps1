#Requires -Version 5.1
$ErrorActionPreference = "Stop"

$Repo = "lixuanqun/fast-knowledge"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Description = Get-Content -Path (Join-Path $Root ".github/description.txt") -Raw -Encoding UTF8
$Description = $Description.Trim()
$Topics = "knowledge-base,rag,retrieval-augmented-generation,langchain4j,pgvector,spring-boot,vue3,docker,self-hosted,private-deployment,on-premise,enterprise-search,semantic-search,hybrid-search,llm,llm-agnostic,java,typescript,open-source,chinese"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) not found. Install from https://cli.github.com/"
}

gh auth status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "gh is not authenticated. Run: gh auth login"
}

Write-Host "Setting repository description..."
gh repo edit $Repo --description $Description
if ($LASTEXITCODE -ne 0) {
    throw "Failed to set repository description"
}

$topicList = @($Topics.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($topicList.Count -gt 20) {
    throw "GitHub allows at most 20 topics; got $($topicList.Count)"
}

Write-Host "Replacing repository topics ($($topicList.Count))..."
$payload = @{ names = $topicList } | ConvertTo-Json -Compress
$payload | gh api -X PUT "repos/$Repo/topics" --input -
if ($LASTEXITCODE -ne 0) {
    throw "Failed to set repository topics"
}

Write-Host "Done: https://github.com/$Repo"
Write-Host "Topics: $($topicList -join ', ')"
