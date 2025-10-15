@echo off
REM 测试修复循环输出问题的脚本 (Windows版本)

echo =========================================
echo 测试NC文件处理器循环输出修复
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

REM 创建测试目录和文件
echo 3. 创建测试环境...
if not exist "input\test-folder" mkdir input\test-folder
echo 创建测试NC文件（空文件用于测试）
echo. > input\test-folder\test.nc

REM 启动容器
echo 4. 启动容器...
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

echo 5. 等待10秒让应用启动并处理文件...
timeout /t 10 /nobreak >nul

echo 6. 查看日志（前50行）...
echo ----------------------------------------
docker logs nc-file-processor

echo.
echo 7. 观察日志是否还有循环输出...
echo ----------------------------------------
echo 等待30秒观察是否有重复的 "开始处理NC文件" 消息...

REM 简化的监控逻辑
echo 请观察日志输出，检查是否有重复的处理消息
timeout /t 30 /nobreak >nul

echo.
echo =========================================
echo 测试结果:
echo 请手动检查上面的日志输出
echo 如果看到重复的"开始处理NC文件"消息，说明问题仍存在
echo 如果只看到一次处理消息，说明问题已修复
echo =========================================

REM 清理测试文件
echo 8. 清理测试环境...
del input\test-folder\test.nc >nul 2>&1
rmdir input\test-folder >nul 2>&1

echo 测试完成！
pause