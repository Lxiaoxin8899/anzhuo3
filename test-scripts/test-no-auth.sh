#!/bin/bash
# 测试无认证模式下的任务接口

DEVICE_IP="${DEVICE_IP:-192.168.1.50}"
DEVICE_PORT="${DEVICE_PORT:-8080}"
BASE_URL="http://${DEVICE_IP}:${DEVICE_PORT}"

echo "=========================================="
echo "测试无认证模式 - 任务下发"
echo "=========================================="
echo "设备地址: ${BASE_URL}"
echo "=========================================="
echo ""

# 生成测试数据
TIMESTAMP=$(date +%s)000
TRANSFER_ID="TF-${TIMESTAMP}-NOAUTH"
RECIPE_CODE="TEST-NOAUTH-$(date +%s)"

echo "测试 1: 不携带 X-API-Key 请求头（应该成功）"
echo "-------------------------------------------"

RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
  -X POST "${BASE_URL}/api/transfer/task" \
  -H "Content-Type: application/json" \
  -d "{
    \"schemaVersion\": \"1.0\",
    \"transferId\": \"${TRANSFER_ID}\",
    \"senderUID\": \"BACKEND-NOAUTH-TEST\",
    \"senderName\": \"无认证测试系统\",
    \"timestamp\": ${TIMESTAMP},
    \"task\": {
      \"title\": \"无认证测试任务\",
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

HTTP_CODE=$(echo "${RESPONSE}" | grep "HTTP_CODE:" | cut -d':' -f2)
BODY=$(echo "${RESPONSE}" | sed '/HTTP_CODE:/d')

echo "HTTP 状态码: ${HTTP_CODE}"
echo ""
echo "响应体:"
echo "${BODY}" | python3 -m json.tool 2>/dev/null || echo "${BODY}"
echo ""

if [ "${HTTP_CODE}" = "201" ]; then
  echo "✅ 测试通过：无需认证即可下发任务"
  echo ""
  echo "=========================================="
  echo "认证已成功移除"
  echo "=========================================="
  echo ""
  echo "后台系统现在可以直接调用接口，无需配对流程："
  echo ""
  echo "curl -X POST http://${DEVICE_IP}:${DEVICE_PORT}/api/transfer/task \\"
  echo "  -H \"Content-Type: application/json\" \\"
  echo "  -d '{...}'"
  echo ""
elif [ "${HTTP_CODE}" = "401" ]; then
  echo "❌ 测试失败：仍然需要认证"
  echo ""
  echo "可能原因："
  echo "  1. 代码修改未生效，需要重新编译安装"
  echo "  2. 设备端应用未重启"
  echo ""
  echo "解决方案："
  echo "  ./gradlew installDebug"
else
  echo "⚠️  返回了其他错误 (HTTP ${HTTP_CODE})"
  echo ""
  echo "请检查响应体了解详细错误信息"
fi
