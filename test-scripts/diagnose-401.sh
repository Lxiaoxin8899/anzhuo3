#!/bin/bash
# SmartDosing 401 错误诊断脚本
# 用途: 自动诊断 401 错误的具体原因

set -e

# 配置参数
DEVICE_IP="${DEVICE_IP:-192.168.1.50}"
DEVICE_PORT="${DEVICE_PORT:-8080}"
API_KEY="${API_KEY}"

BASE_URL="http://${DEVICE_IP}:${DEVICE_PORT}"

echo "=========================================="
echo "SmartDosing 401 错误诊断工具"
echo "=========================================="
echo "设备地址: ${BASE_URL}"
echo "=========================================="
echo ""

# 测试 1: 检查设备连通性
echo "测试 1: 检查设备连通性..."
if curl -s --connect-timeout 5 "${BASE_URL}" > /dev/null 2>&1; then
  echo "✅ 设备可访问"
else
  echo "❌ 设备不可访问"
  echo ""
  echo "可能原因:"
  echo "  1. 设备 IP 地址错误"
  echo "  2. 设备未启动无线传输服务"
  echo "  3. 网络不通"
  echo ""
  echo "解决方案:"
  echo "  1. 检查设备 IP: ping ${DEVICE_IP}"
  echo "  2. 在设备上启动无线传输服务"
  echo "  3. 检查防火墙设置"
  exit 1
fi
echo ""

# 测试 2: 检查配对接口是否可访问（无需认证）
echo "测试 2: 检查配对接口（无需认证）..."
PAIR_TEST=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
  -X POST "${BASE_URL}/api/device/pair" \
  -H "Content-Type: application/json" \
  -d "{
    \"senderUID\": \"DIAG-TEST\",
    \"senderName\": \"诊断工具\",
    \"senderIP\": \"127.0.0.1\",
    \"senderPort\": 3000,
    \"callbackBaseUrl\": \"http://127.0.0.1:3000\",
    \"timestamp\": $(date +%s)000
  }")

HTTP_CODE=$(echo "${PAIR_TEST}" | grep "HTTP_CODE:" | cut -d':' -f2)

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "429" ]; then
  echo "✅ 配对接口正常"
else
  echo "❌ 配对接口异常 (HTTP ${HTTP_CODE})"
  exit 1
fi
echo ""

# 测试 3: 检查是否携带 API Key
echo "测试 3: 检查 API Key 配置..."
if [ -z "${API_KEY}" ]; then
  if [ -f ".api_key" ]; then
    API_KEY=$(cat .api_key)
    echo "⚠️  从 .api_key 文件读取 API Key"
  else
    echo "❌ 未配置 API Key"
    echo ""
    echo "诊断结果: 401 错误原因是「未携带 X-API-Key 请求头」"
    echo ""
    echo "解决方案:"
    echo "  1. 执行配对流程获取 API Key:"
    echo "     ./test-pairing.sh"
    echo ""
    echo "  2. 或手动设置 API_KEY 环境变量:"
    echo "     export API_KEY=\"your-api-key-here\""
    exit 1
  fi
else
  echo "✅ API Key 已配置: ${API_KEY:0:8}..."
fi
echo ""

# 测试 4: 验证 API Key 格式
echo "测试 4: 验证 API Key 格式..."
if echo "${API_KEY}" | grep -qE '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'; then
  echo "✅ API Key 格式正确 (UUID)"
else
  echo "⚠️  API Key 格式异常"
  echo "   期望格式: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
  echo "   实际格式: ${API_KEY}"
  echo ""
  echo "可能原因:"
  echo "  1. API Key 被截断或损坏"
  echo "  2. 使用了错误的 Key"
  echo ""
  echo "建议: 重新执行配对流程获取新的 API Key"
fi
echo ""

# 测试 5: 测试 API Key 有效性
echo "测试 5: 测试 API Key 有效性..."
PING_RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
  -X POST "${BASE_URL}/api/device/ping" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d "{
    \"senderUID\": \"DIAG-TEST\",
    \"timestamp\": $(date +%s)000
  }")

HTTP_CODE=$(echo "${PING_RESPONSE}" | grep "HTTP_CODE:" | cut -d':' -f2)
PING_BODY=$(echo "${PING_RESPONSE}" | sed '/HTTP_CODE:/d')

if [ "${HTTP_CODE}" = "200" ]; then
  echo "✅ API Key 有效"
  echo ""
  echo "设备信息:"
  echo "${PING_BODY}" | python3 -m json.tool 2>/dev/null || echo "${PING_BODY}"
  echo ""
  echo "=========================================="
  echo "诊断结果: API Key 正常"
  echo "=========================================="
  echo ""
  echo "如果任务接口仍返回 401，可能原因:"
  echo "  1. 请求头名称错误（必须是 X-API-Key）"
  echo "  2. 请求头值前后有空格"
  echo "  3. 使用了错误的 HTTP 方法"
  echo ""
  echo "建议使用 curl -v 查看完整请求:"
  echo "  curl -v -H \"X-API-Key: ${API_KEY}\" \\"
  echo "    ${BASE_URL}/api/transfer/task"

elif [ "${HTTP_CODE}" = "401" ]; then
  echo "❌ API Key 无效"
  echo ""
  echo "=========================================="
  echo "诊断结果: 401 错误原因是「API Key 无效」"
  echo "=========================================="
  echo ""
  echo "可能原因:"
  echo "  1. API Key 已过期或被撤销"
  echo "  2. 发送端授权已被禁用"
  echo "  3. 设备端数据库被重置"
  echo "  4. 使用了错误的 API Key"
  echo ""
  echo "解决方案:"
  echo "  重新执行配对流程:"
  echo "    ./test-pairing.sh"
  exit 1

else
  echo "❌ 未知错误 (HTTP ${HTTP_CODE})"
  echo "响应体: ${PING_BODY}"
  exit 1
fi

echo ""
echo "=========================================="
echo "诊断完成"
echo "=========================================="
