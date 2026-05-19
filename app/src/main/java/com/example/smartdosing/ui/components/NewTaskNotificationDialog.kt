package com.example.smartdosing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartdosing.database.entities.ReceivedTaskEntity

/**
 * 局域网新任务弹窗。
 * 是否立即显示由上层根据当前页面决定，避免打断配置作业。
 */
@Composable
fun NewTaskNotificationDialog(
    task: ReceivedTaskEntity,
    onDismiss: () -> Unit,
    onViewTasks: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "收到新任务",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("任务名称：${task.title}")
                Text("配方名称：${task.recipeName}")
                Text("配方编码：${task.recipeCode}")
                Text("发送设备：${task.senderName}")
                Text("任务数量：${task.quantity} ${task.unit}")
                task.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text("备注：$note")
                }
                Text(
                    text = "如果当前正处于实验配置界面，系统会等你离开后再提醒，避免打断当前工作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onViewTasks) {
                Text("查看任务")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}
