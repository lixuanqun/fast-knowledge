#Requires -Version 5.1
$ErrorActionPreference = "Stop"

$Repo = "lixuanqun/fast-knowledge"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Description = Get-Content -Path (Join-Path $Root ".github/description.txt") -Raw -Encoding UTF8
$Description = $Description.Trim()
$Topics = "knowledge-base,knowledge-management,document-management,rag,semantic-search,hybrid-search,self-hosted,private-deployment,on-premise,enterprise-search,spring-boot,java,vue3,typescript,langchain4j,lucene,docker,ollama,open-source,agpl-3.0"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) not found. Install from https://cli.github.com/"
}

gh auth status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "gh is not authenticated. Run: gh auth login"
}

Write-Host "Setting repository description..."
gh repo edit $Repo --description $Description

Write-Host "Setting repository topics..."
foreach ($topic in $Topics.Split(",")) {
    gh repo edit $Repo --add-topic $topic.Trim()
}

Write-Host "Done: https://github.com/$Repo"
Write-Host "Topics: $Topics"
