package com.example.smartdosing.ui.screens.records

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.data.ConfigurationMaterialRecord
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationRecordStatus
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import com.example.smartdosing.ui.theme.SmartDosingTheme
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * 实验报告详情：科研报告风格重构
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationRecordDetailScreen(
    recordId: String,
    onNavigateBack: () -> Unit = {},
    onReconfigure: (ConfigurationRecord) -> Unit = {},
    onFixError: (ConfigurationRecord) -> Unit = {}
) {
    val repository = remember { ConfigurationRepositoryProvider.recordRepository }
    var record by remember(recordId) { mutableStateOf<ConfigurationRecord?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshDetail() {
        scope.launch {
            try {
                isLoading = true
                loadError = null
                record = repository.fetchRecord(recordId)
                if (record == null) loadError = "未找到该报告"
            } catch (e: Exception) {
                loadError = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(recordId) { refreshDetail() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("实验分析报告", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("LABORATORY ANALYSIS REPORT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 1.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Share/Export */ }) {
                        Icon(Icons.Default.Share, "导出报告")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            loadError != null -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { Text(loadError!!, color = MaterialTheme.colorScheme.error) }
            record != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    ReportHeaderSection(record!!)
                    
                    DeviationChartSection(record!!.materialDetails)
                    
                    MaterialAnalysisSection(record!!.materialDetails)
                    
                    if (record!!.note.isNotBlank()) {
                        NoteSection(record!!.note)
                    }
                    
                    SignatureSection(record!!.operator)
                }
            }
        }
    }
}

@Composable
private fun ReportHeaderSection(record: ConfigurationRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(record.recipeName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("报告单号: ${record.id.take(8).uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(status = record.resultStatus)
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InfoColumn("客户名称", record.customer)
                InfoColumn("配方编码", record.recipeCode)
                InfoColumn("完成时间", record.updatedAt.substringBefore(" "))
            }
        }
    }
}

@Composable
private fun InfoColumn(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeviationChartSection(materials: List<ConfigurationMaterialRecord>) {
    Text("偏差分布分析 (Deviation Analysis)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val centerY = height / 2
                
                // 绘制基准线
                drawLine(Color.Gray.copy(0.3f), Offset(0f, centerY), Offset(width, centerY), strokeWidth = 1.dp.toPx())
                
                // 绘制偏差路径
                if (materials.isNotEmpty()) {
                    val stepX = width / (materials.size - 1).coerceAtLeast(1)
                    val maxDev = materials.maxOf { kotlin.math.abs(it.deviation) }.coerceAtLeast(0.1)
                    val scaleY = (height / 2 - 20.dp.toPx()) / maxDev
                    
                    val path = Path().apply {
                        materials.forEachIndexed { index, material ->
                            val x = index * stepX
                            val y = centerY - (material.deviation * scaleY).toFloat()
                            if (index == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    
                    drawPath(
                        path = path,
                        color = Color(0xFF4CAF50),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    
                    // 绘制数据点
                    materials.forEachIndexed { index, material ->
                        val x = index * stepX
                        val y = centerY - (material.deviation * scaleY).toFloat()
                        drawCircle(
                            color = if (material.isOutOfTolerance) Color.Red else Color(0xFF4CAF50),
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialAnalysisSection(materials: List<ConfigurationMaterialRecord>) {
    Text("组分精准度明细 (Material Precision)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        materials.forEach { material ->
            MaterialAnalysisRow(material)
        }
    }
}

@Composable
private fun MaterialAnalysisRow(material: ConfigurationMaterialRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(material.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(material.code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${com.example.smartdosing.utils.FormatUtils.formatWeight(material.actualWeight)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("/", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("${com.example.smartdosing.utils.FormatUtils.formatWeight(material.targetWeight)} g", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            val devPercent = (material.deviation / material.targetWeight * 100)
            Text(
                text = "${if (material.deviation > 0) "+" else ""}${String.format("%.2f", devPercent)}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (material.isOutOfTolerance) Color.Red else Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NoteSection(note: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("实验备注 (Notes)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Text(note, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SignatureSection(operator: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("操作员电子签名 (E-Signature)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(
                text = operator,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Box(Modifier.width(120.dp).height(1.dp).background(Color.LightGray))
        }
    }
}

@Composable
private fun StatusPill(status: ConfigurationRecordStatus) {
    val color = when(status) {
        ConfigurationRecordStatus.COMPLETED -> Color(0xFF4CAF50)
        ConfigurationRecordStatus.IN_REVIEW -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
