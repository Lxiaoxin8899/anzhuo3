package com.example.smartdosing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartdosing.data.device.PairingSession
import kotlin.math.max

/**
 * 设备端配对确认弹窗。
 * 中文注释：该弹窗只负责展示与收集用户决策，确认/拒绝的协议处理由上层复用 Ktor 接口完成。
 */
@Composable
fun PairingConfirmationDialog(
    session: PairingSession,
    remainingSeconds: Long,
    isProcessing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            // 配对请求必须明确确认或拒绝，避免误触空白区域导致发送端一直等待。
        },
        title = {
            Text(
                text = "确认设备配对",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("发送端名称：${session.senderName}")
                Text("发送端 IP：${session.senderIP}")
                Text(
                    text = "请确认是否允许该后台系统向本设备下发任务。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "剩余时间：${max(0L, remainingSeconds)} 秒",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                enabled = !isProcessing && remainingSeconds > 0
            ) {
                Text(if (isProcessing) "处理中" else "确认")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onReject,
                enabled = !isProcessing
            ) {
                Text("拒绝")
            }
        }
    )
}
