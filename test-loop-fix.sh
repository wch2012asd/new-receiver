#!/bin/bash

# 测试修复循环输出问题的脚本

echo "========================================="
echo "测试NC文件处理器循环输出修复"
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

# 创建测试目录和文件
echo "3. 创建测试环境..."
mkdir -p input/test-folder
echo "创建测试NC文件（空文件用于测试）"
touch input/test-folder/test.nc

# 启动容器
echo "4. 启动容器..."
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

echo "5. 等待10秒让应用启动并处理文件..."
sleep 10

echo "6. 查看日志（前50行）..."
echo "----------------------------------------"
docker logs nc-file-processor | head -50

echo ""
echo "7. 观察日志是否还有循环输出..."
echo "----------------------------------------"
echo "等待30秒观察是否有重复的 '开始处理NC文件' 消息..."

# 监控30秒内的日志
start_time=$(date +%s)
end_time=$((start_time + 30))

repeated_messages=0
last_log_count=0

while [ $(date +%s) -lt $end_time ]; do
    current_log_count=$(docker logs nc-file-processor 2>&1 | grep -c "开始处理NC文件" || echo "0")
    
    if [ $current_log_count -gt $last_log_count ]; then
        echo "发现新的处理消息，总计: $current_log_count"
        repeated_messages=$((current_log_count - last_log_count))
        last_log_count=$current_log_count
    fi
    
    sleep 2
done

echo ""
echo "========================================="
echo "测试结果:"
if [ $repeated_messages -gt 1 ]; then
    echo "⚠️  仍然存在重复处理消息 ($repeated_messages 次)"
    echo "建议检查数据库连接和文件检查逻辑"
else
    echo "✅ 循环输出问题已修复"
    echo "处理消息只出现了正常的次数"
fi
echo "========================================="

# 清理测试文件
echo "8. 清理测试环境..."
rm -f input/test-folder/test.nc
rmdir input/test-folder 2>/dev/null || true

echo "测试完成！"