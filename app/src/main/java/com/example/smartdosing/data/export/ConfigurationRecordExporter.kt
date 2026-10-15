package com.example.smartdosing.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.smartdosing.data.ConfigurationRecord
import java.io.File
import java.util.Locale

/**
 * 实验记录导出：生成 CSV 并通过系统分享面板发送
 */
object ConfigurationRecordExporter {

    private const val EXPORT_DIR = "exports"

    /**
     * 生成 CSV 文本。带 UTF-8 BOM，保证 Excel 直接打开时中文不乱码。
     */
    fun toCsv(record: ConfigurationRecord): String = buildString {
        append('\uFEFF')
        appendRow("报告单号", record.id)
        appendRow("配方名称", record.recipeName)
        appendRow("配方编码", record.recipeCode)
        appendRow("客户", record.customer)
        appendRow("操作员", record.operator)
        appendRow("完成时间", record.updatedAt)
        appendRow("目标数量", "${formatNumber(record.quantity)} ${record.unit}")
        appendRow("实际数量", "${formatNumber(record.actualQuantity)} ${record.unit}")
        if (record.note.isNotBlank()) {
            appendRow("备注", record.note)
        }
        append("\r\n")
        appendRow("序号", "材料名称", "材料编码", "目标重量", "实际重量", "单位", "偏差", "偏差率(%)", "是否超差")
        record.materialDetails.sortedBy { it.sequence }.forEach { material ->
            val deviationPercent = if (material.targetWeight != 0.0) {
                material.deviation / material.targetWeight * 100
            } else {
                0.0
            }
            appendRow(
                material.sequence.toString(),
                material.name,
                material.code,
                formatNumber(material.targetWeight),
                formatNumber(material.actualWeight),
                material.unit,
                formatNumber(material.deviation),
                String.format(Locale.US, "%.2f", deviationPercent),
                if (material.isOutOfTolerance) "是" else "否"
            )
        }
    }

    /**
     * 写入缓存目录并拉起系统分享
     */
    fun share(context: Context, record: ConfigurationRecord) {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val safeCode = record.recipeCode.ifBlank { record.id }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "record_${safeCode}_${record.id.take(8)}.csv")
        file.writeText(toCsv(record), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "实验报告 - ${record.recipeName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "导出实验报告").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun StringBuilder.appendRow(vararg cells: String) {
        append(cells.joinToString(",") { escape(it) })
        append("\r\n")
    }

    private fun escape(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun formatNumber(value: Double): String = String.format(Locale.US, "%.3f", value)
}
