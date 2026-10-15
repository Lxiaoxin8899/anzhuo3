package com.example.smartdosing.data

import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * 配方导入解析结果
 *
 * [rejected] 非空表示文件整体不可导入（表头缺失、内容为空等），此时不应写库。
 */
data class RecipeImportParseResult(
    val requests: List<RecipeImportRequest>,
    val errors: List<String>,
    val rejected: ImportSummary? = null
)

/**
 * 配方导入解析器（纯函数，不依赖 Android 与数据库）
 *
 * 支持“垂直展开格式”：同一配方的每种材料占一行，按配方编码（缺省时按名称）分组。
 */
class RecipeImportParser(private val template: TemplateDefinition) {

    private val orderedFields = template.fields.sortedBy { it.order }

    fun parseCsv(csvText: String): RecipeImportParseResult {
        val lines = csvText.removePrefix("\uFEFF")
            .split(Regex("\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.size <= 1) {
            return rejected("CSV内容为空，请使用模板填写数据")
        }

        missingHeaders(parseCsvLine(lines.first()))?.let { missing ->
            return rejected("CSV表头缺少：$missing，请使用最新模板")
        }

        val dataLines = lines.drop(1)
        if (dataLines.isEmpty()) {
            return rejected("CSV中未找到数据行，请保留表头并填写内容")
        }

        val rows = dataLines.mapIndexedNotNull { index, line ->
            val columns = parseCsvLine(line)
            if (columns.all { it.isBlank() }) null else (index + 2) to toValueMap(columns)
        }
        return buildRequests(rows)
    }

    fun parseExcel(bytes: ByteArray): RecipeImportParseResult {
        val entries = unzipEntries(bytes)
        val sheetXml = entries["xl/worksheets/sheet1.xml"]
            ?: return rejected("Excel模板缺少工作表，请使用最新模板")
        val sharedStrings = parseSharedStringsXml(entries["xl/sharedStrings.xml"])

        val sheetRows = parseSheetXml(sheetXml, sharedStrings)
        missingHeaders(sheetRows.firstOrNull().orEmpty())?.let { missing ->
            return rejected("Excel表头缺少：$missing，请使用最新模板")
        }

        val dataRows = sheetRows.drop(1).filter { row -> row.any { it.isNotBlank() } }
        if (dataRows.isEmpty()) {
            return rejected("Excel表格没有数据")
        }

        val rows = dataRows.mapIndexed { index, row -> (index + 2) to toValueMap(row) }
        return buildRequests(rows)
    }

    private fun rejected(message: String) = RecipeImportParseResult(
        requests = emptyList(),
        errors = emptyList(),
        rejected = ImportSummary(0, 0, 0, listOf(message))
    )

    private fun missingHeaders(headerColumns: List<String>): String? {
        val missing = orderedFields
            .filter { it.required }
            .map { it.label }
            .filter { required -> headerColumns.none { it.trim() == required } }
        return missing.takeIf { it.isNotEmpty() }?.joinToString("、")
    }

    private fun toValueMap(columns: List<String>): Map<String, String> =
        orderedFields.mapIndexed { columnIndex, field ->
            field.key to (columns.getOrNull(columnIndex)?.trim() ?: "")
        }.toMap()

    private fun buildRequests(rows: List<Pair<Int, Map<String, String>>>): RecipeImportParseResult {
        val errors = mutableListOf<String>()
        val requests = mutableListOf<RecipeImportRequest>()

        val recipeGroups = rows.groupBy { (_, valueMap) ->
            val code = valueMap["recipe_code"].orEmpty().trim()
            val name = valueMap["recipe_name"].orEmpty().trim()
            normalizeKey(code, name) ?: name
        }

        recipeGroups.forEach { (groupKey, groupRows) ->
            if (groupKey.isBlank()) {
                groupRows.forEach { (rowNumber, _) ->
                    errors += "第$rowNumber 行缺少配方编码或配方名称"
                }
                return@forEach
            }

            val (firstRowNumber, firstRow) = groupRows.first()
            val name = firstRow["recipe_name"].orEmpty()
            if (name.isBlank()) {
                errors += "第$firstRowNumber 行缺少配方名称"
                return@forEach
            }

            val materials = mutableListOf<MaterialImport>()
            groupRows.forEachIndexed { index, (rowNumber, valueMap) ->
                val materialName = valueMap["material_name"].orEmpty()
                val materialWeight = valueMap["material_weight"]?.toDoubleOrNull()
                if (materialName.isBlank() || materialWeight == null) {
                    errors += "第$rowNumber 行材料名称或重量无效"
                    return@forEachIndexed
                }
                materials += MaterialImport(
                    name = materialName,
                    code = valueMap["material_code"].orEmpty(),
                    weight = materialWeight,
                    unit = valueMap["material_unit"].orEmpty().ifBlank { "g" },
                    sequence = valueMap["material_sequence"]?.toIntOrNull() ?: (index + 1),
                    notes = valueMap["material_notes"].orEmpty()
                )
            }

            if (materials.isEmpty()) {
                errors += "配方\"$name\"没有有效的材料信息"
                return@forEach
            }

            // 将所有材料的备注合并为配方描述
            val allNotes = materials.map { it.notes }.filter { it.isNotBlank() }.joinToString("; ")
            val templateNote = firstRow["task_note"].orEmpty()
            val mergedDescription = when {
                templateNote.isNotBlank() && allNotes.isNotBlank() -> "$templateNote；$allNotes"
                templateNote.isNotBlank() -> templateNote
                else -> allNotes
            }

            requests += RecipeImportRequest(
                code = firstRow["recipe_code"].orEmpty(),
                name = name,
                category = normalizeCategory(firstRow["recipe_category"]),
                subCategory = "",
                customer = firstRow["recipe_customer"].orEmpty(),
                salesOwner = firstRow["recipe_sales_owner"].orEmpty(),
                perfumer = firstRow["recipe_perfumer"].orEmpty(),
                batchNo = firstRow["recipe_design_time"].orEmpty(),
                version = "1.0",
                description = mergedDescription,
                materials = materials,
                status = RecipeStatus.ACTIVE,
                priority = parseTaskPriority(firstRow["task_priority"].orEmpty()),
                tags = parseTagsFromValue(firstRow["task_tags"].orEmpty()),
                creator = firstRow["designer"].orEmpty().ifBlank { "IMPORT" },
                reviewer = ""
            )
        }

        return RecipeImportParseResult(requests, errors)
    }

    // =================================
    // 解析辅助方法
    // =================================

    private fun unzipEntries(bytes: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                map[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return map
    }

    private fun parseSheetXml(xml: String, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
        rowRegex.findAll(xml).forEach { rowMatch ->
            val cellValues = mutableMapOf<Int, String>()
            cellRegex.findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
                val attrs = cellMatch.groupValues[1]
                val content = cellMatch.groupValues[2]
                val reference = Regex("r=\\\"([A-Z]+)\\d+\\\"").find(attrs)?.groupValues?.get(1)
                val columnIndex = reference?.let { columnIndexFromRef(it) } ?: cellValues.size
                val cellType = Regex("t=\\\"(\\w+)\\\"").find(attrs)?.groupValues?.get(1)
                val value = when {
                    cellType == "s" -> {
                        val index = Regex("<v>(.*?)</v>").find(content)?.groupValues?.get(1)?.toIntOrNull()
                        index?.let { sharedStrings.getOrNull(it) } ?: ""
                    }
                    content.contains("<is>") -> {
                        Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                            .find(content)
                            ?.groupValues
                            ?.get(1)
                            ?.let(::unescapeXml) ?: ""
                    }
                    else -> Regex("<v>(.*?)</v>").find(content)?.groupValues?.get(1)?.let(::unescapeXml) ?: ""
                }
                cellValues[columnIndex] = value
            }
            if (cellValues.isEmpty()) {
                rows.add(emptyList())
            } else {
                val maxIndex = cellValues.keys.maxOrNull() ?: -1
                val rowData = MutableList(maxIndex + 1) { "" }
                cellValues.forEach { (idx, value) ->
                    if (idx in rowData.indices) rowData[idx] = value
                }
                rows.add(rowData)
            }
        }
        return rows
    }

    private fun unescapeXml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    private fun parseSharedStringsXml(xml: String?): List<String> {
        if (xml.isNullOrBlank()) return emptyList()
        val siRegex = Regex("<si[^>]*>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
        val tRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
        return siRegex.findAll(xml).map { match ->
            tRegex.findAll(match.groupValues[1]).joinToString("") { unescapeXml(it.groupValues[1]) }
        }.toList()
    }

    private fun columnIndexFromRef(columnRef: String): Int {
        var result = 0
        columnRef.forEach { ch -> result = result * 26 + (ch.code - 'A'.code + 1) }
        return result - 1
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

    // 将配方分类限定为“烟油/辅料”两种，便于后续过滤
    private fun normalizeCategory(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return "烟油"
        return when {
            text.equals("烟油", ignoreCase = true) || text.contains("烟") -> "烟油"
            else -> "辅料"
        }
    }

    private fun parseTagsFromValue(tagsString: String): List<String> =
        if (tagsString.isBlank()) {
            emptyList()
        } else {
            tagsString.split(",", ";", "，", "；").map { it.trim() }.filter { it.isNotEmpty() }
        }

    private fun parseTaskPriority(raw: String): RecipePriority {
        if (raw.isBlank()) return RecipePriority.NORMAL
        return when (raw.trim().uppercase(Locale.getDefault())) {
            "LOW", "低" -> RecipePriority.LOW
            "HIGH", "高" -> RecipePriority.HIGH
            "URGENT", "加急" -> RecipePriority.URGENT
            else -> RecipePriority.NORMAL
        }
    }
}
