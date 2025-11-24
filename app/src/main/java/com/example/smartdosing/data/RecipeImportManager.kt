package com.example.smartdosing.data

import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.text.Charsets

/**
 * 批量导入统计
 */
data class ImportSummary(
    val total: Int,
    val success: Int,
    val failed: Int,
    val errors: List<String>
)

/**
 * 配方导入管理器，负责解析模板文件并写入仓库
 */
class RecipeImportManager(
    private val recipeRepository: RecipeRepository,
    private val templateRepository: TemplateRepository = TemplateRepository.getInstance()
) {

    companion object {
        private const val TEMPLATE_ID = "standard_recipe_template"
        private const val MATERIAL_PREFIX = "material_line"
    }

    fun importCsvFile(csvBytes: ByteArray): ImportSummary {
        val text = csvBytes.toString(Charsets.UTF_8)
        return importCsvText(text)
    }

    fun importCsvText(csvText: String): ImportSummary {
        val template = currentTemplate()
        val lines = csvText.split(Regex("\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (lines.size <= 1) {
            return ImportSummary(0, 0, 0, listOf("CSV内容为空，请使用模板填写数据"))
        }

        val dataLines = lines.drop(1)
        if (dataLines.isEmpty()) {
            return ImportSummary(0, 0, 0, listOf("CSV中未找到数据行，请保留表头并填写内容"))
        }
        val errors = mutableListOf<String>()
        val requests = mutableListOf<RecipeImportRequest>()
        val materialFields = template.fields.filter { it.key.startsWith(MATERIAL_PREFIX) }.sortedBy { it.order }

        dataLines.forEachIndexed { index, line ->
            val rowNumber = index + 2
            val columns = parseCsvLine(line)
            if (columns.all { it.isBlank() }) {
                return@forEachIndexed
            }
            val valueMap = mutableMapOf<String, String>()
            template.fields.sortedBy { it.order }.forEachIndexed { columnIndex, field ->
                val value = columns.getOrNull(columnIndex)?.trim() ?: ""
                valueMap[field.key] = value
            }

            val name = valueMap["recipe_name"].orEmpty()
            if (name.isBlank()) {
                errors += "第$rowNumber 行缺少配方名称"
                return@forEachIndexed
            }

            val materials = parseInlineMaterials(materialFields, valueMap, rowNumber, errors)
            if (materials.isEmpty()) {
                errors += "第$rowNumber 行没有有效的材料信息"
                return@forEachIndexed
            }

            val description = buildDescription(
                valueMap["recipe_description"].orEmpty(),
                valueMap["material_notes"].orEmpty()
            )

            requests += RecipeImportRequest(
                code = valueMap["recipe_code"].orEmpty(),
                name = name,
                category = valueMap["recipe_category"].orEmpty().ifBlank { "未分类" },
                batchNo = valueMap["batch_no"].orEmpty(),
                description = description,
                materials = materials
            )
        }

        return persistRequests(requests, errors)
    }

    fun importExcel(bytes: ByteArray): ImportSummary {
        val template = currentTemplate()
        val entries = unzipEntries(bytes)
        val summaryXml = entries["xl/worksheets/sheet1.xml"]
        val detailXml = entries["xl/worksheets/sheet2.xml"]
        if (summaryXml == null || detailXml == null) {
            return ImportSummary(0, 0, 0, listOf("Excel模板缺少必要的工作表，请使用最新模板"))
        }

        val summaryFields = template.fields.filterNot { it.key.startsWith(MATERIAL_PREFIX) }.sortedBy { it.order }
        val summaryRows = parseSheetXml(summaryXml)
        val detailRows = parseSheetXml(detailXml)

        val summaryDataRows = summaryRows.drop(1).filter { row -> row.any { it.isNotBlank() } }
        val detailDataRows = detailRows.drop(1).filter { row -> row.any { it.isNotBlank() } }

        if (summaryDataRows.isEmpty()) {
            return ImportSummary(0, 0, 0, listOf("配方信息表没有数据"))
        }
        if (detailDataRows.isEmpty()) {
            return ImportSummary(0, 0, 0, listOf("材料明细表没有数据"))
        }

        val errors = mutableListOf<String>()
        val detailMap = parseDetailRows(detailDataRows, errors)
        val requests = mutableListOf<RecipeImportRequest>()

        summaryDataRows.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 2
            val valueMap = mutableMapOf<String, String>()
            summaryFields.forEachIndexed { columnIndex, field ->
                valueMap[field.key] = row.getOrNull(columnIndex)?.trim() ?: ""
            }

            val name = valueMap["recipe_name"].orEmpty()
            if (name.isBlank()) {
                errors += "配方信息表第$rowNumber 行缺少配方名称"
                return@forEachIndexed
            }

            val key = normalizeKey(valueMap["recipe_code"], name)
            val materials = detailMap[key].orEmpty()
            if (materials.isEmpty()) {
                errors += "未在材料明细表中找到“${valueMap["recipe_code"].orEmpty().ifBlank { name }}”对应的材料记录"
                return@forEachIndexed
            }

            val description = buildDescription(
                valueMap["recipe_description"].orEmpty(),
                valueMap["material_notes"].orEmpty()
            )

            requests += RecipeImportRequest(
                code = valueMap["recipe_code"].orEmpty(),
                name = name,
                category = valueMap["recipe_category"].orEmpty().ifBlank { "未分类" },
                batchNo = valueMap["batch_no"].orEmpty(),
                description = description,
                materials = materials
            )
        }

        return persistRequests(requests, errors)
    }

    private fun parseDetailRows(
        rows: List<List<String>>,
        errors: MutableList<String>
    ): Map<String, List<MaterialImport>> {
        val detailMap = mutableMapOf<String, MutableList<MaterialImport>>()
        rows.forEachIndexed { index, row ->
            val rowNumber = index + 2
            val recipeCode = row.getOrNull(0)?.trim()
            if (recipeCode.isNullOrBlank()) {
                errors += "材料明细表第$rowNumber 行缺少配方编码"
                return@forEachIndexed
            }
            val normalizedKey = normalizeKey(recipeCode, null)
            if (normalizedKey == null) {
                errors += "材料明细表第$rowNumber 行配方编码无效"
                return@forEachIndexed
            }

            val name = row.getOrNull(2)?.trim().orEmpty()
            val weight = row.getOrNull(3)?.trim()?.toDoubleOrNull()
            val unit = row.getOrNull(4)?.trim().orEmpty().ifBlank { "g" }
            val seq = row.getOrNull(1)?.trim()?.toIntOrNull() ?: ((detailMap[normalizedKey]?.size ?: 0) + 1)
            val notes = row.getOrNull(5)?.trim().orEmpty()

            if (name.isBlank() || weight == null) {
                errors += "材料明细表第$rowNumber 行材料名称或重量无效"
                return@forEachIndexed
            }

            val list = detailMap.getOrPut(normalizedKey) { mutableListOf() }
            list += MaterialImport(name = name, weight = weight, unit = unit.ifBlank { "g" }, sequence = seq, notes = notes)
        }
        return detailMap
    }

    private fun parseInlineMaterials(
        materialFields: List<TemplateField>,
        valueMap: Map<String, String>,
        rowNumber: Int,
        errors: MutableList<String>
    ): List<MaterialImport> {
        val materials = mutableListOf<MaterialImport>()

        // 检查是否为垂直展开格式（直接从valueMap获取字段）
        val materialName = valueMap["material_name"].orEmpty()
        val materialCode = valueMap["material_code"].orEmpty()
        val materialWeight = valueMap["material_weight"]?.toDoubleOrNull()
        val materialUnit = valueMap["material_unit"].orEmpty().ifBlank { "g" }
        val materialSeq = valueMap["material_sequence"]?.toIntOrNull() ?: 1
        val materialNotes = valueMap["material_notes"].orEmpty()

        if (materialName.isNotBlank() && materialWeight != null) {
            // 垂直展开格式：直接从字段获取材料信息
            materials += MaterialImport(
                name = materialName,
                code = materialCode,
                weight = materialWeight,
                unit = materialUnit,
                sequence = materialSeq,
                notes = materialNotes
            )
        } else {
            // 内联格式：从material_line字段解析
            materialFields.forEachIndexed { index, field ->
                val value = valueMap[field.key].orEmpty()
                if (value.isBlank()) {
                    return@forEachIndexed
                }
                val parts = value.split(":").map { it.trim() }
                val name = parts.getOrNull(0).orEmpty()
                val weight = parts.getOrNull(1)?.toDoubleOrNull()
                val unit = parts.getOrNull(2).orEmpty().ifBlank { "g" }
                val sequence = parts.getOrNull(3)?.toIntOrNull() ?: (index + 1)

                if (name.isBlank() || weight == null) {
                    errors += "第$rowNumber 行第${index + 1}个材料格式不正确，应为\"名称:重量:单位:序号\""
                    return@forEachIndexed
                }
                materials += MaterialImport(name = name, code = "", weight = weight, unit = unit, sequence = sequence, notes = "")
            }
        }

        return materials
    }

    private fun persistRequests(
        requests: List<RecipeImportRequest>,
        parseErrors: List<String>
    ): ImportSummary {
        val errors = parseErrors.toMutableList()
        var successCount = 0

        requests.forEachIndexed { index, request ->
            try {
                recipeRepository.addRecipe(request)
                successCount++
            } catch (e: Exception) {
                errors += "第${index + 1}条配方导入失败：${e.message ?: "未知错误"}"
            }
        }

        val total = requests.size + parseErrors.size
        val failed = errors.size

        return ImportSummary(
            total = total,
            success = successCount,
            failed = failed,
            errors = errors
        )
    }

    private fun unzipEntries(bytes: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val content = zip.readBytes().toString(Charsets.UTF_8)
                map[entry.name] = content
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return map
    }

    private fun parseSheetXml(xml: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
        rowRegex.findAll(xml).forEach { rowMatch ->
            val cells = cellRegex.findAll(rowMatch.groupValues[1])
                .map { unescapeXml(it.groupValues[1]) }
                .toList()
            rows += cells
        }
        return rows
    }

    private fun unescapeXml(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result += current.toString()
        return result
    }

    private fun normalizeKey(primary: String?, fallback: String?): String? {
        val base = when {
            !primary.isNullOrBlank() -> primary
            !fallback.isNullOrBlank() -> fallback
            else -> null
        }
        return base?.trim()?.uppercase(Locale.getDefault())
    }

    private fun buildDescription(description: String, notes: String): String {
        return listOf(description, notes.takeIf { it.isNotBlank() }?.let { "备注：$it" })
            .filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun currentTemplate(): TemplateDefinition {
        return templateRepository.getTemplateById(TEMPLATE_ID)
            ?: throw IllegalStateException("未找到标准模板，请先初始化模板数据")
    }
}
