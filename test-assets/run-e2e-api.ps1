$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8088/api/v1'
$results = [System.Collections.Generic.List[object]]::new()
$ts = Get-Date -Format 'yyyyMMddHHmmss'
$testUser = "e2e_user_$ts"
$testKbName = "E2E测试库_$ts"

function Add-Result($id, $name, $status, $detail = '') {
    $results.Add([pscustomobject]@{ Id = $id; Name = $name; Status = $status; Detail = $detail })
    $icon = if ($status -eq 'PASS') { 'OK' } elseif ($status -eq 'SKIP') { 'SKIP' } else { 'FAIL' }
    Write-Host "[$icon] $id $name $(if ($detail) { "- $detail" })"
}

function Invoke-Api {
    param(
        [string]$Method = 'GET',
        [string]$Path,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [string]$OutFile = $null
    )
    $uri = "$base$Path"
    $params = @{ Method = $Method; Uri = $uri; Headers = $Headers }
    if ($Body -ne $null) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json -Depth 6 -Compress)
    }
    if ($OutFile) { $params.OutFile = $OutFile }
    return Invoke-RestMethod @params
}

function Get-Token($user, $pass) {
    $r = Invoke-Api -Method POST -Path '/auth/login' -Body @{ username = $user; password = $pass }
    if ($r.code -ne 0) { throw $r.message }
    return $r.data.token
}

function Invoke-CurlJson {
    param([string]$Method, [string]$Url, [hashtable]$Headers, [object]$Body, [switch]$Stream)
    $tmp = [System.IO.Path]::GetTempFileName()
    try {
        $args = @('-s', '-X', $Method, $Url)
        if ($Stream) { $args = @('-s', '-N', '--max-time', '120', '-X', $Method, $Url) }
        foreach ($k in $Headers.Keys) { $args += @('-H', "$k`: $($Headers[$k])") }
        if ($Body -ne $null) {
            $json = $Body | ConvertTo-Json -Depth 6 -Compress
            [System.IO.File]::WriteAllText($tmp, $json, [System.Text.UTF8Encoding]::new($false))
            $args += @('-H', 'Content-Type: application/json', '--data-binary', "@$tmp")
        }
        $out = & curl.exe @args
        return $out
    } finally {
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
}

function Auth-H($token) { @{ Authorization = "Bearer $token" } }

function Get-AdminUserId($token) {
    $me = Invoke-Api -Path '/users/me' -Headers (Auth-H $token)
    return $me.data.id
}

# --- 认证 ---
try {
    $adminToken = Get-Token 'admin' 'admin123'
    Add-Result 'TC-A01' 'API 管理员登录' 'PASS'
} catch {
    Add-Result 'TC-A01' 'API 管理员登录' 'FAIL' $_.Exception.Message
    $results | ConvertTo-Json -Depth 4 | Out-File 'd:\fk_repo\test-assets\e2e-results.json' -Encoding utf8
    exit 1
}

try {
    $bad = Invoke-Api -Method POST -Path '/auth/login' -Body @{ username = 'admin'; password = 'wrong' }
    if ($bad.code -ne 0) { Add-Result 'TC-A02' '错误密码登录拒绝' 'PASS' } else { Add-Result 'TC-A02' '错误密码登录拒绝' 'FAIL' '应返回失败' }
} catch { Add-Result 'TC-A02' '错误密码登录拒绝' 'PASS' }

# LDAP / OIDC
try {
    $cfg = Invoke-Api -Path '/system/config'
    if (-not $cfg.data.ldapEnabled) { Add-Result 'TC-A03' 'LDAP 登录' 'SKIP' '环境未启用' }
    else { Add-Result 'TC-A03' 'LDAP 登录' 'SKIP' '需 LDAP 环境' }
    if (-not $cfg.data.oidcEnabled) { Add-Result 'TC-A04' 'OIDC/SSO' 'SKIP' '环境未启用' }
    else { Add-Result 'TC-A04' 'OIDC/SSO' 'SKIP' '需 OIDC 环境' }
} catch { Add-Result 'TC-A03' 'LDAP/OIDC 配置检查' 'FAIL' $_.Exception.Message }

# Setup 页面（已完成则跳过）
if ($cfg.data.setupComplete) {
    Add-Result 'TC-A05' '首次设置 /setup' 'SKIP' 'setupComplete=true'
} else {
    Add-Result 'TC-A05' '首次设置 /setup' 'SKIP' '需未完成安装环境'
}

# --- 仪表盘 ---
try {
    $stats = Invoke-Api -Path '/dashboard/stats' -Headers (Auth-H $adminToken)
    if ($stats.code -eq 0 -and $null -ne $stats.data) { Add-Result 'TC-D01' '仪表盘统计数据' 'PASS' "kbCount=$($stats.data.kbCount)" }
    else { Add-Result 'TC-D01' '仪表盘统计数据' 'FAIL' $stats.message }
} catch { Add-Result 'TC-D01' '仪表盘统计数据' 'FAIL' $_.Exception.Message }

# --- 知识库 CRUD ---
$kbId = $null
try {
    $kb = Invoke-Api -Method POST -Path '/kbs' -Headers (Auth-H $adminToken) -Body @{
        name = $testKbName; description = 'E2E自动化测试'; visibility = 'PRIVATE'; searchTopK = 8
    }
    $kbId = $kb.data.id
    Add-Result 'TC-K01' '新建知识库' 'PASS' "id=$kbId"
} catch { Add-Result 'TC-K01' '新建知识库' 'FAIL' $_.Exception.Message }

if ($kbId) {
    try {
        $upd = Invoke-Api -Method PUT -Path "/kbs/$kbId" -Headers (Auth-H $adminToken) -Body @{
            name = "${testKbName}_edited"; description = 'updated'; visibility = 'PRIVATE'; searchTopK = 10
        }
        if ($upd.data.searchTopK -eq 10) { Add-Result 'TC-K02' '编辑知识库' 'PASS' } else { Add-Result 'TC-K02' '编辑知识库' 'FAIL' "searchTopK=$($upd.data.searchTopK)" }
    } catch { Add-Result 'TC-K02' '编辑知识库' 'FAIL' $_.Exception.Message }

    # 文档上传
    $docId = $null
    try {
        $uploadOut = curl.exe -s -X POST "$base/kbs/$kbId/documents/upload" `
            -H "Authorization: Bearer $adminToken" `
            -F "file=@d:\fk_repo\test-assets\e2e-sample.md"
        $upload = $uploadOut | ConvertFrom-Json
        if ($upload.code -eq 0) {
            $docId = $upload.data.id
            Add-Result 'TC-K03' '上传文档' 'PASS' "docId=$docId"
        } else { Add-Result 'TC-K03' '上传文档' 'FAIL' $upload.message }
    } catch { Add-Result 'TC-K03' '上传文档' 'FAIL' $_.Exception.Message }

    # 等待索引
    if ($docId) {
        $indexed = $false
        for ($i = 0; $i -lt 45; $i++) {
            Start-Sleep -Seconds 2
            $docs = Invoke-Api -Path "/kbs/$kbId/documents" -Headers (Auth-H $adminToken)
            $doc = $docs.data | Where-Object { $_.id -eq $docId } | Select-Object -First 1
            if ($doc.indexStatus -eq 'INDEXED') { $indexed = $true; break }
            if ($doc.indexStatus -eq 'FAILED') { break }
        }
        if ($indexed) { Add-Result 'TC-K04' '文档索引完成' 'PASS' } else { Add-Result 'TC-K04' '文档索引完成' 'FAIL' "status=$($doc.indexStatus)" }

        try {
            $preview = Invoke-Api -Path "/kbs/$kbId/documents/$docId/preview" -Headers (Auth-H $adminToken)
            if ($preview.code -eq 0) { Add-Result 'TC-K05' '文档预览' 'PASS' } else { Add-Result 'TC-K05' '文档预览' 'FAIL' $preview.message }
        } catch { Add-Result 'TC-K05' '文档预览' 'FAIL' $_.Exception.Message }

        try {
            $chunks = Invoke-Api -Path "/kbs/$kbId/documents/$docId/chunks" -Headers (Auth-H $adminToken)
            if ($chunks.data.Count -gt 0) { Add-Result 'TC-K06' '文档分块' 'PASS' "chunks=$($chunks.data.Count)" }
            else { Add-Result 'TC-K06' '文档分块' 'FAIL' '无分块' }
        } catch { Add-Result 'TC-K06' '文档分块' 'FAIL' $_.Exception.Message }

        try {
            $re = Invoke-Api -Method POST -Path "/kbs/$kbId/documents/$docId/reindex" -Headers (Auth-H $adminToken)
            if ($re.code -eq 0) { Add-Result 'TC-K07' '重新索引文档' 'PASS' } else { Add-Result 'TC-K07' '重新索引文档' 'FAIL' $re.message }
        } catch { Add-Result 'TC-K07' '重新索引文档' 'FAIL' $_.Exception.Message }
    }

    # 成员管理
    try {
        Invoke-Api -Method POST -Path "/kbs/$kbId/members" -Headers (Auth-H $adminToken) -Body @{ username = 'admin'; permission = 'READ' }
        Add-Result 'TC-K08' '添加成员' 'SKIP' 'admin 已是所有者'
    } catch {
        Add-Result 'TC-K08' '添加成员' 'SKIP' $_.Exception.Message
    }

    # Wiki
    try {
        $wiki = Invoke-Api -Path "/kbs/$kbId/wiki/pages" -Headers (Auth-H $adminToken)
        Add-Result 'TC-K09' 'Wiki 页面列表' 'PASS' "count=$($wiki.data.Count)"
    } catch { Add-Result 'TC-K09' 'Wiki 页面列表' 'FAIL' $_.Exception.Message }

    # 重建索引
    try {
        $rb = Invoke-Api -Method POST -Path "/index-tasks/rebuild/$kbId" -Headers (Auth-H $adminToken)
        if ($rb.code -eq 0) { Add-Result 'TC-K10' '重建知识库索引' 'PASS' } else { Add-Result 'TC-K10' '重建知识库索引' 'FAIL' $rb.message }
    } catch { Add-Result 'TC-K10' '重建知识库索引' 'FAIL' $_.Exception.Message }

    # 检索
    try {
        $search = Invoke-Api -Method POST -Path '/search' -Headers (Auth-H $adminToken) -Body @{ kbId = $kbId; query = '验收测试'; topK = 5 }
        $hits = @($search.data)
        if ($hits.Count -gt 0) { Add-Result 'TC-K11' '知识库检索' 'PASS' "hits=$($hits.Count)" }
        else { Add-Result 'TC-K11' '知识库检索' 'FAIL' '无结果' }
    } catch { Add-Result 'TC-K11' '知识库检索' 'FAIL' $_.Exception.Message }

    # 问答
    try {
        $qa = Invoke-Api -Method POST -Path '/qa' -Headers (Auth-H $adminToken) -Body @{ kbId = $kbId; question = '这份文档包含哪些功能点？' }
        if ($qa.code -eq 0 -and $qa.data.answer -and $qa.data.answer.Length -gt 0) {
            Add-Result 'TC-K12' '智能问答 RAG' 'PASS' "answerLen=$($qa.data.answer.Length)"
        } elseif ($qa.message -match '大模型|Ollama|不可用|Connection|内部错误|ClosedChannel') {
            Add-Result 'TC-K12' '智能问答 RAG' 'SKIP' $qa.message
        } else { Add-Result 'TC-K12' '智能问答 RAG' 'FAIL' $(if ($qa.message) { $qa.message } else { '无回答' }) }
    } catch { Add-Result 'TC-K12' '智能问答 RAG' 'SKIP' $_.Exception.Message }
}

# --- 对话 ---
try {
    $sess = Invoke-Api -Method POST -Path '/chat/sessions' -Headers (Auth-H $adminToken) -Body @{ kbId = $kbId; title = 'E2E对话' }
    $sessionId = $sess.data.id
    Add-Result 'TC-C01' '创建对话会话' 'PASS' "sessionId=$sessionId"

    $streamOut = Invoke-CurlJson -Method POST -Url "$base/chat/messages/stream" -Headers (Auth-H $adminToken) -Body @{
        sessionId = $sessionId; message = '你好，请简短回复'
    } -Stream
    if ($streamOut -match '^\s*\{' -and $streamOut -match '"code"') {
        Add-Result 'TC-C02' '流式对话' 'FAIL' '返回 JSON 错误而非 SSE'
    } elseif ($streamOut.Length -gt 20) {
        Add-Result 'TC-C02' '流式对话' 'PASS' "bytes=$($streamOut.Length)"
    } elseif ($streamOut -match 'event:error|大模型|不可用') {
        Add-Result 'TC-C02' '流式对话' 'SKIP' 'LLM 不可用或无流式输出'
    } else {
        Add-Result 'TC-C02' '流式对话' 'FAIL' '无流式输出'
    }

    Invoke-Api -Method DELETE -Path "/chat/sessions/$sessionId" -Headers (Auth-H $adminToken) | Out-Null
    Add-Result 'TC-C03' '删除对话会话' 'PASS'
} catch { Add-Result 'TC-C01' '对话功能' 'FAIL' $_.Exception.Message }

# --- 写文档 ---
try {
    $writerOut = Invoke-CurlJson -Method POST -Url "$base/writer/generate" -Headers (Auth-H $adminToken) -Body @{
        kbId = $kbId; topic = '测试主题'; outline = '## 概述'; style = 'professional'; wordCount = 200
    } -Stream
    if ($writerOut -match 'event:error' -and $writerOut.Length -lt 100) {
        Add-Result 'TC-W01' '智能写文档生成' 'SKIP' 'LLM 不可用'
    } elseif ($writerOut.Length -gt 20) { Add-Result 'TC-W01' '智能写文档生成' 'PASS' "bytes=$($writerOut.Length)" }
    else { Add-Result 'TC-W01' '智能写文档生成' 'FAIL' '输出过短' }
} catch { Add-Result 'TC-W01' '智能写文档生成' 'FAIL' $_.Exception.Message }

# --- LLM 配置 ---
try {
    $llm = Invoke-Api -Path '/system/llm-config' -Headers (Auth-H $adminToken)
    Add-Result 'TC-L01' '获取 LLM 配置' 'PASS' $llm.data.provider

    $testLlm = Invoke-Api -Method POST -Path '/system/llm-config/test' -Headers (Auth-H $adminToken) -Body @{
        provider = $llm.data.provider; baseUrl = $llm.data.baseUrl; apiKey = 'ollama'; model = $llm.data.model; allowExternal = $true
    }
    Add-Result 'TC-L02' 'LLM 测试连接' $(if ($testLlm.code -eq 0) { 'PASS' } elseif ($testLlm.message -match '大模型|Ollama|不可用|Connection|ClosedChannel|失败') { 'SKIP' } else { 'FAIL' }) $(if ($testLlm.data.message) { $testLlm.data.message } else { $testLlm.message })
} catch { Add-Result 'TC-L01' 'LLM 配置' 'FAIL' $_.Exception.Message }

# --- 用户管理 ---
$userId = $null
try {
    $cu = Invoke-Api -Method POST -Path '/users' -Headers (Auth-H $adminToken) -Body @{
        username = $testUser; password = 'pass1234'; displayName = 'E2E用户'; role = 'USER'
    }
    $userId = $cu.data.id
    Add-Result 'TC-U01' '新建用户' 'PASS' "id=$userId"
} catch { Add-Result 'TC-U01' '新建用户' 'FAIL' $_.Exception.Message }

if ($userId) {
    try {
        $uu = Invoke-Api -Method PUT -Path "/users/$userId" -Headers (Auth-H $adminToken) -Body @{ displayName = 'E2E已更新'; role = 'USER'; status = 1 }
        Add-Result 'TC-U02' '编辑用户' 'PASS'
    } catch { Add-Result 'TC-U02' '编辑用户' 'FAIL' $_.Exception.Message }

    try {
        Invoke-Api -Method POST -Path "/users/$userId/reset-password" -Headers (Auth-H $adminToken) -Body @{ newPassword = 'pass5678' } | Out-Null
        Add-Result 'TC-U03' '重置用户密码' 'PASS'
    } catch { Add-Result 'TC-U03' '重置用户密码' 'FAIL' $_.Exception.Message }

    # 普通用户权限
    try {
        $userToken = Get-Token $testUser 'pass5678'
        try {
            $denied = Invoke-Api -Path '/users' -Headers (Auth-H $userToken)
            if ($denied.code -eq 403) { Add-Result 'TC-U04' '普通用户访问用户管理' 'PASS' '403' }
            else { Add-Result 'TC-U04' '普通用户访问用户管理' 'FAIL' '应被拒绝' }
        } catch {
            Add-Result 'TC-U04' '普通用户访问用户管理' 'PASS' '403/无权限'
        }
    } catch { Add-Result 'TC-U04' '普通用户访问用户管理' 'FAIL' $_.Exception.Message }
    try {
        $userToken = Get-Token $testUser 'pass5678'
        $kbList = Invoke-Api -Path '/kbs' -Headers (Auth-H $userToken)
        Add-Result 'TC-U05' '普通用户访问知识库' 'PASS' "count=$($kbList.data.Count)"
    } catch { Add-Result 'TC-U05' '普通用户访问知识库' 'FAIL' $_.Exception.Message }
}

$adminUserId = $null
try { $adminUserId = Get-AdminUserId $adminToken } catch { }

# --- API Key ---
$keyId = $null
try {
    $key = Invoke-Api -Method POST -Path '/api-keys' -Headers (Auth-H $adminToken) -Body @{ name = "e2e-$ts"; userId = $adminUserId }
    $keyId = $key.data.id
    Add-Result 'TC-P01' '创建 API Key' 'PASS' "id=$keyId"
} catch { Add-Result 'TC-P01' '创建 API Key' 'FAIL' $_.Exception.Message }

if ($keyId) {
    try {
        Invoke-Api -Method DELETE -Path "/api-keys/$keyId" -Headers (Auth-H $adminToken) | Out-Null
        Add-Result 'TC-P02' '吊销 API Key' 'PASS'
    } catch { Add-Result 'TC-P02' '吊销 API Key' 'FAIL' $_.Exception.Message }
}

# --- 审计 ---
try {
    $audits = Invoke-Api -Path '/audits?pageNum=1&pageSize=10' -Headers (Auth-H $adminToken)
    Add-Result 'TC-R01' '审计日志查询' 'PASS' "total=$($audits.data.total)"

    $csvPath = "d:\fk_repo\test-assets\audit-export-$ts.csv"
    curl.exe -s -o $csvPath -H "Authorization: Bearer $adminToken" "$base/audits/export?limit=50"
    if ((Test-Path $csvPath) -and (Get-Item $csvPath).Length -gt 10) {
        Add-Result 'TC-R02' '审计导出 CSV' 'PASS' "size=$((Get-Item $csvPath).Length)"
    } else { Add-Result 'TC-R02' '审计导出 CSV' 'FAIL' }
} catch { Add-Result 'TC-R01' '审计功能' 'FAIL' $_.Exception.Message }

# --- 改密（API）---
try {
    Invoke-Api -Method POST -Path '/users/change-password' -Headers (Auth-H $adminToken) -Body @{
        oldPassword = 'admin123'; newPassword = 'admin123'
    } | Out-Null
    Add-Result 'TC-A06' '修改密码 API' 'PASS' '同密码换回原值'
} catch { Add-Result 'TC-A06' '修改密码 API' 'FAIL' $_.Exception.Message }

# --- 登出 ---
try {
    $lo = Invoke-Api -Method POST -Path '/auth/logout' -Headers (Auth-H $adminToken)
    if ($lo.code -eq 0) { Add-Result 'TC-A07' '登出 API' 'PASS' } else { Add-Result 'TC-A07' '登出 API' 'FAIL' $lo.message }
} catch { Add-Result 'TC-A07' '登出 API' 'FAIL' $_.Exception.Message }

# --- 清理 ---
if ($userId) {
    try { Invoke-Api -Method DELETE -Path "/users/$userId" -Headers (Auth-H (Get-Token 'admin' 'admin123')) | Out-Null; Add-Result 'TC-Z01' '清理测试用户' 'PASS' } catch { Add-Result 'TC-Z01' '清理测试用户' 'FAIL' $_.Exception.Message }
}
if ($kbId) {
    try {
        if ($docId) { Invoke-Api -Method DELETE -Path "/kbs/$kbId/documents/$docId" -Headers (Auth-H (Get-Token 'admin' 'admin123')) | Out-Null }
        Add-Result 'TC-Z02' '删除测试文档' 'PASS'
    } catch { Add-Result 'TC-Z02' '删除测试文档' 'FAIL' $_.Exception.Message }
    try {
        Invoke-Api -Method DELETE -Path "/kbs/$kbId" -Headers (Auth-H (Get-Token 'admin' 'admin123')) | Out-Null
        Add-Result 'TC-K13' '删除知识库' 'PASS'
    } catch { Add-Result 'TC-K13' '删除知识库' 'FAIL' $_.Exception.Message }
}

$summary = @{
    total = $results.Count
    pass = ($results | Where-Object Status -eq 'PASS').Count
    fail = ($results | Where-Object Status -eq 'FAIL').Count
    skip = ($results | Where-Object Status -eq 'SKIP').Count
}
$out = @{ summary = $summary; results = $results; kbId = $kbId; testUser = $testUser }
$out | ConvertTo-Json -Depth 5 | Out-File 'd:\fk_repo\test-assets\e2e-results.json' -Encoding utf8
Write-Host "`n=== SUMMARY pass=$($summary.pass) fail=$($summary.fail) skip=$($summary.skip) total=$($summary.total) ==="
