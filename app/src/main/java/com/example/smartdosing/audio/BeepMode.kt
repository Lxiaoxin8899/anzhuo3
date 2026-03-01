package com.example.smartdosing.audio

/**
 * 提示音模式
 */
enum class BeepMode(val displayName: String) {
    OFF("关闭"),
    PROGRESSIVE("渐进模式"),
    THRESHOLD("阈值模式")
}
