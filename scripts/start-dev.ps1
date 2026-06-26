# ============================================================
# IoT Platform - 本地开发启动脚本 (Windows PowerShell)
#
# 用途: 一键启动后端(Spring Boot) + 前端(Vue Vite dev server)
#
# 端口规划:
#   33400 - Frontend (Vite dev)
#   33401 - Backend API
#   33410 - TCP Server (后端内嵌)
#
# 用法:
#   1. 确保已安装 JDK 17 + Maven + Node 20+
#   2. 确保中间件已启动(Docker: mysql + tdengine + emqx)
#   3. 在 PowerShell 中执行:
#        .\scripts\start-dev.ps1
# ============================================================

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  IoT Platform - 本地开发启动" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "项目根目录: $projectRoot" -ForegroundColor Gray
Write-Host ""

# ---- 1. 环境检查 ----
Write-Host "[1/4] 检查环境..." -ForegroundColor Yellow
$checks = @{
    "JDK 17"      = { & java -version 2>&1 | Select-String "17\." }
    "Maven"       = { & mvn -version 2>&1 | Select-String "Apache Maven" }
    "Node.js 20+" = { & node -v 2>&1 | Select-String "^v(2[0-9]|[3-9][0-9])\." }
    "Docker"      = { & docker ps 2>&1 | Select-String "CONTAINER" }
}

$failed = $false
foreach ($k in $checks.Keys) {
    try {
        $r = & $checks[$k]
        if ($r) { Write-Host "  ✓ $k" -ForegroundColor Green }
        else    { Write-Host "  ✗ $k (未安装或不可用)" -ForegroundColor Red; $failed = $true }
    } catch {
        Write-Host "  ✗ $k ($_)" -ForegroundColor Red; $failed = $true
    }
}

if ($failed) {
    Write-Host ""
    Write-Host "环境检查未通过。请先:" -ForegroundColor Red
    Write-Host "  winget install EclipseAdoptium.Temurin.17.JDK" -ForegroundColor Yellow
    Write-Host "  winget install Apache.Maven" -ForegroundColor Yellow
    Write-Host "  手动启动 Docker Desktop(右下角图标变绿)" -ForegroundColor Yellow
    exit 1
}

# ---- 2. 检查中间件 ----
Write-Host ""
Write-Host "[2/4] 检查中间件..." -ForegroundColor Yellow
$middlewareOk = $true
$ports = @{
    "MySQL     :33402"   = (Test-NetConnection -ComputerName localhost -Port 33402 -WarningAction SilentlyContinue -InformationLevel Quiet)
    "TDengine  :33403"   = (Test-NetConnection -ComputerName localhost -Port 33403 -WarningAction SilentlyContinue -InformationLevel Quiet)
    "EMQX MQTT :33405"   = (Test-NetConnection -ComputerName localhost -Port 33405 -WarningAction SilentlyContinue -InformationLevel Quiet)
}
foreach ($p in $ports.Keys) {
    if ($ports[$p]) {
        Write-Host "  ✓ $p" -ForegroundColor Green
    } else {
        Write-Host "  ✗ $p (无响应)" -ForegroundColor Red
        $middlewareOk = $false
    }
}

if (-not $middlewareOk) {
    Write-Host ""
    Write-Host "中间件未就绪。请先在 deploy 目录执行:" -ForegroundColor Yellow
    Write-Host "  cd deploy && docker compose up -d mysql tdengine emqx" -ForegroundColor Cyan
    Write-Host ""
    $ans = Read-Host "是否仍要继续启动后端(部分功能不可用)? [y/N]"
    if ($ans -ne "y") { exit 1 }
}

# ---- 3. 启动后端 ----
Write-Host ""
Write-Host "[3/4] 启动后端 (Spring Boot)..." -ForegroundColor Yellow
Write-Host "  工作目录: $projectRoot\backend" -ForegroundColor Gray

# 用 mvnw(优先) 或 mvn
$mvnCmd = "mvn"
if (Test-Path "$projectRoot\backend\mvnw.cmd") {
    $mvnCmd = "$projectRoot\backend\mvnw.cmd"
}

# 后端放到新窗口
Start-Process powershell -ArgumentList @(
    "-NoExit", "-Command",
    "cd '$projectRoot\backend'; & $mvnCmd spring-boot:run -Dspring-boot.run.jvmArguments='-DMYSQL_HOST=localhost -DMYSQL_PORT=33402 -DTDENGINE_HOST=localhost -DTDENGINE_PORT=33403 -DMQTT_BROKER=tcp://localhost:33405 -DTCP_PORT=33410'"
) -WindowStyle Normal

Write-Host "  ✓ 后端启动中(新窗口)" -ForegroundColor Green
Write-Host "    等待 30-60 秒后访问: http://localhost:33401/api" -ForegroundColor Gray

# ---- 4. 启动前端 ----
Write-Host ""
Write-Host "[4/4] 启动前端 (Vite dev)..." -ForegroundColor Yellow
Write-Host "  工作目录: $projectRoot\frontend" -ForegroundColor Gray

# 前端放到新窗口
Start-Process powershell -ArgumentList @(
    "-NoExit", "-Command",
    "cd '$projectRoot\frontend'; npm run dev"
) -WindowStyle Normal

Write-Host "  ✓ 前端启动中(新窗口)" -ForegroundColor Green
Write-Host "    等待 5-10 秒后访问: http://localhost:33400" -ForegroundColor Gray

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  启动完成!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "访问地址:" -ForegroundColor Yellow
Write-Host "  前端:   http://localhost:33400" -ForegroundColor White
Write-Host "  后端:   http://localhost:33401/api" -ForegroundColor White
Write-Host "  Swagger: http://localhost:33401/api/swagger-ui.html" -ForegroundColor White
Write-Host "  TCP:    localhost:33410 (设备接入)" -ForegroundColor White
Write-Host ""
Write-Host "默认账号:" -ForegroundColor Yellow
Write-Host "  租户: default" -ForegroundColor White
Write-Host "  用户: admin" -ForegroundColor White
Write-Host "  密码: 123456" -ForegroundColor White
Write-Host ""
Write-Host "提示: 在对应的 PowerShell 窗口中按 Ctrl+C 停止服务" -ForegroundColor Gray
