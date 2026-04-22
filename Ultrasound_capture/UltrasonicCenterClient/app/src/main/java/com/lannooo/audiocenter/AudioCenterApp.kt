package com.lannooo.audiocenter

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.WifiTetheringError
import androidx.compose.material.icons.rounded.WifiTetheringOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lannooo.audiocenter.ui.theme.AudioCenterTheme
import java.util.Locale

enum class ConnectionUiState(@StringRes val desc: Int, val icon: ImageVector, val color: Color) {
    NONE(R.string.connect_status_none, Icons.Rounded.WifiTetheringOff, Color.Gray),
    CONNECTING(R.string.connect_status_connecting, Icons.Rounded.WifiFind, Color.Blue),
    READY(R.string.connect_status_ready, Icons.Rounded.WifiTethering, Color.Green),
    LOST(R.string.connect_status_lost, Icons.Rounded.WifiTetheringError, Color.Red)
}

fun enableTextFieldInput(state: ConnectionStatus): Boolean = state == ConnectionStatus.NONE || state == ConnectionStatus.LOST
fun enableConnectBtn(state: ConnectionStatus): Boolean = state == ConnectionStatus.NONE || state == ConnectionStatus.LOST
fun enableDisconnectBtn(state: ConnectionStatus): Boolean = state == ConnectionStatus.READY
fun enableSendBtn(state: ConnectionStatus): Boolean = state == ConnectionStatus.READY
fun connectionStatusToState(status: ConnectionStatus): ConnectionUiState = when (status) {
    ConnectionStatus.NONE -> ConnectionUiState.NONE
    ConnectionStatus.CONNECTING -> ConnectionUiState.CONNECTING
    ConnectionStatus.READY -> ConnectionUiState.READY
    ConnectionStatus.LOST -> ConnectionUiState.LOST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioControlTopBar(modifier: Modifier = Modifier) {
    TopAppBar(title = { Text(text = stringResource(R.string.app_name), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, modifier = modifier)
}

@Composable
fun InformationPart(records: List<MessageRecord>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(8.dp)) {
        records.forEach {
            MessageRecordItem(it.who, it.type.name.lowercase(), it.shortContent, it.timestamp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MessageRecordItem(who: Char, msgType: String, shortContent: String, timeStr: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Rounded.ChatBubble, contentDescription = null, tint = if (who == 'C') Color.Magenta else Color.Blue, modifier = Modifier.padding(4.dp).size(24.dp).align(Alignment.Top))
        Column {
            Row {
                Text(text = msgType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = timeStr, style = MaterialTheme.typography.bodySmall)
            }
            Text(text = shortContent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WelcomePart(modifier: Modifier = Modifier) {
    Text(text = stringResource(id = R.string.welcome), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun FootnotePart(modifier: Modifier = Modifier) {
    Text(text = "Developed by ${stringResource(R.string.publisher)}", fontSize = 18.sp, color = Color.DarkGray, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
fun ActionExtraPart(networkStatus: ConnectionStatus, onSendClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Button(onClick = onSendClick, modifier = Modifier.padding(8.dp).fillMaxWidth(), enabled = enableSendBtn(networkStatus)) {
            Text(text = stringResource(R.string.send))
        }
    }
}

@Composable
fun ConnectionPart(networkStatus: ConnectionStatus, onConnectClick: () -> Unit, onCloseClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        val state = connectionStatusToState(networkStatus)
        Icon(imageVector = state.icon, contentDescription = stringResource(state.desc), tint = state.color, modifier = Modifier.size(48.dp).padding(8.dp, 0.dp).align(Alignment.CenterVertically))
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onCloseClick, enabled = enableDisconnectBtn(networkStatus), modifier = Modifier.padding(8.dp)) {
            Text(text = stringResource(R.string.disconnect))
        }
        Button(onClick = onConnectClick, enabled = enableConnectBtn(networkStatus), modifier = Modifier.padding(8.dp)) {
            Text(text = stringResource(R.string.connect))
        }
    }
}

@Composable
fun InputFieldPart(ip: String, port: String, networkStatus: ConnectionStatus, onIpChange: (String) -> Unit, onPortChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        TextField(value = ip, onValueChange = onIpChange, enabled = enableTextFieldInput(networkStatus), label = { Text(stringResource(R.string.ip_address)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, modifier = Modifier.weight(2f))
        Spacer(modifier = Modifier.size(8.dp))
        TextField(value = port, onValueChange = onPortChange, enabled = enableTextFieldInput(networkStatus), label = { Text(stringResource(R.string.port)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
    }
}

@Composable
fun UltrasonicConfigPart(
    state: AudioCenterUiState,
    onOverrideChange: (Boolean) -> Unit,
    onSampleRateChange: (String) -> Unit,
    onStartFreqChange: (String) -> Unit,
    onEndFreqChange: (String) -> Unit,
    onChirpMsChange: (String) -> Unit,
    onIdleMsChange: (String) -> Unit,
    onAmplitudeChange: (String) -> Unit,
    onWindowTypeChange: (String) -> Unit,
    onRepeatChange: (Boolean) -> Unit,
    onApplyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ultrasonic FMCW", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = state.manualUltrasonicOverride, onCheckedChange = onOverrideChange)
            }
            Text("Enable local manual override for ultrasonic parameters.", style = MaterialTheme.typography.bodySmall)
            TextField(
                value = state.sampleRateHz,
                onValueChange = onSampleRateChange,
                label = { Text("Sample Rate") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.size(8.dp))
            TextField(
                value = state.amplitude,
                onValueChange = onAmplitudeChange,
                label = { Text("Amplitude (0.0 - 1.0)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row {
                TextField(value = state.startFreqHz, onValueChange = onStartFreqChange, label = { Text("Start Hz") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                TextField(value = state.endFreqHz, onValueChange = onEndFreqChange, label = { Text("End Hz") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            Row {
                TextField(value = state.chirpDurationMs, onValueChange = onChirpMsChange, label = { Text("Chirp ms") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                TextField(value = state.idleDurationMs, onValueChange = onIdleMsChange, label = { Text("Idle ms") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(value = state.windowType, onValueChange = onWindowTypeChange, label = { Text("Window") }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Repeat")
                Switch(checked = state.repeatChirp, onCheckedChange = onRepeatChange)
            }
            Button(onClick = onApplyClick, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                Text("Apply Ultrasonic Params")
            }
        }
    }
}

@Composable
fun RouteCalibrationPart(
    state: AudioCenterUiState,
    onOutputIdChange: (String) -> Unit,
    onInputIdChange: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Mate 40 Pro Route Calibration", style = MaterialTheme.typography.titleMedium)
            Text("Preset: ${state.routePresetName.ifBlank { "mate40pro_bottom_speaker_bottom_mic" }}", style = MaterialTheme.typography.bodySmall)
            Text("Device: ${state.routeDeviceSummary.ifBlank { "Unavailable" }}", style = MaterialTheme.typography.bodySmall)
            Text("Status: ${state.routeCalibrationStatus}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.size(8.dp))
            TextField(
                value = state.routeOutputDeviceId,
                onValueChange = onOutputIdChange,
                label = { Text("Bottom speaker device id") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.size(8.dp))
            TextField(
                value = state.routeInputDeviceId,
                onValueChange = onInputIdChange,
                label = { Text("Bottom microphone device id") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.size(8.dp))
            Row {
                OutlinedButton(onClick = onRefreshClick, modifier = Modifier.weight(1f)) {
                    Text("Refresh Devices")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSaveClick, modifier = Modifier.weight(1f)) {
                    Text("Save Calibration")
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text(state.routeDiagnosticsText.ifBlank { "No device diagnostics yet." }, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ConnectDialog(msg: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = onDismiss, modifier = modifier) { Text(text = stringResource(R.string.connect_alert_confirm)) } }, title = { Text(text = stringResource(R.string.connect_alert_title)) }, text = { Text(text = stringResource(R.string.connect_alert_message, msg)) }, modifier = modifier)
}

@Composable
fun AudioCenterApp(acViewModel: AudioCenterViewModel = viewModel()) {
    val acUiState by acViewModel.uiState.collectAsState()

    Scaffold(topBar = { AudioControlTopBar() }) { innerPadding ->
        Surface(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.statusBarsPadding().padding(8.dp, 0.dp).verticalScroll(rememberScrollState()).safeDrawingPadding(), verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
                WelcomePart(modifier = Modifier.fillMaxWidth())
                InformationPart(acViewModel.messageRecords, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                InputFieldPart(acUiState.ip, acUiState.port, acUiState.networkStatus, onIpChange = { acViewModel.updateIp(it) }, onPortChange = { acViewModel.updatePort(it) }, modifier = Modifier.fillMaxWidth())
                ConnectionPart(acUiState.networkStatus, onConnectClick = { acViewModel.connect() }, onCloseClick = { acViewModel.disconnect() }, modifier = Modifier.fillMaxWidth())
                UltrasonicConfigPart(
                    state = acUiState,
                    onOverrideChange = { acViewModel.updateManualUltrasonicOverride(it) },
                    onSampleRateChange = { acViewModel.updateSampleRateHz(it) },
                    onStartFreqChange = { acViewModel.updateStartFreqHz(it) },
                    onEndFreqChange = { acViewModel.updateEndFreqHz(it) },
                    onChirpMsChange = { acViewModel.updateChirpDurationMs(it) },
                    onIdleMsChange = { acViewModel.updateIdleDurationMs(it) },
                    onAmplitudeChange = { acViewModel.updateAmplitude(it) },
                    onWindowTypeChange = { acViewModel.updateWindowType(it) },
                    onRepeatChange = { acViewModel.updateRepeatChirp(it) },
                    onApplyClick = { acViewModel.applyUltrasonicConfig() },
                    modifier = Modifier.fillMaxWidth()
                )
                RouteCalibrationPart(
                    state = acUiState,
                    onOutputIdChange = { acViewModel.updateRouteOutputDeviceId(it) },
                    onInputIdChange = { acViewModel.updateRouteInputDeviceId(it) },
                    onRefreshClick = { acViewModel.refreshRouteDiagnostics() },
                    onSaveClick = { acViewModel.saveRouteCalibration() },
                    modifier = Modifier.fillMaxWidth()
                )
                ActionExtraPart(acUiState.networkStatus, onSendClick = { acViewModel.sendTestMessage() }, modifier = Modifier.fillMaxWidth())
                FootnotePart(modifier = Modifier.fillMaxWidth())
            }
            if (acUiState.showDialog) {
                ConnectDialog(msg = acUiState.networkMessage, onDismiss = { acViewModel.updateDialog(false) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "My Preview")
@Composable
fun AudioCenterAppPreview() {
    AudioCenterTheme {
        AudioCenterApp()
    }
}


