package com.example.smartdosing.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 配方库启动实验时的重量输入对话框
 * 用于在配方管理中启动实验时，输入本次实验的总重量
 */
@Composable
fun RecipeWeightInputDialog(
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
    onViewOnly: () -> Unit
) {
    var weightInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("设置配置总量", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "请输入本次实验需要配置的总重量，系统将按配方比例自动计算每种材料的目标重量。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = {
                        weightInput = it
                        inputError = null
                    },
                    label = { Text("总重量 (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = inputError != null,
                    supportingText = inputError?.let { err -> { Text(err) } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onViewOnly) {
                    Text("仅查看配方详情")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val weight = weightInput.toDoubleOrNull()
                if (weight == null || weight <= 0) {
                    inputError = "请输入有效的正数"
                } else {
                    onConfirm(weight)
                }
            }) {
                Text("开始实验")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
