@echo off
REM Docker日志测试脚本 (Windows版本)

echo =========================================
echo Docker日志测试脚本
echo =========================================

REM 停止现有容器
echo 1. 停止现有容器...
docker stop nc-file-processor >nul 2>&1
docker rm nc-file-processor >nul 2>&1

REM 重新构建镜像
echo 2. 重新构建Docker镜像...
docker build -t nc-file-processor:latest .

if %errorlevel% neq 0 (
    echo 构建失败，请检查Dockerfile
    pause
    exit /b 1
)

REM 启动容器
echo 3. 启动容器...
docker run -d ^
    --name nc-file-processor ^
    -v %cd%\input:/app/input ^
    -v %cd%\output:/app/output ^
    -v %cd%\config.properties:/app/config.properties ^
    nc-file-processor:latest

if %errorlevel% neq 0 (
    echo 启动失败，请检查配置
    pause
    exit /b 1
)

echo 4. 等待5秒让应用启动...
timeout /t 5 /nobreak >nul

echo 5. 检查容器状态...
docker ps | findstr nc-file-processor

echo.
echo 6. 查看启动日志（前30行）...
echo ----------------------------------------
docker logs nc-file-processor | findstr /N "."

echo.
echo 7. 实时跟踪日志（按Ctrl+C停止）...
echo ----------------------------------------
echo 注意观察是否出现 "[yyyy-MM-dd HH:mm:ss] NC文件处理器正在运行中..." 消息
echo.

REM 实时跟踪日志
docker logs -f nc-file-processor