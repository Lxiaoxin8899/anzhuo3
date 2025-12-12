package com.example.smartdosing.bluetooth

import com.example.smartdosing.bluetooth.model.WeightData

class OhausDataParser {
    private val buffer = StringBuilder()

    // 奥豪斯格式: "     15.06 kg  " 或 "     15.06 kg ?" (不稳定时有?)
    private val pattern = Regex("""^\s*([+-]?\d+\.?\d*)\s*(kg|g|lb|oz)\s*(\?)?""", RegexOption.IGNORE_CASE)

    fun parse(data: ByteArray): WeightData? {
        buffer.append(String(data, Charsets.UTF_8))

        // 查找完整行 (以 \r\n 结尾)
        val lineEnd = buffer.indexOf("\r\n")
        if (lineEnd == -1) {
            // 检查是否只有 \n
            val lfIndex = buffer.indexOf("\n")
            if (lfIndex == -1) return null

            val line = buffer.substring(0, lfIndex)
            buffer.delete(0, lfIndex + 1)
            return parseLine(line)
        }

        val line = buffer.substring(0, lineEnd)
        buffer.delete(0, lineEnd + 2)

        return parseLine(line)
    }

    private fun parseLine(line: String): WeightData? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        val match = pattern.find(trimmed) ?: return null

        val valueStr = match.groupValues[1]
        val value = valueStr.toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()
        val isStable = match.groupValues.getOrNull(3)?.isEmpty() != false

        return WeightData(
            value = value,
            unit = unit,
            isStable = isStable,
            rawString = line
        )
    }

    fun clear() {
        buffer.clear()
    }
}
