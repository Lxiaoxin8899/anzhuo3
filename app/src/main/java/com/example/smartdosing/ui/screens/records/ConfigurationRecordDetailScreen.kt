package com.example.smartdosing.ui.screens.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationRecordSampleData
import com.example.smartdosing.ui.theme.SmartDosingTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationRecordDetailScreen(
    recordId: String,
    onNavigateBack: () -> Unit = {},
    onReconfigure: (ConfigurationRecord) -> Unit = {},
    onFixError: (ConfigurationRecord) -> Unit = {}
) {
    val record = remember(recordId) {
        ConfigurationRecordSampleData.records().find { it.id == recordId }
    } ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配置详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DetailCard(record = record)
            ActionCard(
                record = record,
                onReconfigure = onReconfigure,
                onFixError = onFixError
            )
        }
    }
}

@Composable
private fun DetailCard(record: ConfigurationRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = record.recipeName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = "任务 ${record.taskId} · 状态 ${record.resultStatus}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow(label = "配方编码", value = record.recipeCode)
            InfoRow(label = "调香师", value = record.operator)
            InfoRow(label = "客户", value = record.customer)
            InfoRow(label = "业务员", value = record.salesOwner)
            InfoRow(label = "目标产量", value = "${record.quantity} ${record.unit}")
            InfoRow(label = "实际产量", value = "${record.actualQuantity} ${record.unit}")
            InfoRow(label = "更新时间", value = record.updatedAt)
            if (record.note.isNotBlank()) {
                Text(
                    text = "备注：${record.note}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    record: ConfigurationRecord,
    onReconfigure: (ConfigurationRecord) -> Unit,
    onFixError: (ConfigurationRecord) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "可复用该配方快速再次配置，或在发现偏差时立即进入纠错流程。",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onReconfigure(record) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("再次配置")
                }
                Button(
                    onClick = { onFixError(record) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("纠错处理")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfigurationRecordDetailScreenPreview() {
    SmartDosingTheme {
        val record = ConfigurationRecordSampleData.records().first()
        ConfigurationRecordDetailScreen(recordId = record.id)
    }
}
