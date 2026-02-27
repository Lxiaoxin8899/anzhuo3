#!/bin/bash
# SmartDosing 设备配对测试脚本
# 用途: 自动化测试配对流程和 API Key 获取

set -e

# 配置参数
DEVICE_IP="${DEVICE_IP:-192.168.1.50}"
DEVICE_PORT="${DEVICE_PORT:-8080}"
SENDER_UID="${SENDER_UID:-BACKEND-$(date +%s)}"
SENDER_NAME="${SENDER_NAME:-测试后台系统}"
SENDER_IP="${SENDER_IP:-192.168.1.100}"
SENDER_PORT="${SENDER_PORT:-3000}"
CALLBACK_URL="${CALLBACK_URL:-http://${SENDER_IP}:${SENDER_PORT}}"

BASE_URL="http://${DEVICE_IP}:${DEVICE_PORT}"

echo "=========================================="
echo "SmartDosing 设备配对测试"
echo "=========================================="
echo "设备地址: ${BASE_URL}"
echo "发送端 UID: ${SENDER_UID}"
echo "发送端名称: ${SENDER_NAME}"
echo "回调地址: ${CALLBACK_URL}"
echo "=========================================="
echo ""

# 步骤 1: 发起配对请求
echo "步骤 1: 发起配对请求..."
PAIR_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/device/pair" \
  -H "Content-Type: application/json" \
  -d "{
    \"senderUID\": \"${SENDER_UID}\",
    \"senderName\": \"${SENDER_NAME}\",
    \"senderIP\": \"${SENDER_IP}\",
    \"senderPort\": ${SENDER_PORT},
    \"callbackBaseUrl\": \"${CALLBACK_URL}\",
    \"timestamp\": $(date +%s)000
  }")

echo "响应: ${PAIR_RESPONSE}"
echo ""

# 提取 pairingId
PAIRING_ID=$(echo "${PAIR_RESPONSE}" | grep -o '"pairingId":"[^"]*"' | cut -d'"' -f4)

if [ -z "${PAIRING_ID}" ]; then
  echo "❌ 错误: 未能获取 pairingId"
  echo "完整响应: ${PAIR_RESPONSE}"
  exit 1
fi

echo "✅ 配对请求已发送"
echo "配对 ID: ${PAIRING_ID}"
echo ""

# 步骤 2: 等待设备确认
echo "步骤 2: 等待设备屏幕确认..."
echo "⚠️  请在设备屏幕上点击「确认配对」按钮"
echo ""
echo "开始轮询配对结果 (最多等待 60 秒)..."

MAX_ATTEMPTS=20
ATTEMPT=0
API_KEY=""

while [ ${ATTEMPT} -lt ${MAX_ATTEMPTS} ]; do
  ATTEMPT=$((ATTEMPT + 1))
  echo -n "尝试 ${ATTEMPT}/${MAX_ATTEMPTS}... "

  POLL_RESPONSE=$(curl -s "${BASE_URL}/api/device/pair/${PAIRING_ID}")
  STATUS=$(echo "${POLL_RESPONSE}" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

  echo "状态: ${STATUS}"

  case "${STATUS}" in
    "WAITING")
      sleep 3
      ;;
    "APPROVED")
      echo ""
      echo "✅ 配对成功！"
      API_KEY=$(echo "${POLL_RESPONSE}" | grep -o '"apiKey":"[^"]*"' | cut -d'"' -f4)
      DEVICE_UID=$(echo "${POLL_RESPONSE}" | grep -o '"deviceUID":"[^"]*"' | cut -d'"' -f4)
      DEVICE_NAME=$(echo "${POLL_RESPONSE}" | grep -o '"deviceName":"[^"]*"' | cut -d'"' -f4)

      echo "=========================================="
      echo "配对信息"
      echo "=========================================="
      echo "API Key: ${API_KEY}"
      echo "设备 UID: ${DEVICE_UID}"
      echo "设备名称: ${DEVICE_NAME}"
      echo "=========================================="
      echo ""

      # 保存到文件
      echo "${API_KEY}" > .api_key
      echo "API Key 已保存到 .api_key 文件"
      echo ""
      break
      ;;
    "REJECTED")
      echo ""
      echo "❌ 配对被拒绝"
      exit 1
      ;;
    "EXPIRED")
      echo ""
      echo "❌ 配对请求已过期"
      exit 1
      ;;
    *)
      echo ""
      echo "❌ 未知状态: ${STATUS}"
      echo "完整响应: ${POLL_RESPONSE}"
      exit 1
      ;;
  esac
done

if [ -z "${API_KEY}" ]; then
  echo ""
  echo "❌ 超时: 60 秒内未完成配对"
  exit 1
fi

# 步骤 3: 测试 API Key
echo "步骤 3: 测试 API Key..."
PING_RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
  -X POST "${BASE_URL}/api/device/ping" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d "{
    \"senderUID\": \"${SENDER_UID}\",
    \"timestamp\": $(date +%s)000
  }")

HTTP_CODE=$(echo "${PING_RESPONSE}" | grep "HTTP_CODE:" | cut -d':' -f2)
PING_BODY=$(echo "${PING_RESPONSE}" | sed '/HTTP_CODE:/d')

echo "HTTP 状态码: ${HTTP_CODE}"
echo "响应体: ${PING_BODY}"
echo ""

if [ "${HTTP_CODE}" = "200" ]; then
  echo "✅ API Key 验证成功"
  echo ""
  echo "=========================================="
  echo "配对完成！"
  echo "=========================================="
  echo "您现在可以使用以下命令测试任务下发:"
  echo ""
  echo "export API_KEY=\"${API_KEY}\""
  echo "export DEVICE_IP=\"${DEVICE_IP}\""
  echo "./test-task.sh"
  echo ""
else
  echo "❌ API Key 验证失败"
  exit 1
fi
