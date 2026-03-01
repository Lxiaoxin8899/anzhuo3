package com.example.smartdosing.audio

import android.media.ToneGenerator

/**
 * 提示音模式
 */
enum class BeepMode(val displayName: String) {
    OFF("关闭"),
    PROGRESSIVE("渐进模式"),
    THRESHOLD("阈值模式")
}

/**
 * 可选提示音音调类型
 */
enum class BeepToneType(val displayName: String, val toneId: Int) {
    BEEP("标准提示", ToneGenerator.TONE_PROP_BEEP),
    BEEP2("双音提示", ToneGenerator.TONE_PROP_BEEP2),
    ACK("确认音", ToneGenerator.TONE_PROP_ACK),
    NACK("警告音", ToneGenerator.TONE_PROP_NACK),
    DTMF_0("数字音", ToneGenerator.TONE_DTMF_0),
    SUP_DIAL("拨号音", ToneGenerator.TONE_SUP_DIAL)
}
