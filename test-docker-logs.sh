#!/bin/bash

# Docker日志测试脚本

echo "========================================="
echo "Docker日志测试脚本"
echo "========================================="

# 停止现有容器
echo "1. 停止现有容器..."
docker stop nc-file-processor 2>/dev/null || true
docker rm nc-file-processor 2>/dev/null || true

# 重新构建镜像
echo "2. 重新构建Docker镜像..."
docker build -t nc-file-processor:latest .

if [ $? -ne 0 ]; then
    echo "构建失败，请检查Dockerfile"
    exit 1
fi

# 启动容器
echo "3. 启动容器..."
docker run -d \
    --name nc-file-processor \
    -v $(pwd)/input:/app/input \
    -v $(pwd)/output:/app/output \
    -v $(pwd)/config.properties:/app/config.properties \
    nc-file-processor:latest

if [ $? -ne 0 ]; then
    echo "启动失败，请检查配置"
    exit 1
fi

echo "4. 等待5秒让应用启动..."
sleep 5

echo "5. 检查容器状态..."
docker ps | grep nc-file-processor

echo ""
echo "6. 查看启动日志（前30行）..."
echo "----------------------------------------"
docker logs nc-file-processor | head -30

echo ""
echo "7. 实时跟踪日志（按Ctrl+C停止）..."
echo "----------------------------------------"
echo "注意观察是否出现 '[yyyy-MM-dd HH:mm:ss] NC文件处理器正在运行中...' 消息"
echo ""

# 实时跟踪日志
docker logs -f nc-file-processor