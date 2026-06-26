# ============================================================
# IoT Platform - 一键启动所有中间件 (Docker Compose)
#
# 用途: 启动 MySQL + TDengine + EMQX(后端和前端用本地 IDE 跑)
#
# 用法 (PowerShell):
#   .\scripts\start-docker.ps1
# ============================================================

$ErrorActionPreference = "Stop"
$deployDir = Join-Path (Split-Path -Parent $PSScriptRoot) "deploy"
Set-Location $deployDir

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  IoT Platform - 启动中间件" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Docker
Write-Host "[1/2] 检查 Docker..." -ForegroundColor Yellow
try {
    $dockerVer = docker --version
    Write-Host "  ✓ $dockerVer" -ForegroundColor Green
} catch {
    Write-Host "  ✗ Docker 未安装或未运行" -ForegroundColor Red
    Write-Host "  请先启动 Docker Desktop" -ForegroundColor Yellow
    exit 1
}

# 启动中间件
Write-Host ""
Write-Host "[2/2] 启动 mysql + tdengine + emqx..." -ForegroundColor Yellow
docker compose up -d mysql tdengine emqx

if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ 启动失败" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "  ✓ 中间件已启动" -ForegroundColor Green
Write-Host ""
Write-Host "查看日志:" -ForegroundColor Yellow
Write-Host "  docker compose logs -f" -ForegroundColor Cyan
Write-Host ""
Write-Host "停止:" -ForegroundColor Yellow
Write-Host "  docker compose stop mysql tdengine emqx" -ForegroundColor Cyan
Write-Host ""
Write-Host "访问:" -ForegroundColor Yellow
Write-Host "  EMQX Dashboard: http://localhost:33409 (admin/public)" -ForegroundColor White
