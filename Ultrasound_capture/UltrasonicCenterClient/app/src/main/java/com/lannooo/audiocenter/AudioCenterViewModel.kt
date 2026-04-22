package com.lannooo.audiocenter

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lannooo.audiocenter.client.ClientService.ClientBinder
import com.lannooo.audiocenter.client.Message
import com.lannooo.audiocenter.client.MessageListener
import com.lannooo.audiocenter.tool.AppUtil.currentDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioCenterViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AudioCenterUiState())
    val uiState: StateFlow<AudioCenterUiState> = _uiState.asStateFlow()
    private val _messageRecords = mutableStateListOf(
        MessageRecord('C', Message.MessageType.NOTIFICATION, "Hello, world!", currentDateTime())
    )
    val messageRecords: List<MessageRecord>
        get() = _messageRecords

    fun updateIp(it: String) = _uiState.update { s -> s.copy(ip = it) }
    fun updatePort(it: String) = _uiState.update { s -> s.copy(port = it) }
    fun updateDialog(show: Boolean) = _uiState.update { s -> s.copy(showDialog = show) }
    fun updateManualUltrasonicOverride(it: Boolean) = _uiState.update { s -> s.copy(manualUltrasonicOverride = it) }
    fun updateSampleRateHz(it: String) = _uiState.update { s -> s.copy(sampleRateHz = it) }
    fun updateStartFreqHz(it: String) = _uiState.update { s -> s.copy(startFreqHz = it) }
    fun updateEndFreqHz(it: String) = _uiState.update { s -> s.copy(endFreqHz = it) }
    fun updateChirpDurationMs(it: String) = _uiState.update { s -> s.copy(chirpDurationMs = it) }
    fun updateIdleDurationMs(it: String) = _uiState.update { s -> s.copy(idleDurationMs = it) }
    fun updateAmplitude(it: String) = _uiState.update { s -> s.copy(amplitude = it) }
    fun updateWindowType(it: String) = _uiState.update { s -> s.copy(windowType = it) }
    fun updateRepeatChirp(it: Boolean) = _uiState.update { s -> s.copy(repeatChirp = it) }
    fun updateRouteOutputDeviceId(it: String) = _uiState.update { s -> s.copy(routeOutputDeviceId = it) }
    fun updateRouteInputDeviceId(it: String) = _uiState.update { s -> s.copy(routeInputDeviceId = it) }

    private fun addMessageRecord(fromMe: Boolean, type: Message.MessageType, content: String) {
        val record = MessageRecord(if (fromMe) 'C' else 'S', type, content, currentDateTime())
        _messageRecords.add(record)
        if (_messageRecords.size >= 32) {
            _messageRecords.removeFirst()
        }
    }

    fun sendTestMessage() {
        viewModelScope.launch(Dispatchers.IO) {
            binder?.sendRegisterMessage()
        }
    }

    fun refreshRouteDiagnostics() {
        val currentBinder = binder ?: return
        _uiState.update { currentState ->
            currentState.copy(
                routePresetName = currentBinder.routePresetName,
                routeDeviceSummary = currentBinder.routeDeviceIdentitySummary,
                routeCalibrationStatus = currentBinder.routeCalibrationStatus,
                routeOutputDeviceId = currentBinder.savedRouteOutputDeviceId,
                routeInputDeviceId = currentBinder.savedRouteInputDeviceId,
                routeDiagnosticsText = currentBinder.routeDiagnosticsText
            )
        }
    }

    fun saveRouteCalibration() {
        val currentBinder = binder ?: return
        val state = _uiState.value
        currentBinder.saveRouteCalibration(state.routeOutputDeviceId, state.routeInputDeviceId)
        refreshRouteDiagnostics()
        _uiState.update { currentState ->
            currentState.copy(showDialog = true, networkMessage = "Route calibration saved on device.")
        }
    }

    fun applyUltrasonicConfig() {
        val state = _uiState.value
        binder?.updateManualUltrasonicConfig(
            state.manualUltrasonicOverride,
            state.sampleRateHz.toIntOrNull() ?: 48000,
            state.startFreqHz.toDoubleOrNull() ?: 18000.0,
            state.endFreqHz.toDoubleOrNull() ?: 21000.0,
            state.chirpDurationMs.toIntOrNull() ?: 30,
            state.idleDurationMs.toIntOrNull() ?: 10,
            state.amplitude.toDoubleOrNull() ?: 0.8,
            state.windowType.ifBlank { "hann" },
            state.repeatChirp
        )
        _uiState.update { currentState ->
            currentState.copy(showDialog = true, networkMessage = "Ultrasonic parameters applied on device.")
        }
    }

    fun connect() {
        if (binder == null) {
            return
        }
        val ip = _uiState.value.ip
        val port = _uiState.value.port

        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { currentState -> currentState.copy(networkStatus = ConnectionStatus.CONNECTING) }
            var success = false
            withContext(Dispatchers.IO) {
                success = binder?.connect(ip, port.toInt()) == true
            }
            if (success) {
                applyUltrasonicConfig()
                refreshRouteDiagnostics()
                _uiState.update { currentState ->
                    currentState.copy(networkStatus = ConnectionStatus.READY, showDialog = true, networkMessage = "Connection with ${_uiState.value.ip}:${_uiState.value.port} is established.")
                }
            } else {
                _uiState.update { currentState ->
                    currentState.copy(networkStatus = ConnectionStatus.NONE, showDialog = true, networkMessage = "Failed to connect to ${_uiState.value.ip}:${_uiState.value.port}.")
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                binder?.disconnect()
            }
            _uiState.update { currentState -> currentState.copy(networkStatus = ConnectionStatus.NONE) }
        }
    }

    private val _messageListener = MessageListener { fromMe, type, shortContent ->
        viewModelScope.launch(Dispatchers.Main) {
            addMessageRecord(fromMe, type, shortContent)
        }
    }

    private val _serviceBinder: MutableLiveData<ClientBinder?> = MutableLiveData()
    private val _connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.i(TAG, "Service Connected")
            val clientBinder = service as ClientBinder
            clientBinder.setMessageListener(_messageListener)
            _serviceBinder.value = clientBinder
            applyUltrasonicConfig()
            refreshRouteDiagnostics()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _serviceBinder.value?.setMessageListener(null)
            _serviceBinder.value = null
        }
    }

    val connection: ServiceConnection
        get() = _connection

    val binder: ClientBinder?
        get() = _serviceBinder.value

    companion object {
        const val TAG = "AudioCenterViewModel"
    }
}
