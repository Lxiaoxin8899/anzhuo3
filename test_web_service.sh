#!/bin/bash

# SmartDosing Web服务测试脚本
# 用于快速测试Web API功能

echo "=========================================="
echo "SmartDosing Web服务测试脚本"
echo "=========================================="
echo ""

# 配置
DEVICE_IP="192.168.1.100"  # 请修改为你的设备IP
PORT="8080"
BASE_URL="http://${DEVICE_IP}:${PORT}"

echo "提示: 请先修改脚本中的DEVICE_IP变量为你的设备IP地址"
echo "当前配置: ${BASE_URL}"
echo ""

# 检测设备IP
echo "1. 检测Android设备IP..."
echo "   执行: adb shell ip -f inet addr show wlan0"
adb shell ip -f inet addr show wlan0 2>/dev/null | grep inet
echo ""

# 检查Web服务状态
echo "2. 测试Web服务连接..."
echo "   访问: ${BASE_URL}"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" ${BASE_URL} 2>/dev/null)
if [ "$HTTP_CODE" = "200" ]; then
    echo "   ✅ Web服务正常运行 (HTTP $HTTP_CODE)"
else
    echo "   ❌ 无法连接到Web服务 (HTTP $HTTP_CODE)"
    echo "   请检查:"
    echo "   - 设备IP是否正确"
    echo "   - Web服务是否已启动"
    echo "   - 设备和电脑是否在同一网络"
    exit 1
fi
echo ""

# 测试获取配方列表
echo "3. 测试获取配方列表..."
echo "   GET ${BASE_URL}/api/recipes"
RECIPES=$(curl -s "${BASE_URL}/api/recipes")
echo "   响应: ${RECIPES}" | head -c 200
echo "..."
echo ""

# 测试获取统计数据
echo "4. 测试获取统计数据..."
echo "   GET ${BASE_URL}/api/stats"
STATS=$(curl -s "${BASE_URL}/api/stats")
echo "   响应: ${STATS}"
echo ""

# 测试导入CSV文件
echo "5. 测试CSV文件导入..."
if [ -f "test_recipes.csv" ]; then
    echo "   POST ${BASE_URL}/api/import/recipes"
    echo "   文件: test_recipes.csv"
    IMPORT_RESULT=$(curl -s -X POST "${BASE_URL}/api/import/recipes" -F "file=@test_recipes.csv")
    echo "   响应: ${IMPORT_RESULT}"
    echo ""

    # 解析结果
    SUCCESS_COUNT=$(echo ${IMPORT_RESULT} | grep -o '"success":[0-9]*' | grep -o '[0-9]*')
    FAILED_COUNT=$(echo ${IMPORT_RESULT} | grep -o '"failed":[0-9]*' | grep -o '[0-9]*')

    echo "   📊 导入结果："
    echo "   - 成功: ${SUCCESS_COUNT} 条"
    echo "   - 失败: ${FAILED_COUNT} 条"
else
    echo "   ⚠️  未找到 test_recipes.csv 文件"
    echo "   请确保文件存在于当前目录"
fi
echo ""

# 再次获取统计，验证导入效果
echo "6. 验证导入效果..."
echo "   GET ${BASE_URL}/api/stats"
NEW_STATS=$(curl -s "${BASE_URL}/api/stats")
echo "   响应: ${NEW_STATS}"
echo ""

# 测试搜索功能
echo "7. 测试搜索功能..."
echo "   GET ${BASE_URL}/api/recipes?search=Web测试"
SEARCH_RESULT=$(curl -s "${BASE_URL}/api/recipes?search=Web测试")
echo "   响应: ${SEARCH_RESULT}" | head -c 300
echo "..."
echo ""

# 测试分类筛选
echo "8. 测试分类筛选..."
echo "   GET ${BASE_URL}/api/recipes?category=香精"
CATEGORY_RESULT=$(curl -s "${BASE_URL}/api/recipes?category=香精")
echo "   响应: ${CATEGORY_RESULT}" | head -c 300
echo "..."
echo ""

echo "=========================================="
echo "测试完成！"
echo "=========================================="
echo ""
echo "📝 后续步骤："
echo "1. 在浏览器中访问: ${BASE_URL}"
echo "2. 查看导入的配方: ${BASE_URL}/recipes"
echo "3. 在Android应用中验证数据同步"
echo ""
echo "🔍 查看日志："
echo "adb logcat | grep 'WebServerManager\\|DBTest'"
echo ""
