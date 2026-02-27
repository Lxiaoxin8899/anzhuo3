#!/bin/bash
# SmartDosing 任务下发测试脚本
# 用途: 测试任务接口是否正常工作

set -e

# 配置参数
DEVICE_IP="${DEVICE_IP:-192.168.1.50}"
DEVICE_PORT="${DEVICE_PORT:-8080}"
API_KEY="${API_KEY}"
SENDER_UID="${SENDER_UID:-BACKEND-TEST}"
SENDER_NAME="${SENDER_NAME:-测试后台系统}"

BASE_URL="http://${DEVICE_IP}:${DEVICE_PORT}"

# 检查 API Key
if [ -z "${API_KEY}" ]; then
  if [ -f ".api_key" ]; then
    API_KEY=$(cat .api_key)
    echo "从 .api_key 文件读取 API Key"
  else
    echo "❌ 错误: 未设置 API_KEY 环境变量"
    echo ""
    echo "请先运行配对脚本获取 API Key:"
    echo "  ./test-pairing.sh"
    echo ""
    echo "或手动设置 API_KEY:"
    echo "  export API_KEY=\"your-api-key-here\""
    exit 1
  fi
fi

echo "=========================================="
echo "SmartDosing 任务下发测试"
echo "=========================================="
echo "设备地址: ${BASE_URL}"
echo "API Key: ${API_KEY:0:8}..."
echo "=========================================="
echo ""

# 生成测试数据
TIMESTAMP=$(date +%s)000
TRANSFER_ID="TF-${TIMESTAMP}-TEST"
RECIPE_CODE="TEST-$(date +%s)"

echo "测试任务信息:"
echo "  传输 ID: ${TRANSFER_ID}"
echo "  配方编码: ${RECIPE_CODE}"
echo ""

# 发送任务
echo "发送任务请求..."
TASK_RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
  -X POST "${BASE_URL}/api/transfer/task" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d "{
    \"schemaVersion\": \"1.0\",
    \"transferId\": \"${TRANSFER_ID}\",
    \"senderUID\": \"${SENDER_UID}\",
    \"senderName\": \"${SENDER_NAME}\",
    \"timestamp\": ${TIMESTAMP},
    \"task\": {
      \"title\": \"测试任务 - $(date '+%Y-%m-%d %H:%M:%S')\",
      \"recipeCode\": \"${RECIPE_CODE}\",
      \"recipeName\": \"测试配方\",
      \"quantity\": 100,
      \"unit\": \"g\",
      \"priority\": \"NORMAL\"
    },
    \"recipe\": {
      \"code\": \"${RECIPE_CODE}\",
      \"name\": \"测试配方\",
      \"totalWeight\": 100,
      \"materials\": [
        {
          \"name\": \"材料A\",
          \"weight\": 50,
          \"unit\": \"g\",
          \"sequence\": 1
        },
        {
          \"name\": \"材料B\",
          \"weight\": 50,
          \"unit\": \"g\",
          \"sequence\": 2
        }
      ]
    }
  }")

HTTP_CODE=$(echo "${TASK_RESPONSE}" | grep "HTTP_CODE:" | cut -d':' -f2)
TASK_BODY=$(echo "${TASK_RESPONSE}" | sed '/HTTP_CODE:/d')

echo ""
echo "=========================================="
echo "响应结果"
echo "=========================================="
echo "HTTP 状态码: ${HTTP_CODE}"
echo ""
echo "响应体:"
echo "${TASK_BODY}" | python3 -m json.tool 2>/dev/null || echo "${TASK_BODY}"
echo ""

# 判断结果
case "${HTTP_CODE}" in
  201)
    echo "✅ 任务下发成功"
    TASK_ID=$(echo "${TASK_BODY}" | grep -o '"receivedTaskId":"[^"]*"' | cut -d'"' -f4)
    if [ -n "${TASK_ID}" ]; then
      echo "接收任务 ID: ${TASK_ID}"
    fi
    ;;
  401)
    echo "❌ 认证失败 (401 Unauthorized)"
    echo ""
    echo "可能原因:"
    echo "  1. API Key 无效或已过期"
    echo "  2. 未携带 X-API-Key 请求头"
    echo "  3. 发送端授权已被撤销"
    echo ""
    echo "解决方案:"
    echo "  重新执行配对流程: ./test-pairing.sh"
    exit 1
    ;;
  400)
    echo "❌ 请求错误 (400 Bad Request)"
    ERROR_CODE=$(echo "${TASK_BODY}" | grep -o '"errorCode":"[^"]*"' | cut -d'"' -f4)
    echo ""
    echo "错误码: ${ERROR_CODE}"
    echo ""
    case "${ERROR_CODE}" in
      "UNAUTHORIZED_SENDER")
        echo "原因: 发送端未授权"
        echo "解决方案: 重新执行配对流程"
        ;;
      "DUPLICATE_TRANSFER")
        echo "原因: 传输 ID 重复"
        echo "解决方案: 使用新的 transferId"
        ;;
      "RECIPE_EXISTS")
        echo "原因: 配方编码已存在"
        echo "解决方案: 更换配方编码或手动删除旧配方"
        ;;
      "UNSUPPORTED_VERSION")
        echo "原因: 协议版本不受支持"
        echo "解决方案: 使用 schemaVersion: \"1.0\""
        ;;
      *)
        echo "请查看响应体了解详细错误信息"
        ;;
    esac
    exit 1
    ;;
  *)
    echo "❌ 未知错误 (HTTP ${HTTP_CODE})"
    exit 1
    ;;
esac

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
