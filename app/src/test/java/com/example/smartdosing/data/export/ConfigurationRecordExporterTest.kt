package com.example.smartdosing.data.export

import com.example.smartdosing.data.ConfigurationMaterialRecord
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationRecordStatus
import com.example.smartdosing.ui.screens.records.filterRecordsByKeyword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationRecordExporterTest {

    private val record = ConfigurationRecord(
        id = "CR-1",
        taskId = "T-1",
        recipeId = "R-1",
        recipeName = "薄荷, 加强版",
        recipeCode = "MINT-01",
        category = "烟油",
        operator = "张三",
        quantity = 100.0,
        unit = "g",
        actualQuantity = 99.5,
        customer = "A客户",
        salesOwner = "李四",
        resultStatus = ConfigurationRecordStatus.COMPLETED,
        updatedAt = "2026-10-15 10:00",
        note = "说明含\"引号\"",
        materialDetails = listOf(
            ConfigurationMaterialRecord(2, "丙二醇", "PG", 80.0, 79.6, "g", -0.4, false),
            ConfigurationMaterialRecord(1, "薄荷醇", "MT", 20.0, 19.9, "g", -0.1, true)
        )
    )

    @Test
    fun `CSV 带 BOM 正确转义 并按序号输出材料`() {
        val lines = ConfigurationRecordExporter.toCsv(record).split("\r\n")

        assertTrue(lines.first().startsWith("\uFEFF报告单号,CR-1"))
        assertTrue(lines.contains("配方名称,\"薄荷, 加强版\""))
        assertTrue(lines.contains("备注,\"说明含\"\"引号\"\"\""))
        assertTrue(lines.contains("目标数量,100.000 g"))

        val header = lines.indexOfFirst { it.startsWith("序号,") }
        assertEquals("1,薄荷醇,MT,20.000,19.900,g,-0.100,-0.50,是", lines[header + 1])
        assertEquals("2,丙二醇,PG,80.000,79.600,g,-0.400,-0.50,否", lines[header + 2])
    }

    @Test
    fun `档案搜索匹配名称 编码 客户 操作员 忽略大小写`() {
        val other = record.copy(id = "CR-2", recipeName = "草莓", recipeCode = "BERRY", customer = "B客户", operator = "王五")
        val records = listOf(record, other)

        assertEquals(listOf("CR-1"), filterRecordsByKeyword(records, "mint").map { it.id })
        assertEquals(listOf("CR-2"), filterRecordsByKeyword(records, " 王五 ").map { it.id })
        assertEquals(listOf("CR-2"), filterRecordsByKeyword(records, "b客户").map { it.id })
        assertEquals(2, filterRecordsByKeyword(records, "").size)
        assertTrue(filterRecordsByKeyword(records, "不存在").isEmpty())
    }
}
