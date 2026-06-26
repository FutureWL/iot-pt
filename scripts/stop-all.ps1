# ============================================================
# IoT Platform - 停止所有服务
# ============================================================

$projectRoot = Split-Path -Parent $PSScriptRoot

Write-Host "停止 Docker 中间件..." -ForegroundColor Yellow
$deployDir = Join-Path $projectRoot "deploy"
if (Test-Path $deployDir) {
    Set-Location $deployDir
    docker compose stop mysql tdengine emqx 2>&1 | Out-Null
    Write-Host "  ✓ Docker 中间件已停止" -ForegroundColor Green
}

Write-Host ""
Write-Host "停止后端和前端(在对应的 PowerShell 窗口按 Ctrl+C)..." -ForegroundColor Yellow
Write-Host ""
Write-Host "完全清理(包括数据卷):" -ForegroundColor Yellow
Write-Host "  cd deploy && docker compose down -v" -ForegroundColor Cyan
