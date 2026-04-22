package com.lannooo.audiocenter

import com.lannooo.audiocenter.client.Message

data class AudioCenterUiState(
    val ip: String = "10.98.67.186",
    val port: String = "6666",
    val networkStatus: ConnectionStatus = ConnectionStatus.NONE,
    val showDialog: Boolean = false,
    val networkMessage: String = "",
    val manualUltrasonicOverride: Boolean = false,
    val sampleRateHz: String = "48000",
    val startFreqHz: String = "20000",
    val endFreqHz: String = "22000",
    val chirpDurationMs: String = "40",
    val idleDurationMs: String = "0",
    val amplitude: String = "0.30",
    val windowType: String = "hann",
    val repeatChirp: Boolean = true,
)

data class MessageRecord(
    val who: Char,
    val type: Message.MessageType,
    val shortContent: String,
    val timestamp: String
)

enum class ConnectionStatus {
    NONE,
    CONNECTING,
    READY,
    LOST
}
